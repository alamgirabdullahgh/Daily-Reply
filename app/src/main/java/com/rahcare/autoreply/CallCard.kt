package com.rahcare.autoreply

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// কল আসার সময় চেনা ব্যক্তির তথ্যের কার্ড: অন্য অ্যাপের ওপরে ভাসে (অনুমতি থাকলে), নইলে বড় নোটিফিকেশন। কল শেষ হলে সরে যায়।
object CallCard {
    private const val CH = "call_info"
    private const val NID = 7301
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var lastId = ""
    private var lastT = 0L

    // একই কলের জন্য দুবার সিস্টেম-সংকেত আসে: ৩০ সেকেন্ডের মধ্যে একই ব্যক্তির জন্য সংখ্যা একবারই বাড়ে
    fun incoming(c: Context, id: String, via: String) {
        val app = c.applicationContext
        main.post {
            try {
                val now = System.currentTimeMillis()
                if (!(id == lastId && now - lastT < 30000L)) Contacts.addCall(app, id)
                lastId = id
                lastT = now
                val p = Contacts.byId(app, id) ?: return@post
                show(app, p, via)
            } catch (e: Exception) {
                Store.ev(app, "কল-কার্ড ব্যর্থ: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    fun hide(c: Context) {
        val app = c.applicationContext
        main.post {
            try {
                view?.let { v -> app.getSystemService(WindowManager::class.java).removeView(v) }
            } catch (e: Exception) {}
            view = null
            try { app.getSystemService(NotificationManager::class.java).cancel(NID) } catch (e: Exception) {}
        }
    }

    private fun lines(c: Context, p: Person): Triple<String, String, String> {
        val name = if (p.name.isNotBlank()) p.name else (p.aliases.firstOrNull()?.substringAfter("|") ?: "নাম নেই")
        val ph = if (p.phones.isEmpty()) "নম্বর নেই" else p.phones.joinToString(", ")
        val ad = if (p.addr.isBlank()) "ঠিকানা নেই" else p.addr
        return Triple(name, ph, ad)
    }

    private fun stats(c: Context, p: Person): String {
        val lm = Contacts.lastMsg(c, p)
        val t = if (lm > 0) " · শেষ মেসেজ " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(lm)) else ""
        return "এবারসহ ফোন " + p.calls + " বার · মেসেজ " + Contacts.msgCount(c, p) + " বার" + t
    }

    private fun show(c: Context, p: Person, via: String) {
        val (name, ph, ad) = lines(c, p)
        val st = stats(c, p)
        if (Settings.canDrawOverlays(c)) {
            hideViewOnly(c)
            val dm = c.resources.displayMetrics
            fun d(v: Int) = (v * dm.density).toInt()
            val card = LinearLayout(c)
            card.orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#121A1F"))
            bg.cornerRadius = d(18).toFloat()
            bg.setStroke(d(2), Color.parseColor("#5CC9BC"))
            card.background = bg
            card.setPadding(d(16), d(12), d(16), d(14))
            val top = LinearLayout(c)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            fun tv(t: String, sp: Float, col: Int, bold: Boolean = false): TextView {
                val v = TextView(c)
                v.text = t
                v.textSize = sp
                v.setTextColor(col)
                if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
                return v
            }
            top.addView(tv(name, 22f, Color.WHITE, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val x = tv("✕", 20f, Color.parseColor("#8FA3A8"))
            x.setPadding(d(14), d(4), d(2), d(4))
            x.setOnClickListener { hide(c) }
            top.addView(x)
            card.addView(top)
            card.addView(tv("📞 $ph", 16f, Color.parseColor("#5CC9BC"), true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(6) })
            card.addView(tv("📍 $ad", 16f, Color.parseColor("#EAF2F3")), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(4) })
            card.addView(tv(st, 13f, Color.parseColor("#8FA3A8")), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            val wrap = FrameLayout(c)
            wrap.setPadding(d(10), 0, d(10), 0)
            wrap.addView(card)
            val lp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP
            lp.y = d(40)
            c.getSystemService(WindowManager::class.java).addView(wrap, lp)
            view = wrap
            return
        }
        // ভেসে ওঠার অনুমতি নেই: বড় নোটিফিকেশন
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "কলের সময় যোগাযোগের তথ্য", NotificationManager.IMPORTANCE_HIGH))
        val body = "📞 $ph\n📍 $ad\n$st"
        val n = android.app.Notification.Builder(c, CH)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(name)
            .setContentText("📞 $ph")
            .setStyle(android.app.Notification.BigTextStyle().bigText(body))
            .setCategory(android.app.Notification.CATEGORY_CALL)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NID, n)
    }

    private fun hideViewOnly(c: Context) {
        try { view?.let { v -> c.getSystemService(WindowManager::class.java).removeView(v) } } catch (e: Exception) {}
        view = null
    }
}
