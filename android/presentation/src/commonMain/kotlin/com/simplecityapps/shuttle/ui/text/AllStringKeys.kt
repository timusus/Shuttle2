package com.simplecityapps.shuttle.ui.text

/** Every [StringKey] entry of every feature enum. Kept out of StringKey.kt so the interface doesn't depend on its enums. */
val allStringKeys: List<StringKey> =
    SongInfoString.entries +
        MediaActionString.entries +
        SettingsString.entries +
        TagEditorString.entries +
        EqualizerPresetString.entries +
        HomeString.entries
