package com.simplecityapps.mediaprovider

/** The progress and error messages [MediaImporter] reports, in the user's language; each platform reads them from its own resources. */
interface MediaImportStrings {
    /** The import is asking a provider for its songs. */
    val retrievingSongs: String

    /** The import is asking a provider for its playlists. */
    val retrievingPlaylists: String

    /** The import is writing what it found to the library. */
    val updatingDatabase: String

    /** A provider's import failed. */
    val importError: String
}
