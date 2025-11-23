package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.taglib

data class DirectorySelectionViewState(
    val directories: List<DirectorySelectionContract.Directory>,
    val isTraversalComplete: Boolean
) {
    companion object {
        val Empty = DirectorySelectionViewState(
            directories = emptyList(),
            isTraversalComplete = false
        )
    }
}
