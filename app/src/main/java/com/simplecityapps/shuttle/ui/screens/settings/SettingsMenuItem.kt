package com.simplecityapps.shuttle.ui.screens.settings;

import com.simplecityapps.shuttle.R

enum class SettingsMenuItem {

    Home, Library, Folders, SleepTimer;

    val icon: Int
        get() {
            return when (this) {
                Home -> R.drawable.ic_home_black_24dp
                Library -> R.drawable.ic_library_music_black_24dp
                Folders -> R.drawable.ic_folder_open_black_24dp
                SleepTimer -> R.drawable.ic_sleep_24dp
            }
        }

    val title: String
        get() {
            return when (this) {
                Home -> "Home"
                Library -> "Library"
                Folders -> "Folders"
                SleepTimer -> "Sleep Timer"
            }
        }

    val navDestination: Int?
        get() {
            return when (this) {
                Home -> R.id.homeFragment
                Library -> R.id.libraryFragment
                Folders -> R.id.folderFragment
                else -> null
            }
        }
}