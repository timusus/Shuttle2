package com.simplecityapps.taglib

class FileScanner {

    external fun getAudioFiles(path: String): ArrayList<AudioFile>

    companion object {

        init {
            System.loadLibrary("file-scanner")
        }
    }
}