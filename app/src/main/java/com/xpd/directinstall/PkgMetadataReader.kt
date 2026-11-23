package com.xpd.directinstall

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.Locale

/**
 * Read-only PS4/PS5 package metadata reader.
 *
 * The CNT/FIH container layout and metadata field mapping were independently
 * implemented for XPD. Reference project: Loopayeh/pkg-sender (MIT).
 * No upstream source or payload binary is copied into this file.
 */
data class PkgMetadata(
    val platform: String = "PlayStation",
    val title: String = "",
    val titleId: String = "",
    val contentId: String = "",
    val category: String = "",
    val version: String = "",
    val size: Long = -1L,
    val digest: String = "",
    val iconPng: ByteArray? = null,
    val isDlc: Boolean = false,
    val isUpdate: Boolean = false,
    val validContainer: Boolean = false,
    val detail: String = ""
) {
    val displayTitle: String
        get() = title.ifBlank { titleId.ifBlank { contentId.ifBlank { "Unknown package" } } }
}

object PkgMetadataReader {
    private const val CNT_HEADER_SIZE = 0x5A0
    private const val ENTRY_SIZE = 0x20
    private const val MAX_ENTRIES = 65536

    fun read(resolver: ContentResolver, uri: Uri): PkgMetadata {
        return try {
            val pfd = resolver.openFileDescriptor(uri, "r") ?: return PkgMetadata(detail = "Cannot open file")
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    read(stream.channel, descriptor.statSize.takeIf { it >= 0L } ?: stream.channel.size())
                }
            }
        } catch (e: Exception) {
            PkgMetadata(detail = e.message ?: "Could not read package")
        }
    }

    fun hasPkgMagic(resolver: ContentResolver, uri: Uri): Boolean {
        return try {
            val pfd = resolver.openFileDescriptor(uri, "r") ?: return false
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    val b = ByteArray(4)
                    val buffer = ByteBuffer.wrap(b)
                    while (buffer.hasRemaining()) {
                        if (stream.channel.read(buffer) <= 0) return false
                    }
                    isCntMagic(b) || isFihMagic(b)
                }
            }
        } catch (_: Exception) { false }
    }

    private fun read(channel: FileChannel, fileSize: Long): PkgMetadata {
        if (fileSize < CNT_HEADER_SIZE) return PkgMetadata(size = fileSize, detail = "File is too small to be a PlayStation package")
        val magic = readAt(channel, 0, 4) ?: return PkgMetadata(size = fileSize, detail = "Cannot read package header")
        val cntBase: Long
        val platform: String
        if (isFihMagic(magic)) {
            platform = "PS5"
            val fih = readAt(channel, 0, 0x60) ?: return PkgMetadata(size = fileSize, detail = "Invalid PS5 FIH header")
            val embedded = u64le(fih, 0x58)
            if (embedded <= 0 || embedded > fileSize - CNT_HEADER_SIZE) return PkgMetadata(size = fileSize, detail = "Invalid embedded CNT offset")
            cntBase = embedded
        } else if (isCntMagic(magic)) {
            platform = "PS4"
            cntBase = 0
        } else {
            return PkgMetadata(size = fileSize, detail = "Unsupported package signature")
        }

        val header = readAt(channel, cntBase, CNT_HEADER_SIZE)
            ?: return PkgMetadata(platform = platform, size = fileSize, detail = "Truncated CNT header")
        if (!isCntMagic(header.copyOfRange(0, 4))) return PkgMetadata(platform = platform, size = fileSize, detail = "CNT header not found")
        val count = u32be(header, 0x10).toInt()
        val tableOffset = u32be(header, 0x18)
        if (count !in 1..MAX_ENTRIES || tableOffset < CNT_HEADER_SIZE || tableOffset > fileSize - cntBase) {
            return PkgMetadata(platform = platform, size = fileSize, detail = "Invalid package entry table")
        }
        val tableBytes = count.toLong() * ENTRY_SIZE
        if (tableBytes > 4L * 1024 * 1024 || cntBase + tableOffset + tableBytes > fileSize) {
            return PkgMetadata(platform = platform, size = fileSize, detail = "Package entry table is out of bounds")
        }
        val table = readAt(channel, cntBase + tableOffset, tableBytes.toInt())
            ?: return PkgMetadata(platform = platform, size = fileSize, detail = "Cannot read package entry table")
        data class Entry(val id: Long, val nameOffset: Long, val flags: Long, val offset: Long, val length: Long, var name: String = "")
        val entries = ArrayList<Entry>(count)
        for (i in 0 until count) {
            val o = i * ENTRY_SIZE
            entries += Entry(u32be(table,o),u32be(table,o+4),u32be(table,o+8),u32be(table,o+0x10),u32be(table,o+0x14))
        }
        val nameTable = entries.firstOrNull { it.id == 0x0200L && it.flags and 0x80000000L == 0L }
        if (nameTable != null && nameTable.length in 1..(4L*1024*1024) && nameTable.offset + nameTable.length <= fileSize - cntBase) {
            val bytes = readAt(channel, cntBase + nameTable.offset, nameTable.length.toInt())
            if (bytes != null) {
                val names = HashMap<Long,String>()
                var start = 0
                for (i in 0..bytes.size) {
                    if (i == bytes.size || bytes[i].toInt() == 0) {
                        if (i > start) names[start.toLong()] = String(bytes,start,i-start,Charsets.US_ASCII)
                        start = i + 1
                    }
                }
                entries.forEach { it.name = names[it.nameOffset] ?: "" }
            }
        }
        fun find(id: Long, name: String): Entry? =
            entries.firstOrNull { it.id == id && it.flags and 0x80000000L == 0L }
                ?: entries.firstOrNull { it.name.equals(name,true) && it.flags and 0x80000000L == 0L }
        fun readEntry(e: Entry?, cap: Long): ByteArray? {
            if (e == null || e.length <= 0 || e.length > cap || e.offset + e.length > fileSize - cntBase) return null
            return readAt(channel,cntBase+e.offset,e.length.toInt())
        }
        val contentId = ascii(header,0x40,0x30)
        val digestBytes = readAt(channel,cntBase+0xFE0,32)
        val digest = digestBytes?.joinToString("") { "%02X".format(it) } ?: ""
        var title = ""
        var titleId = ""
        var category = ""
        var version = ""
        if (platform == "PS4") {
            val sfo = readEntry(find(0x1000,"param.sfo"),1024L*1024)
            if (sfo != null) {
                val values = parseSfo(sfo)
                title = values["TITLE"].orEmpty()
                titleId = values["TITLE_ID"].orEmpty()
                category = values["CATEGORY"].orEmpty()
                version = values["APP_VER"].orEmpty().ifBlank { values["VERSION"].orEmpty() }
            }
        } else {
            val json = readEntry(find(0x2000,"param.json"),2L*1024*1024)
            if (json != null) {
                try {
                    val root = JSONObject(String(json,Charsets.UTF_8))
                    version = root.optString("contentVersion","")
                    val localized = root.optJSONObject("localizedParameters")
                    val lang = localized?.optString("defaultLanguage","en-US") ?: "en-US"
                    title = localized?.optJSONObject(lang)?.optString("titleName","").orEmpty()
                    if (title.isBlank() && localized != null) {
                        val keys = localized.keys()
                        while (keys.hasNext() && title.isBlank()) title = localized.optJSONObject(keys.next())?.optString("titleName","").orEmpty()
                    }
                } catch (_: Exception) { }
            }
            val mid = contentId.substringAfter('-', contentId).substringBefore('_')
            if (mid.length in 4..16) titleId = mid
        }
        val iconEntry = entries.firstOrNull { it.id == 0x1200L && it.flags and 0x80000000L == 0L }
            ?: entries.firstOrNull { it.id in 0x1201L..0x1220L && it.flags and 0x80000000L == 0L }
        val icon = readEntry(iconEntry,8L*1024*1024)?.takeIf { it.size >= 8 && it[0] == 0x89.toByte() && it[1] == 0x50.toByte() }
        val isDlc = category.equals("ac",true) || title.contains("DLC",true) || contentId.contains("-DLC",true) || contentId.contains("_DLC",true)
        val isUpdate = category.equals("gp",true) || category.equals("patch",true) || title.contains("Update",true)
        val valid = if (platform == "PS4") title.isNotBlank() || contentId.isNotBlank() else contentId.isNotBlank()
        return PkgMetadata(platform,title,titleId,contentId,category,version,fileSize,digest,icon,isDlc,isUpdate,valid,
            if (valid) "Package metadata read successfully" else "Container found, but game metadata was not found")
    }

    private fun parseSfo(b: ByteArray): Map<String,String> {
        if (b.size < 0x14 || b[0] != 0.toByte() || b[1] != 0x50.toByte() || b[2] != 0x53.toByte() || b[3] != 0x46.toByte()) return emptyMap()
        val keyTable = u32le(b,8).toInt()
        val dataTable = u32le(b,12).toInt()
        val count = u32le(b,16).toInt()
        if (count !in 0..4096 || 0x14L + count.toLong()*16 > b.size) return emptyMap()
        val out = LinkedHashMap<String,String>()
        repeat(count) { i ->
            val o = 0x14+i*16
            val keyOff = (b[o].toInt() and 255) or ((b[o+1].toInt() and 255) shl 8)
            val format = (b[o+2].toInt() and 255) or ((b[o+3].toInt() and 255) shl 8)
            val length = u32le(b,o+4).toInt()
            val dataOff = u32le(b,o+12).toInt()
            val keyStart = keyTable + keyOff
            if (keyStart !in b.indices) return@repeat
            val keyEnd = (keyStart until b.size).firstOrNull { b[it] == 0.toByte() } ?: b.size
            val key = String(b,keyStart,keyEnd-keyStart,Charsets.US_ASCII)
            val start = dataTable.toLong()+dataOff
            if (start < 0 || start > b.size) return@repeat
            val value = when(format) {
                0x404 -> if (start+4<=b.size) "%08X".format(u32le(b,start.toInt())) else ""
                0x204,0x4 -> {
                    val n = if(format==0x204) (length-1).coerceAtLeast(0) else length
                    if(start+n>b.size) "" else String(b,start.toInt(),n,Charsets.UTF_8).trim('\u0000')
                }
                else -> ""
            }
            if(key.isNotBlank()) out[key]=value
        }
        return out
    }

    private fun readAt(channel: FileChannel, offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length < 0) return null
        return try {
            val buffer = ByteBuffer.allocate(length)
            var position = offset
            while (buffer.hasRemaining()) {
                val n = channel.read(buffer,position)
                if (n <= 0) return null
                position += n
            }
            buffer.array()
        } catch (_: Exception) { null }
    }
    private fun isCntMagic(b: ByteArray) = b.size>=4 && b[0]==0x7f.toByte() && b[1]==0x43.toByte() && b[2]==0x4e.toByte() && b[3]==0x54.toByte()
    private fun isFihMagic(b: ByteArray) = b.size>=4 && b[0]==0x7f.toByte() && b[1]==0x46.toByte() && b[2]==0x49.toByte() && b[3]==0x48.toByte()
    private fun ascii(b:ByteArray,o:Int,n:Int):String { val end=(o until minOf(o+n,b.size)).firstOrNull{b[it]==0.toByte()}?:minOf(o+n,b.size);return String(b,o,(end-o).coerceAtLeast(0),Charsets.US_ASCII).trim() }
    private fun u32be(b:ByteArray,o:Int):Long = ((b[o].toLong() and 255) shl 24) or ((b[o+1].toLong() and 255) shl 16) or ((b[o+2].toLong() and 255) shl 8) or (b[o+3].toLong() and 255)
    private fun u32le(b:ByteArray,o:Int):Long = (b[o].toLong() and 255) or ((b[o+1].toLong() and 255) shl 8) or ((b[o+2].toLong() and 255) shl 16) or ((b[o+3].toLong() and 255) shl 24)
    private fun u64le(b:ByteArray,o:Int):Long { var v=0L;for(i in 7 downTo 0)v=(v shl 8) or (b[o+i].toLong() and 255);return v }
}
