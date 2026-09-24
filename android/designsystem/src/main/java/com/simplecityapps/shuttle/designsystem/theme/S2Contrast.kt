package com.simplecityapps.shuttle.designsystem.theme

import android.app.UiModeManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.Contrast

/** The contrast levels S2 generates schemes for, mapped onto MaterialKolor's [Contrast]. */
enum class S2Contrast(internal val materialKolor: Contrast) {
    Default(Contrast.Default),
    Medium(Contrast.Medium),
    High(Contrast.High),
    ;

    companion object {
        /**
         * Maps the platform contrast setting (`UiModeManager.getContrast()`, -1..1) onto a level.
         * Anything below Medium, including the platform's reduced contrast, stays at Default.
         */
        fun fromSystemContrast(contrast: Float): S2Contrast = when {
            contrast >= High.materialKolor.value.toFloat() -> High
            contrast >= Medium.materialKolor.value.toFloat() -> Medium
            else -> Default
        }
    }
}

/** The system contrast setting on API 34+, [S2Contrast.Default] below it. */
@Composable
fun rememberSystemContrast(): S2Contrast {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(UiModeManager::class.java)
                ?.let { S2Contrast.fromSystemContrast(it.contrast) }
                ?: S2Contrast.Default
        } else {
            S2Contrast.Default
        }
    }
}
