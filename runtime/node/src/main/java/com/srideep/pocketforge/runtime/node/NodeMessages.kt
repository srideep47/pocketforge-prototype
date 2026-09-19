package com.srideep.pocketforge.runtime.node

/** Wire constants for the Messenger protocol between `:main` and `:node`. */
object NodeMessages {
    const val MSG_START = 1
    const val MSG_STOP = 2
    const val MSG_STATUS = 3

    const val KEY_PROJECT_PATH = "project_path"
    const val KEY_PORT = "port"
    const val KEY_RUNNING = "running"
    const val KEY_URL = "url"
    const val KEY_ERROR = "error"

    const val DEFAULT_PORT = 5173
}
