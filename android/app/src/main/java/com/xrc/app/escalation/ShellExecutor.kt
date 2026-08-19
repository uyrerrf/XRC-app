package com.xrc.app.escalation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Universal shell command executor.
 * Attempts multiple execution methods in order of privilege:
 * 1. Shizuku (API level)
 * 2. ADB shell (UID 2000)
 * 3. Normal shell (app UID)
 * 4. Runtime exec (lowest privilege)
 */
class ShellExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ShellExecutor"

        // Execution methods
        enum class ExecMethod {
            SHIZUKU, ADB_SHELL, NORMAL_SHELL, RUNTIME_EXEC
        }

        // Timeout
        private const val DEFAULT_TIMEOUT_MS = 10000L
        private const val LONG_TIMEOUT_MS = 30000L
    }

    private val adbEscalation = ADBEscalation(context)
    private val shizukuManager = ShizukuManager(context)

    @Volatile
    private var bestMethod: ExecMethod = ExecMethod.RUNTIME_EXEC

    /**
     * Execute a shell command, automatically selecting the best available method.
     */
    suspend fun execute(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        asRoot: Boolean = false
    ): ShellCmdResult {
        Log.d(TAG, "Executing: $command (method=$bestMethod, asRoot=$asRoot)")

        // Try methods in order of privilege
        val methods = listOf(
            ExecMethod.SHIZUKU,
            ExecMethod.ADB_SHELL,
            ExecMethod.NORMAL_SHELL,
            ExecMethod.RUNTIME_EXEC
        )

        // Start from the best known method
        val startIndex = methods.indexOf(bestMethod).coerceAtLeast(0)
        val orderedMethods = methods.subList(startIndex, methods.size) +
            methods.subList(0, startIndex.coerceAtMost(methods.size))

        var lastError: String? = null

        for (method in orderedMethods) {
            if (!isMethodAvailable(method)) continue

            val result = try {
                withContext(Dispatchers.IO) {
                    executeWithMethod(method, command, timeoutMs)
                }
            } catch (e: Exception) {
                continue
            }

            if (result.success) {
                bestMethod = method
                return result
            } else {
                lastError = result.stderr
            }
        }

        return ShellCmdResult(
            command = command,
            stderr = lastError ?: "All execution methods exhausted",
            exitCode = -1
        )
    }

    /**
     * Execute a command and return stdout as string.
     */
    suspend fun executeForOutput(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        val result = execute(command, timeoutMs)
        return if (result.success) result.stdout else null
    }

    /**
     * Execute multiple commands sequentially.
     */
    suspend fun executeBatch(commands: List<String>, timeoutMs: Long = LONG_TIMEOUT_MS): List<ShellCmdResult> {
        val results = mutableListOf<ShellCmdResult>()
        for (cmd in commands) {
            results.add(execute(cmd, timeoutMs))
        }
        return results
    }

    /**
     * Check if an execution method is currently available.
     */
    private fun isMethodAvailable(method: ExecMethod): Boolean {
        return when (method) {
            ExecMethod.SHIZUKU -> shizukuManager.isShizukuAvailable() && shizukuManager.isPermissionGranted()
            ExecMethod.ADB_SHELL -> adbEscalation.isShellEnabled()
            ExecMethod.NORMAL_SHELL -> true
            ExecMethod.RUNTIME_EXEC -> true
        }
    }

    /**
     * Execute using a specific method.
     */
    private fun executeWithMethod(
        method: ExecMethod,
        command: String,
        timeoutMs: Long
    ): ShellCmdResult {
        return when (method) {
            ExecMethod.SHIZUKU -> executeViaShizuku(command)
            ExecMethod.ADB_SHELL -> executeViaAdbShell(command)
            ExecMethod.NORMAL_SHELL -> executeViaNormalShell(command)
            ExecMethod.RUNTIME_EXEC -> executeViaRuntime(command)
        }
    }

    private fun executeViaShizuku(command: String): ShellCmdResult {
        return try {
            // Use Shizuku API if available
            val result = runBlockingShizuku(command)
            ShellCmdResult(
                command = command,
                stdout = result.stdout ?: "",
                stderr = result.stderr ?: "",
                exitCode = result.exitCode
            )
        } catch (e: Exception) {
            ShellCmdResult(command = command, stderr = "Shizuku: ${e.message}", exitCode = -1)
        }
    }

    private fun executeViaAdbShell(command: String): ShellCmdResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            ShellCmdResult(command, stdout, stderr, exitCode)
        } catch (e: Exception) {
            ShellCmdResult(command = command, stderr = "ADB shell: ${e.message}", exitCode = -1)
        }
    }

    private fun executeViaNormalShell(command: String): ShellCmdResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            ShellCmdResult(command, stdout, stderr, exitCode)
        } catch (e: Exception) {
            ShellCmdResult(command = command, stderr = "Normal shell: ${e.message}", exitCode = -1)
        }
    }

    private fun executeViaRuntime(command: String): ShellCmdResult {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            ShellCmdResult(command, stdout, stderr, exitCode)
        } catch (e: Exception) {
            ShellCmdResult(command = command, stderr = "Runtime exec: ${e.message}", exitCode = -1)
        }
    }

    /**
     * Interactive shell session for long-running commands.
     */
    suspend fun interactiveSession(
        initialCommand: String? = null
    ): InteractiveShell? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh"))
            val writer = OutputStreamWriter(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            if (initialCommand != null) {
                writer.write("$initialCommand\n")
                writer.flush()
            }

            InteractiveShell(process, writer, reader, errorReader)
        } catch (e: Exception) {
            Log.e(TAG, "Interactive shell failed: ${e.message}")
            null
        }
    }

    /**
     * Grant a permission using the best available method.
     */
    suspend fun grantPermission(permission: String): Boolean {
        val cmd = "pm grant ${context.packageName} $permission"
        val result = execute(cmd)
        return result.success
    }

    /**
     * Get the current best execution method name.
     */
    fun getBestMethodName(): String = bestMethod.name

    /** Run Shizuku command synchronously */
    private data class ShizukuResult(val stdout: String?, val stderr: String?, val exitCode: Int)

    private fun runBlockingShizuku(command: String): ShizukuResult {
        // This is a simplified version — in production, use the real Shizuku API
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            ShizukuResult(
                stdout = process.inputStream.bufferedReader().readText(),
                stderr = process.errorStream.bufferedReader().readText(),
                exitCode = process.waitFor()
            )
        } catch (e: Exception) {
            ShizukuResult(null, e.message, -1)
        }
    }

    /** Interactive shell session wrapper */
    class InteractiveShell(
        val process: Process,
        val writer: OutputStreamWriter,
        val reader: BufferedReader,
        val errorReader: BufferedReader
    ) {
        fun sendCommand(command: String) {
            writer.write("$command\n")
            writer.flush()
        }

        fun readOutput(): String = reader.readText()

        fun readError(): String = errorReader.readText()

        fun close() {
            writer.close()
            reader.close()
            errorReader.close()
            process.destroy()
        }
    }
}

/** Shell command execution result */
data class ShellCmdResult(
    val command: String,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val success: Boolean = exitCode == 0
)
