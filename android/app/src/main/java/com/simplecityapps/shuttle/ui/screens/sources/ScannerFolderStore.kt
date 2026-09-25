package com.simplecityapps.shuttle.ui.screens.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.simplecityapps.localmediaprovider.local.provider.taglib.FolderFilter
import com.simplecityapps.localmediaprovider.local.provider.taglib.ScannerFolders
import com.simplecityapps.localmediaprovider.local.provider.taglib.externalStorageTreeFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** How the S2 scanner treats a folder picked in Sources. */
enum class FolderKind {
    /** Only these folders are scanned, when there are any. */
    Include,

    /** Never scanned, even inside an included folder. */
    Exclude,

    /** Read directly, for folders MediaStore skips (`.nomedia`) or formats it doesn't index (#415). */
    Extra
}

/** A folder in Sources: [uri] is its SAF tree (null for an exclude, which keeps only the path), [path] its file path when known. */
data class SourceFolder(val uri: String?, val path: String?, val name: String)

data class FolderLists(
    val includes: List<SourceFolder> = emptyList(),
    val excludes: List<SourceFolder> = emptyList(),
    val extras: List<SourceFolder> = emptyList()
)

/** The folders picked in Sources, which the S2 scanner reads at the start of each import. */
interface ScannerFolderStore {
    val folders: StateFlow<FolderLists>

    /** Adds the tree the folder picker returned; false if the folder can't be used that way (an exclude needs a file path). */
    fun add(kind: FolderKind, treeUri: String): Boolean

    fun remove(kind: FolderKind, folder: SourceFolder)

    /** Reads the folders again, for grants changed outside the app. */
    fun refresh()
}

/**
 * Includes and extras are persisted SAF grants; the extras are also listed in [SourcesSettings.extraFolders], so every
 * other grant is an include. That keeps the folders picked before #379 (#207) as includes without a migration.
 * Excludes are plain paths, since nothing needs to read them.
 */
@Singleton
class SafScannerFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SourcesSettings
) : ScannerFolderStore {
    private val _folders = MutableStateFlow(load())
    override val folders: StateFlow<FolderLists> = _folders.asStateFlow()

    /** What [com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider] scans. */
    fun scannerFolders(): ScannerFolders {
        val lists = load()
        return ScannerFolders(
            filter = FolderFilter(includes = lists.includes.mapNotNull { it.path }, excludes = lists.excludes.mapNotNull { it.path }),
            extraTrees = lists.extras.mapNotNull { folder -> folder.uri?.let(Uri::parse) }
        )
    }

    override fun add(kind: FolderKind, treeUri: String): Boolean {
        val uri = Uri.parse(treeUri)
        when (kind) {
            FolderKind.Include, FolderKind.Extra -> {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: SecurityException) {
                    Timber.e(e, "Couldn't keep access to $treeUri")
                    return false
                }
                if (kind == FolderKind.Extra) settings.extraFolders.value = (settings.extraFolders.value + treeUri).distinct()
            }

            FolderKind.Exclude -> {
                val path = uri.folderPath() ?: return false
                settings.excludedFolders.value = (settings.excludedFolders.value + path).distinct()
            }
        }
        refresh()
        return true
    }

    override fun remove(kind: FolderKind, folder: SourceFolder) {
        when (kind) {
            FolderKind.Include, FolderKind.Extra -> folder.uri?.let { treeUri ->
                runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    .onFailure { Timber.w(it, "Couldn't release $treeUri") }
                settings.extraFolders.value = settings.extraFolders.value - treeUri
            }

            FolderKind.Exclude -> settings.excludedFolders.value = settings.excludedFolders.value - folder.path.orEmpty()
        }
        refresh()
    }

    override fun refresh() {
        _folders.value = load()
    }

    private fun load(): FolderLists {
        val grants = context.contentResolver.persistedUriPermissions
            .filter { permission -> permission.isReadPermission || permission.isWritePermission }
            .map { permission -> permission.uri.toString() }
        val extras = settings.extraFolders.value.toSet()
        return FolderLists(
            includes = grants.filter { it !in extras }.map { it.toSourceFolder() },
            excludes = settings.excludedFolders.value.map { path -> SourceFolder(uri = null, path = path, name = path.substringAfterLast('/')) },
            extras = grants.filter { it in extras }.map { it.toSourceFolder() }
        )
    }

    private fun String.toSourceFolder(): SourceFolder {
        val uri = Uri.parse(this)
        val path = uri.folderPath()
        val name = path?.substringAfterLast('/')
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()?.substringAfterLast(':')?.substringAfterLast('/')
            ?: this
        return SourceFolder(uri = this, path = path, name = name)
    }

    private fun Uri.folderPath(): String? {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
        @Suppress("DEPRECATION")
        return externalStorageTreeFolder(authority, treeDocumentId, Environment.getExternalStorageDirectory().path)
    }
}
