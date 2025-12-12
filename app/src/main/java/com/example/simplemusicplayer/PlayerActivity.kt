package com.example.simplemusicplayer

import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.simplemusicplayer.player.PlayerController

class PlayerActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var nowPlayingTitle: TextView

    private lateinit var seekBarProgress: SeekBar
    private lateinit var elapsedTimeText: TextView
    private lateinit var totalTimeText: TextView

    private lateinit var prevButton: ImageButton
    private lateinit var playPauseButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var repeatButton: ImageButton

    private lateinit var volumeLabel: TextView
    private lateinit var volumeMinIcon: ImageView
    private lateinit var volumeMaxIcon: ImageView
    private lateinit var volumeSeekBar: SeekBar

    private lateinit var playerController: PlayerController
    private lateinit var audioManager: AudioManager

    private var isPlaying: Boolean = false
    private var isRepeatOn: Boolean = false

    // MainActivity から受け取る情報
    private var trackId: Long = -1L
    private var trackTitle: String = ""
    private var trackUri: String = ""

    // シークバー更新用
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            if (playerController.isPrepared()) {
                val pos = playerController.getCurrentPosition()
                seekBarProgress.progress = pos
                elapsedTimeText.text = formatMillis(pos)
            }
            if (playerController.isPlaying()) {
                progressHandler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // View を取得
        backButton = findViewById(R.id.btn_back)
        titleText = findViewById(R.id.tv_current_track_title)
        nowPlayingTitle = findViewById(R.id.tv_now_playing_title)

        seekBarProgress = findViewById(R.id.seekbar_progress)
        elapsedTimeText = findViewById(R.id.tv_elapsed_time)
        totalTimeText = findViewById(R.id.tv_total_time)

        prevButton = findViewById(R.id.btn_prev)
        playPauseButton = findViewById(R.id.btn_play_pause)
        nextButton = findViewById(R.id.btn_next)
        repeatButton = findViewById(R.id.btn_repeat)

        volumeLabel = findViewById(R.id.tv_volume_label)
        volumeMinIcon = findViewById(R.id.iv_volume_min)
        volumeMaxIcon = findViewById(R.id.iv_volume_max)
        volumeSeekBar = findViewById(R.id.seekbar_volume)

        // Intent から曲情報を受け取る
        trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L)
        trackTitle = intent.getStringExtra(EXTRA_TRACK_TITLE) ?: ""
        trackUri = intent.getStringExtra(EXTRA_TRACK_URI) ?: ""

        // 曲名を表示
        titleText.text = trackTitle
        elapsedTimeText.text = "00:00"
        totalTimeText.text = "--:--"

        // PlayerController 初期化
        playerController = PlayerController(applicationContext)
        playerController.setOnCompletionListener {
            // 再生完了時の処理（とりあえず停止＋先頭戻し）
            runOnUiThread {
                isPlaying = false
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                seekBarProgress.progress = 0
                elapsedTimeText.text = "00:00"
                stopProgressUpdater()
                Log.d("PlayerActivity", "再生完了: $trackTitle")
            }
        }

        // 音量（システムの STREAM_MUSIC と連動）
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = currentVolume
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        progress,
                        0
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // トラックを事前にprepare（再生はまだ開始しない）
        preparePlayer()

        // 戻るボタン
        backButton.setOnClickListener {
            finish()
        }

        // 再生/一時停止ボタン
        playPauseButton.setOnClickListener {
            if (!playerController.isPrepared()) {
                preparePlayer {
                    togglePlayPause()
                }
            } else {
                togglePlayPause()
            }
        }

        // 前/次ボタン（今はログのみ）
        prevButton.setOnClickListener {
            Log.d("PlayerActivity", "前の曲へ（未実装）")
        }
        nextButton.setOnClickListener {
            Log.d("PlayerActivity", "次の曲へ（未実装）")
        }

        // リピートボタン（ON/OFF だけUIで表現）
        repeatButton.setOnClickListener {
            isRepeatOn = !isRepeatOn
            repeatButton.alpha = if (isRepeatOn) 1.0f else 0.5f
            Log.d("PlayerActivity", "リピート: $isRepeatOn")
        }

        // シークバー操作（ユーザーが触ったときに位置を移動）
        seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            private var userSeeking = false

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    elapsedTimeText.text = formatMillis(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
                stopProgressUpdater()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                val pos = seekBar?.progress ?: 0
                playerController.seekTo(pos)
                if (playerController.isPlaying()) {
                    startProgressUpdater()
                }
            }
        })
    }

    /** トラックを準備し、durationなどをUIに反映 */
    private fun preparePlayer(onPrepared: (() -> Unit)? = null) {
        if (trackUri.isBlank()) return

        playerController.prepare(trackUri) {
            val duration = playerController.getDuration()
            if (duration > 0) {
                seekBarProgress.max = duration
                totalTimeText.text = formatMillis(duration)
            } else {
                totalTimeText.text = "--:--"
            }
            onPrepared?.invoke()
        }
    }

    /** 再生/一時停止のトグル */
    private fun togglePlayPause() {
        val nowPlaying = playerController.togglePlayPause()
        isPlaying = nowPlaying
        if (nowPlaying) {
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            startProgressUpdater()
            Log.d("PlayerActivity", "再生開始: $trackTitle")
        } else {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            stopProgressUpdater()
            Log.d("PlayerActivity", "一時停止: $trackTitle")
        }
    }

    private fun startProgressUpdater() {
        progressHandler.removeCallbacks(progressUpdater)
        progressHandler.post(progressUpdater)
    }

    private fun stopProgressUpdater() {
        progressHandler.removeCallbacks(progressUpdater)
    }

    private fun formatMillis(ms: Int): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdater()
        playerController.release()
    }

    companion object {
        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_TRACK_URI = "extra_track_uri"
    }
}
