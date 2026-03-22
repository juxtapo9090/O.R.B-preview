package com.phantom.ai.termux

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Bundle

class TermuxResultService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TermuxBridge.CALLBACK_ACTION) {
            val requestId = intent.getStringExtra(TermuxBridge.CALLBACK_REQUEST_ID)
            val resultBundle = intent.getBundleExtra(TermuxBridge.EXTRA_PLUGIN_RESULT_BUNDLE)
            LocalTermuxSession.onResult(requestId, resultBundle.toLocalCommandResult())
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private fun Bundle?.toLocalCommandResult(): LocalCommandResult {
    if (this == null) {
        return LocalCommandResult(
            stdout = "",
            stderr = "",
            exitCode = -1,
            errCode = -1,
            errMsg = "No result bundle returned by Termux."
        )
    }

    return LocalCommandResult(
        stdout = getString(TermuxBridge.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT).orEmpty(),
        stderr = getString(TermuxBridge.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR).orEmpty(),
        exitCode = getInt(TermuxBridge.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, -1),
        errCode = getInt(TermuxBridge.EXTRA_PLUGIN_RESULT_BUNDLE_ERR, 0),
        errMsg = getString(TermuxBridge.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG).orEmpty()
    )
}
