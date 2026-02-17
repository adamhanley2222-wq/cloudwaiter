package com.example.raivodashboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.raivodashboard.EpsonPrinter
import com.example.raivodashboard.data.Order
import com.example.raivodashboard.data.OrderStatus

sealed class DashboardTab(val title: String) {
    object New : DashboardTab("NEW")
    object Preparing : DashboardTab("PREP")
    object Ready : DashboardTab("READY")
    object Completed : DashboardTab("HISTORY")
    object Cancelled : DashboardTab("CXL")
}

private val tabs = listOf(DashboardTab.New, DashboardTab.Preparing, DashboardTab.Ready, DashboardTab.Completed, DashboardTab.Cancelled)

private fun colorForTab(tab: DashboardTab): Color {
    return when (tab) {
        DashboardTab.New -> Color(0xFF4A90E2)
        DashboardTab.Preparing -> Color(0xFFFFD700)
        DashboardTab.Ready -> Color(0xFF7ED321)
        DashboardTab.Completed -> Color(0xFFBDBDBD)
        DashboardTab.Cancelled -> Color(0xFFD0021B)
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf<DashboardTab>(DashboardTab.New) }
    val context = LocalContext.current

    val epsonPrinter = remember { EpsonPrinter(context) }
    var orderToPrint by remember { mutableStateOf<Order?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            orderToPrint?.let {
                epsonPrinter.print(it)
                Toast.makeText(context, "Printing order #${it.id?.takeLast(3)}...", Toast.LENGTH_SHORT).show()
                orderToPrint = null
            }
        } else {
            Toast.makeText(context, "Bluetooth permissions are required to print.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestPermissionsAndPrint(order: Order) {
        orderToPrint = order

        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNotGranted.toTypedArray())
        } else {
            epsonPrinter.print(order)
            Toast.makeText(context, "Printing order #${order.id?.takeLast(3) ?: ""}...", Toast.LENGTH_SHORT).show()
        }
    }

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
                                        withStyle(style = SpanStyle(color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
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

        val filteredOrders = when (selectedTab) {
            DashboardTab.New -> uiState.orders.filter { it.status == OrderStatus.NEW }
            DashboardTab.Preparing -> uiState.orders.filter { it.status == OrderStatus.PREPARING }
            DashboardTab.Ready -> uiState.orders.filter { it.status == OrderStatus.READY }
            DashboardTab.Completed -> uiState.orders.filter { it.status == OrderStatus.COMPLETED }
            DashboardTab.Cancelled -> uiState.orders.filter { it.status == OrderStatus.CANCELLED }
        }

        LazyColumn {
            items(filteredOrders) { order ->
                OrderCard(
                    order = order,
                    onStatusChange = { newStatus ->
                        if (order.status == OrderStatus.NEW && newStatus == OrderStatus.PREPARING) {
                            requestPermissionsAndPrint(order)
                        }
                        order.id?.let { id -> viewModel.updateOrderStatus(id, newStatus) }
                    },
                    cardColor = selectedColor,
                    onPrint = {
                        Log.d("DashboardScreen", "Print button clicked for order: ${order.id}")
                        requestPermissionsAndPrint(order)
                    }
                )
            }
        }
    }
}
