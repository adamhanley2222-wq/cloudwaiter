package com.example.raivodashboard

import android.content.Context
import android.util.Log
import com.epson.epos2.Epos2Exception
import com.epson.epos2.discovery.Discovery
import com.epson.epos2.discovery.DiscoveryListener
import com.epson.epos2.discovery.FilterOption
import com.epson.epos2.printer.Printer
import com.example.raivodashboard.data.Order
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpsonPrinter(private val context: Context) {

    private var mPrinter: Printer? = null
    private var mPrinterTarget: String? = null
    private var mPrinterSeries: Int = -1 // Variable to hold the correct printer series
    private var orderForFirstPrint: Order? = null // Variable to hold order for the first print job

    private val mDiscoveryListener = DiscoveryListener { deviceInfo ->
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
            Log.d("EpsonPrinter", "Found printer: ${deviceInfo.deviceName}, Series: $mPrinterSeries, Target: $mPrinterTarget")

            orderForFirstPrint?.let {
                print(it)
                orderForFirstPrint = null
            }
        }
    }

    fun print(order: Order) {
        if (mPrinterTarget == null) {
            Log.d("EpsonPrinter", "Printer not yet discovered. Starting discovery.")
            orderForFirstPrint = order
            startDiscovery()
            return
        }

        Log.d("EpsonPrinter", "Printer already discovered. Printing directly.")
        Thread {
            runPrintSequence(order)
        }.start()
    }

    private fun startDiscovery() {
        try {
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
        if (!initializeObject()) return
        if (!createReceiptData(order)) {
            finalizeObject()
            return
        }
        if (!sendData()) {
            // Finalize/disconnect is handled within sendData on failure
        }
    }

    private fun initializeObject(): Boolean {
        if (mPrinterSeries == -1) {
            Log.e("EpsonPrinter", "Printer series not yet discovered. Cannot initialize object.")
            return false
        }
        try {
            mPrinter = Printer(mPrinterSeries, Printer.MODEL_ANK, context)
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error creating printer object: ${e.errorStatus}")
            return false
        }

        mPrinter?.setReceiveEventListener { _, code, status, _ ->
            Log.d("EpsonPrinter", "Print job finished. Code: $code")
            disconnectPrinter()
            finalizeObject()
        }
        return true
    }

    private fun createReceiptData(order: Order): Boolean {
        val printer = mPrinter ?: return false
        try {
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

            // Customer Name
            printer.addTextAlign(Printer.ALIGN_LEFT)
            printer.addTextSize(2, 2)
            printer.addText("Name: ${order.customerName ?: "N/A"}\n\n")
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

        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error creating receipt data: ${e.errorStatus}")
            return false
        }
        return true
    }

    private fun sendData(): Boolean {
        val printer = mPrinter ?: return false

        if (!connectPrinter()) {
            finalizeObject()
            return false
        }

        try {
            printer.beginTransaction()
            printer.sendData(Printer.PARAM_DEFAULT)
            Log.d("EpsonPrinter", "Sent print data. Waiting for async callback to disconnect.")
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error sending data: ${e.errorStatus}")
            try {
                printer.endTransaction()
            } catch (ex: Epos2Exception) {
                // ignore
            }
            disconnectPrinter()
            finalizeObject()
            return false
        }

        return true
    }

    private fun connectPrinter(): Boolean {
        val printer = mPrinter ?: return false
        try {
            printer.connect(mPrinterTarget, Printer.PARAM_DEFAULT)
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error connecting to printer: ${e.errorStatus}")
            return false
        }
        return true
    }

    private fun disconnectPrinter() {
        try {
            mPrinter?.disconnect()
        } catch (e: Epos2Exception) {
            Log.e("EpsonPrinter", "Error disconnecting printer: ${e.errorStatus}")
        }
    }

    private fun finalizeObject() {
        mPrinter?.setReceiveEventListener(null)
        mPrinter = null
    }
}
