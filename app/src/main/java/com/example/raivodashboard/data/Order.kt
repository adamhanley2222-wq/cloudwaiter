package com.example.raivodashboard.data

import androidx.annotation.Keep

@Keep
data class Order(
    val id: String? = null,
    val customerName: String? = null,
    val items: List<OrderItem>? = null,
    val status: OrderStatus? = null,
    val timestamp: Long? = null,
    val pickupTime: String? = null,
    val specialRequests: String? = null,
    val totalCost: Any? = null,
    val recordingUrl: String? = null,
    val conversationText: String? = null
)

@Keep
data class OrderItem(
    val name: String? = null,
    val quantity: Int? = null,
    val details: String? = null,
    val spiceLevel: String? = null,
    val price: Double? = null
)

enum class OrderStatus {
    NEW,
    PREPARING,
    READY,
    COMPLETED,
    CANCELLED
}
