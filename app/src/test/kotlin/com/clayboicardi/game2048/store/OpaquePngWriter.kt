package com.clayboicardi.game2048.store

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Writes [source] composited over [background] as an 8-bit RGB PNG (IHDR color type 2, no alpha).
 */
internal object OpaquePngWriter {
    fun write(source: Bitmap, background: Int, file: File) {
        val w = source.width
        val h = source.height
        val opaque = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        opaque.eraseColor(background)
        Canvas(opaque).drawBitmap(source, 0f, 0f, null)
        val argb = IntArray(w * h)
        opaque.getPixels(argb, 0, w, 0, 0, w, h)
        val raw = ByteArray(h * (1 + 3 * w))
        var i = 0
        for (y in 0 until h) {
            raw[i++] = 0 // filter type: None
            for (x in 0 until w) {
                val p = argb[y * w + x]
                raw[i++] = (p shr 16).toByte()
                raw[i++] = (p shr 8).toByte()
                raw[i++] = p.toByte()
            }
        }
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val idat =
            try {
                ByteArrayOutputStream()
                    .also { bytes -> DeflaterOutputStream(bytes, deflater).use { it.write(raw) } }
                    .toByteArray()
            } finally {
                deflater.end()
            }
        val ihdr =
            ByteBuffer.allocate(13)
                .putInt(w)
                .putInt(h)
                .put(8.toByte()) // bit depth
                .put(2.toByte()) // color type: RGB
                .put(0.toByte()) // compression
                .put(0.toByte()) // filter method
                .put(0.toByte()) // interlace: none
                .array()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            fun chunk(type: String, data: ByteArray) {
                val t = type.toByteArray(Charsets.US_ASCII)
                out.writeInt(data.size)
                out.write(t)
                out.write(data)
                out.writeInt(
                    CRC32()
                        .apply {
                            update(t)
                            update(data)
                        }
                        .value
                        .toInt(),
                )
            }
            chunk("IHDR", ihdr)
            chunk("IDAT", idat)
            chunk("IEND", ByteArray(0))
        }
    }
}
