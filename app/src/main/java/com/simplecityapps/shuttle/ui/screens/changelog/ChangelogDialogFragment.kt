package com.simplecityapps.shuttle.ui.screens.changelog

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.vdurmont.semver4j.Semver
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.lang.reflect.Type
import javax.inject.Inject

@AndroidEntryPoint
class ChangelogDialogFragment : DialogFragment() {

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var adapter: RecyclerAdapter by autoCleared()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val changelog = try {
            val changeSetList: Type = Types.newParameterizedType(MutableList::class.java, Changeset::class.java)
            moshi.adapter<List<Changeset>>(changeSetList).lenient().fromJson(requireContext().assets.open("changelog.json").bufferedReader().use { it.readText() })
        } catch (e: RuntimeException) {
            Timber.e(e, "Invalid changelog")
            null
        }

        val view = layoutInflater.inflate(R.layout.fragment_dialog_changelog, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.addItemDecoration(SpacesItemDecoration(4.dp))
        adapter = RecyclerAdapter(lifecycleScope)
        recyclerView.adapter = adapter

        val viewBinders = mutableListOf<ViewBinder>()
        if (changelog?.firstOrNull()?.notes.orEmpty().isNotEmpty()) {
            changelog?.firstOrNull()?.notes?.firstOrNull()?.let { notes ->
                viewBinders.add(NotesBinder(notes))
            }
        }
        viewBinders.addAll(changelog?.mapIndexed { index, changeset ->
            ChangesetBinder(
                expanded = index == 0 || changeset.version > (preferenceManager.lastViewedChangelogVersion?.let { version ->
                    try {
                        Semver(version)
                    } catch (e: Exception) {
                        Timber.e(e)
                        null
                    }
                } ?: Semver("0.0.0")),
                changeset = changeset,
                listener = listener
            )
        }.orEmpty())
        adapter.update(viewBinders)

        preferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.changelog_title))
            .setView(view)
            .setNegativeButton(getString(R.string.dialog_button_close), null)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    val listener = object : ChangesetBinder.Listener {
        override fun onItemClicked(position: Int, expanded: Boolean) {
            val items = adapter.items.toMutableList()
            items[position] = (items[position] as ChangesetBinder).clone(!expanded)
            adapter.update(items)
        }
    }

    companion object {
        const val TAG = "ChangelogDialog"

        fun newInstance() = ChangelogDialogFragment()
    }
}