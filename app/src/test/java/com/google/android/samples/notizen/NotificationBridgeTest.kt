package com.google.android.samples.notizen

import android.Manifest
import android.app.NotificationManager
import android.app.NotificationChannel
import android.webkit.WebView
import androidx.activity.ComponentActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationBridgeTest {
    private lateinit var bridge: NotificationBridge
    private lateinit var view: WebView
    private lateinit var manager: NotificationManager
    private var foreground = true
    private val note = "01234567-89ab-cdef-0123-456789abcdef"
    private val tag = "user:$note:2026-09-27T05:00:00Z"

    @Before fun setup() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.get()
        bridge = NotificationBridge(activity) { foreground }
        controller.setup()
        view = WebView(activity)
        manager = activity.getSystemService(NotificationManager::class.java)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun show(id: String = note, origin: String = NotificationBridge.ORIGIN, main: Boolean = true, notificationId: String = tag) {
        bridge.handle(view, JSONObject().put("version", 1).put("type", "SHOW_NOTIFICATION")
            .put("noteId", id).put("notificationId", notificationId).put("title", "Notizen reminder")
            .put("body", "My offline note").toString(), origin, main) { fail("No delivery acknowledgment") }
    }
    @Test fun displayAndTapPayloadAndDuplicateReplacement() {
        show(); show()
        val notifications = manager.activeNotifications
        assertEquals(1, notifications.size)
        val notification = notifications[0].notification
        assertEquals("Notizen reminder", notification.extras.getCharSequence("android.title"))
        assertEquals("My offline note", notification.extras.getCharSequence("android.text"))
        assertEquals(NotificationBridge.CHANNEL, notification.channelId)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(NotificationBridge.OPEN_NOTE, intent.action)
        assertEquals(note, intent.getStringExtra(NotificationBridge.NOTE_ID))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(notification.contentIntent.isImmutable)
        show(notificationId = "$tag:next")
        assertEquals(2, manager.activeNotifications.size)
    }
    @Test fun rejectsBackgroundUntrustedIframeAndInvalidNote() {
        foreground = false; show(); foreground = true
        show(origin = "https://notizen.dev.evil.example")
        show(main = false); show(id = "../other")
        bridge.handle(view, "not json", NotificationBridge.ORIGIN, true) {}
        assertEquals(0, manager.activeNotifications.size)
    }
    @Test fun permissionAndChannelBlockingAreHonored() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show(); assertEquals(0, manager.activeNotifications.size)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.createNotificationChannel(NotificationChannel(NotificationBridge.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_NONE))
        show(); assertEquals(0, manager.activeNotifications.size)
    }
    @Test fun stateQueryAndCleanup() {
        var state: JSONObject? = null
        bridge.handle(view, """{"version":1,"type":"GET_STATE","requestId":"query-1"}""", NotificationBridge.ORIGIN, true) { state = JSONObject(it) }
        assertEquals("granted", state!!.getString("permission"))
        assertEquals("query-1", state!!.getString("requestId"))
        assertTrue(state!!.getBoolean("active"))
        show()
        bridge.handle(view, JSONObject().put("version", 1).put("type", "CLOSE_NOTIFICATION").put("notificationId", tag).toString(), NotificationBridge.ORIGIN, true) {}
        assertEquals(0, manager.activeNotifications.size)
        show()
        bridge.handle(view, """{"version":1,"type":"CLEAR_NOTIFICATIONS"}""", NotificationBridge.ORIGIN, true) {}
        assertEquals(0, manager.activeNotifications.size)
    }
}
