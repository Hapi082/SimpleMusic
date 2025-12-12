package com.example.simplemusicplayer.domain.model

/**
 * アプリ内で扱う論理的な「曲」データ。
 * UI層から直接 Room の Entity に依存しないようにするためのモデル。
 */
data class Track(
    val id: Long,
    val title: String,
    val uri: String,
    val addedAt: Long
)
