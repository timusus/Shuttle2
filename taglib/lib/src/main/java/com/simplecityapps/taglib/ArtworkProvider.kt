package com.simplecityapps.taglib

class ArtworkProvider {

    external fun getArtwork(fd: Int): ByteArray?

    companion object {
        init {
            System.loadLibrary("artwork-provider")
        }
    }
}