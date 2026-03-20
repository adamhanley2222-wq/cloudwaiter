package com.example.raivodashboard

import android.content.Context
import android.util.Log
import com.epson.epos2.Epos2Exception
import com.epson.epos2.discovery.Discovery
import com.epson.epos2.discovery.DiscoveryListener
import com.epson.epos2.discovery.FilterOption
import com.epson.epos2.printer.Printer
import com.epson.epos2.printer.PrinterStatusInfo
import com.example.raivodashboard.data.Order
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.Queue

class EpsonPrinter(private val context: Context) {

    private var mPrinter: Printer? = null
    private var mPrinterTarget: String? = null
    private var mPrinterSeries: Int = -1 
    private val mPrintQueue: Queue<Order> = LinkedList()
    private var isPrinting: Boolean = false

    private val mDiscoveryListener = DiscoveryListener { deviceInfo ->
        Log.d("EpsonPrinter", "Discovery found device: ${deviceInfo.deviceName}")
        if (deviceInfo.deviceName.startsWith("TM-T88")) {
            try {
                Discovery.stop()
            } catch (e: Epos2Exception) {
                if (e.errorStatus != Epos2Exception.ERR_PROCESSING) {
                    Log.e("EpsonPrinter", "Error stopping discovery: ${e.errorStatus}")
                }
            }

            mPrinterTarget = deviceInfo.target
            mPrinterSeries = deviceInfo.deviceType
            Log.d("EpsonPrinter", "Target Printer Found: ${deviceInfo.deviceName}, Series: $mPrinterSeries, Target: $mPrinterTarget")

            // Process any queued orders once discovery is finished
            processQueue()
        }
    }

    private fun formatPhoneNumber(phone: Long?): String {
        if (phone == null) return "N/A"
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

    @Synchronized
    fun print(order: Order) {
        mPrintQueue.add(order)
        Log.d("EpsonPrinter", "Order added to print queue. Queue size: ${mPrintQueue.size}")
        
        if (mPrinterTarget == null) {
            Log.d("EpsonPrinter", "Printer target is null. Starting discovery.")
            startDiscovery()
            return
        }

        if (!isPrinting) {
            processQueue()
        } else {
            Log.d("EpsonPrinter", "Printer is already busy. Order will print when current job is finished.")
        }
    }

    @Synchronized
    private fun processQueue() {
        if (isPrinting) return
        
        val nextOrder = mPrintQueue.poll() ?: return
        isPrinting = true
        
        Log.d("EpsonPrinter", "Processing next order in queue: #${nextOrder.id?.takeLast(3)}")
        Thread {
            runPrintSequence(nextOrder)
        }.start()
    }

    private fun startDiscovery() {
        try {
            Log.d("EpsonPrinter", "Starting Bluetooth discovery...")
            val filterOption = FilterOption()
            filterOption.deviceType = Discovery.TYPE_PRINTER
            filterOption.epsonFilter = Discovery.FILTER_NONE
            filterOption.portType = Discovery.PORTTYPE_BLUETOOTH
            Discovery.start(context, filterOption, mDiscoveryListener)
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error starting discovery: ${e.errorStatus}")
        }
    }

    private fun runPrintSequence(order: Order) {
        Log.d("EpsonPrinter", "Beginning print sequence for order #${order.id?.takeLast(3)}")
        if (!initializeObject()) {
            Log.e("EpsonPrinter", "Failed to initialize printer object.")
            onPrintFinished()
            return
        }
        if (!createReceiptData(order)) {
            Log.e("EpsonPrinter", "Failed to create receipt data.")
            finalizeObject()
            onPrintFinished()
            return
        }
        if (!sendData()) {
            Log.e("EpsonPrinter", "Failed to send data to printer.")
            // onPrintFinished() will be called from finalizeObject/disconnect failures in sendData logic
            // But let's add it here to be safe if sendData doesn't clean up
            finalizeObject()
            onPrintFinished()
        }
    }

    private fun initializeObject(): Boolean {
        if (mPrinterSeries == -1) {
            Log.e("EpsonPrinter", "Printer series is unknown.")
            return false
        }
        try {
            mPrinter = Printer(mPrinterSeries, Printer.MODEL_ANK, context)
            Log.d("EpsonPrinter", "Printer object initialized.")
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error creating printer object: ${e.errorStatus}")
            return false
        }

        mPrinter?.setReceiveEventListener { _, code, status, _ ->
            Log.d("EpsonPrinter", "Receive Event Callback - Code: $code")
            logStatus(status)
            disconnectPrinter()
            finalizeObject()
            onPrintFinished()
        }
        return true
    }

    @Synchronized
    private fun onPrintFinished() {
        isPrinting = false
        Log.d("EpsonPrinter", "Print job finished. Checking for more orders in queue...")
        processQueue()
    }

    private fun logStatus(status: PrinterStatusInfo?) {
        if (status == null) return
        Log.d("EpsonPrinter", "Printer Status: Connection=${status.connection}, Online=${status.online}, Paper=${status.paper}")
    }

    private fun createReceiptData(order: Order): Boolean {
        val printer = mPrinter ?: return false
        try {
            Log.d("EpsonPrinter", "Building receipt data...")
            // Header
            printer.addTextAlign(Printer.ALIGN_CENTER)
            printer.addTextSize(2, 2)
            printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT)
            printer.addText("TAKEAWAY\n")
            printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
            printer.addTextSize(1, 1)

            // Order ID & Date
            printer.addText("Order #${order.id?.takeLast(3) ?: "N/A"}\n")
            val formattedDateTime = SimpleDateFormat("EEE dd/MM/yy hh:mm:ss a", Locale.getDefault()).format(Date(order.timestamp ?: 0))
            printer.addText("$formattedDateTime\n")

            // Separator
            printer.addText("------------------------------------------\n")
            printer.addFeedLine(1)

            // Customer Name & Phone
            printer.addTextAlign(Printer.ALIGN_LEFT)
            printer.addTextSize(2, 2)
            printer.addText("Name: ${order.customerName ?: "N/A"}\n")
            val phone = order.callerPhone ?: order.customerPhone
            printer.addText("Phone: ${formatPhoneNumber(phone)}\n\n")
            printer.addTextSize(1, 1)

            // Items
            order.items?.forEach { item ->
                printer.addTextSize(2, 2)
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT)
                printer.addText("${item.quantity ?: 0} ${item.name ?: "Item"}\n")
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
                printer.addTextSize(1, 1)

                if (!item.spiceLevel.isNullOrBlank()) {
                    printer.addText("  > ${item.spiceLevel}\n")
                }
                if (!item.details.isNullOrBlank() && item.details != "None") {
                    printer.addText("  > ${item.details}\n")
                }
            }
            printer.addFeedLine(1)

            // Special Requests - WHITE on BLACK
            if (!order.specialRequests.isNullOrBlank() && order.specialRequests != "None") {
                printer.addTextStyle(Printer.TRUE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
                printer.addTextSize(2, 2)
                printer.addText("${order.specialRequests} \n")
                printer.addTextSize(1, 1)
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
                printer.addFeedLine(1)
            }

            // Pickup Time
            if (!order.pickupTime.isNullOrBlank() && order.pickupTime != "N/A") {
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT)
                printer.addTextSize(2, 1)
                printer.addText("PICKUP: ${order.pickupTime}\n\n")
                printer.addTextSize(1, 1)
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT)
            }

            // Total
            order.totalCost?.let {
                val costAsDouble = when (val cost = it) {
                    is Double -> cost
                    is Long -> cost.toDouble()
                    is String -> cost.replace(Regex("[^\\d.]"), "").toDoubleOrNull()
                    else -> null
                } ?: 0.0
                val formattedTotal = NumberFormat.getCurrencyInstance(Locale("en", "US")).format(costAsDouble)
                printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT)
                printer.addTextSize(2, 2)
                printer.addText("TOTAL: $formattedTotal\n")
            }

            printer.addFeedLine(5)
            printer.addCut(Printer.CUT_FEED)
            Log.d("EpsonPrinter", "Receipt data build complete.")

        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error creating receipt data: ${e.errorStatus}")
            return false
        }
        return true
    }

    private fun sendData(): Boolean {
        val printer = mPrinter ?: return false

        Log.d("EpsonPrinter", "Attempting to connect to printer at $mPrinterTarget...")
        if (!connectPrinter()) {
            Log.e("EpsonPrinter", "Connection failed.")
            return false
        }

        try {
            Log.d("EpsonPrinter", "Sending data to printer...")
            printer.beginTransaction()
            printer.sendData(Printer.PARAM_DEFAULT)
            Log.d("EpsonPrinter", "Data sent. Waiting for completion...")
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error during sendData: ${e.errorStatus}")
            try {
                printer.endTransaction()
            } catch (ex: Epos2Exception) {
                // ignore
            }
            disconnectPrinter()
            return false
        }

        return true
    }

    private fun connectPrinter(): Boolean {
        val printer = mPrinter ?: return false
        try {
            printer.connect(mPrinterTarget, Printer.PARAM_DEFAULT)
            Log.d("EpsonPrinter", "Printer connected successfully.")
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Connection error: ${e.errorStatus}")
            return false
        }
        return true
    }

    private fun disconnectPrinter() {
        try {
            Log.d("EpsonPrinter", "Disconnecting printer...")
            mPrinter?.disconnect()
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error during disconnect: ${e.errorStatus}")
        }
    }

    private fun finalizeObject() {
        Log.d("EpsonPrinter", "Finalizing printer object.")
        mPrinter?.clearCommandBuffer()
        mPrinter?.setReceiveEventListener(null)
        mPrinter = null
    }
}
