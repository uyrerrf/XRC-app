package com.xrc.app.util

import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.floor
import kotlin.random.Random

/**
 * Obfuscation techniques for evading static analysis,
 * string scanning, and signature detection.
 */
object Obfuscation {

    private const val TAG = "Obfuscation"

    /**
     * Obfuscate a string by encoding it with a rotating XOR key.
     */
    fun xorObfuscate(input: String, key: String? = null): String {
        val actualKey = key ?: generateKey(16)
        val keyBytes = actualKey.toByteArray()
        val inputBytes = input.toByteArray()
        val result = ByteArray(inputBytes.size)

        for (i in inputBytes.indices) {
            result[i] = (inputBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }

        return Base64.encodeToString(
            keyBytes + result,
            Base64.NO_WRAP
        )
    }

    /**
     * Deobfuscate a string that was XOR-obfuscated.
     */
    fun xorDeobfuscate(obfuscated: String): String {
        return try {
            val decoded = Base64.decode(obfuscated, Base64.DEFAULT)
            if (decoded.size < 17) return ""

            val keyBytes = decoded.copyOfRange(0, 16)
            val dataBytes = decoded.copyOfRange(16, decoded.size)
            val result = ByteArray(dataBytes.size)

            for (i in dataBytes.indices) {
                result[i] = (dataBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }

            String(result)
        } catch (e: Exception) {
            Log.e(TAG, "Deobfuscation failed: ${e.message}")
            ""
        }
    }

    /**
     * Compress data using Deflate to reduce size and obscure patterns.
     */
    fun compress(data: ByteArray): ByteArray {
        return try {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            deflater.setInput(data)
            deflater.finish()

            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(8192)

            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                baos.write(buffer, 0, count)
            }

            deflater.end()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed: ${e.message}")
            data
        }
    }

    /**
     * Decompress Deflate-compressed data.
     */
    fun decompress(data: ByteArray): ByteArray {
        return try {
            val inflater = Inflater()
            inflater.setInput(data)

            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(8192)

            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                baos.write(buffer, 0, count)
            }

            inflater.end()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Decompression failed: ${e.message}")
            data
        }
    }

    /**
     * Split a string into chunks separated by junk characters
     * to evade simple string matching.
     */
    fun splitObfuscate(input: String, chunkSize: Int = 3): String {
        val junkChars = "!@#\$%^&*()-_=+[]{}|;:',.<>?/~`"
        val sb = StringBuilder()
        var pos = 0

        while (pos < input.length) {
            val end = minOf(pos + chunkSize, input.length)
            sb.append(input.substring(pos, end))
            if (end < input.length) {
                sb.append(junkChars[Random.nextInt(junkChars.length)])
            }
            pos = end
        }
        return sb.toString()
    }

    /**
     * Remove junk characters to recover original string.
     */
    fun splitDeobfuscate(obfuscated: String): String {
        return obfuscated.replace(Regex("""[!@#\$%^&*()\-_=+\[\]{}|;:',.<>?/~`]"""), "")
    }

    /**
     * Encode a string in Base64 with custom alphabet mapping.
     */
    fun customBase64Encode(input: ByteArray): String {
        val standard = Base64.encodeToString(input, Base64.NO_WRAP)
        // Swap common characters to evade detection
        return standard
            .replace('a', '@')
            .replace('e', '3')
            .replace('i', '1')
            .replace('o', '0')
            .replace('s', '$')
            .replace('t', '7')
            .replace('l', '1')
    }

    /**
     * Decode a custom-Base64 string.
     */
    fun customBase64Decode(input: String): ByteArray {
        val standard = input
            .replace('@', 'a')
            .replace('3', 'e')
            .replace('1', 'i')
            .replace('0', 'o')
            .replace('$', 's')
            .replace('7', 't')
        return Base64.decode(standard, Base64.DEFAULT)
    }

    /**
     * Reverse a string (simple obfuscation).
     */
    fun reverse(str: String): String = str.reversed()

    /**
     * Hide an IP address as a dotted decimal that looks innocent.
     */
    fun obfuscateIp(ip: String): String {
        val parts = ip.split(".")
        if (parts.size != 4) return ip
        return parts.joinToString(".") { (it.toInt() + 100).toString() }
    }

    /**
     * Recover an IP from obfuscated form.
     */
    fun deobfuscateIp(obfuscated: String): String {
        val parts = obfuscated.split(".")
        if (parts.size != 4) return obfuscated
        return parts.joinToString(".") { (it.toInt() - 100).toString() }
    }

    /**
     * Generate a random key of specified length.
     */
    fun generateKey(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Embed string data inside a larger random string to hide it.
     */
    fun embedInNoise(data: String, noiseRatio: Float = 3.0f): String {
        val noiseChars = "0123456789ABCDEF"
        val sb = StringBuilder()
        var dataIndex = 0

        while (dataIndex < data.length) {
            // Add random noise
            val noiseLen = floor(noiseRatio * Random.nextFloat()).toInt() + 1
            repeat(noiseLen) {
                sb.append(noiseChars[Random.nextInt(noiseChars.length)])
            }
            // Add one data character
            sb.append(data[dataIndex])
            dataIndex++
        }

        return sb.toString()
    }

    /**
     * Extract embedded data from noise.
     */
    fun extractFromNoise(noisy: String): String {
        val sb = StringBuilder()
        // Every Nth character based on the pattern
        var skip = 0
        for (i in noisy.indices) {
            if (noisy[i] in "0123456789ABCDEF") {
                skip++
            } else {
                sb.append(noisy[i])
                skip = 0
            }
        }
        return sb.toString()
    }

    /**
     * Convert class names to obfuscated forms for reflection.
     */
    fun obfuscateClassName(className: String): String {
        return Base64.encodeToString(className.toByteArray(), Base64.NO_WRAP)
            .replace("=", "")
            .replace("/", "_")
            .replace("+", "-")
    }

    /**
     * Deobfuscate class names from obfuscated form.
     */
    fun deobfuscateClassName(obfuscated: String): String {
        val standard = obfuscated
            .replace("_", "/")
            .replace("-", "+")
        return String(Base64.decode(standard, Base64.DEFAULT))
    }

    /**
     * Obfuscate a URL by splitting it into path components.
     */
    fun obfuscateUrl(url: String): String {
        return url
            .replace("https://", "h__")
            .replace("http://", "h_")
            .replace("ws://", "w_")
            .replace("wss://", "ws_")
            .replace(".", "[dot]")
            .replace("/", "[slash]")
            .replace(":", "[colon]")
    }

    /**
     * Deobfuscate a URL.
     */
    fun deobfuscateUrl(obfuscated: String): String {
        return obfuscated
            .replace("[dot]", ".")
            .replace("[slash]", "/")
            .replace("[colon]", ":")
            .replace("h__", "https://")
            .replace("h_", "http://")
            .replace("ws_", "wss://")
            .replace("w_", "ws://")
    }
}
