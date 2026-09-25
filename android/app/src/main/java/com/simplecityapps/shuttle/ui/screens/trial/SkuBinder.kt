package com.simplecityapps.shuttle.ui.screens.trial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isInvisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.trial.PaywallOffer
import com.squareup.phrase.Phrase

class SkuBinder(val offer: PaywallOffer, val listener: Listener) : ViewBinder {
    interface Listener {
        fun onClick(offer: PaywallOffer)
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
                viewBinder?.listener?.onClick(viewBinder!!.offer)
            }
        }

        override fun bind(
            viewBinder: SkuBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            val offer = viewBinder.offer
            title.text = offer.productDetails.name.substringBefore('(')
            subtitle.text = offer.productDetails.description
            val periodPrice = when (offer.billingPeriod) {
                "P1M" -> R.string.purchase_price_monthly
                "P1Y" -> R.string.purchase_price_annual
                else -> null
            }
            val formattedPrice = periodPrice?.let { Phrase.from(itemView.context, it).put("price", offer.formattedPrice).format() } ?: offer.formattedPrice
            price.text = formattedPrice
            priceOutlined.text = formattedPrice
            price.isInvisible = offer.isSubscription
            priceOutlined.isInvisible = !offer.isSubscription
        }
    }
}
