package com.simplecityapps.shuttle.ui.screens.trial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isInvisible
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class SkuBinder(val skuDetails: SkuDetails, val litener: Listener) : ViewBinder {

    interface Listener {
        fun onClick(skuDetails: SkuDetails)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_sku, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Sku
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SkuBinder>(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val price: Button = itemView.findViewById(R.id.price)
        val priceOutlined: Button = itemView.findViewById(R.id.outlinedPrice)

        init {
            itemView.setOnClickListener {
                viewBinder?.litener?.onClick(viewBinder!!.skuDetails)
            }
        }

        override fun bind(viewBinder: SkuBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.skuDetails.title.substringBefore('(')
            subtitle.text = viewBinder.skuDetails.description
            price.text = viewBinder.skuDetails.price
            priceOutlined.text = viewBinder.skuDetails.price
            price.isInvisible = viewBinder.skuDetails.type == BillingClient.SkuType.SUBS
            priceOutlined.isInvisible = viewBinder.skuDetails.type == BillingClient.SkuType.INAPP
        }
    }
}