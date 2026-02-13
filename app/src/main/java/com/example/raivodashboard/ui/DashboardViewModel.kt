package com.example.raivodashboard.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.raivodashboard.data.Order
import com.example.raivodashboard.data.OrderStatus
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DashboardUiState(
    val orders: List<Order> = emptyList()
)

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore

    init {
        db.collection("orders").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("DashboardViewModel", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshots == null) {
                Log.w("DashboardViewModel", "Snapshots are null")
                return@addSnapshotListener
            }

            val orders = snapshots.documents.mapNotNull { doc ->
                try {
                    Log.d("DashboardViewModel", "Document data for ${doc.id}: ${doc.data}")
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Failed to deserialize document ${doc.id}", e)
                    // Attempt to manually deserialize if automatic mapping fails
                    try {
                        val data = doc.data
                        val statusString = data?.get("status") as? String
                        val status = try {
                            statusString?.let { OrderStatus.valueOf(it) }
                        } catch (e: IllegalArgumentException) {
                            Log.w("DashboardViewModel", "Unknown OrderStatus value: $statusString for doc ${doc.id}")
                            null // Set status to null if it's an unknown value
                        }

                        Order(
                            id = doc.id,
                            customerName = data?.get("customerName") as? String,
                            // Assuming 'items' is a List of Maps that can be converted to OrderItem
                            items = null, // Manual deserialization of nested objects is more complex
                            status = status,
                            timestamp = data?.get("timestamp") as? Long,
                            pickupTime = data?.get("pickupTime") as? String,
                            specialRequests = data?.get("specialRequests") as? String,
                            totalCost = data?.get("totalCost"),
                            recordingUrl = data?.get("recordingUrl") as? String,
                            conversationTranscript = data?.get("conversationTranscript") as? String,
                            callSid = data?.get("callSid") as? String,
                            deliveryAddress = data?.get("deliveryAddress") as? String,
                            orderType = data?.get("orderType") as? String
                        )
                    } catch (manualError: Exception) {
                        Log.e("DashboardViewModel", "Manual deserialization also failed for doc ${doc.id}", manualError)
                        null
                    }
                }
            }

            val sortedOrders = orders.sortedByDescending { it.timestamp ?: 0 }

            _uiState.value = _uiState.value.copy(orders = sortedOrders)
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        db.collection("orders").document(orderId).update("status", newStatus.name)
    }
}
