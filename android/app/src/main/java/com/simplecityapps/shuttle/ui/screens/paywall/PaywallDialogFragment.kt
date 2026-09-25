package com.simplecityapps.shuttle.ui.screens.paywall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import com.simplecityapps.trial.PaywallSource
import com.simplecityapps.trial.ServerAccessGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** The Compose paywall, full screen over the legacy [com.simplecityapps.shuttle.ui.MainActivity]. */
@AndroidEntryPoint
class PaywallDialogFragment : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A plain full-screen window; the paywall draws its own surface and top bar.
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val source = PaywallSource.valueOf(requireArguments().getString(ARG_SOURCE)!!)
        setContent {
            S2AppTheme {
                PaywallEntry(source, onClose = { dismiss() })
            }
        }
    }

    companion object {
        private const val TAG = "PaywallDialogFragment"
        private const val ARG_SOURCE = "source"

        fun newInstance(source: PaywallSource): PaywallDialogFragment = PaywallDialogFragment().withArgs {
            putString(ARG_SOURCE, source.name)
        }

        /** Shows the paywall over [fragmentManager], unless it's already showing. */
        fun show(
            fragmentManager: FragmentManager,
            source: PaywallSource
        ) {
            if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved) {
                newInstance(source).show(fragmentManager, TAG)
            }
        }
    }
}

/** Shows the paywall over this activity whenever a [ServerAccessGate] check refuses while the activity is started. */
fun FragmentActivity.showPaywallOnRequest(serverAccessGate: ServerAccessGate) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            serverAccessGate.paywallRequests.collect { source -> PaywallDialogFragment.show(supportFragmentManager, source) }
        }
    }
}
