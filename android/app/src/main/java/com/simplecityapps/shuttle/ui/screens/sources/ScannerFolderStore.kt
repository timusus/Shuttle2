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

/**
 * A folder in Sources: [uri] is its SAF tree (null for an exclude, which keeps only the path), [path] its file path
 * when known. [hasAccess] is false once its SAF grant was revoked outside the app (#479); excludes need no grant,
 * so they're always true.
 */
data class SourceFolder(val uri: String?, val path: String?, val name: String, val hasAccess: Boolean = true)

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
 * Includes and extras are persisted SAF grants: their tree URIs are listed in [SourcesSettings.includedFolders] and
 * [SourcesSettings.extraFolders] respectively, independent of whether the system still holds the grant, so a folder
 * whose access was revoked outside the app stays listed and flagged rather than disappearing (#479). Excludes are plain
 * paths, since nothing needs to read them.
 *
 * Folders picked before #479 (#207) were tracked only by their live grants. The first store to run adopts those grants
 * as includes, once, and records that in [SourcesSettings.includedFoldersMigrated]; after that [load] only reads, so a
 * grant `remove` failed to release, or one taken later for something else, never turns into a scan root.
 *
 * Tree URIs are matched by their authority and tree document id rather than their string form, so the same folder
 * granted again under a differently encoded URI replaces its entry instead of being listed twice.
 *
 * Every read-modify-write of the folder settings, and every publish to [folders], runs under [lock]. The settings
 * are written nowhere else, so a scan reading the folders while the user edits Sources can't clobber an add or a
 * remove, and a stale [load] can't overwrite a newer one in [folders]. The methods are plain blocking calls (the
 * scanner reads [scannerFolders] synchronously), hence a JVM monitor rather than a coroutine `Mutex`.
 */
@Singleton
class SafScannerFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SourcesSettings
) : ScannerFolderStore {
    private val lock = Any()

    init {
        synchronized(lock) { adoptPreexistingGrants() }
    }

    private val _folders = MutableStateFlow(synchronized(lock) { load() })
    override val folders: StateFlow<FolderLists> = _folders.asStateFlow()

    /** What [com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider] scans. */
    fun scannerFolders(): ScannerFolders {
        val lists = synchronized(lock) { load() }
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
                synchronized(lock) {
                    val preference = if (kind == FolderKind.Extra) settings.extraFolders else settings.includedFolders
                    preference.value = preference.value.withTree(treeUri)
                }
            }

            FolderKind.Exclude -> {
                val path = uri.folderPath() ?: return false
                synchronized(lock) {
                    settings.excludedFolders.value = (settings.excludedFolders.value + path).distinct()
                }
            }
        }
        refresh()
        return true
    }

    override fun remove(kind: FolderKind, folder: SourceFolder) {
        synchronized(lock) {
            when (kind) {
                FolderKind.Include, FolderKind.Extra -> folder.uri?.let { treeUri ->
                    val key = treeKey(treeUri)
                    val preference = if (kind == FolderKind.Extra) settings.extraFolders else settings.includedFolders
                    preference.value = preference.value.filterNot { treeKey(it) == key }
                    // The same tree may still be listed as the other kind, which needs the grant kept
                    val stillListed = (settings.includedFolders.value + settings.extraFolders.value).any { treeKey(it) == key }
                    if (!stillListed) releaseGrants(key)
                }

                FolderKind.Exclude -> settings.excludedFolders.value = settings.excludedFolders.value - folder.path.orEmpty()
            }
        }
        refresh()
    }

    override fun refresh() {
        synchronized(lock) { _folders.value = load() }
    }

    /** Adopts the live grants of a pre-#479 install as includes, the first time only. Call under [lock]. */
    private fun adoptPreexistingGrants() {
        if (settings.includedFoldersMigrated.value) return
        val tracked = (settings.includedFolders.value + settings.extraFolders.value).map(::treeKey).toSet()
        val untracked = liveGrants().filterNot { treeKey(it) in tracked }
        if (untracked.isNotEmpty()) {
            settings.includedFolders.value = untracked.fold(settings.includedFolders.value) { folders, treeUri -> folders.withTree(treeUri) }
        }
        settings.includedFoldersMigrated.value = true
    }

    /** The folders as persisted, each flagged by whether its grant is still held. Only reads. Call under [lock]. */
    private fun load(): FolderLists {
        val liveKeys = liveGrants().map(::treeKey).toSet()
        return FolderLists(
            includes = settings.includedFolders.value.map { it.toSourceFolder(hasAccess = treeKey(it) in liveKeys) },
            excludes = settings.excludedFolders.value.map { path -> SourceFolder(uri = null, path = path, name = path.substringAfterLast('/')) },
            extras = settings.extraFolders.value.map { it.toSourceFolder(hasAccess = treeKey(it) in liveKeys) }
        )
    }

    private fun liveGrants(): List<String> = context.contentResolver.persistedUriPermissions
        .filter { permission -> permission.isReadPermission || permission.isWritePermission }
        .map { permission -> permission.uri.toString() }

    /** Releases every grant held on the tree [key] names, under whichever URI form it was taken. */
    private fun releaseGrants(key: String) {
        liveGrants().filter { treeKey(it) == key }.forEach { treeUri ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onFailure { Timber.w(it, "Couldn't release $treeUri") }
        }
    }

    /** [treeUri] with its tree listed once, under [treeUri]'s form, in place of any other form of the same tree. */
    private fun List<String>.withTree(treeUri: String): List<String> {
        val key = treeKey(treeUri)
        return if (any { treeKey(it) == key }) {
            map { if (treeKey(it) == key) treeUri else it }.distinctBy(::treeKey)
        } else {
            distinctBy(::treeKey) + treeUri
        }
    }

    /** Identifies a tree by its provider and decoded tree document id, whatever encoding its URI string uses. */
    private fun treeKey(treeUri: String): String {
        val uri = Uri.parse(treeUri)
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return treeUri
        return "${uri.authority}/$treeDocumentId"
    }

    private fun String.toSourceFolder(hasAccess: Boolean): SourceFolder {
        val uri = Uri.parse(this)
        val path = uri.folderPath()
        val name = path?.substringAfterLast('/')
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()?.substringAfterLast(':')?.substringAfterLast('/')
            ?: this
        return SourceFolder(uri = this, path = path, name = name, hasAccess = hasAccess)
    }

    private fun Uri.folderPath(): String? {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
        @Suppress("DEPRECATION")
        return externalStorageTreeFolder(authority, treeDocumentId, Environment.getExternalStorageDirectory().path)
    }
}
