package com.simplecityapps.shuttle.ui.screens.changelog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import timber.log.Timber
import java.lang.reflect.Type
import javax.inject.Inject

class ChangelogDialogFragment : DialogFragment(), Injectable {

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var adapter: RecyclerAdapter by autoCleared()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val changelog = try {
            val changeSetList: Type = Types.newParameterizedType(MutableList::class.java, Changeset::class.java)
            moshi.adapter<List<Changeset>>(changeSetList).fromJson(requireContext().assets.open("changelog.json").bufferedReader().use { it.readText() })
        } catch (e: RuntimeException) {
            Timber.e(e, "Invalid changelog")
            null
        }

        val view = LayoutInflater.from(context).inflate(R.layout.fragment_dialog_changelog, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.addItemDecoration(SpacesItemDecoration(4.dp))
        adapter = RecyclerAdapter(lifecycleScope)
        recyclerView.adapter = adapter

        adapter.update(
            changelog?.mapIndexed { index, changeset ->
                ChangesetBinder(index == 0 || changeset.version > preferenceManager.lastViewedChangelogVersion?.let { version -> Version(version) }, changeset, listener)
            }.orEmpty()
        )

        preferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Changelog")
            .setView(view)
            .setNegativeButton("Close", null)
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