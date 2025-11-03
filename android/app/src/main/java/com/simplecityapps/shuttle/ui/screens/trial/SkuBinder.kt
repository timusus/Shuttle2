package com.simplecityapps.shuttle.ui.screens.trial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isInvisible
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class SkuBinder(val productDetails: ProductDetails, val litener: Listener) : ViewBinder {
    interface Listener {
        fun onClick(productDetails: ProductDetails)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_sku, parent, false))

    override fun viewType(): Int = ViewTypes.Sku

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SkuBinder>(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val price: Button = itemView.findViewById(R.id.price)
        val priceOutlined: Button = itemView.findViewById(R.id.outlinedPrice)

        init {
            itemView.setOnClickListener {
                viewBinder?.litener?.onClick(viewBinder!!.productDetails)
            }
        }

        override fun bind(
            viewBinder: SkuBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.productDetails.name.substringBefore('(')
            subtitle.text = viewBinder.productDetails.description
            val formattedPrice = viewBinder.productDetails.oneTimePurchaseOfferDetails?.formattedPrice
                ?: viewBinder.productDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                ?: ""
            price.text = formattedPrice
            priceOutlined.text = formattedPrice
            price.isInvisible = viewBinder.productDetails.productType == BillingClient.ProductType.SUBS
            priceOutlined.isInvisible = viewBinder.productDetails.productType == BillingClient.ProductType.INAPP
        }
    }
}
