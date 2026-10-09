package com.example.seacargosticker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var customer: EditText
    private lateinit var phone: EditText
    private lateinit var destination: EditText
    private lateinit var reference: EditText
    private lateinit var weight: EditText
    private lateinit var packageCount: EditText
    private lateinit var preview: TextView
    private val prefs by lazy { getSharedPreferences("cargo_data", MODE_PRIVATE) }
    private var selectedDevice: BluetoothDevice? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updatePreview()
        requestBluetoothPermissionIfNeeded()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(0xFFF5F7FA.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "SEA CARGO\nSTICKER MAKER"
            textSize = 24f
            setTextColor(0xFF12345A.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        })
        customer = field(root, "Customer name")
        phone = field(root, "Phone number")
        destination = field(root, "Destination / Address")
        reference = field(root, "Cargo reference / Tracking no.")
        weight = field(root, "Weight (kg)", "1.0")
        packageCount = field(root, "Number of packages", "1")

        val saved = Button(this).apply {
            text = "Save customer details"
            setOnClickListener { saveCustomer() }
        }
        root.addView(saved, buttonParams())
        val load = Button(this).apply {
            text = "Load saved customer"
            setOnClickListener { chooseSavedCustomer() }
        }
        root.addView(load, buttonParams())

        root.addView(TextView(this).apply {
            text = "LABEL PREVIEW"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF12345A.toInt())
            setPadding(0, dp(16), 0, dp(8))
        })
        preview = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF111827.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        root.addView(Button(this).apply {
            text = "Update preview"
            setOnClickListener { updatePreview() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "Export labels as PDF"
            setOnClickListener { exportPdf() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "Choose paired Bluetooth printer"
            setOnClickListener { choosePrinter() }
        }, buttonParams())
        root.addView(Button(this).apply {
            text = "Print all labels"
            setOnClickListener { printLabels() }
        }, buttonParams())
        root.addView(TextView(this).apply {
            text = "Bluetooth printing uses generic ESC/POS. Printer compatibility depends on the model."
            textSize = 12f
            setTextColor(0xFF5B6472.toInt())
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun field(parent: LinearLayout, hint: String, value: String = ""): EditText {
        val edit = EditText(this).apply {
            this.hint = hint
            setText(value)
            textSize = 16f
            singleLine = hint != "Destination / Address"
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        parent.addView(edit, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        return edit
    }

    private fun buttonParams() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(8)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun count(): Int = (packageCount.text.toString().toIntOrNull() ?: 1).coerceIn(1, 100)
    private fun safeWeight(): String = weight.text.toString().trim().ifBlank { "—" }
    private fun labelText(index: Int): String {
        return """
            SEA CARGO
            ------------------------------
            CUSTOMER: ${customer.text.toString().ifBlank { "—" }}
            PHONE: ${phone.text.toString().ifBlank { "—" }}
            DESTINATION: ${destination.text.toString().ifBlank { "—" }}
            REF: ${reference.text.toString().ifBlank { "—" }}
            WEIGHT: ${safeWeight()} KG
            PACKAGE: $index/${count()}
            ------------------------------
        """.trimIndent()
    }

    private fun updatePreview() {
        preview.text = labelText(1)
    }

    private fun saveCustomer() {
        val name = customer.text.toString().trim()
        if (name.isBlank()) {
            toast("Enter a customer name first")
            return
        }
        val old = prefs.getString("customers", "") ?: ""
        val record = listOf(name, phone.text.toString(), destination.text.toString())
            .joinToString("|") { it.replace("|", " ") }
        val records = old.split("\n").filter { it.isNotBlank() && !it.startsWith("$name|") }
        prefs.edit().putString("customers", (records + record).joinToString("\n")).apply()
        toast("Customer details saved on this phone")
    }

    private fun chooseSavedCustomer() {
        val records = (prefs.getString("customers", "") ?: "").split("\n").filter { it.isNotBlank() }
        if (records.isEmpty()) {
            toast("No saved customers yet")
            return
        }
        val names = records.map { it.substringBefore("|") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose saved customer")
            .setItems(names) { _, which ->
                val parts = records[which].split("|")
                customer.setText(parts.getOrElse(0) { "" })
                phone.setText(parts.getOrElse(1) { "" })
                destination.setText(parts.getOrElse(2) { "" })
                updatePreview()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun exportPdf() {
        try {
            val document = PdfDocument()
            val pageWidth = 283  // approximately 100 mm at 72 dpi
            val pageHeight = 425 // approximately 150 mm at 72 dpi
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
            for (i in 1..count()) {
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i).create()
                val page = document.startPage(info)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 22f
                page.canvas.drawText("SEA CARGO", 18f, 34f, paint)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 12f
                var y = 65f
                labelText(i).lines().drop(2).dropLast(1).forEach { line ->
                    page.canvas.drawText(line.take(42), 18f, y, paint)
                    y += 24f
                }
                paint.strokeWidth = 1.5f
                page.canvas.drawRect(8f, 8f, pageWidth - 8f, pageHeight - 8f, paint)
                document.finishPage(page)
            }
            val filename = "SeaCargo_${System.currentTimeMillis()}.pdf"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val resolver = contentResolver
            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                document.close()
                toast("Could not create PDF")
                return
            }
            resolver.openOutputStream(uri)?.use { document.writeTo(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            document.close()
            toast("PDF saved in Downloads: $filename")
        } catch (e: Exception) {
            toast("PDF error: ${e.message}")
        }
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 401)
        }
    }

    private fun choosePrinter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 401)
            toast("Allow Bluetooth permission, then tap again")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            toast("This phone has no Bluetooth adapter")
            return
        }
        if (!adapter.isEnabled) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 402)
            return
        }
        try {
            val devices = adapter.bondedDevices.toList()
            if (devices.isEmpty()) {
                toast("Pair your printer in Android Settings first")
                return
            }
            val labels = devices.map { "${it.name ?: "Bluetooth device"} (${it.address})" }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Paired Bluetooth devices")
                .setItems(labels) { _, which ->
                    selectedDevice = devices[which]
                    toast("Selected: ${selectedDevice?.name}")
                }.setNegativeButton("Cancel", null).show()
        } catch (e: SecurityException) {
            toast("Bluetooth permission is required")
        }
    }

    private fun printLabels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 401)
            toast("Allow Bluetooth permission, then tap Print again")
            return
        }
        val device = selectedDevice
        if (device == null) {
            toast("Choose a paired printer first")
            return
        }
        thread {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                val out: OutputStream = socket.outputStream
                out.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialize
                for (i in 1..count()) {
                    val text = labelText(i).replace("—", "-")
                    out.write(byteArrayOf(0x1B, 0x61, 0x01)) // center
                    out.write(byteArrayOf(0x1B, 0x21, 0x10)) // double-height text
                    out.write("SEA CARGO\n".toByteArray(Charsets.UTF_8))
                    out.write(byteArrayOf(0x1B, 0x21, 0x00))
                    out.write(byteArrayOf(0x1B, 0x61, 0x00))
                    text.lines().drop(1).forEach { line ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(byteArrayOf(0x0A))
                    }
                    out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                }
                out.flush()
                runOnUiThread { toast("Print data sent. Check printer output.") }
            } catch (e: Exception) {
                runOnUiThread { toast("Print failed: ${e.message ?: "check printer compatibility"}") }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
