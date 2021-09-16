package com.simplecityapps.shuttle.compose.ui.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class ThemedPreviewProvider : PreviewParameterProvider<Boolean> {

    override val values: Sequence<Boolean>
        get() = sequenceOf(false, true)

}