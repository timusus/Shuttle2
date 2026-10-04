package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

/**
 * Which of a full import's deletes [MediaImporter] applies. Deleting a song takes its play history and playlist places
 * with it, and a source that fails part way can look like one that holds fewer songs, so some deletes are held back:
 * - those under a root the provider couldn't read this time ([MediaProvider.unreadableRoots]), for as long as it can't;
 * - a mass removal, of more than [MAX_DELETED_FRACTION] of the source's songs (and more than [MIN_GUARDED_DELETES]) or
 *   of every one when it found none. It's held for one pass: the next full pass that finds the same songs gone removes
 *   them, so a library the user really did shrink catches up an import later, while a source that failed once doesn't.
 *   A removal the user asked for (by changing which folders are read) isn't held;
 * - every one, when the listing came to less than the source said it holds ([FlowEvent.Success.complete]): what it left
 *   out can't be told from what's gone. The next full pass decides, and until then the mass removal held before stays
 *   as it was, neither confirmed nor replaced.
 * A pass that held a mass removal or listed incompletely [awaitsFullPass][Decision.awaitsFullPass].
 */
class DeleteGuard(
    /** Where the songs each source's last full pass held back as a mass removal are kept, across restarts. */
    private val preferenceManager: GeneralPreferenceManager
) {
    /** [decide]s which of [type]'s [deletes] to apply, and remembers the mass removal it held back for the next pass. */
    fun deletesToApply(
        type: MediaProviderType,
        existingCount: Int,
        foundCount: Int,
        deletes: List<Song>,
        unreadableRoots: Set<String>,
        userRemoval: Boolean = false,
        listingComplete: Boolean = true
    ): Decision {
        val decision = decide(existingCount, foundCount, deletes, unreadableRoots, heldLastPass = preferenceManager.heldDeletes(type.name), userRemoval, listingComplete)
        if (listingComplete) preferenceManager.setHeldDeletes(type.name, decision.heldMassRemoval.map { song -> song.id }.toSet())
        return decision
    }

    /**
     * @property apply the deletes to apply
     * @property heldUnreadable how many deletes were held back for being under an unreadable root
     * @property heldMassRemoval the deletes held back as a mass removal, which the next pass applies if they're still gone
     * @property heldIncomplete how many deletes were held back for the listing being incomplete
     * @property listingComplete whether the listing held everything the source said it holds
     */
    data class Decision(
        val apply: List<Song>,
        val heldUnreadable: Int,
        val heldMassRemoval: List<Song>,
        val heldIncomplete: Int = 0,
        val listingComplete: Boolean = true
    ) {
        /**
         * Whether the source's next sync should be a full pass: one is waiting to confirm a mass removal, or this listing
         * left songs out, which an incremental sync wouldn't fetch unless they changed.
         */
        val awaitsFullPass: Boolean get() = heldMassRemoval.isNotEmpty() || !listingComplete
    }

    companion object {
        /** A full pass that would delete more than this fraction of a source's songs is a mass removal. */
        const val MAX_DELETED_FRACTION = 0.5

        /** A pass deleting this many songs or fewer is never a mass removal, unless it found none at all. */
        const val MIN_GUARDED_DELETES = 20

        /**
         * Of [deletes], those a full pass that found [foundCount] songs, of a source holding [existingCount], applies:
         * none under [unreadableRoots] (path prefixes, each ending in a separator), and, of a mass removal, only those
         * [heldLastPass] held back too, unless it's a [userRemoval]: the user took those songs out of what's read, so they go
         * at once. A listing that isn't [listingComplete] applies none.
         */
        fun decide(
            existingCount: Int,
            foundCount: Int,
            deletes: List<Song>,
            unreadableRoots: Set<String>,
            heldLastPass: Set<Long>,
            userRemoval: Boolean = false,
            listingComplete: Boolean = true
        ): Decision {
            val (unreadable, readable) = deletes.partition { song -> unreadableRoots.any { root -> song.path.startsWith(root) } }
            if (!listingComplete) {
                return Decision(apply = emptyList(), heldUnreadable = unreadable.size, heldMassRemoval = emptyList(), heldIncomplete = readable.size, listingComplete = false)
            }
            val massRemoval =
                !userRemoval &&
                    readable.isNotEmpty() &&
                    (foundCount == 0 || (readable.size > MIN_GUARDED_DELETES && readable.size > existingCount * MAX_DELETED_FRACTION))
            if (!massRemoval) return Decision(apply = readable, heldUnreadable = unreadable.size, heldMassRemoval = emptyList())
            val (confirmed, unconfirmed) = readable.partition { song -> song.id in heldLastPass }
            return Decision(apply = confirmed, heldUnreadable = unreadable.size, heldMassRemoval = unconfirmed)
        }
    }
}
