package com.simplecityapps.shuttle.ui.common

import android.view.Menu
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType

object TagEditorMenuSanitiser {

    /**
     * Hides 'edit tags' item from a menu, based on whether the media provider supports tag editing
     */
    fun sanitise(menu: Menu, mediaProviders: List<MediaProviderType>) {
        if (mediaProviders.any { mediaProvider -> !mediaProvider.supportsTagEditing }) {
            menu.findItem(R.id.editTags)?.isVisible = false
        }
    }
}
