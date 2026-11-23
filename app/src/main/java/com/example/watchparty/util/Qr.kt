package com.example.watchparty.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class QrJoinPayload(
    val url: String,
    val ip: String,
    val port: Int = Net.DEFAULT_PORT,
    val room: String,
    val pwd: String = ""
)

object Qr {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodePayload(ip: String, port: Int, roomCode: String, password: String = ""): String {
        val wsUrl = if (password.isNotBlank()) {
            "ws://$ip:$port?room=$roomCode&pwd=${Uri.encode(password)}"
        } else {
            "ws://$ip:$port?room=$roomCode"
        }
        val payload = QrJoinPayload(
            url = wsUrl,
            ip = ip,
            port = port,
            room = roomCode,
            pwd = password
        )
        return json.encodeToString(payload)
    }

    fun parsePayload(raw: String): QrJoinPayload? {
        val trimmed = raw.trim()
        // Try JSON first
        try {
            return json.decodeFromString<QrJoinPayload>(trimmed)
        } catch (_: Exception) {
        }
        // Try ws://IP:8080?room=4F2 URL format
        try {
            if (trimmed.startsWith("ws://", ignoreCase = true)) {
                val uri = Uri.parse(trimmed)
                val host = uri.host ?: return null
                val port = if (uri.port != -1) uri.port else Net.DEFAULT_PORT
                val room = uri.getQueryParameter("room")
                    ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
                    ?: "4F2"
                val pwd = uri.getQueryParameter("pwd").orEmpty()
                return QrJoinPayload(url = trimmed, ip = host, port = port, room = room, pwd = pwd)
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
