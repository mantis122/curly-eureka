package com.littlemachineworks.svgvectorconverter

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Small, app-local wrapper around Google Play Billing.
 *
 * This first billing stage intentionally does not enforce any Pro-only feature.
 * It only discovers the Pro product, launches its purchase flow, restores owned
 * purchases, acknowledges completed purchases, and exposes entitlement state to
 * MainActivity.
 */
class PlayBillingManager(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onBillingStateChanged(state: State)
        fun onBillingMessage(message: String)
    }

    data class State(
        val isReady: Boolean = false,
        val isPro: Boolean = false,
        val proPrice: String? = null,
        val purchasePending: Boolean = false
    )

    companion object {
        const val PRO_PRODUCT_ID = "pro_upgrade"
    }

    private var state = State()
    private var proProductDetails: ProductDetails? = null
    private var connectionInProgress = false

    private val purchasesUpdatedListener = com.android.billingclient.api.PurchasesUpdatedListener {
            billingResult, purchases ->
        handlePurchasesUpdated(billingResult, purchases)
    }

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            publish(state.copy(isReady = true))
            refreshPurchases()
            queryProProduct()
            return
        }
        if (connectionInProgress) return

        connectionInProgress = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connectionInProgress = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    publish(state.copy(isReady = true))
                    refreshPurchases()
                    queryProProduct()
                } else {
                    publish(state.copy(isReady = false))
                }
            }

            override fun onBillingServiceDisconnected() {
                connectionInProgress = false
                publish(state.copy(isReady = false))
            }
        })
    }

    fun refresh() {
        if (billingClient.isReady) {
            refreshPurchases()
            queryProProduct()
        } else {
            start()
        }
    }

    fun launchProPurchase(activity: Activity) {
        if (!billingClient.isReady) {
            listener.onBillingMessage("Google Play billing is not ready yet")
            start()
            return
        }

        val details = proProductDetails
        if (details == null) {
            listener.onBillingMessage("Pro is not available from Google Play yet")
            queryProProduct()
            return
        }

        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
        if (offer == null) {
            listener.onBillingMessage("No eligible Pro purchase offer is available")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            listener.onBillingMessage(
                result.debugMessage.ifBlank { "Could not open Google Play purchase screen" }
            )
        }
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun handlePurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(purchases.orEmpty())
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshPurchases()
            }

            else -> {
                listener.onBillingMessage(
                    billingResult.debugMessage.ifBlank { "Google Play purchase failed" }
                )
            }
        }
    }

    private fun queryProProduct() {
        if (!billingClient.isReady) return

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                proProductDetails = null
                publish(state.copy(proPrice = null))
                return@queryProductDetailsAsync
            }

            proProductDetails = queryResult.productDetailsList
                .firstOrNull { it.productId == PRO_PRODUCT_ID }

            val price = proProductDetails
                ?.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.formattedPrice

            publish(state.copy(proPrice = price))
        }
    }

    private fun refreshPurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val proPurchases = purchases.filter { purchase ->
            PRO_PRODUCT_ID in purchase.products
        }

        val purchased = proPurchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pending = proPurchases.any {
            it.purchaseState == Purchase.PurchaseState.PENDING
        }

        publish(
            state.copy(
                isPro = purchased.isNotEmpty(),
                purchasePending = pending
            )
        )

        purchased
            .filterNot { it.isAcknowledged }
            .forEach { acknowledge(it) }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                listener.onBillingMessage(
                    billingResult.debugMessage.ifBlank { "Could not acknowledge Pro purchase" }
                )
            }
        }
    }

    private fun publish(newState: State) {
        state = newState
        listener.onBillingStateChanged(newState)
    }
}
