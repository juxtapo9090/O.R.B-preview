package com.phantom.ai.termux

object TermuxBridge {
    const val DEFAULT_PACKAGE = "com.termux"

    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT = "stdout"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_STDERR = "stderr"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE = "exitCode"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_ERR = "err"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG = "errmsg"

    const val SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY = "3"

    const val CALLBACK_ACTION = "com.phantom.ai.TERMUX_COMMAND_RESULT"
    const val CALLBACK_REQUEST_ID = "request_id"
    const val CALLBACK_COMMAND = "command"
}
