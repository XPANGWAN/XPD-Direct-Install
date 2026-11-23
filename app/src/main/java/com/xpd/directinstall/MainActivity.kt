package com.xpd.directinstall

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.database.Cursor
import android.provider.OpenableColumns
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

class MainActivity : Activity() {
    private val silver = Color.rgb(205, 210, 218)
    private val muted = Color.rgb(145, 151, 160)
    private val panel = Color.rgb(27, 29, 34)
    private val bg = Color.rgb(12, 13, 16)

    private var selectedUri: Uri? = null
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var fileInfo: TextView
    private lateinit var status: TextView
    private lateinit var connectionButton: Button
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 22)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content.addView(label("XPD  /  DIRECT INSTALL", 21f, silver).apply { letterSpacing = .12f })
        content.addView(label("PKG SENDER FOR ANDROID", 11f, muted).apply { setPadding(0, 8, 0, 28) })
        content.addView(sectionTitle("CONSOLE CONNECTION"))

        ipInput = input("PS4 IP address  (e.g. 192.168.1.50)", false)
        content.addView(ipInput, params(LinearLayout.LayoutParams.MATCH_PARENT, 54).apply { bottomMargin = 10 })

        portInput = input("Receiver port", true)
        content.addView(portInput, params(LinearLayout.LayoutParams.MATCH_PARENT, 54))

        connectionButton = Button(this).apply {
            text = "TEST CONNECTION"
            setTextColor(silver)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
            setOnClickListener { testConnection() }
        }
        content.addView(connectionButton, params(LinearLayout.LayoutParams.MATCH_PARENT, 52).apply { topMargin = 12 })

        content.addView(sectionTitle("PACKAGE FILE").apply { setPadding(0, 28, 0, 12) })
        val choose = Button(this).apply {
            text = "CHOOSE PKG FILE"
            setTextColor(silver)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/vnd.playstation.pkg", "application/octet-stream"
                    ))
                }
                startActivityForResult(intent, 41)
            }
        }
        content.addView(choose, params(LinearLayout.LayoutParams.MATCH_PARENT, 54))
        fileInfo = label("No package selected", 13f, muted).apply { setPadding(2, 12, 2, 12) }
        content.addView(fileInfo)

        sendButton = Button(this).apply {
            text = "SEND TO PS4"
            isEnabled = false
            setTextColor(Color.BLACK)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(silver))
            setOnClickListener {
                status.text = "Sending is locked until the receiver protocol is verified."
            }
        }
        content.addView(sendButton, params(LinearLayout.LayoutParams.MATCH_PARENT, 56).apply { topMargin = 18 })

        status = label("Ready • Choose a PKG and test your receiver", 12f, muted).apply { setPadding(2, 18, 2, 8) }
        content.addView(status)
        content.addView(sectionTitle("TRANSFER QUEUE").apply { setPadding(0, 24, 0, 10) })
        content.addView(label("Selected package details and verified transfer progress will appear here.", 12f, muted))
        content.addView(label("XPD • Local network transfer • Android", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 12)
        })

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun input(hintText: String, numeric: Boolean) = EditText(this).apply {
        hint = hintText
        setHintTextColor(muted)
        setTextColor(Color.WHITE)
        textSize = 14f
        setSingleLine(true)
        setPadding(14, 13, 14, 13)
        setBackgroundColor(panel)
        inputType = if (numeric) android.text.InputType.TYPE_CLASS_NUMBER
                    else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun testConnection() {
        val host = ipInput.text.toString().trim()
        val port = portInput.text.toString().trim().toIntOrNull()
        if (!host.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) ||
            host.split(".").any { it.toIntOrNull() !in 0..255 }) {
            status.text = "Enter a valid IPv4 address."
            return
        }
        if (port == null || port !in 1..65535) {
            status.text = "Enter a valid receiver port (1–65535)."
            return
        }

        connectionButton.isEnabled = false
        status.text = "Testing connection to $host:$port …"
        Thread {
            val result = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 3500)
                }
                "Connection successful • $host:$port is reachable."
            } catch (_: Exception) {
                "Connection failed • Check the PS4 receiver, IP, port and Wi-Fi."
            }
            runOnUiThread {
                status.text = result
                connectionButton.isEnabled = true
            }
        }.start()
    }

    private fun sectionTitle(s: String) = label(s, 12f, silver).apply { letterSpacing = .08f }
    private fun label(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
    }
    private fun params(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    @Deprecated("Legacy result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val name = queryName(uri) ?: "Selected package"
                if (!name.lowercase(Locale.ROOT).endsWith(".pkg")) {
                    status.text = "Invalid file • Choose a file with the .pkg extension."
                    return
                }
                selectedUri = uri
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // The current read grant remains available for this activity session.
                }
                fileInfo.text = name
                status.text = "PKG selected • Receiver protocol still needs verification."
            }
        }
    }

    private fun queryName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) cursor.getString(i) else uri.lastPathSegment
            } else uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }
}
