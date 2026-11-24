package com.xpd.directinstall

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GoldHEN BinLoader bridge.
 *
 * The bundled PS4 installer payload is from DirectPackageInstaller (GPL-3.0).
 * XPD supplies the phone-side metadata service and transfer orchestration.
 */
class GoldHenPayloadBridge(
    private val context: Context,
    private val entries: List<PkgEntry>,
    private val phoneIp: String,
    private val onStatus: (String) -> Unit,
    private val onFinished: (Boolean, String) -> Unit
) {
    private val stopped = AtomicBoolean(false)
    @Volatile private var service: ServerSocket? = null

    fun start(consoleIp: String, consolePort: Int = 9090): Boolean {
        return try {
            val bindAddress = InetAddress.getByName(phoneIp)
            val listener = ServerSocket(0, 8, bindAddress).apply {
                soTimeout = 45000
            }
            service = listener
            val port = listener.localPort
            val payload = loadPayload()
            val marker = byteArrayOf(
                0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte(),
                0xB4.toByte(), 0xB4.toByte()
            )
            val offset = payload.indexOf(marker)
            require(offset >= 0) { "GoldHEN payload address marker is missing" }
            val ipBytes = bindAddress.address
            require(ipBytes.size == 4) { "Phone must use an IPv4 address" }
            System.arraycopy(ipBytes, 0, payload, offset, 4)
            payload[offset + 4] = ((port ushr 8) and 0xff).toByte()
            payload[offset + 5] = (port and 0xff).toByte()

            Thread({ servePackages(listener) }, "XPD-GoldHEN-Bridge").apply {
                isDaemon = true
                start()
            }

            onStatus("Connecting to GoldHEN BinLoader on port $consolePort…")
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(java.net.InetSocketAddress(consoleIp, consolePort), 5000)
                socket.soTimeout = 10000
                socket.getOutputStream().use { output ->
                    output.write(payload)
                    output.flush()
                }
            }
            true
        } catch (e: Exception) {
            stop()
            onFinished(false, "Could not start GoldHEN transfer: ${e.message ?: "connection failed"}")
            false
        }
    }

    private fun servePackages(listener: ServerSocket) {
        try {
            entries.forEachIndexed { index, entry ->
                if (stopped.get()) return
                onStatus("GoldHEN: preparing ${index + 1}/${entries.size} — ${entry.metadata?.displayTitle ?: entry.name}")
                val client = acceptPayloadConnection(listener)
                client.use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = 15000
                    val meta = entry.metadata
                    val url = "http://$phoneIp:9898/pkg/${entry.id}"
                    val name = (meta?.displayTitle?.takeIf { it.isNotBlank() } ?: entry.name)
                    val packageId = meta?.contentId?.takeIf { it.isNotBlank() }
                        ?: meta?.titleId?.takeIf { it.isNotBlank() }.orEmpty()
                    val icon = meta?.iconPng ?: byteArrayOf()
                    val packet = ByteArrayOutputStream()
                    DataOutputStream(packet).use { data ->
                        data.writeIntLE(1)
                        data.writeStringLE(url)
                        data.writeStringLE(name)
                        data.writeStringLE(packageId)
                        data.writeStringLE(packageType(meta))
                        data.writeLongLE(entry.size.coerceAtLeast(0L))
                        data.writeIntLE(icon.size)
                        if (icon.isNotEmpty()) data.write(icon)
                    }
                    socket.getOutputStream().apply {
                        write(packet.toByteArray())
                        flush()
                    }
                }
            }
            if (!stopped.get()) {
                val client = acceptPayloadConnection(listener)
                client.use { socket ->
                    socket.getOutputStream().apply {
                        write(byteArrayOf(0, 0, 0, 0))
                        flush()
                    }
                }
                onFinished(true, "GoldHEN received all package tasks. The PS4 is fetching the PKG files; installation progress is shown on the console.")
            }
        } catch (e: SocketTimeoutException) {
            if (!stopped.get()) onFinished(false, "GoldHEN payload did not connect back to the phone. Check that the phone and PS4 are on the same Wi-Fi network.")
        } catch (e: Exception) {
            if (!stopped.get()) onFinished(false, "GoldHEN transfer error: ${e.message ?: "connection interrupted"}")
        } finally {
            try { listener.close() } catch (_: Exception) {}
            service = null
        }
    }

    private fun packageType(meta: PkgMetadata?): String = when (meta?.category?.lowercase()) {
        "ac" -> "PS4AC"
        "gp", "patch" -> "PS4GP"
        else -> "PS4GD"
    }

    private fun acceptPayloadConnection(listener: ServerSocket): Socket {
        val socket = listener.accept()
        socket.keepAlive = true
        return socket
    }

    private fun loadPayload(): ByteArray {
        val encoded = context.assets.open("xpd-dpi-payload.b64")
            .bufferedReader(Charsets.US_ASCII).use { it.readText() }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.isNotEmpty()) { "Bundled GoldHEN payload is empty" }
        return bytes
    }

    fun stop() {
        stopped.set(true)
        try { service?.close() } catch (_: Exception) {}
        service = null
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        if (pattern.isEmpty() || pattern.size > size) return -1
        for (i in 0..size - pattern.size) {
            var match = true
            for (j in pattern.indices) if (this[i + j] != pattern[j]) { match = false; break }
            if (match) return i
        }
        return -1
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun DataOutputStream.writeLongLE(value: Long) {
        write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
    }

    private fun DataOutputStream.writeStringLE(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeIntLE(bytes.size)
        write(bytes)
    }
}
