package com.rahcare.autoreply

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// "যোগাযোগ" পাতা: মেসেজ থেকে জমা হওয়া নাম, নম্বর, ঠিকানা এবং মেসেজ/ফোনের সংখ্যা। সবই ফোনের ভেতরে; এখান থেকে সম্পাদনা বা মুছে ফেলা যায়।
class ContactsActivity : Activity() {
    private val ink = Color.parseColor("#EAF2F3")
    private val mute = Color.parseColor("#8FA3A8")
    private val panel = Color.parseColor("#121A1F")
    private val line = Color.parseColor("#1F2C32")
    private val accent = Color.parseColor("#5CC9BC")
    private val warn = Color.parseColor("#F2867F")
    private lateinit var listBox: LinearLayout
    private lateinit var countTv: TextView
    private lateinit var permBox: LinearLayout
    private var q = ""

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = d(r).toFloat()
        if (stroke != 0) g.setStroke(d(1), stroke)
        return g
    }

    private fun tv(t: String, sp: Float = 15f, c: Int = ink, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sp
        v.setTextColor(c)
        v.setLineSpacing(0f, 1.15f)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.parseColor("#0E3A36")
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.parseColor("#0B1115"))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.parseColor("#0E3A36"))
        bar.setPadding(d(6), d(26), d(14), d(8))
        val back = tv("←", 22f, Color.WHITE, true)
        back.setPadding(d(12), d(6), d(14), d(6))
        back.setOnClickListener { finish() }
        bar.addView(back)
        bar.addView(tv("যোগাযোগ", 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, wc, 1f))
        val clr = tv("সব মুছুন", 13f, warn, true)
        clr.setPadding(d(10), d(8), d(6), d(8))
        clr.setOnClickListener {
            AlertDialog.Builder(this).setTitle("সব যোগাযোগ মুছবেন?")
                .setMessage("জমা থাকা সব নাম, নম্বর, ঠিকানা ও গোনা মুছে যাবে। এটা ফেরানো যাবে না।")
                .setPositiveButton("মুছুন") { _, _ -> Contacts.clear(this); fill() }
                .setNegativeButton("বাতিল", null).show()
        }
        bar.addView(clr)
        col.addView(bar, LinearLayout.LayoutParams(mp, wc))

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(16), d(12), d(16), d(40))
        val search = EditText(this)
        search.hint = "নাম, নম্বর বা ঠিকানা খুঁজুন"
        search.setHintTextColor(mute)
        search.setTextColor(ink)
        search.textSize = 15f
        search.setSingleLine(true)
        search.background = rounded(panel, 12, line)
        search.setPadding(d(14), d(10), d(14), d(10))
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) { q = e?.toString() ?: ""; fill() }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        permBox = LinearLayout(this)
        permBox.orientation = LinearLayout.VERTICAL
        body.addView(permBox, LinearLayout.LayoutParams(mp, wc).apply { bottomMargin = d(10) })
        body.addView(search, LinearLayout.LayoutParams(mp, wc))
        countTv = tv("", 12f, mute)
        body.addView(countTv, LinearLayout.LayoutParams(mp, wc).apply { topMargin = d(8) })
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        body.addView(listBox, LinearLayout.LayoutParams(mp, wc).apply { topMargin = d(6) })
        val sv = ScrollView(this)
        sv.addView(body)
        col.addView(sv, LinearLayout.LayoutParams(mp, 0, 1f))
        setContentView(col)
    }

    override fun onResume() {
        super.onResume()
        perms()
        fill()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        perms()
    }

    private fun callOk() = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    private fun btn(t: String, f: () -> Unit): TextView {
        val b = tv(t, 13f, accent, true)
        b.gravity = Gravity.CENTER
        b.background = rounded(Color.parseColor("#1A252B"), 10)
        b.setPadding(d(12), d(10), d(12), d(10))
        b.setOnClickListener { f() }
        return b
    }

    // কলের সময় তথ্য দেখাতে দুটো অনুমতি লাগে: কল ধরার (ফোন ও কল-লগ) আর অন্য অ্যাপের ওপরে ভেসে ওঠার
    private fun perms() {
        permBox.removeAllViews()
        val okC = callOk()
        val okO = Settings.canDrawOverlays(this)
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT
        if (okC && okO) {
            permBox.addView(tv("✓ কল আসার সময় চেনা ব্যক্তির তথ্য পর্দায় দেখানো চালু", 12.5f, accent), LinearLayout.LayoutParams(mp, wc))
            return
        }
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(panel, 16, warn)
        card.setPadding(d(14), d(12), d(14), d(14))
        card.addView(tv("কল আসার সময় তথ্য দেখাতে অনুমতি লাগবে", 15f, ink, true))
        card.addView(tv("জমা থাকা কেউ ফোন করলে নাম, ঠিকানা আর আগে কতবার ফোন/মেসেজ করেছেন তা পর্দায় ভেসে উঠবে। তথ্য ফোনের বাইরে যায় না।", 12.5f, mute), LinearLayout.LayoutParams(mp, wc).apply { topMargin = d(6) })
        if (!okC) card.addView(btn("১. কল ধরার অনুমতি দিন") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG), 71)
        }, LinearLayout.LayoutParams(mp, wc).apply { topMargin = d(10) })
        if (!okO) card.addView(btn((if (okC) "১" else "২") + ". অন্য অ্যাপের ওপরে দেখানোর অনুমতি দিন") {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
        }, LinearLayout.LayoutParams(mp, wc).apply { topMargin = d(8) })
        if (!okO) card.addView(tv("এই অনুমতি না দিলে কার্ডের বদলে বড় নোটিফিকেশন আসবে।", 12f, mute), LinearLayout.LayoutParams(mp, wc).apply { topMargin = d(6) })
        permBox.addView(card, LinearLayout.LayoutParams(mp, wc))
    }

    private fun fmt(t: Long): String = if (t <= 0L) "—" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(t))

    private fun title(p: Person): String {
        if (p.name.isNotBlank()) return p.name
        val a = p.aliases.firstOrNull()
        return if (a != null) a.substringAfter("|") else "নাম নেই"
    }

    private fun fill() {
        listBox.removeAllViews()
        val all = Contacts.all(this)
        val k = ContactsParse.digits(q).trim().lowercase()
        val kd = k.filter { it.isDigit() }
        val shown = all.filter { p ->
            k.isEmpty() || title(p).lowercase().contains(k) || p.addr.lowercase().contains(k) ||
                (kd.length >= 3 && p.phones.any { it.filter { ch -> ch.isDigit() }.contains(kd) })
        }
        countTv.text = if (all.isEmpty()) "" else "মোট " + all.size + " জন" + (if (k.isNotEmpty()) " · পাওয়া গেছে " + shown.size else "")
        if (all.isEmpty()) {
            listBox.addView(tv("এখনো কেউ জমা হয়নি। কেউ মেসেজে নাম, নম্বর বা ঠিকানা দিলে এখানে নিজে থেকে আসবে।\n\nউদাহরণ: \"আমার নাম রহিম, ঠিকানা মিরপুর, মোবাইল ০১৭…\"", 14f, mute), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(20) })
            return
        }
        for (p in shown) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.background = rounded(panel, 16, line)
            card.setPadding(d(14), d(12), d(14), d(12))
            card.addView(tv(title(p), 17f, ink, true))
            card.addView(tv("📞 " + (if (p.phones.isEmpty()) "নম্বর নেই" else p.phones.joinToString(", ")), 14f, if (p.phones.isEmpty()) mute else accent), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(6) })
            card.addView(tv("📍 " + (if (p.addr.isBlank()) "ঠিকানা নেই" else p.addr), 14f, if (p.addr.isBlank()) mute else ink), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(4) })
            val lm = Contacts.lastMsg(this, p)
            card.addView(tv("✉ মেসেজ " + Contacts.msgCount(this, p) + " · 📲 ফোন " + p.calls + (if (lm > 0) " · শেষ মেসেজ " + fmt(lm) else ""), 12.5f, mute), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            card.setOnClickListener { editDialog(p) }
            listBox.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10) })
        }
    }

    private fun field(hint: String, v: String, multi: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(v)
        e.textSize = 15f
        if (multi) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        return e
    }

    private fun editDialog(p: Person) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(d(20), d(8), d(20), d(0))
        val n = field("নাম", p.name)
        val ph = field("নম্বর (একাধিক হলে কমা দিয়ে)", p.phones.joinToString(", "))
        val ad = field("ঠিকানা", p.addr, true)
        box.addView(n)
        box.addView(ph)
        box.addView(ad)
        AlertDialog.Builder(this).setTitle("সম্পাদনা").setView(box)
            .setPositiveButton("সংরক্ষণ") { _, _ ->
                Contacts.update(this, p.id, n.text.toString(), ph.text.toString().split(Regex("[,،;\\n]")).map { it.trim() }.filter { it.isNotEmpty() }, ad.text.toString())
                fill()
            }
            .setNeutralButton("মুছুন") { _, _ -> Contacts.delete(this, p.id); fill() }
            .setNegativeButton("বাতিল", null).show()
    }
}
