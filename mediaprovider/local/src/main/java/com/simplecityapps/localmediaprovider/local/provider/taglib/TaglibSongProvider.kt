package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.os.Environment
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongData
import com.simplecityapps.localmediaprovider.local.provider.SongProvider
import com.simplecityapps.taglib.FileScanner
import io.reactivex.Single

class TaglibSongProvider(
    private val fileScanner: FileScanner
) : SongProvider {

    override fun findSongs(): Single<List<SongData>> {
        return Single.fromCallable { fileScanner.getAudioFiles(Environment.getExternalStorageDirectory().path) }
            .map { audioFiles ->
                audioFiles
                    .map { audioFile -> audioFile.toSongData() }
            }
    }

    companion object {
        const val TAG = "LocalMediaProvider"

        init {
            System.loadLibrary("file-scanner")
        }
    }
}