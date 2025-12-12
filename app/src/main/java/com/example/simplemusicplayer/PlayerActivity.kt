package com.example.simplemusicplayer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.simplemusicplayer.player.MusicPlaybackService

class PlayerActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView

    private lateinit var seekBar: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var total: TextView

    private lateinit var prev: ImageButton
    private lateinit var playPause: ImageButton
    private lateinit var next: ImageButton
    private lateinit var repeat: ImageButton

    private lateinit var volumeSeekBar: SeekBar

    private var trackId: Long = -1L

    private var service: MusicPlaybackService? = null
    private var bound = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiTicker = object : Runnable {
        override fun run() {
            val s = service
            if (s != null && bound) {
                val dur = s.getDuration()
                val pos = s.getCurrentPosition()
                if (dur > 0) {
                    seekBar.max = dur
                    total.text = formatMillis(dur)
                    seekBar.progress = pos.coerceAtMost(dur)
                    elapsed.text = formatMillis(pos)
                }
                // タイトル更新（曲送り時）
                val cur = s.getCurrentTrack()
                titleText.text = cur?.title ?: titleText.text

                playPause.setImageResource(
                    if (s.isPlaying()) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as MusicPlaybackService.LocalBinder
            service = b.getService()
            bound = true

            // 現在曲をセットして prepare
            service?.setCurrentById(trackId)
            service?.prepareCurrent(autoPlay = false)

            uiHandler.post(uiTicker)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        backButton = findViewById(R.id.btn_back)
        titleText = findViewById(R.id.tv_current_track_title)

        seekBar = findViewById(R.id.seekbar_progress)
        elapsed = findViewById(R.id.tv_elapsed_time)
        total = findViewById(R.id.tv_total_time)

        prev = findViewById(R.id.btn_prev)
        playPause = findViewById(R.id.btn_play_pause)
        next = findViewById(R.id.btn_next)
        repeat = findViewById(R.id.btn_repeat)

        volumeSeekBar = findViewById(R.id.seekbar_volume)

        trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L)
        titleText.text = intent.getStringExtra(EXTRA_TRACK_TITLE) ?: "Track"

        backButton.setOnClickListener { finish() }

        playPause.setOnClickListener {
            // サービスへ委譲
            service?.togglePlayPause()
        }
        prev.setOnClickListener { service?.playPrev(inheritPlaying = true) }
        next.setOnClickListener { service?.playNext(inheritPlaying = true) }

        repeat.setOnClickListener {
            val s = service ?: return@setOnClickListener
            val on = !s.isRepeatOne()
            s.setRepeatOne(on)
            repeat.alpha = if (on) 1.0f else 0.5f
        }

        repeat.alpha = 0.5f

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) elapsed.text = formatMillis(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                service?.seekTo(sb?.progress ?: 0)
            }
        })

        // 音量は Step=12 でやった “システム音量連動” を残したい場合、
        // そのまま PlayerActivity に AudioManager を持たせて連動してください（ここでは省略）。
    }

    override fun onStart() {
        super.onStart()
        // サービスを起動してからバインド（バックグラウンド再生の土台）
        val i = Intent(this, MusicPlaybackService::class.java)
        startService(i)
        bindService(i, conn, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        uiHandler.removeCallbacks(uiTicker)
        if (bound) {
            unbindService(conn)
            bound = false
        }
    }

    private fun formatMillis(ms: Int): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    companion object {
        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_TRACK_TITLE = "extra_track_title"
        const val EXTRA_TRACK_URI = "extra_track_uri"
    }
}
