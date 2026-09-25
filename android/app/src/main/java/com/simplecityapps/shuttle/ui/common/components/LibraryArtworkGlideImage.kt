package com.simplecityapps.shuttle.ui.common.components

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt

/** The artwork thumbnail shared by the album, album artist and song list rows. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LibraryArtworkGlideImage(
    model: Any?,
    placeholderDrawableResId: Int,
    modifier: Modifier = Modifier,
    artworkPreloadRequestBuilder: RequestBuilder<Drawable>? = null,
) {
    GlideImage(
        model = model,
        contentDescription = stringResource(R.string.artwork),
        modifier = modifier,
        loading = placeholder(placeholderDrawableResId),
    ) {
        // If this request finishes before than the one from the thumbnail,
        // the result of the thumbnail one won't replace it. So, we need to
        // repeat all options again here.
        // TODO: Find a way to copy options from artworkPreloadRequestBuilder
        //  to `it`. Maybe wait for the Compose API to stabilize first.
        val builder = it
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(CenterCrop(), RoundedCorners(8.dpToInt))
            .transition(withCrossFade(200))
        if (artworkPreloadRequestBuilder != null) {
            builder.thumbnail(artworkPreloadRequestBuilder)
        } else {
            builder
        }
    }
}
