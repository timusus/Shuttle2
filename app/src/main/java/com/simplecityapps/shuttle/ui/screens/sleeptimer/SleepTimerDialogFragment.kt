package com.simplecityapps.shuttle.ui.screens.sleeptimer

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.squareup.phrase.Phrase
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SleepTimerDialogFragment : DialogFragment(), Injectable {

    @Inject
    lateinit var sleepTimer: SleepTimer

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var handler: Handler? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sleep_timer_dialog_title))

        sleepTimer.timeRemaining()?.let { timeRemaining ->
            // Sleep timer is currently active

            if (timeRemaining > 0L) {
                builder.setMessage(
                    Phrase.from(requireContext(), R.string.sleep_timer_time_remaining)
                        .put("time", timeRemaining.toInt().toHms())
                        .format()
                )

                handler = Handler()
                updateTimeRemaining()
            } else {
                builder.setMessage(getString(R.string.sleep_timer_waiting_track_end))
            }
            builder.setNegativeButton(getString(R.string.sleep_timer_dialog_button_stop_timer)) { _, _ -> sleepTimer.stopTimer() }
            builder.setPositiveButton(R.string.sleep_timer_dialog_button_close, null)
            builder.setNeutralButton(getString(R.string.sleep_timer_dialog_button_set_time)) { _, _ ->
                sleepTimer.stopTimer()
                dismiss()
                SleepTimerDialogFragment().show(parentFragmentManager)
            }
        } ?: run {

            // Sleep timer is not currently active

            val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sleep_timer, null)
            val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
            val playToEndSwitch: SwitchCompat = view.findViewById(R.id.playToEndSwitch)
            playToEndSwitch.isChecked = preferenceManager.sleepTimerPlayToEnd
            playToEndSwitch.setOnCheckedChangeListener { _, isChecked ->
                preferenceManager.sleepTimerPlayToEnd = isChecked
            }
            val adapter = RecyclerAdapter(lifecycleScope)
            recyclerView.adapter = adapter
            adapter.update(SleepTimerDuration.values().map {
                SleepTimerBinder(it, object : SleepTimerBinder.Listener {
                    override fun onClick(duration: SleepTimerDuration) {
                        sleepTimer.startTimer(duration.duration, playToEndSwitch.isChecked)
                        dialog?.dismiss()
                    }
                })
            })
            builder
                .setTitle(R.string.sleep_timer_dialog_title)
                .setView(view)
                .setNegativeButton(getString(R.string.sleep_timer_dialog_button_close), null)
        }

        return builder.show()
    }

    private fun updateTimeRemaining() {
        sleepTimer.timeRemaining()?.let { timeRemaining ->
            if (timeRemaining > 0L) {
                (dialog as? AlertDialog)?.setMessage(
                    Phrase.from(requireContext(), R.string.sleep_timer_time_remaining)
                        .put("time", timeRemaining.toInt().toHms())
                        .format()
                )
            } else {
                (dialog as? AlertDialog)?.setMessage(getString(R.string.sleep_timer_waiting_track_end))
            }
            handler?.postDelayed({ updateTimeRemaining() }, 1000)
        } ?: run {
            dialog?.dismiss()
        }
    }

    override fun onDestroyView() {
        handler?.removeCallbacksAndMessages(null)

        super.onDestroyView()
    }

    fun show(manager: FragmentManager) {
        super.show(manager, TAG)
    }

    companion object {
        fun newInstance() = SleepTimerDialogFragment()

        const val TAG = "SleepTimerDialog"
    }
}

enum class SleepTimerDuration(@StringRes val nameResId: Int, val duration: Long) {
    FiveMins(R.string.sleep_timer_5_minutes, TimeUnit.MINUTES.toMillis(1)),
    FifteenMins(R.string.sleep_timer_15_minutes, TimeUnit.MINUTES.toMillis(15)),
    ThirtyMins(R.string.sleep_timer_30_minutes, TimeUnit.MINUTES.toMillis(30)),
    OneHour(R.string.sleep_timer_1_hour, TimeUnit.MINUTES.toMillis(60))
}