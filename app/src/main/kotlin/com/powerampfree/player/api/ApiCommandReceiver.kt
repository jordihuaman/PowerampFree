package com.powerampfree.player.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Public command surface for this app — own namespace, own semantics
 * (`com.powerampfree.player.API_COMMAND` with extras `cmd` and `arg`).
 *
 * This is an original API designed for our project, not a replica of any
 * reference app's public intents (interoperability limited to our own clients).
 */
class ApiCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO(integrate): route to PlaybackService via MediaSession controller.
    }
}
