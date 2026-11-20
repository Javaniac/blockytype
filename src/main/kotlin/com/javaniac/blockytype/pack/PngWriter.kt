package com.javaniac.blockytype.pack

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Minimal 8-bit RGBA PNG encoder, enough for flat generated sprites. */
object PngWriter {
    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A
    )

    /** One pixel as `[r, g, b, a]`, each 0..255. */
    fun interface Pixel {
        fun at(x: Int, y: Int): IntArray
    }

    fun rgba(r: Int, g: Int, b: Int, a: Int) = intArrayOf(r, g, b, a)

    fun encode(width: Int, height: Int, pixel: Pixel): ByteArray {
        val raw = ByteArrayOutputStream(height * (1 + width * 4))
        for (y in 0 until height) {
            raw.write(0) // filter: none
            for (x in 0 until width) {
                pixel.at(x, y).forEach { raw.write(it) }
            }
        }

        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        chunk(out, "IHDR", ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8) // bit depth
                writeByte(6) // color type RGBA
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray())
        chunk(out, "IDAT", deflate(raw.toByteArray()))
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf))
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(typeBytes)
            write(data)
            writeInt(crc.value.toInt())
        }
    }
}
