package com.simplecityapps.shuttle.ui.screens.settings.screens.appearance

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.ItemTouchHelperCallback
import com.simplecityapps.shuttle.ui.common.view.ThemeButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppearancePreferenceFragment : Fragment() {

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var themeManager: ThemeManager

    var themeButtonLight: ThemeButton by autoCleared()
    var themeButtonDark: ThemeButton by autoCleared()
    var themeButtonSystem: ThemeButton by autoCleared()

    var extraDarkSwitchBackground: View by autoCleared()
    var extraDarkSwitch: SwitchCompat by autoCleared()

    var showHomeOnLaunchSwitchBackground: View by autoCleared()
    var showHomeOnLaunchSwitch: SwitchCompat by autoCleared()

    var accentButtonBlue: ImageView by autoCleared()
    var accentButtonRed: ImageView by autoCleared()
    var accentButtonCyan: ImageView by autoCleared()
    var accentButtonPurple: ImageView by autoCleared()
    var accentButtonGreen: ImageView by autoCleared()
    var accentButtonOrange: ImageView by autoCleared()

    var tabsRecyclerView: RecyclerView by autoCleared()
    var recyclerAdapter: RecyclerAdapter by autoCleared()

    lateinit var libraryTabs: MutableList<LibraryTab>

    private var recyclerViewState: Parcelable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preferences_appearance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        libraryTabs = preferenceManager.allLibraryTabs.toMutableList()

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        toolbar.setTitle(R.string.pref_category_title_display)

        themeButtonLight = view.findViewById(R.id.themeButtonLight)
        themeButtonDark = view.findViewById(R.id.themeButtonDark)
        themeButtonSystem = view.findViewById(R.id.themeButtonSystem)
        val themeButtons = arrayOf(themeButtonLight, themeButtonDark, themeButtonSystem)

        extraDarkSwitchBackground = view.findViewById(R.id.switchExtraDarkBackground)
        extraDarkSwitch = view.findViewById(R.id.switchExtraDark)

        showHomeOnLaunchSwitchBackground = view.findViewById(R.id.switchShowHomeOnLaunchBackground)
        showHomeOnLaunchSwitch = view.findViewById(R.id.switchShowHomeOnLaunch)

        accentButtonBlue = view.findViewById(R.id.accentButtonBlue)
        accentButtonRed = view.findViewById(R.id.accentButtonRed)
        accentButtonCyan = view.findViewById(R.id.accentButtonCyan)
        accentButtonPurple = view.findViewById(R.id.accentButtonPurple)
        accentButtonGreen = view.findViewById(R.id.accentButtonGreen)
        accentButtonOrange = view.findViewById(R.id.accentButtonOrange)
        val accentButtons = arrayOf(accentButtonBlue, accentButtonRed, accentButtonCyan, accentButtonPurple, accentButtonGreen, accentButtonOrange)

        when (preferenceManager.themeBase) {
            GeneralPreferenceManager.Theme.Light -> themeButtons.forEach { it.isActivated = it == themeButtonLight }
            GeneralPreferenceManager.Theme.Dark -> themeButtons.forEach { it.isActivated = it == themeButtonDark }
            GeneralPreferenceManager.Theme.DayNight -> themeButtons.forEach { it.isActivated = it == themeButtonSystem }
        }

        themeButtons.forEach { button ->
            button.setOnClickListener { v ->
                themeButtons.forEach { button -> button.isActivated = button == v }

                val newTheme: GeneralPreferenceManager.Theme = when (v) {
                    themeButtonLight -> GeneralPreferenceManager.Theme.Light
                    themeButtonDark -> GeneralPreferenceManager.Theme.Dark
                    themeButtonSystem -> GeneralPreferenceManager.Theme.DayNight
                    else -> throw IllegalStateException("Invalid theme choice")
                }

                if (newTheme != preferenceManager.themeBase) {
                    preferenceManager.themeBase = newTheme
                    setTheme()
                }
            }
        }

        extraDarkSwitch.isChecked = preferenceManager.themeExtraDark
        extraDarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (preferenceManager.themeExtraDark != isChecked) {
                preferenceManager.themeExtraDark = isChecked
                setTheme()
            }
        }
        extraDarkSwitchBackground.setOnClickListener {
            extraDarkSwitch.toggle()
        }

        showHomeOnLaunchSwitch.isChecked = preferenceManager.showHomeOnLaunch
        showHomeOnLaunchSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.showHomeOnLaunch = isChecked
        }
        showHomeOnLaunchSwitchBackground.setOnClickListener {
            showHomeOnLaunchSwitch.toggle()
        }

        when (preferenceManager.themeAccent) {
            GeneralPreferenceManager.Accent.Default -> accentButtons.forEach { it.isActivated = it == accentButtonBlue }
            GeneralPreferenceManager.Accent.Orange -> accentButtons.forEach { it.isActivated = it == accentButtonRed }
            GeneralPreferenceManager.Accent.Cyan -> accentButtons.forEach { it.isActivated = it == accentButtonCyan }
            GeneralPreferenceManager.Accent.Purple -> accentButtons.forEach { it.isActivated = it == accentButtonPurple }
            GeneralPreferenceManager.Accent.Green -> accentButtons.forEach { it.isActivated = it == accentButtonGreen }
            GeneralPreferenceManager.Accent.Amber -> accentButtons.forEach { it.isActivated = it == accentButtonOrange }
        }

        accentButtons.forEach {
            it.setOnClickListener { v ->
                accentButtons.forEach { button -> button.isActivated = button == v }

                val newAccent: GeneralPreferenceManager.Accent = when (v) {
                    accentButtonBlue -> GeneralPreferenceManager.Accent.Default
                    accentButtonRed -> GeneralPreferenceManager.Accent.Orange
                    accentButtonCyan -> GeneralPreferenceManager.Accent.Cyan
                    accentButtonPurple -> GeneralPreferenceManager.Accent.Purple
                    accentButtonGreen -> GeneralPreferenceManager.Accent.Green
                    accentButtonOrange -> GeneralPreferenceManager.Accent.Amber
                    else -> throw IllegalStateException("Invalid accent choice")
                }

                if (newAccent != preferenceManager.themeAccent) {
                    preferenceManager.themeAccent = newAccent
                    setTheme()
                }
            }
        }

        tabsRecyclerView = view.findViewById(R.id.tabsRecyclerView)
        itemTouchHelper.attachToRecyclerView(tabsRecyclerView)
        recyclerAdapter = RecyclerAdapter(
            scope = viewLifecycleOwner.lifecycle.coroutineScope,
            skipIntermediateUpdates = false
        )
        tabsRecyclerView.adapter = recyclerAdapter

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }
        recyclerAdapter.update(libraryTabs.map { LibraryTabBinder(it, preferenceManager.enabledLibraryTabs.contains(it), listener) }) {
            recyclerViewState?.let {
                tabsRecyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        }
    }

    override fun onPause() {
        recyclerViewState = tabsRecyclerView.layoutManager?.onSaveInstanceState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        itemTouchHelper.attachToRecyclerView(null)
        super.onDestroyView()
    }

    // Private

    private val listener = object : LibraryTabBinder.Listener {
        override fun onStartDrag(viewHolder: LibraryTabBinder.ViewHolder) {
            itemTouchHelper.startDrag(viewHolder)
        }

        override fun onChecked(tab: LibraryTab, isChecked: Boolean) {
            val tabs = preferenceManager.enabledLibraryTabs.toMutableSet()
            if (isChecked) {
                tabs.add(tab)
            } else {
                tabs.remove(tab)
            }
            preferenceManager.enabledLibraryTabs = tabs.toList()

            recyclerAdapter.update(libraryTabs.map { LibraryTabBinder(it, preferenceManager.enabledLibraryTabs.contains(it), this) })
        }
    }

    private val itemTouchHelper = object : ItemTouchHelper(object : ItemTouchHelperCallback(object : OnItemMoveListener {
        override fun onItemMoved(from: Int, to: Int) {
            libraryTabs.add(to, libraryTabs.removeAt(from))
            preferenceManager.allLibraryTabs = libraryTabs
        }
    }) {}) {}

    private fun setTheme() {
        themeManager.setDayNightMode()
        activity?.recreate()
    }

    // Static
    companion object {
        const val ARG_RECYCLER_STATE = "recycler_state"
    }
}
