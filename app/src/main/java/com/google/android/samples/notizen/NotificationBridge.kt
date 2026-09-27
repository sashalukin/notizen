package com.google.android.samples.notizen

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** Display only. The web app owns reminder scheduling and persistence. */
class NotificationBridge(
    private val activity: ComponentActivity,
    private val isForeground: (WebView) -> Boolean,
) {
    companion object {
        const val ORIGIN = "https://notizen.dev"
        const val CHANNEL = "notizen_reminders"
        const val OPEN_NOTE = "com.google.android.samples.notizen.OPEN_REMINDER"
        const val NOTE_ID = "reminder_note_id"
        fun validNoteId(id: String) = id.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
    }

    private val manager = activity.getSystemService(NotificationManager::class.java)
    private val preferences = activity.getSharedPreferences("notification_permissions", 0)
    private var permissionReply: (() -> Unit)? = null
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionReply?.invoke()
        permissionReply = null
    }

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun permission(): String {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return if (preferences.getBoolean("requested", false)) "denied" else "default"
        }
        return if (NotificationManagerCompat.from(activity).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) "granted" else "denied"
    }

    fun install(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(webView, "AndroidNotifications", setOf(ORIGIN)) { view, message, origin, mainFrame, reply ->
            handle(view, message.data, origin.toString(), mainFrame) { reply.postMessage(it) }
        }
    }

    internal fun handle(view: WebView, data: String?, origin: String, mainFrame: Boolean, reply: (String) -> Unit) {
        if (!mainFrame || origin != ORIGIN) return
        val raw = data ?: return
        if (raw.length > 8192) return
        try {
            val command = JSONObject(raw)
            if (command.optInt("version") != 1) return
            val requestId = command.optString("requestId")
            val respond = {
                if (requestId.length in 1..100) {
                    // Replies are only for state/permission queries, never delivery acknowledgments.
                    runCatching { reply(JSONObject().put("requestId", requestId)
                        .put("permission", permission()).put("active", isForeground(view)).toString()) }
                }
                Unit
            }
            when (command.optString("type")) {
                "GET_STATE" -> respond()
                "REQUEST_PERMISSION" -> {
                    if (requestId.length !in 1..100) return
                    if (isForeground(view) && permission() == "default" && permissionReply == null) {
                        permissionReply = respond
                        preferences.edit().putBoolean("requested", true).apply()
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else respond()
                }
                "SHOW_NOTIFICATION" -> {
                    if (isForeground(view) && permission() == "granted") show(command)
                }
                "CLOSE_NOTIFICATION" -> {
                    val tag = command.optString("notificationId")
                    if (tag.length in 1..512) manager.cancel(tag, 0)
                }
                "CLEAR_NOTIFICATIONS" -> manager.cancelAll()
            }
        } catch (_: Exception) {
            // Untrusted/invalid messages cannot crash the app. No retries or delivery acknowledgment.
        }
    }

    private fun show(command: JSONObject) {
        val id = command.optString("noteId")
        val tag = command.optString("notificationId")
        val title = command.optString("title")
        val body = command.optString("body")
        if (!validNoteId(id) || tag.length !in 1..512 || title.length !in 1..200 || body.length > 4096) return
        val intent = Intent(activity, MainActivity::class.java).apply {
            action = OPEN_NOTE
            // Distinct immutable PendingIntents for distinct reminder occurrences.
            data = android.net.Uri.Builder().scheme("notizen-reminder").authority("note").appendPath(tag).build()
            putExtra(NOTE_ID, id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tap = PendingIntent.getActivity(activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(activity, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(tag, 0, notification)
    }
}
