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

class MainActivity : Activity() {
    private val silver = Color.rgb(205, 210, 218)
    private val muted = Color.rgb(145, 151, 160)
    private val panel = Color.rgb(27, 29, 34)
    private val bg = Color.rgb(12, 13, 16)
    private var selectedUri: Uri? = null
    private lateinit var fileInfo: TextView
    private lateinit var status: TextView

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
        val brand = TextView(this).apply {
            text = "XPD  /  DIRECT INSTALL"
            textSize = 21f; setTextColor(silver); letterSpacing = .12f
        }
        content.addView(brand)
        content.addView(label("PKG SENDER FOR ANDROID", 11f, muted).apply { setPadding(0, 8, 0, 28) })
        content.addView(sectionTitle("CONSOLE CONNECTION"))
        val ip = EditText(this).apply {
            hint = "PS4 IP address  (e.g. 192.168.1.50)"
            setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 14f
            setSingleLine(true); setPadding(14, 13, 14, 13); setBackgroundColor(panel)
            inputType =  android.text.InputType.TYPE_CLASS_TEXT
        }
        content.addView(ip, params(LinearLayout.LayoutParams.MATCH_PARENT, 54).apply { bottomMargin = 10 })
        val port = EditText(this).apply {
            hint = "Port (confirm with your PS4 receiver)"
            setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 14f
            setSingleLine(true); setPadding(14, 13, 14, 13); setBackgroundColor(panel)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        content.addView(port, params(LinearLayout.LayoutParams.MATCH_PARENT, 54))
        content.addView(sectionTitle("PACKAGE FILE").apply { setPadding(0, 28, 0, 12) })
        val choose = Button(this).apply {
            text = "＋   CHOOSE PKG FILE"
            setTextColor(silver); setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                }
                startActivityForResult(intent, 41)
            }
        }
        content.addView(choose, params(LinearLayout.LayoutParams.MATCH_PARENT, 54))
        fileInfo = label("No package selected", 13f, muted).apply { setPadding(2, 12, 2, 12) }
        content.addView(fileInfo)
        val send = Button(this).apply {
            text = "SEND TO PS4"
            isEnabled = false
            setTextColor(Color.BLACK)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(silver))
            setOnClickListener {
                status.text = "Receiver protocol is not configured yet. No data has been sent."
            }
        }
        content.addView(send, params(LinearLayout.LayoutParams.MATCH_PARENT, 56).apply { topMargin = 18 })
        status = label("Ready • Select a PKG to begin", 12f, muted).apply { setPadding(2, 18, 2, 8) }
        content.addView(status)
        content.addView(sectionTitle("TRANSFER QUEUE").apply { setPadding(0, 24, 0, 10) })
        content.addView(label("Your selected packages and transfer progress will appear here.", 12f, muted))
        content.addView(label("XPD • Local network transfer • Android", 10f, muted).apply {
            gravity = Gravity.CENTER; setPadding(0, 40, 0, 12)
        })
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun sectionTitle(s: String) = label(s, 12f, silver).apply { letterSpacing = .08f }
    private fun label(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
    }
    private fun params(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    @Deprecated("Legacy result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedUri = uri
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = queryName(uri) ?: "Selected package"
                fileInfo.text = name
                status.text = "Package selected • Transfer protocol pending verification"
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
        } finally { cursor?.close() }
    }
}
