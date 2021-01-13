package au.com.simplecityapps.shuttle.imageloading.coil.fetcher

import coil.bitmap.BitmapPool
import coil.decode.Options
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.size.Size

class MultiFetcher<T : Any>(private val delegates: Set<Fetcher<T>>) : Fetcher<T> {

    override fun handles(data: T): Boolean {
        return delegates.any { it.handles(data) }
    }

    override fun key(data: T): String? {
        return delegates.first { it.handles(data) }.key(data)
    }

    override suspend fun fetch(pool: BitmapPool, data: T, size: Size, options: Options): FetchResult {
        for (delegate in delegates.filter { it.handles(data) }) {
            try {
                return delegate.fetch(pool, data, size, options)
            } catch (e: Exception) {
                // Nothing to do
            }
        }
        throw IllegalStateException("Failed to retrieve image")
    }
}