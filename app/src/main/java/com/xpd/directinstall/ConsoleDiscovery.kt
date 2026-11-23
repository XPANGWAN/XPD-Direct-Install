package com.xpd.directinstall

import android.content.Context
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ConsoleCandidate(val address: String, val service: String, val port: Int)

object ConsoleDiscovery {
    fun scan(context: Context, onProgress: (String) -> Unit = {}): List<ConsoleCandidate> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()
        @Suppress("DEPRECATION")
        val dhcp = wifi.dhcpInfo ?: return emptyList()
        if (dhcp.ipAddress == 0 || dhcp.netmask == 0) return emptyList()

        val mask = dhcp.netmask
        val network = dhcp.ipAddress and mask
        val hostMask = mask.inv()
        val hostCount = hostMask.toLong() and 0xffffffffL
        if (hostCount < 2 || hostCount > 1024) {
            onProgress("Network range is too large or unavailable; enter the console IP manually.")
            return emptyList()
        }

        val pool = Executors.newFixedThreadPool(40)
        try {
            val tasks = ArrayList<Callable<ConsoleCandidate?>>(hostCount.toInt())
            for (host in 1 until hostCount.toInt()) {
                val raw = network + host
                val address = try {
                    InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(raw).array()).hostAddress
                } catch (_: Exception) { continue }
                if (address == null || address == localAddress(dhcp.ipAddress)) continue
                tasks += Callable { probe(address) }
            }
            onProgress("Searching ${tasks.size} local addresses…")
            val found = ArrayList<ConsoleCandidate>()
            val completed = pool.invokeAll(tasks, 8, TimeUnit.SECONDS)
            for (future in completed) {
                try {
                    val candidate = if (future.isDone && !future.isCancelled) future.get() else null
                    if (candidate != null && found.none { it.address == candidate.address }) found += candidate
                } catch (_: Exception) { }
            }
            return found
        } finally {
            pool.shutdownNow()
        }
    }

    private fun localAddress(ip: Int): String? = try {
        InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ip).array()).hostAddress
    } catch (_: Exception) { null }

    private fun probe(ip: String): ConsoleCandidate? {
        val rpi = http(ip, 12800, "/api")
        if (rpi != null && (rpi.contains("Unsupported method", true) && rpi.contains("fail", true))) {
            return ConsoleCandidate(ip, "PS4 Remote Package Installer", 12800)
        }
        if (rpi != null && rpi.isNotBlank()) {
            return ConsoleCandidate(ip, "PKG Receiver", 12800)
        }
        val goldhen = http(ip, 9090, "/status")
        if (goldhen != null && goldhen.replace(" ", "").contains("\"status\":\"ready\"", true)) {
            return ConsoleCandidate(ip, "GoldHEN Payload Server", 9090)
        }
        for (port in intArrayOf(9090, 9021, 9020)) {
            if (tcp(ip, port)) return ConsoleCandidate(ip, "GoldHEN Payload Server", port)
        }
        return null
    }

    private fun http(ip: String, port: Int, path: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("http://$ip:$port$path").openConnection() as HttpURLConnection
            connection.connectTimeout = 650
            connection.readTimeout = 650
            connection.requestMethod = "GET"
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText().take(2048) }
        } catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }

    private fun tcp(ip: String, port: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress(ip, port), 350); true }
        } catch (_: Exception) { false }
    }
}
