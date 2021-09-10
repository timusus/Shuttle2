package com.simplecityapps.trial

import java.util.concurrent.TimeUnit
import kotlin.math.min

sealed class TrialState {
    object Unknown : TrialState() {
        override fun toString(): String {
            return "Unknown"
        }
    }

    data class Pretrial(val timeRemaining: Long) : TrialState()

    object Paid : TrialState() {
        override fun toString(): String {
            return "Paid"
        }
    }

    data class Trial(val timeRemaining: Long) : TrialState()

    data class Expired(val timeSince: Long) : TrialState() {
        fun multiplier(): Float {
            return min(1 + ((timeSince / TimeUnit.DAYS.toMillis(1)) * 0.02f), 1.5f)
        }
    }
}