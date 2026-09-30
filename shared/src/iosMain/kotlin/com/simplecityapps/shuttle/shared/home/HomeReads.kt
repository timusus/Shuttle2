package com.simplecityapps.shuttle.shared.home

import com.simplecityapps.shuttle.ui.screens.home.HomeItemProgress
import com.simplecityapps.shuttle.ui.text.resolve

/** How far into a Jump back in item its queue was left ("Track 5 of 12"), from the app's Localizable table. */
fun HomeItemProgress.localized(): String = text.resolve()
