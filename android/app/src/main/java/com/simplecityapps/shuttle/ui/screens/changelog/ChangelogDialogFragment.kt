package com.simplecityapps.shuttle.ui.screens.changelog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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
import java.lang.reflect.Type
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class ChangelogDialogFragment : BottomSheetDialogFragment() {
    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var adapter: RecyclerAdapter by autoCleared()

    private var showOnLaunchSwitch: SwitchCompat by autoCleared()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contextThemeWrapper = ContextThemeWrapper(activity, requireContext().theme)
        return inflater.cloneInContext(contextThemeWrapper).inflate(R.layout.fragment_dialog_changelog, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        showOnLaunchSwitch = view.findViewById(R.id.showOnLaunchSwitch)
        showOnLaunchSwitch.isChecked = preferenceManager.showChangelogOnLaunch
        showOnLaunchSwitch.setOnCheckedChangeListener { _, b ->
            preferenceManager.showChangelogOnLaunch = b
        }

        val changelog =
            try {
                val changeSetList: Type = Types.newParameterizedType(MutableList::class.java, Changeset::class.java)
                moshi.adapter<List<Changeset>>(changeSetList).lenient().fromJson(requireContext().assets.open("changelog.json").bufferedReader().use { it.readText() })
            } catch (e: RuntimeException) {
                Timber.e(e, "Invalid changelog")
                null
            }

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
        viewBinders.addAll(
            changelog?.mapIndexed { index, changeset ->
                ChangesetBinder(
                    expanded =
                    index == 0 ||
                        changeset.version > (
                            preferenceManager.lastViewedChangelogVersion?.let { version ->
                                try {
                                    Semver(version)
                                } catch (e: Exception) {
                                    Timber.e(e)
                                    null
                                }
                            } ?: Semver("0.0.0")
                            ),
                    changeset = changeset,
                    listener = listener
                )
            }.orEmpty()
        )
        adapter.update(viewBinders)

        preferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    val listener =
        object : ChangesetBinder.Listener {
            override fun onItemClicked(
                position: Int,
                expanded: Boolean
            ) {
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
