package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.simplecityapps.shuttle.ui.promo.PodcastsPromo
import dev.zacsweers.metro.Inject

class PodcastsPromoInitializer
@Inject
constructor(
    private val promo: PodcastsPromo
) : AppInitializer {
    override fun init(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = promo.recordOpen()
            }
        )
    }
}
