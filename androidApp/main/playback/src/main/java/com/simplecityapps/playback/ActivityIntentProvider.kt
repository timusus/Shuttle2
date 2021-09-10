package com.simplecityapps.playback

import android.content.Intent

interface ActivityIntentProvider {
    fun provideMainActivityIntent(): Intent
}