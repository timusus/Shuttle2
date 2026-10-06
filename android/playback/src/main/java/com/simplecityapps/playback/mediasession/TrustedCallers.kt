package com.simplecityapps.playback.mediasession

import androidx.media3.session.MediaSession.ControllerInfo

/**
 * Which controllers may browse the library and edit the queue (see [SessionCallback]): any Media3 trusts (the system,
 * S2 itself, a holder of MEDIA_CONTENT_CONTROL or an enabled notification listener), and the Google apps that browse
 * media for the user but aren't always system apps: Android Auto, Wear OS and Google Assistant.
 *
 * The known apps are matched by package name, not signature: one only a missing Google app's impostor could take, and
 * all it gets is the library's listing.
 */
object TrustedCallers {
    /** Android Auto, the desktop head unit and Android Automotive (see [CarAccess.CAR_PACKAGES]), Wear OS and Google Assistant's. */
    val KNOWN_PACKAGES: Set<String> =
        CarAccess.CAR_PACKAGES +
            setOf(
                "com.google.android.wearable.app",
                "com.google.android.googlequicksearchbox",
                "com.google.android.carassistant"
            )

    fun isTrusted(controller: ControllerInfo): Boolean = controller.isTrusted || controller.packageName in KNOWN_PACKAGES
}
