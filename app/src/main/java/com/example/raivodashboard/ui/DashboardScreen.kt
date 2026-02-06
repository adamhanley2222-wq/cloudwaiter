package com.example.raivodashboard.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.raivodashboard.data.Order
import com.example.raivodashboard.data.OrderStatus
import com.epson.epos2.Epos2Exception
import com.epson.epos2.printer.Printer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "PrintingTest"

// Create a CoroutineScope for printing tasks
private val printerScope = CoroutineScope(Dispatchers.IO)
// Create a Mutex to ensure only one print job runs at a time
private val printerMutex = Mutex()
// Use a shared printer instance for a persistent connection
private var epsonPrinter: Printer? = null

// Represents the visible tabs in the UI
sealed class DashboardTab(val title: String) {
    object New : DashboardTab("NEW")
    object Preparing : DashboardTab("PREP")
    object Ready : DashboardTab("READY")
    object ChefsList : DashboardTab("CHEF")
    object Completed : DashboardTab("HISTORY")
    object Cancelled : DashboardTab("CXL")
}

private val tabs = listOf(DashboardTab.New, DashboardTab.Preparing, DashboardTab.ChefsList, DashboardTab.Ready, DashboardTab.Completed, DashboardTab.Cancelled)

// Helper function to map tab to a color
private fun colorForTab(tab: DashboardTab): Color {
    return when (tab) {
        DashboardTab.New -> Color(0xFF4A90E2) // Blue
        DashboardTab.Preparing -> Color(0xFFFFD700) // Gold/Yellow
        DashboardTab.Ready -> Color(0xFF7ED321)    // Green
        DashboardTab.ChefsList -> Color.DarkGray
        DashboardTab.Completed -> Color(0xFFBDBDBD) // Light Grey
        DashboardTab.Cancelled -> Color(0xFFD0021B)   // Red
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf<DashboardTab>(DashboardTab.New) }
    val context = LocalContext.current

    val newOrdersCount = uiState.orders.count { it.status == OrderStatus.NEW }
    val preparingOrdersCount = uiState.orders.count { it.status == OrderStatus.PREPARING }
    val readyOrdersCount = uiState.orders.count { it.status == OrderStatus.READY }

    val selectedColor = colorForTab(selectedTab)

    Column {
        TabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            indicator = { /* No indicator */ },
            divider = { /* No divider */ }
        ) {
            tabs.forEach { tab ->
                val tabColor = colorForTab(tab)
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    content = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(tabColor).height(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val textColor = if (tab == DashboardTab.Preparing || tab == DashboardTab.Completed) Color.Black else Color.White
                            val count = when (tab) {
                                DashboardTab.New -> newOrdersCount
                                DashboardTab.Preparing -> preparingOrdersCount
                                DashboardTab.Ready -> readyOrdersCount
                                else -> null
                            }

                            if (count != null) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(style = SpanStyle(color = textColor, fontSize = 10.sp)) {
                                            append("${tab.title} ")
                                        }
                                        withStyle(style = SpanStyle(color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)) {
                                            append("($count)")
                                        }
                                    },
                                )
                            } else {
                                Text(tab.title, fontSize = 10.sp, color = textColor)
                            }
                        }
                    }
                )
            }
        }

        when (selectedTab) {
            is DashboardTab.ChefsList -> {
                val preparingOrders = uiState.orders.filter { it.status == OrderStatus.PREPARING }
                val itemCounts = preparingOrders
                    .flatMap { it.items ?: emptyList() }
                    .groupBy {
                        val spice = if (!it.spiceLevel.isNullOrBlank()) " (${it.spiceLevel})" else ""
                        val details = if (!it.details.isNullOrBlank() && it.details != "None") " - ${it.details}" else ""
                        (it.name ?: "Unknown Item") + spice + details
                    }
                    .mapValues { entry -> entry.value.sumOf { it.quantity ?: 0 } }
                    .toList()
                    .sortedByDescending { it.second }

                val specialRequests = preparingOrders
                    .mapNotNull { it.specialRequests }
                    .filter { it.isNotBlank() }

                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Text("Consolidated Items", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(itemCounts) { (itemName, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("$count x", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                            Text(itemName, fontSize = 16.sp)
                        }
                    }

                    if (specialRequests.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Special Requests", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(specialRequests) {
                            Text("• $it", fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            else -> {
                val filteredOrders = when (selectedTab) {
                    DashboardTab.New -> uiState.orders.filter { it.status == OrderStatus.NEW }
                    DashboardTab.Preparing -> uiState.orders.filter { it.status == OrderStatus.PREPARING }
                    DashboardTab.Ready -> uiState.orders.filter { it.status == OrderStatus.READY }
                    DashboardTab.Completed -> uiState.orders.filter { it.status == OrderStatus.COMPLETED }
                    DashboardTab.Cancelled -> uiState.orders.filter { it.status == OrderStatus.CANCELLED }
                    else -> emptyList()
                }

                LazyColumn {
                    items(filteredOrders) { order ->
                        OrderCard(
                            order = order,
                            onStatusChange = { newStatus ->
                                if (order.status == OrderStatus.NEW && newStatus == OrderStatus.PREPARING) {
                                    printOrder(context, order) { _, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                order.id?.let { id -> viewModel.updateOrderStatus(id, newStatus) }
                            },
                            cardColor = selectedColor,
                            onPrint = {
                                Log.d(TAG, "Print button clicked for order: ${order.id}")
                                printOrder(context, order) { _, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

fun printOrder(context: Context, order: Order, onResult: (Boolean, String) -> Unit) {
    Log.d(TAG, "printOrder called for order: ${order.id}")
    printerScope.launch {
        val (success, message) = printToEpsonPrinter(context, order)
        // Switch to the main thread to show UI feedback
        launch(Dispatchers.Main) {
            onResult(success, message)
        }
    }
}

private suspend fun printToEpsonPrinter(context: Context, order: Order): Pair<Boolean, String> {
    return printerMutex.withLock {
        val printerIpAddress = "192.168.101.61"

        try {
            // 1. Establish connection if it doesn't exist.
            if (epsonPrinter == null) {
                Log.d(TAG, "No persistent connection found. Establishing new connection...")
                try {
                    val newPrinter = Printer(Printer.TM_U220, Printer.LANG_EN, context)
                    newPrinter.setReceiveEventListener { _, code, _, _ ->
                        Log.d(TAG, "Printer event received: $code")
                    }
                    newPrinter.connect("TCP:$printerIpAddress", Printer.PARAM_DEFAULT)
                    epsonPrinter = newPrinter // Assign to shared instance only on success
                    Log.d(TAG, "Printer connection established successfully.")
                } catch (e: Epos2Exception) {
                    Log.e(TAG, "Failed to connect to printer. Error code: ${e.errorStatus}", e)
                    epsonPrinter = null // Ensure instance is null on failure
                    return@withLock Pair(false, "Printer connection failed. Code: ${e.errorStatus}")
                }
            }

            val printer = epsonPrinter!!

            // 2. Check status before printing.
            val status = printer.status
            if (status.online == Printer.FALSE) {
                Log.e(TAG, "Printer is offline. Resetting connection.")
                // Disconnect and nullify to force a full reconnect on the next attempt.
                try {
                    printer.disconnect()
                } catch (e: Epos2Exception) { 
                    Log.w(TAG, "Error during disconnect after finding printer offline.", e)
                }
                epsonPrinter = null
                return@withLock Pair(false, "Printer is offline.")
            }
            if (status.paper == Printer.PAPER_NEAR_END || status.paper == Printer.PAPER_EMPTY) {
                Log.e(TAG, "Printer is out of paper.")
                return@withLock Pair(false, "Printer is out of paper.")
            }

            // 3. Build and send the print data.
            printer.addTextAlign(Printer.ALIGN_CENTER)
            printer.addTextSize(2, 2)
            printer.addText("Order for: ${order.customerName ?: "N/A"}")
            printer.addTextSize(1, 1)
            val formattedTime = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(order.timestamp ?: 0))
            printer.addText("ID: #${order.id?.takeLast(3)} @ $formattedTime")
            printer.addFeedLine(1)
            printer.addTextAlign(Printer.ALIGN_LEFT)
            printer.addText("ITEMS:")
            order.items?.forEach { item ->
                val spice = if (!item.spiceLevel.isNullOrBlank()) " (${item.spiceLevel})" else ""
                val details = if (!item.details.isNullOrBlank() && item.details != "None") "  - ${item.details}" else ""
                printer.addText("${item.quantity}x ${item.name}$spice$details")
            }

            printer.addFeedLine(1)

            if (!order.specialRequests.isNullOrBlank() && order.specialRequests != "None") {
                printer.addText("REQUEST:")
                printer.addTextSize(2, 2)
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT)
                printer.addText(order.specialRequests + "")
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
                printer.addTextSize(1, 1)
                printer.addFeedLine(1)
            }

            if (!order.pickupTime.isNullOrBlank() && order.pickupTime != "N/A") {
                printer.addTextSize(2, 2)
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT)
                printer.addText("PICKUP: ${order.pickupTime}")
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
                printer.addTextSize(1, 1)
            }

            order.totalCost?.let {
                printer.addTextSize(2, 2)
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT)
                printer.addText("TOTAL: $it")
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
                printer.addTextSize(1, 1)
                printer.addFeedLine(1)
            }

            printer.addFeedLine(5)
            
            // 4. Send data within a transaction.
            printer.beginTransaction()
            printer.sendData(Printer.PARAM_DEFAULT)
            printer.endTransaction()

            // 5. Success! Do not disconnect. Leave the connection open for the next job.
            Log.d(TAG, "Print successful. Leaving connection open.")
            Pair(true, "Print successful.")

        } catch (e: Epos2Exception) {
            Log.e(TAG, "Epos2Exception during print job. Connection will be reset. Error: ${e.errorStatus}", e)
            // 6. On any failure, disconnect and destroy the instance to force a full reset on the next job.
            epsonPrinter?.let {
                try {
                    it.disconnect()
                } catch (disconnectError: Epos2Exception) {
                    Log.e(TAG, "Failed to disconnect after error.", disconnectError)
                }
            }
            epsonPrinter = null
            Pair(false, "Printer error. Code: ${e.errorStatus}")
        }
    }
}
