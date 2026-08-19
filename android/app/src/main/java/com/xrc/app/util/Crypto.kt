package com.xrc.app.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for C2 payload encryption.
 */
object Crypto {

    private const val AES_ALGORITHM = "AES"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    private val secureRandom = SecureRandom()

    fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGen.init(256, secureRandom)
        return keyGen.generateKey()
    }

    fun keyToBase64(key: SecretKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun keyFromBase64(base64: String): SecretKey {
        val decoded = Base64.decode(base64, Base64.NO_WRAP)
        return SecretKeySpec(decoded, AES_ALGORITHM)
    }

    fun encrypt(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = ByteArray(IV_LENGTH).apply { secureRandom.nextBytes(this) }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(data)
        // Prepend IV to ciphertext
        return iv + encrypted
    }

    fun decrypt(encryptedData: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = encryptedData.copyOfRange(0, IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(IV_LENGTH, encryptedData.size)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(data: String, key: SecretKey): String {
        return Base64.encodeToString(encrypt(data.toByteArray(Charsets.UTF_8), key), Base64.NO_WRAP)
    }

    fun decryptString(encoded: String, key: SecretKey): String {
        val decoded = Base64.decode(encoded, Base64.NO_WRAP)
        return String(decrypt(decoded, key), Charsets.UTF_8)
    }

    fun obfuscateString(input: String): String {
        val bytes = input.toByteArray()
        val xorKey = 0x5A.toByte()
        val xorred = bytes.map { (it.toInt() xor xorKey.toInt()).toByte() }.toByteArray()
        return Base64.encodeToString(xorred, Base64.NO_WRAP)
    }

    fun deobfuscateString(input: String): String {
        val xorKey = 0x5A.toByte()
        val decoded = Base64.decode(input, Base64.NO_WRAP)
        val xorred = decoded.map { (it.toInt() xor xorKey.toInt()).toByte() }.toByteArray()
        return String(xorred, Charsets.UTF_8)
    }
}
