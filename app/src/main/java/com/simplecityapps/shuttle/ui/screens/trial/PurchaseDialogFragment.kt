package com.simplecityapps.shuttle.ui.screens.trial

import android.app.Dialog
import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.SkuDetails
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.DividerItemDecoration
import com.simplecityapps.trial.BillingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class PurchaseDialogFragment : DialogFragment() {

    private var enabledSkus = listOf(
        "s2_subscription_full_version_monthly",
        "s2_subscription_full_version_yearly",
        "s2_iap_full_version"
    )

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var firebaseConfig: FirebaseRemoteConfig

    private var adapter: RecyclerAdapter by autoCleared()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        if (firebaseConfig.getString("pricing_tier") == "low") {
            enabledSkus = listOf(
                "s2_subscription_full_version_yearly_low",
                "s2_iap_full_version_low"
            )
        }

        adapter = RecyclerAdapter(lifecycleScope)

        billingManager.skuDetails
            .onEach { detailsList ->
                adapter.update(detailsList
                    .filter { enabledSkus.contains(it.sku) }
                    .map { skuDetails ->
                        SkuBinder(skuDetails, object : SkuBinder.Listener {
                            override fun onClick(skuDetails: SkuDetails) {
                                billingManager.launchPurchaseFlow(requireActivity(), skuDetails)
                                dismiss()
                            }
                        })
                    })
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

