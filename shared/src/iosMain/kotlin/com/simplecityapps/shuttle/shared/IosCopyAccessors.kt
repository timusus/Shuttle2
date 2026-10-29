package com.simplecityapps.shuttle.shared

import com.simplecityapps.shuttle.ui.actions.ClearPrimaryCopy
import com.simplecityapps.shuttle.ui.actions.ObserveCopyContext
import com.simplecityapps.shuttle.ui.actions.SetAlbumPrimarySource
import com.simplecityapps.shuttle.ui.actions.SetPrimaryCopy

/** Copies of one song across sources: what decides which plays, and the overrides; part of [IosAppGraph]. */
interface IosCopyAccessors {
    /** The merge setting, the user's primaries and what is playable now, for the copy menus and Song Info. */
    val observeCopyContext: ObserveCopyContext
    val setPrimaryCopy: SetPrimaryCopy
    val clearPrimaryCopy: ClearPrimaryCopy
    val setAlbumPrimarySource: SetAlbumPrimarySource
}
