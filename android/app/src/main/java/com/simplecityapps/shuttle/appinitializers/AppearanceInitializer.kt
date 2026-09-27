package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.settings.SettleDynamicColourDefault
import dev.zacsweers.metro.Inject

class AppearanceInitializer
@Inject
constructor(
    private val settleDynamicColourDefault: SettleDynamicColourDefault
) : AppInitializer {
    override fun init(application: Application) {
        settleDynamicColourDefault()
    }
}
