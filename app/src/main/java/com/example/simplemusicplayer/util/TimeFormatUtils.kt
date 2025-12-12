package com.example.simplemusicplayer.util

/**
 * ミリ秒 → "mm:ss" 形式への変換ユーティリティ。
 */
object TimeFormatUtils {

    fun formatMillis(ms: Int): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }
}
