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
                    // Deserialize the document into an Order object, 
                    // then create a copy with the ID set from the document's own ID.
                    // This ensures the ID is never null.
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Failed to deserialize document ${doc.id}", e)
                    null
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
