package com.simplecityapps.shuttle.ui.screens.trial

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.DividerItemDecoration
import com.simplecityapps.trial.BillingManager
import com.simplecityapps.trial.PaywallOffer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class PurchaseDialogFragment : DialogFragment() {
    @Inject
    lateinit var billingManager: BillingManager

    private var adapter: RecyclerAdapter by autoCleared()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        adapter = RecyclerAdapter(lifecycleScope)

        billingManager.offers
            .onEach { offers ->
                adapter.update(
                    offers
                        .map { offer ->
                            SkuBinder(
                                offer,
                                object : SkuBinder.Listener {
                                    override fun onClick(offer: PaywallOffer) {
                                        val launched = billingManager.launchPurchaseFlow(requireActivity(), offer)
                                        if (launched) {
                                            dismiss()
                                        } else {
                                            Toast.makeText(
                                                requireContext(),
                                                "Unable to connect to Google Play. Please try again.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                )
            }.launchIn(lifecycleScope)

        val view = layoutInflater.inflate(R.layout.dialog_purchase, null)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.divider,
                    requireContext().theme
                )!!
            )
        )
        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    companion object {
        private const val TAG = "PurchaseDialogFragment"

        fun newInstance(): PurchaseDialogFragment = PurchaseDialogFragment()
    }
}
