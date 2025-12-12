package com.example.simplemusicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room用エンティティ。
 * ローカルに保存される「マイリスト」の1曲分を表す。
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val title: String,

    /**
     * ストレージ上のファイルURIを文字列で保持
     * 例: content:// 〜
     */
    val uri: String,

    /**
     * 追加日時（ミリ秒タイムスタンプ）
     */
    val addedAt: Long
)
