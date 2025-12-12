package com.example.simplemusicplayer.player

import com.example.simplemusicplayer.domain.model.Track

class PlaylistManager {

    private var tracks: List<Track> = emptyList()
    private var currentIndex: Int = -1

    fun setPlaylist(list: List<Track>) {
        tracks = list
        if (tracks.isEmpty()) {
            currentIndex = -1
        } else {
            if (currentIndex !in tracks.indices) currentIndex = 0
        }
    }

    fun setCurrentById(trackId: Long): Boolean {
        val idx = tracks.indexOfFirst { it.id == trackId }
        currentIndex = idx
        return idx != -1
    }

    fun getCurrent(): Track? =
        if (currentIndex in tracks.indices) tracks[currentIndex] else null

    fun hasNext(): Boolean = currentIndex in tracks.indices && currentIndex < tracks.lastIndex
    fun hasPrev(): Boolean = currentIndex in tracks.indices && currentIndex > 0

    fun next(): Track? {
        if (!hasNext()) return null
        currentIndex += 1
        return getCurrent()
    }

    fun prev(): Track? {
        if (!hasPrev()) return null
        currentIndex -= 1
        return getCurrent()
    }
}
