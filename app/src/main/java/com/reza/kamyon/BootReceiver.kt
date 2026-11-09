package com.reza.kamyon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("kamyon_reza", Context.MODE_PRIVATE)
            ReminderScheduler.schedule(context, "insurance", prefs.getString("date_insurance", "") ?: "")
            ReminderScheduler.schedule(context, "inspection", prefs.getString("date_inspection", "") ?: "")
        }
    }
}
