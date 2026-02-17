package com.example.raivodashboard.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.raivodashboard.R
import com.example.raivodashboard.data.Order
import com.example.raivodashboard.data.OrderStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

// Helper to format the timestamp
private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null) return ""
    val date = Date(timestamp)
    val format = SimpleDateFormat("EEE, HH:mm", Locale.getDefault())
    return format.format(date)
}

// Helper to format currency
private fun formatCurrency(amount: Double): String {
    return NumberFormat.getCurrencyInstance(Locale.US).format(amount)
}

// Helper to format phone number
private fun formatPhoneNumber(phone: Long?): String {
    if (phone == null) return ""
    val phoneStr = phone.toString()
    val localNumber = if (phoneStr.startsWith("61")) {
        "0" + phoneStr.substring(2)
    } else {
        phoneStr
    }

    if (localNumber.length == 10) {
        return localNumber.substring(0, 4) + " " + localNumber.substring(4, 7) + " " + localNumber.substring(7, 10)
    }
    return localNumber
}

@Composable
fun ConversationDialog(conversationTranscript: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation Transcript") },
        text = {
            val lines = conversationTranscript.split(Regex("(?=agent:)|(?=user:)"))
            LazyColumn {
                items(lines.filter { it.isNotBlank() }) { line ->
                    val annotatedString = buildAnnotatedString {
                        if (line.startsWith("agent:")) {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF007BFF))) {
                                append("Agent: ")
                            }
                            append(line.substringAfter("agent:").trim())
                        } else if (line.startsWith("user:")) {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF28A745))) {
                                append("User: ")
                            }
                            append(line.substringAfter("user:").trim())
                        } else {
                            append(line.trim())
                        }
                    }
                    Text(annotatedString, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun OrderCard(
    order: Order,
    onStatusChange: (OrderStatus) -> Unit,
    cardColor: Color,
    onPrint: () -> Unit
) {
    val context = LocalContext.current
    var showConversationDialog by remember { mutableStateOf(false) }

    if (showConversationDialog && !order.conversationTranscript.isNullOrBlank()) {
        ConversationDialog(conversationTranscript = order.conversationTranscript!!) {
            showConversationDialog = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Top row with Order ID, Time, Customer, and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#${order.id?.takeLast(3) ?: ""}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                Text(formatTimestamp(order.timestamp), fontSize = 10.sp, color = Color.Black)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Log the conversation transcript to help debug why the button might not be showing
                    Log.d("OrderCardDebug", "Order #${order.id?.takeLast(3)} | conversationTranscript: '${order.conversationTranscript}'")

                    if (!order.conversationTranscript.isNullOrBlank()) {
                        CompactButton(onClick = { showConversationDialog = true }, text = "T")
                        Spacer(Modifier.width(4.dp))
                    }
                    if (!order.recordingUrl.isNullOrBlank()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, order.recordingUrl.toUri())
                            context.startActivity(intent)
                        }) {
                            Icon(painter = painterResource(id = R.drawable.ic_play_arrow), contentDescription = "Play Recording")
                        }
                    }
                    Column {
                        Text(order.customerName ?: "Unknown", fontSize = 16.sp, color = Color.Black)
                        val phone = order.callerPhone ?: order.customerPhone
                        phone?.let {
                            Text(formatPhoneNumber(it), fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }

                Text(order.status?.name ?: "Unknown", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }

            // Combined Row for Special Requests and Pickup Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!order.specialRequests.isNullOrBlank()) {
                    Text(
                        text = order.specialRequests,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f)) // This will push pickup time to the right
                if (!order.pickupTime.isNullOrBlank() && order.pickupTime != "N/A") {
                    Text(
                        text = "Pickup: ${order.pickupTime}",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Items list (full width)
            Column(modifier = Modifier.fillMaxWidth()) {
                (order.items ?: emptyList()).forEach { item ->
                    val spice = if (!item.spiceLevel.isNullOrBlank()) " (${item.spiceLevel})" else ""
                    val details = if (!item.details.isNullOrBlank() && item.details != "None") " - ${item.details}" else ""
                    Text(
                        text = "${item.quantity ?: 0}x ${item.name ?: "Item"}$spice$details",
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row for buttons and total cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left-aligned buttons
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (order.status) {
                        OrderStatus.NEW -> {
                            CompactButton(onClick = { onStatusChange(OrderStatus.CANCELLED) }, text = "Cancel")
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactButton(onClick = { onPrint() }, text = "Print")
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactButton(onClick = { onStatusChange(OrderStatus.PREPARING) }, text = "Accept")
                        }
                        OrderStatus.PREPARING -> {
                            CompactButton(onClick = { onPrint() }, text = "Print")
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactButton(onClick = { onStatusChange(OrderStatus.READY) }, text = "Ready")
                        }
                        OrderStatus.READY -> {
                            CompactButton(onClick = { onPrint() }, text = "Print")
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactButton(onClick = { onStatusChange(OrderStatus.PREPARING) }, text = "To Prep")
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactButton(onClick = { onStatusChange(OrderStatus.COMPLETED) }, text = "Complete")
                        }
                        OrderStatus.COMPLETED -> {
                            CompactButton(onClick = { onPrint() }, text = "Print")
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactButton(onClick = { onStatusChange(OrderStatus.READY) }, text = "To Ready")
                        }
                        OrderStatus.CANCELLED -> {
                            CompactButton(onClick = { onPrint() }, text = "Print")
                            Spacer(modifier = Modifier.width(4.dp))
                            CompactButton(onClick = { onStatusChange(OrderStatus.NEW) }, text = "Restore")
                        }
                        null -> { /* Do nothing */ }
                    }
                }

                // Right-aligned Total Cost
                if (order.status != OrderStatus.CANCELLED) {
                    val costAsDouble = when (val cost = order.totalCost) {
                        is Double -> cost
                        is Long -> cost.toDouble()
                        is String -> cost.replace(Regex("[^\\d.]"), "").toDoubleOrNull()
                        else -> null
                    } ?: 0.0

                    Text(
                        text = "Total: ${formatCurrency(costAsDouble)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactButton(onClick: () -> Unit, text: String) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 11.sp)
    }
}
