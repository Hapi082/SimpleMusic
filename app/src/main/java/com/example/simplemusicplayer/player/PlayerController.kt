package com.example.simplemusicplayer.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/**
 * 単一トラックを再生するためのシンプルなコントローラ。
 * - prepare(uri) で非同期準備
 * - togglePlayPause() で再生/一時停止
 * - seekTo(), getDuration(), getCurrentPosition() などを提供
 */
class PlayerController(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: Uri? = null
    private var prepared: Boolean = false
    private var onCompletion: (() -> Unit)? = null

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletion = listener
        mediaPlayer?.setOnCompletionListener { onCompletion?.invoke() }
    }

    /**
     * トラックを準備する（非同期）。
     * すでに同じURIで準備済みの場合は即座に onPrepared を呼ぶ。
     */
    fun prepare(uriString: String, onPrepared: (() -> Unit)? = null) {
        val uri = Uri.parse(uriString)

        if (prepared && uri == currentUri && mediaPlayer != null) {
            onPrepared?.invoke()
            return
        }

        release()

        currentUri = uri
        val mp = MediaPlayer()
        mediaPlayer = mp
        prepared = false

        mp.setDataSource(context, uri)
        mp.setOnPreparedListener {
            prepared = true
            onPrepared?.invoke()
        }
        mp.setOnCompletionListener {
            onCompletion?.invoke()
        }
        mp.prepareAsync()
    }

    fun isPrepared(): Boolean = prepared

    fun play() {
        if (prepared) {
            mediaPlayer?.start()
        }
    }

    fun pause() {
        if (prepared) {
            mediaPlayer?.pause()
        }
    }

    /**
     * 再生/一時停止をトグルし、
     * 再生中なら true, 停止状態なら false を返す。
     */
    fun togglePlayPause(): Boolean {
        val mp = mediaPlayer ?: return false
        if (!prepared) return false
        return if (mp.isPlaying) {
            mp.pause()
            false
        } else {
            mp.start()
            true
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun getDuration(): Int = if (prepared) mediaPlayer?.duration ?: 0 else 0

    fun getCurrentPosition(): Int = if (prepared) mediaPlayer?.currentPosition ?: 0 else 0

    fun seekTo(positionMs: Int) {
        if (prepared) {
            mediaPlayer?.seekTo(positionMs)
        }
    }

    fun setVolume(left: Float, right: Float) {
        mediaPlayer?.setVolume(left, right)
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentUri = null
        prepared = false
    }
}
