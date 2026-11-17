package com.rahcare.autoreply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

// ফোন আসার সংকেত: নম্বর জমা থাকা কারো হলে তথ্যের কার্ড দেখায়; কল শেষ হলে সরিয়ে দেয়
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // প্রথম সংকেতে নম্বর থাকে না; নম্বর-সহ সংকেতেই কাজ হয়
                @Suppress("DEPRECATION")
                val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
                val p = Contacts.findByPhone(c, num) ?: return
                CallCard.incoming(c, p.id, "ফোন কল")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> CallCard.hide(c)
        }
    }
}
