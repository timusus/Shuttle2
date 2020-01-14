package com.simplecityapps.mediaprovider.repository

interface SongRepositoryProvider {
    fun provideSongRepository(): SongRepository
}