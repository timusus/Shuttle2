package com.simplecityapps.shuttle.ui.screens.sleeptimer

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.utils.toHms
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SleepTimerDialogFragment : DialogFragment(), Injectable {

    @Inject lateinit var sleepTimer: SleepTimer

    private var handler: Handler? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context!!)
            .setTitle("Sleep Timer")

        sleepTimer.timeRemaining()?.let { timeRemaining ->
            // Sleep timer is currently active

            if (timeRemaining > 0L) {
                builder.setMessage("${timeRemaining.toInt().toHms()} remaining")

                handler = Handler()
                updateTimeRemaining()
            } else {
                builder.setMessage("Waiting for track to end...")
            }
            builder.setNegativeButton("Stop Timer") { _, _ -> sleepTimer.stopTimer() }
            builder.setPositiveButton("Close", null)
            builder.setNeutralButton("Set Time") { _, _ ->
                sleepTimer.stopTimer()
                fragmentManager?.let { fragmentManager ->
                    dismiss()
                    show(fragmentManager)
                }
            }
        } ?: run {

            // Sleep timer is not currently active

            builder
                .setTitle("Sleep Timer")
                .setItems(arrayOf("5 Minutes", "15 Minutes", "30 Minutes", "1 hour")) { _, index ->
                    when (index) {
                        0 -> sleepTimer.startTimer(TimeUnit.MINUTES.toMillis(5), false)
                        1 -> sleepTimer.startTimer(TimeUnit.MINUTES.toMillis(15), false)
                        2 -> sleepTimer.startTimer(TimeUnit.MINUTES.toMillis(30), false)
                        3 -> sleepTimer.startTimer(TimeUnit.MINUTES.toMillis(60), false)
                    }
                }
                .setNegativeButton("Close", null)
        }

        return builder.show()
    }

    private fun updateTimeRemaining() {
        sleepTimer.timeRemaining()?.let { timeRemaining ->
            if (timeRemaining > 0L) {
                (dialog as? AlertDialog)?.setMessage("${timeRemaining.toInt().toHms()} remaining")
            } else {
                (dialog as? AlertDialog)?.setMessage("Waiting for track to end...")
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
        const val TAG = "SleepTimerDialog"
    }
}