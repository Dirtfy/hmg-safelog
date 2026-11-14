package com.hmg.safelog.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.hmg.safelog.core.Clock
import com.hmg.safelog.core.Contact
import com.hmg.safelog.core.ContactNotifier
import com.hmg.safelog.core.DriverNotifier
import com.hmg.safelog.core.EventAlertPolicy
import com.hmg.safelog.core.SafeLogEngine
import com.hmg.safelog.core.Speaker
import java.util.Calendar

/** Foreground service skeleton: wires adapters to the core engine. */
class SafeLogService : Service() {
    private var adapter: VehicleSignalAdapter? = null
    private var speaker: PleosTtsSpeaker? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "SafeLog", NotificationManager.IMPORTANCE_LOW))
        startForeground(
            1,
            Notification.Builder(this, CHANNEL)
                .setContentTitle("SafeLog PoC")
                .setContentText("차량 신호 모니터링 중")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build(),
        )

        val tts: Speaker = try {
            PleosTtsSpeaker(this).also { speaker = it }
        } catch (e: SecurityException) {
            Log.w("SafeLog", "Pleos TTS unavailable: $e")
            object : Speaker { override fun speak(text: String, languageTag: String) = Log.i("SafeLog", "SPEAK $text") }
        }
        val clock = object : Clock {
            override fun nowMs() = System.currentTimeMillis()
            override fun localHour(epochMs: Long): Int =
                Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)
        }
        // Default: no contacts, nothing is shared. Consent UI is out of scope for the PoC.
        val contacts = emptyList<Contact>()
        val contactNotifier = object : ContactNotifier {
            override fun notifyContact(contact: Contact, message: String) {
                Log.i("SafeLog", "notify ${contact.name}: $message")
            }
        }
        val driverNotifier = object : DriverNotifier {
            override fun remind(message: String) = tts.speak(message)
        }
        val engine = SafeLogEngine(
            speaker = tts,
            store = FileEventStore(filesDir.resolve("events")),
            clock = clock,
            alerts = EventAlertPolicy({ contacts }, contactNotifier, driverNotifier),
            onTripSummary = { Log.i("SafeLog", it.toKorean()) },
        )
        adapter = VehicleSignalAdapter(this).also { it.start(engine) }
    }

    override fun onDestroy() {
        adapter?.stop()
        speaker?.release()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "safelog"
    }
}
