package com.simplecityapps.taglib

class ArtworkProvider {

    external fun getArtwork(path: String): ByteArray

    companion object {
        init {
            System.loadLibrary("artwork-provider")
        }
    }
}