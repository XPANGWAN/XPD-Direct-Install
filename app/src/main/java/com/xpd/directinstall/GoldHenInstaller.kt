package com.xpd.directinstall

import android.content.Context
import android.util.Base64
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GoldHenResult(val success: Boolean, val message: String)

object GoldHenInstaller {
    fun install(
        context: Context,
        consoleIp: String,
        phoneIp: String,
        entry: PkgEntry,
        serverPort: Int = 9898
    ): GoldHenResult {
        val meta = entry.metadata
        if (meta == null || meta.platform != "PS4" || !meta.validContainer ||
            meta.digest.length != 64 || meta.contentId.isBlank()) {
            return GoldHenResult(false, "GoldHEN requires a readable PS4 PKG with Content ID and header digest.")
        }
        if (entry.size <= 0L) return GoldHenResult(false, "The PKG file size could not be read.")

        val payload = try {
            val encoded = context.assets.open("ps4_dpi_payload.bin.b64").bufferedReader().use { it.readText() }
            Base64.decode(encoded, Base64.DEFAULT)
        } catch (e: Exception) {
            return GoldHenResult(false, "Could not load the licensed GoldHEN payload: ${e.message}")
        }
        val marker = findMarker(payload)
        if (marker < 0 || marker + 6 > payload.size) return GoldHenResult(false, "GoldHEN payload callback marker is missing.")

        val phoneBytes = try {
            val parsed = InetAddress.getByName(phoneIp)
            if (parsed !is Inet4Address) return GoldHenResult(false, "The phone LAN address must be IPv4.")
            parsed.address
        } catch (_: Exception) {
            return GoldHenResult(false, "Invalid phone LAN address: $phoneIp")
        }

        try {
            ServerSocket().use { listener ->
                listener.reuseAddress = true
                listener.bind(InetSocketAddress("0.0.0.0", 0))
                listener.soTimeout = 15000
                val callbackPort = listener.localPort
                val patched = payload.clone()
                System.arraycopy(phoneBytes, 0, patched, marker, 4)
                patched[marker + 4] = (callbackPort ushr 8).toByte()
                patched[marker + 5] = callbackPort.toByte()

                var injected = false
                var lastError = "No GoldHEN binloader port accepted the payload."
                for (attempt in 1..3) {
                    val socket = connectBinloader(consoleIp)
                    if (socket == null) {
                        lastError = "GoldHEN binloader is not reachable on 9090, 9021 or 9020 (attempt $attempt/3)."
                    } else {
                        try {
                            socket.tcpNoDelay = true
                            socket.sendBufferSize = patched.size
                            socket.getOutputStream().use { out -> out.write(patched); out.flush() }
                            injected = true
                            socket.close()
                            break
                        } catch (e: Exception) {
                            lastError = "Payload transfer failed: ${e.message}"
                            try { socket.close() } catch (_: Exception) { }
                        }
                    }
                    if (attempt < 3) Thread.sleep(1200L * attempt)
                }
                if (!injected) return GoldHenResult(false, lastError + " Re-enable GoldHEN Payload Server and retry.")

                val callback = try {
                    listener.accept()
                } catch (_: Exception) {
                    return GoldHenResult(false, "Payload was sent, but the PS4 did not call back. Check that the phone and PS4 can reach each other on the same LAN.")
                }
                callback.use { client ->
                    val expectedConsoleIp = try { InetAddress.getByName(consoleIp).hostAddress } catch (_: Exception) { consoleIp }
                    if (client.inetAddress.hostAddress != expectedConsoleIp) {
                        return GoldHenResult(false, "Callback came from an unexpected LAN device.")
                    }
                    client.tcpNoDelay = true
                    val title = (meta.title.ifBlank { meta.titleId }).toByteArray(Charsets.UTF_8)
                    val contentId = meta.contentId.toByteArray(Charsets.UTF_8)
                    val category = meta.category.trim().uppercase().let {
                        when {
                            it.startsWith("PS4") -> it
                            it.isBlank() -> "PS4GD"
                            else -> "PS4$it"
                        }
                    }.toByteArray(Charsets.UTF_8)
                    val manifestUrl = "http://$phoneIp:$serverPort/manifest/${entry.id}".toByteArray(Charsets.UTF_8)
                    val icon = meta.iconPng ?: ByteArray(0)
                    val buffer = ByteBuffer.allocate(
                        4 + 4 + manifestUrl.size + 4 + title.size + 4 + contentId.size +
                            4 + category.size + 8 + 4 + icon.size
                    ).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.putInt(1)
                    putBlob(buffer, manifestUrl)
                    putBlob(buffer, title)
                    putBlob(buffer, contentId)
                    putBlob(buffer, category)
                    buffer.putLong(entry.size)
                    if (icon.isEmpty()) buffer.putInt(0) else putBlob(buffer, icon)
                    client.getOutputStream().use { out -> out.write(buffer.array()); out.flush() }
                }
                return GoldHenResult(true, "GoldHEN accepted the installer payload and package metadata. Confirm the install notification on the PS4.")
            }
        } catch (e: Exception) {
            return GoldHenResult(false, "GoldHEN install failed: ${e.message ?: "network error"}")
        }
    }

    private fun connectBinloader(ip: String): Socket? {
        for (port in intArrayOf(9090, 9021, 9020)) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(ip, port), 3000)
                socket.soTimeout = 5000
                return socket
            } catch (_: Exception) {
                try { socket.close() } catch (_: Exception) { }
            }
        }
        return null
    }

    private fun findMarker(bytes: ByteArray): Int {
        for (i in 0..bytes.size - 6) {
            var matches = true
            for (j in 0 until 6) if (bytes[i + j] != 0xB4.toByte()) { matches = false; break }
            if (matches) return i
        }
        return -1
    }

    private fun putBlob(buffer: ByteBuffer, value: ByteArray) {
        buffer.putInt(value.size)
        buffer.put(value)
    }
}
