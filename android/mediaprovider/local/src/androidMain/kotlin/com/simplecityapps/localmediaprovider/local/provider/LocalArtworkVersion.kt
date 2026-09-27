package com.simplecityapps.localmediaprovider.local.provider

import java.io.File

/** An image file near a song, which the image loader may use as its folder or artist art. */
data class FolderImage(
    val name: String,
    val lastModified: Long,
    val size: Long
)

/**
 * The artwork version for a local song. Retagging embedded art changes the audio file's modified time, and
 * replacing folder or artist art changes one of the images in the song's folder or the folder above it, which
 * is where the image loader looks for them.
 */
fun localArtworkVersion(
    audioLastModified: Long,
    folderImages: Collection<FolderImage>
): String {
    if (folderImages.isEmpty()) {
        return audioLastModified.toString()
    }
    val images = folderImages.map { image -> "${image.name}:${image.lastModified}:${image.size}" }.sorted().joinToString("|")
    return "$audioLastModified-${Integer.toHexString(images.hashCode())}"
}

/**
 * Lists the images in a song's folder and the folder above it, reading each folder once per scan.
 *
 * Where shared storage listings leave images out (Android 13+, where the app holds only READ_MEDIA_AUDIO), the image
 * loader gets folder art from MediaStore's audio thumbnail instead, and the song's folder itself stands in for its images:
 * the folder's modified time changes whenever an image is added to, removed from or renamed in it.
 */
class FolderImageReader(private val sharedStorageListsImages: Boolean) {
    private val imagesByFolder = HashMap<String, List<FolderImage>>()

    fun imagesNear(songPath: String): List<FolderImage> {
        val folder = File(songPath).parentFile ?: return emptyList()
        if (!sharedStorageListsImages) {
            return imagesByFolder.getOrPut(folder.path) { listOf(FolderImage("${folder.name}/", folder.lastModified(), 0)) }
        }
        return images(folder) + folder.parentFile?.let { images(it) }.orEmpty()
    }

    private fun images(folder: File): List<FolderImage> = imagesByFolder.getOrPut(folder.path) {
        folder.listFiles()
            ?.filter { file -> file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.map { file -> FolderImage(file.name, file.lastModified(), file.length()) }
            .orEmpty()
    }

    companion object {
        // The formats the image loader's folder and artist art patterns accept
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
