package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.os.Build
import com.simplecityapps.shuttle.ui.ShortcutManager
import javax.inject.Inject

class ShortcutInitializer
@Inject
constructor(
    private val shortcutManager: ShortcutManager
) : AppInitializer {

    override fun init(application: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            shortcutManager.registerCallbacks()
        }
    }
}
