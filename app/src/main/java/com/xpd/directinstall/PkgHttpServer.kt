package com.xpd.directinstall

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class PkgEntry(val id: String, val uri: Uri, val name: String, val size: Long, val metadata: PkgMetadata? = null)

class PkgHttpServer(
    private val resolver: ContentResolver,
    private val port: Int,
    private val entries: List<PkgEntry>,
    private val publicHost: String = "127.0.0.1",
    private val onBytes: (String, Long) -> Unit,
    private val onServerError: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null

    fun start(): Boolean {
        if (running.get()) return true
        return try {
            val socket = ServerSocket(port, 16, java.net.InetAddress.getByName("0.0.0.0"))
            server = socket
            running.set(true)
            Thread {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        workers.execute { handle(client) }
                    } catch (e: SocketException) {
                        if (running.get()) onServerError(e.message ?: "Socket error")
                    } catch (e: Exception) {
                        if (running.get()) onServerError(e.message ?: "Server error")
                    }
                }
            }.apply { name = "XPD-PKG-HTTP"; isDaemon = true }.start()
            true
        } catch (e: Exception) {
            onServerError(e.message ?: "Could not start local HTTP server")
            false
        }
    }

    fun stop() {
        running.set(false)
        try { server?.close() } catch (_: Exception) {}
        workers.shutdownNow()
        server = null
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 15000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val request = readLine(input) ?: return
            val parts = request.split(" ")
            if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) {
                respond(output, 400, "Bad Request", "Invalid request")
                return
            }
            val method = parts[0].uppercase()
            val path = parts[1].substringBefore("?")
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
            if (method != "GET" && method != "HEAD") {
                respond(output, 405, "Method Not Allowed", "Only GET and HEAD are supported")
                return
            }
            if (path.startsWith("/manifest/")) {
                val id = path.removePrefix("/manifest/")
                val entry = entries.firstOrNull { it.id == id }
                if (entry == null) { respond(output,404,"Not Found","Package not found"); return }
                serveManifest(entry, method == "HEAD", output)
                return
            }
            val id = path.removePrefix("/pkg/")
            val entry = entries.firstOrNull { it.id == id }
            if (entry == null || !path.startsWith("/pkg/")) {
                respond(output, 404, "Not Found", "Package not found")
                return
            }
            serve(entry, method == "HEAD", headers["range"], output)
        }
    }

    private fun serveManifest(entry: PkgEntry, head: Boolean, output: OutputStream) {
        val meta = entry.metadata
        if (entry.size <= 0L || meta == null || meta.digest.length != 64 || meta.platform != "PS4") {
            respond(output, 422, "Unprocessable Entity", "A valid PS4 package digest is required for GoldHEN")
            return
        }
        val pieces = when {
            entry.size >= (1L shl 30) -> 4
            entry.size >= (256L shl 20) -> 2
            else -> 1
        }
        val url = "http://$publicHost:$port/pkg/\${entry.id}"
        val json = StringBuilder()
            .append("{\"originalFileSize\":").append(entry.size)
            .append(",\"packageDigest\":\"").append(meta.digest).append('"')
            .append(",\"numberOfSplitFiles\":").append(pieces)
            .append(",\"pieces\":[")
        for (i in 0 until pieces) {
            if (i > 0) json.append(',')
            val offset = entry.size * i / pieces
            val length = entry.size * (i + 1) / pieces - offset
            json.append("{\"url\":\"").append(url).append('"')
                .append(",\"fileOffset\":").append(offset)
                .append(",\"fileSize\":").append(length)
                .append(",\"hashValue\":\"0000000000000000000000000000000000000000\"}")
        }
        json.append("]}")
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: \${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (!head) output.write(bytes)
        output.flush()
    }

    private fun serve(entry: PkgEntry, head: Boolean, rangeHeader: String?, output: OutputStream) {
        val size = entry.size
        if (size < 0L) {
            respond(output, 500, "Internal Server Error", "Unknown package size")
            return
        }
        var start = 0L
        var end = size - 1
        var partial = false
        if (rangeHeader != null && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val rawSpec = rangeHeader.substringAfter('=').trim()
            if (rawSpec.contains(',')) {
                output.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                output.flush()
                return
            }
            val spec = rawSpec
            val dash = spec.indexOf('-')
            if (dash < 0 || size == 0L) {
                respond(output, 416, "Range Not Satisfiable", "")
                return
            }
            val left = spec.substring(0, dash).trim()
            val right = spec.substring(dash + 1).trim()
            try {
                if (left.isEmpty()) {
                    val suffix = right.toLong()
                    if (suffix <= 0) throw NumberFormatException()
                    start = (size - suffix).coerceAtLeast(0)
                } else {
                    start = left.toLong()
                    if (right.isNotEmpty()) end = right.toLong()
                }
            } catch (_: Exception) {
                respond(output, 416, "Range Not Satisfiable", "")
                return
            }
            if (start >= size || start < 0 || end < start) {
                output.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                output.flush()
                return
            }
            end = end.coerceAtMost(size - 1)
            partial = true
        }
        val length = if (size == 0L) 0L else end - start + 1
        val status = if (partial) "206 Partial Content" else "200 OK"
        val header = StringBuilder()
            .append("HTTP/1.1 ").append(status).append("\r\n")
            .append("Content-Type: application/octet-stream\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Length: ").append(length).append("\r\n")
            .append("Content-Disposition: attachment; filename=\"").append(safeFileName(entry.name)).append("\"\r\n")
            .append("Connection: close\r\n")
        if (partial) header.append("Content-Range: bytes ").append(start).append("-").append(end).append("/").append(size).append("\r\n")
        header.append("\r\n")
        output.write(header.toString().toByteArray(Charsets.US_ASCII))
        output.flush()
        if (head || length == 0L) return

        var sent = 0L
        try {
            val pfd: ParcelFileDescriptor = resolver.openFileDescriptor(entry.uri, "r")
                ?: throw IllegalStateException("Cannot open package")
            pfd.use {
                FileInputStream(it.fileDescriptor).use { stream ->
                    val channel = stream.channel
                    channel.position(start)
                    val buffer = ByteArray(128 * 1024)
                    var remaining = length
                    while (remaining > 0 && running.get()) {
                        val wanted = minOf(buffer.size.toLong(), remaining).toInt()
                        val count = stream.read(buffer, 0, wanted)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        output.flush()
                        remaining -= count
                        sent += count
                        onBytes(entry.id, count.toLong())
                    }
                }
            }
        } catch (e: Exception) {
            onServerError("Serving ${entry.name}: ${e.message ?: "read error"}")
        }
    }

    private fun safeFileName(value: String): String {
        val cleaned = value.replace("\r", "").replace("\n", "").replace("\\", "_").replace("\"", "")
            .map { if (it.code in 32..126) it else '_' }.joinToString("").take(180)
        return cleaned.ifBlank { "package.pkg" }
    }

    private fun respond(out: OutputStream, code: Int, reason: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (value == 10) break
            if (value != 13) bytes.write(value)
            if (bytes.size() > 8192) return null
        }
        return bytes.toString("UTF-8")
    }
}
