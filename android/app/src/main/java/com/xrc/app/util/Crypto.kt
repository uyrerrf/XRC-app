package com.xrc.app.util

import android.util.Base64
import android.util.Log
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utilities for XRC.
 * Handles AES-256-GCM encryption/decryption and RSA key management.
 */
object CryptoUtils {

    private const val TAG = "CryptoUtils"

    // AES-256-GCM
    private const val AES_ALGO = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // RSA
    private const val RSA_ALGO = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val RSA_KEY_SIZE = 2048

    // Key derivation iterations
    private const val PBKDF2_ITERATIONS = 100000
    private const val PBKDF2_KEY_LENGTH = 256

    @Volatile
    private var aesKey: SecretKey? = null

    @Volatile
    private var rsaKeyPair: KeyPair? = null

    /**
     * Generate or load the AES encryption key.
     */
    fun getOrCreateAesKey(): SecretKey {
        return aesKey ?: synchronized(this) {
            aesKey ?: run {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(AES_KEY_SIZE)
                val key = keyGen.generateKey()
                aesKey = key
                key
            }
        }
    }

    /**
     * Set the AES key from a Base64-encoded string.
     */
    fun setAesKey(base64Key: String) {
        try {
            val bytes = Base64.decode(base64Key, Base64.DEFAULT)
            aesKey = SecretKeySpec(bytes, "AES")
            Log.i(TAG, "AES key set from Base64")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set AES key: ${e.message}")
        }
    }

    /**
     * Get the AES key as Base64 string.
     */
    fun getAesKeyBase64(): String? {
        return aesKey?.encoded?.let { Base64.encodeToString(it, Base64.DEFAULT) }
    }

    /**
     * Encrypt data using AES-256-GCM.
     * Returns Base64-encoded IV + ciphertext.
     */
    fun aesEncrypt(plaintext: ByteArray): String? {
        return try {
            val key = getOrCreateAesKey()
            val cipher = Cipher.getInstance(AES_ALGO)

            // Generate random IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            val ciphertext = cipher.doFinal(plaintext)

            // Prepend IV to ciphertext
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "AES encrypt failed: ${e.message}")
            null
        }
    }

    /**
     * Decrypt data using AES-256-GCM.
     * Expects Base64-encoded IV + ciphertext.
     */
    fun aesDecrypt(encryptedBase64: String): ByteArray? {
        return try {
            val key = getOrCreateAesKey()
            val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance(AES_ALGO)

            // Extract IV from first bytes
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "AES decrypt failed: ${e.message}")
            null
        }
    }

    /**
     * Generate RSA key pair for asymmetric encryption.
     */
    fun getOrCreateRsaKeyPair(): KeyPair {
        return rsaKeyPair ?: synchronized(this) {
            rsaKeyPair ?: run {
                val keyGen = KeyPairGenerator.getInstance("RSA")
                keyGen.initialize(RSA_KEY_SIZE, SecureRandom())
                val pair = keyGen.generateKeyPair()
                rsaKeyPair = pair
                pair
            }
        }
    }

    /**
     * Encrypt data with RSA public key.
     */
    fun rsaEncrypt(plaintext: ByteArray, publicKeyBase64: String): ByteArray? {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance(RSA_ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            cipher.doFinal(plaintext)
        } catch (e: Exception) {
            Log.e(TAG, "RSA encrypt failed: ${e.message}")
            null
        }
    }

    /**
     * Decrypt data with RSA private key.
     */
    fun rsaDecrypt(ciphertext: ByteArray): ByteArray? {
        return try {
            val pair = getOrCreateRsaKeyPair()
            val cipher = Cipher.getInstance(RSA_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, pair.private)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "RSA decrypt failed: ${e.message}")
            null
        }
    }

    /**
     * Get the public key as Base64-encoded X.509 string.
     */
    fun getPublicKeyBase64(): String? {
        return try {
            val pair = getOrCreateRsaKeyPair()
            Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key: ${e.message}")
            null
        }
    }

    /**
     * Generate a SHA-256 hash of the input.
     */
    fun sha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }

    /**
     * Generate a SHA-256 hash as hex string.
     */
    fun sha256Hex(input: String): String {
        val bytes = sha256(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate HMAC-SHA256.
     */
    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(key, "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    /**
     * Generate a cryptographically secure random string.
     */
    fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Generate random bytes.
     */
    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    /**
     * Simple XOR obfuscation for string data.
     * NOT cryptographically secure — for obfuscation only.
     */
    fun xorObfuscate(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return result
    }
}
