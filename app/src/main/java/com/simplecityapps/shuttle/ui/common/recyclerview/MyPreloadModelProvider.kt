package com.simplecityapps.shuttle.ui.common.recyclerview

import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.bumptech.glide.ListPreloader.PreloadModelProvider
import com.bumptech.glide.RequestBuilder

class MyPreloadModelProvider<T>(
    val imageLoader: GlideImageLoader,
    val options: List<ArtworkImageLoader.Options>
) : PreloadModelProvider<T> {

    var items: List<T> = emptyList()

    override fun getPreloadItems(position: Int): List<T> {
        if (items.isEmpty() || position > items.size - 1) {
            return emptyList()
        }
        return listOf(items[position])
    }

    override fun getPreloadRequestBuilder(item: T): RequestBuilder<*> {
        return imageLoader.getRequestBuilder(options).load(item)
    }
}