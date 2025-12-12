package com.example.simplemusicplayer.data.repository

import com.example.simplemusicplayer.data.db.AppDatabase
import com.example.simplemusicplayer.data.db.TrackEntity
import com.example.simplemusicplayer.domain.model.Track

class TrackRepositoryImpl(
    private val db: AppDatabase
) : TrackRepository {

    private val trackDao = db.trackDao()

    override suspend fun getAllTracks(): List<Track> {
        return trackDao.getAllTracks().map { it.toDomain() }
    }

    override suspend fun addTrack(track: Track) {
        trackDao.insertTrack(track.toEntity())
    }

    override suspend fun deleteTrack(id: Long) {
        val entity = trackDao.getTrackById(id) ?: return
        trackDao.deleteTrack(entity)
    }

    override suspend fun getTrackById(id: Long): Track? {
        return trackDao.getTrackById(id)?.toDomain()
    }

    // Entity -> Domain
    private fun TrackEntity.toDomain(): Track =
        Track(
            id = id,
            title = title,
            uri = uri,
            addedAt = addedAt
        )

    // Domain -> Entity
    private fun Track.toEntity(): TrackEntity =
        TrackEntity(
            id = id,
            title = title,
            uri = uri,
            addedAt = addedAt
        )
}
