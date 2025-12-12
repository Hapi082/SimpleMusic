package com.example.simplemusicplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.room.Room
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.simplemusicplayer.MainActivity
import com.example.simplemusicplayer.R
import com.example.simplemusicplayer.data.db.AppDatabase
import com.example.simplemusicplayer.data.repository.TrackRepository
import com.example.simplemusicplayer.data.repository.TrackRepositoryImpl
import com.example.simplemusicplayer.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIF_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.example.simplemusicplayer.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.simplemusicplayer.action.NEXT"
        const val ACTION_PREV = "com.example.simplemusicplayer.action.PREV"
        const val ACTION_STOP = "com.example.simplemusicplayer.action.STOP"

        const val EXTRA_TRACK_ID = "extra_track_id"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var repository: TrackRepository
    private val playlistManager = PlaylistManager()
    private val controller by lazy { PlayerController(applicationContext) }

    private lateinit var mediaSession: MediaSessionCompat

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var shouldResumeAfterFocusGain = false

    private var repeatOne: Boolean = false

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // MediaSession（ロック画面/イヤホン操作の中核）
        mediaSession = MediaSessionCompat(this, "MusicPlaybackService").apply {
            isActive = true

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onSkipToNext() = playNext(inheritPlaying = true)
                override fun onSkipToPrevious() = playPrev(inheritPlaying = true)
                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                    updateMediaSession()
                    updateNotification()
                }

                override fun onStop() {
                    pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
        }

        createNotificationChannel()

        // Room / Repository
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "simple_music_player.db"
        ).build()
        repository = TrackRepositoryImpl(db)

        // DBからプレイリスト読み込み
        scope.launch {
            val tracks = withContext(Dispatchers.IO) { repository.getAllTracks() }
            playlistManager.setPlaylist(tracks)
            // 初期通知/セッション反映
            updateMediaSession()
            updateNotification()
        }

        controller.setOnCompletionListener {
            // 曲終了時
            if (repeatOne) {
                controller.seekTo(0)
                controller.play()
                startForegroundCompat()
                updateMediaSession()
                updateNotification()
            } else {
                if (playlistManager.hasNext()) {
                    playNext(inheritPlaying = true)
                } else {
                    pause()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    updateMediaSession()
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ★ イヤホン/ロック画面の media button を受け取る
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext(inheritPlaying = true)
            ACTION_PREV -> playPrev(inheritPlaying = true)
            ACTION_STOP -> {
                pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // （任意）特定IDで再生開始したい場合
                val trackId = intent?.getLongExtra(EXTRA_TRACK_ID, -1L) ?: -1L
                if (trackId != -1L) {
                    scope.launch {
                        ensurePlaylistLoaded()
                        val ok = playlistManager.setCurrentById(trackId)
                        val current = playlistManager.getCurrent()
                        if (ok && current != null) {
                            prepare(current, autoPlay = true)
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    // ====== Activity から呼ぶ API ======
    suspend fun ensurePlaylistLoaded() {
        if (playlistManager.getCurrent() != null) return
        val tracks = withContext(Dispatchers.IO) { repository.getAllTracks() }
        playlistManager.setPlaylist(tracks)
    }

    fun setRepeatOne(on: Boolean) {
        repeatOne = on
        updateMediaSession()
        updateNotification()
    }

    fun isRepeatOne(): Boolean = repeatOne

    fun getCurrentTrack(): Track? = playlistManager.getCurrent()

    fun isPlaying(): Boolean = controller.isPlaying()

    fun getDuration(): Int = controller.getDuration()

    fun getCurrentPosition(): Int = controller.getCurrentPosition()

    fun seekTo(ms: Int) {
        controller.seekTo(ms)
        updateMediaSession()
        updateNotification()
    }

    fun setCurrentById(trackId: Long) {
        playlistManager.setCurrentById(trackId)
        updateMediaSession()
        updateNotification()
    }

    fun refreshPlaylistAsync() {
        scope.launch {
            val tracks = withContext(Dispatchers.IO) { repository.getAllTracks() }
            playlistManager.setPlaylist(tracks)
            updateMediaSession()
            updateNotification()
        }
    }

    fun prepareCurrent(autoPlay: Boolean) {
        val cur = playlistManager.getCurrent() ?: return
        prepare(cur, autoPlay)
    }

    fun togglePlayPause() {
        if (!controller.isPrepared()) {
            val cur = playlistManager.getCurrent() ?: return
            prepare(cur, autoPlay = true)
            return
        }

        val playing = controller.togglePlayPause()
        if (playing) {
            startForegroundCompat()
        } else {
            // 一時停止でも通知は残す
            stopForeground(STOP_FOREGROUND_DETACH)
            abandonAudioFocus()
        }

        updateMediaSession()
        updateNotification()
    }

    fun play() {
        if (!controller.isPrepared()) {
            val cur = playlistManager.getCurrent() ?: return
            prepare(cur, autoPlay = true)
            return
        }
        if (requestAudioFocus()) {
            controller.play()
            startForegroundCompat()
            updateMediaSession()
            updateNotification()
        }
    }

    fun pause() {
        controller.pause()
        stopForeground(STOP_FOREGROUND_DETACH)
        abandonAudioFocus()
        updateMediaSession()
        updateNotification()
    }

    fun playNext(inheritPlaying: Boolean) {
        val next = playlistManager.next() ?: return
        prepare(next, autoPlay = inheritPlaying)
    }

    fun playPrev(inheritPlaying: Boolean) {
        val prev = playlistManager.prev() ?: return
        prepare(prev, autoPlay = inheritPlaying)
    }

    // ====== 内部 ======
    private fun prepare(track: Track, autoPlay: Boolean) {
        controller.prepare(track.uri) {
            if (autoPlay) {
                if (requestAudioFocus()) {
                    controller.play()
                    startForegroundCompat()
                }
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            updateMediaSession()
            updateNotification()
        }
    }

    private fun startForegroundCompat() {
        startForeground(NOTIF_ID, buildNotification())
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val currentTitle = playlistManager.getCurrent()?.title ?: "No Track"
        val isPlaying = controller.isPlaying()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )
        val nextIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )
        val prevIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )
        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(currentTitle)
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Prev",
                    prevIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPauseIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopIntent
                )
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateMediaSession() {
        val current = playlistManager.getCurrent()
        val isPlaying = controller.isPlaying()
        val duration = controller.getDuration().toLong().coerceAtLeast(0L)
        val position = controller.getCurrentPosition().toLong().coerceAtLeast(0L)

        // 曲情報（最低限：タイトルと長さ）
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, current?.title ?: "No Track")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build()
        mediaSession.setMetadata(metadata)

        // 再生状態（ロック画面やイヤホン操作のために重要）
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                position,
                1.0f
            )
            .build()

        mediaSession.setPlaybackState(state)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Music Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(ch)
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    // ===== Audio Focus =====
    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            shouldResumeAfterFocusGain = false
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            shouldResumeAfterFocusGain = controller.isPlaying()
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (shouldResumeAfterFocusGain) {
                                shouldResumeAfterFocusGain = false
                                play()
                            }
                        }
                    }
                }
                .build()

            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            shouldResumeAfterFocusGain = false
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            shouldResumeAfterFocusGain = controller.isPlaying()
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (shouldResumeAfterFocusGain) {
                                shouldResumeAfterFocusGain = false
                                play()
                            }
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            // 旧APIの abandon は簡略化（必要なら実装）
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.release()
        mediaSession.release()
    }
}
