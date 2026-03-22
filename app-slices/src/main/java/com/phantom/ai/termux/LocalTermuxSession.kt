package com.phantom.ai.termux

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.phantom.ai.data.SettingsRepository
import com.phantom.ai.ui.overlay.ChatEventBus
import com.phantom.ai.ui.overlay.ChatMessage
import com.phantom.ai.ui.overlay.MessageType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class LocalTermuxRequest(
    val id: String,
    val command: String,
    val lane: String
)

data class LocalCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errCode: Int,
    val errMsg: String
)

object LocalTermuxSession {
    private const val TAG = "LocalTermuxSession"
    private const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"

    private val pending = ConcurrentHashMap<String, LocalTermuxRequest>()

    fun handleChatCommand(app: Application, rawInput: String): Boolean {
        val isCmssh = rawInput.startsWith("~~")
        val command = if (isCmssh) {
            rawInput.removePrefix("~~").trim()
        } else {
            rawInput.removePrefix("~").trim()
        }
        if (command.isBlank()) {
            val label = if (isCmssh) "CMssh" else "local"
            postSystem("⚠️ Empty $label command.")
            return true
        }

        postUserEcho(rawInput)

        if (isCmssh) {
            enqueue(app, command, "cmssh", buildCmsshInvocation(command))
        } else {
            enqueue(app, command, "local", buildLocalInvocation(command))
        }
        return true
    }

    fun onResult(requestId: String?, result: LocalCommandResult) {
        if (requestId.isNullOrBlank()) {
            postSystem("⚠️ Termux returned a result without request id.")
            return
        }

        val request = pending.remove(requestId)
        if (request == null) {
            postSystem("⚠️ Termux returned an unknown local command result.")
            return
        }

        val combined = buildString {
            val cleanStdout = sanitizeTermuxText(result.stdout)
            val cleanStderr = sanitizeTermuxText(result.stderr)

            if (cleanStdout.isNotBlank()) {
                append(cleanStdout.trimEnd())
            }
            if (cleanStderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(cleanStderr.trimEnd())
            }
            if (isEmpty()) {
                append("(no output)")
            }
        }

        val header = "${request.lane}> ${request.command}"
        val suffix = when {
            result.errCode > -1 -> "\n[termux err=${result.errCode}] ${result.errMsg.ifBlank { "unknown error" }}"
            result.exitCode != 0 -> "\n[exit ${result.exitCode}]"
            else -> ""
        }

        ChatEventBus.postMessage(
            ChatMessage(
                content = "$header\n$combined$suffix",
                isUser = false,
                messageType = MessageType.TOOL
            )
        )
    }

    private fun enqueue(app: Application, logicalCommand: String, lane: String, shellCommand: String) {
        val settings = runBlocking { SettingsRepository(app).settingsFlow.first() }
        val termuxPackage = settings.termuxPackage.ifBlank { TermuxBridge.DEFAULT_PACKAGE }

        if (!isTermuxInstalled(app, termuxPackage)) {
            postSystem("⚠️ Termux package `$termuxPackage` not found.")
            return
        }

        if (!hasRunCommandPermission(app)) {
            postSystem("⚠️ O.R.B. lacks `com.termux.permission.RUN_COMMAND`.")
            return
        }

        val request = LocalTermuxRequest(
            id = UUID.randomUUID().toString(),
            command = logicalCommand,
            lane = lane
        )
        pending[request.id] = request

        val callbackIntent = Intent(app, TermuxResultService::class.java).apply {
            action = TermuxBridge.CALLBACK_ACTION
            putExtra(TermuxBridge.CALLBACK_REQUEST_ID, request.id)
            putExtra(TermuxBridge.CALLBACK_COMMAND, logicalCommand)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0

        val callback = PendingIntent.getService(
            app,
            request.id.hashCode(),
            callbackIntent,
            flags
        )

        val intent = Intent(TermuxBridge.ACTION_RUN_COMMAND).apply {
            setClassName(termuxPackage, TermuxBridge.RUN_COMMAND_SERVICE)
            putExtra(TermuxBridge.EXTRA_COMMAND_PATH, "$TERMUX_BIN/bash")
            putExtra(TermuxBridge.EXTRA_ARGUMENTS, arrayOf("-lc", shellCommand))
            putExtra(TermuxBridge.EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(TermuxBridge.EXTRA_BACKGROUND, true)
            putExtra(
                TermuxBridge.EXTRA_SESSION_ACTION,
                TermuxBridge.SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY
            )
            putExtra(TermuxBridge.EXTRA_COMMAND_LABEL, "O.R.B local")
            putExtra(TermuxBridge.EXTRA_PENDING_INTENT, callback)
            component = ComponentName(termuxPackage, TermuxBridge.RUN_COMMAND_SERVICE)
        }

        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (e: Exception) {
            pending.remove(request.id)
            Log.e(TAG, "Failed to launch Termux command", e)
            postSystem("⚠️ Failed to launch Termux command: ${e.message}")
        }
    }

    private fun buildLocalInvocation(rawCommand: String): String {
        val encodedCommand = Base64.encodeToString(rawCommand.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = listOf(
            "ORB_CMD_B64=${shSingleQuote(encodedCommand)}",
            "CMD=\"$(printf '%s' \"${'$'}ORB_CMD_B64\" | base64 -d)\"",
            "bash -lc \"${'$'}CMD\""
        ).joinToString("\n")
        return wrapShell(rawCommand, body)
    }

    private fun buildCmsshInvocation(rawCommand: String): String {
        val encodedCommand = Base64.encodeToString(rawCommand.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = listOf(
            "CMSSH_BIN=\"$(command -v cmssh 2>/dev/null || command -v CMssh 2>/dev/null || true)\"",
            "if [ -z \"${'$'}CMSSH_BIN\" ]; then",
            "  echo \"cmssh not found in PATH\"",
            "  exit 127",
            "fi",
            "CMSSH_CMD_B64=${shSingleQuote(encodedCommand)}",
            "RAW=\"$(printf '%s' \"${'$'}CMSSH_CMD_B64\" | base64 -d)\"",
            "eval \"\\\"${'$'}CMSSH_BIN\\\" ${'$'}RAW\""
        ).joinToString("\n")
        return wrapShell(rawCommand, body)
    }

    private fun wrapShell(command: String, body: String): String {
        val label = command.replace("\n", " ").take(120)
        return """
            export PATH="$TERMUX_BIN:${'$'}PATH"
            export HOME="$TERMUX_HOME"
            $body
        """.trimIndent().also {
            Log.d(TAG, "Dispatching local command: $label")
        }
    }

    private fun sanitizeTermuxText(text: String): String {
        return text
            .lineSequence()
            .map { line ->
                line
                    .replace(Regex("\\u001B\\[[0-9;?]*[ -/]*[@-~]"), "")
                    .replace(Regex("\\u001B\\][^\\u0007\\u001B]*(\\u0007|\\u001B\\\\)"), "")
                    .replace(Regex("\\u001B[@-_]"), "")
                    .replace(Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F]"), "")
                    .trimEnd()
            }
            .filterNot { line ->
                line.isBlank() ||
                    line.startsWith("no server running on ") ||
                    line.contains("/default\"") ||
                    line.contains("/default'")
            }
            .joinToString("\n")
    }

    private fun postUserEcho(rawInput: String) {
        ChatEventBus.postMessage(
            ChatMessage(
                content = rawInput,
                isUser = true,
                messageType = MessageType.USER
            )
        )
    }

    private fun postSystem(message: String) {
        ChatEventBus.postMessage(
            ChatMessage(
                content = message,
                isUser = false,
                messageType = MessageType.SYSTEM
            )
        )
    }

    private fun isTermuxInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun hasRunCommandPermission(context: Context): Boolean {
        return context.checkSelfPermission(TermuxBridge.PERMISSION_RUN_COMMAND) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun shSingleQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

}
