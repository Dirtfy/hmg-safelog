package com.hmg.safelog.core

/**
 * Consent-first sharing. Defaults: nothing is ever sent to contacts.
 *  (a) Driver-initiated share: only through [driverShare] (explicit call).
 *  (b) Event alerts: only to contacts that consented to that category.
 */
class SharePolicy(private val notifier: ContactNotifier) {
    /** Explicit driver action: share a message with one chosen contact. */
    fun driverShare(contact: Contact, message: String) {
        notifier.notifyContact(contact, message)
    }
}

data class AlertConfig(
    val telltaleDays: Int = 3,
    val reminderIntervalMs: Long = 24 * 3_600_000L,
)

class EventAlertPolicy(
    private val contacts: () -> List<Contact>,
    private val contactNotifier: ContactNotifier,
    private val driverNotifier: DriverNotifier,
    private val config: AlertConfig = AlertConfig(),
) {
    private val onSince = HashMap<WarningTelltale, Long>()
    private val lastReminder = HashMap<WarningTelltale, Long>()

    fun onAirbagDeployed(tMs: Long) {
        for (c in contacts()) {
            if (AlertCategory.AIRBAG_DEPLOYED in c.consented) {
                contactNotifier.notifyContact(c, "에어백이 전개되었습니다. 운전자의 안전을 확인해 주세요.")
            }
        }
    }

    fun onTelltales(tMs: Long, readings: List<TelltaleReading>) {
        for (r in readings) {
            if (!r.on) {
                onSince.remove(r.type)
                lastReminder.remove(r.type)
                continue
            }
            val since = onSince.getOrPut(r.type) { tMs }
            val thresholdMs = config.telltaleDays * 24 * 3_600_000L
            if (tMs - since < thresholdMs) continue
            val last = lastReminder[r.type]
            if (last != null && tMs - last < config.reminderIntervalMs) continue
            lastReminder[r.type] = tMs
            driverNotifier.remind("${r.type.name} 경고등이 ${config.telltaleDays}일 이상 켜져 있습니다. 점검을 권장합니다.")
            for (c in contacts()) {
                if (AlertCategory.WARNING_TELLTALE in c.consented) {
                    contactNotifier.notifyContact(c, "${r.type.name} 경고등이 ${config.telltaleDays}일 이상 켜져 있습니다.")
                }
            }
        }
    }
}
