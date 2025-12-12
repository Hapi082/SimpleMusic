package com.example.simplemusicplayer.data.repository

import com.example.simplemusicplayer.domain.model.Track

/**
 * UI層 / ドメイン層から利用する「マイリスト」操作用インターフェース。
 * 実装は Step6 で TrackRepositoryImpl として追加する。
 */
interface TrackRepository {

    /**
     * マイリスト内の全Trackを取得する。
     */
    suspend fun getAllTracks(): List<Track>

    /**
     * Trackを追加する。
     */
    suspend fun addTrack(track: Track)

    /**
     * 指定IDのTrackを削除する。
     */
    suspend fun deleteTrack(id: Long)

    /**
     * 指定IDのTrackを1件取得する（存在しない場合はnull）。
     */
    suspend fun getTrackById(id: Long): Track?
}
