package com.rahcare.autoreply

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// যোগাযোগ: মেসেজে কেউ নাম/নম্বর/ঠিকানা দিলে ফোনের ভেতরে জমা থাকে (কোথাও পাঠানো হয় না)।
// মেসেজ গোনা হয় প্রেরকের নাম ধরে (অ্যাপভেদে), ফোন গোনা হয় নম্বর ধরে।
class Person(
    var id: String,
    var name: String,
    val phones: MutableList<String>,
    var addr: String,
    val aliases: MutableList<String>,
    var calls: Int,
    var lastCall: Long,
    var created: Long
)

object Contacts {
    private const val KP = "ct_people"
    private const val KC = "ct_cnt"
    private const val MAXC = 3000

    private fun load(c: Context): ArrayList<Person> {
        val out = ArrayList<Person>()
        try {
            val a = JSONArray(Store.sp(c).getString(KP, "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                fun strs(k: String): MutableList<String> {
                    val l = ArrayList<String>()
                    val x = o.optJSONArray(k)
                    if (x != null) for (j in 0 until x.length()) l.add(x.getString(j))
                    return l
                }
                out.add(Person(o.getString("id"), o.optString("name"), strs("phones"), o.optString("addr"), strs("aliases"), o.optInt("calls"), o.optLong("lastCall"), o.optLong("created")))
            }
        } catch (e: Exception) {}
        return out
    }

    private fun save(c: Context, l: List<Person>) {
        val a = JSONArray()
        for (p in l) {
            val o = JSONObject()
            o.put("id", p.id).put("name", p.name).put("addr", p.addr).put("calls", p.calls).put("lastCall", p.lastCall).put("created", p.created)
            o.put("phones", JSONArray(p.phones)).put("aliases", JSONArray(p.aliases))
            a.put(o)
        }
        Store.sp(c).edit().putString(KP, a.toString()).apply()
    }

    private fun loadCnt(c: Context): JSONObject = try { JSONObject(Store.sp(c).getString(KC, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun saveCnt(c: Context, o: JSONObject) {
        if (o.length() > MAXC) {
            // সবচেয়ে পুরনো ১০% বাদ (যাদের নামে জমা ব্যক্তি আছে তারাও যেতে পারে, তাই শুধু অনেক বেশি হলে)
            val ks = ArrayList<String>()
            val it = o.keys()
            while (it.hasNext()) ks.add(it.next())
            ks.sortBy { o.getJSONObject(it).optLong("t") }
            for (k in ks.take(MAXC / 10)) o.remove(k)
        }
        Store.sp(c).edit().putString(KC, o.toString()).apply()
    }

    @Synchronized
    fun all(c: Context): List<Person> = load(c).sortedByDescending { lastSeen(c, it) }

    @Synchronized
    fun msgCount(c: Context, p: Person): Int {
        val cnt = loadCnt(c)
        var n = 0
        for (a in p.aliases) n += cnt.optJSONObject(a)?.optInt("n") ?: 0
        return n
    }

    @Synchronized
    fun lastMsg(c: Context, p: Person): Long {
        val cnt = loadCnt(c)
        var t = 0L
        for (a in p.aliases) t = maxOf(t, cnt.optJSONObject(a)?.optLong("t") ?: 0L)
        return t
    }

    private fun lastSeen(c: Context, p: Person): Long = maxOf(p.lastCall, lastMsg(c, p), p.created)

    // নম্বর ধরে খোঁজা (+৮৮০১৭… ও ০১৭… একই)
    @Synchronized
    fun findByPhone(c: Context, phone: String): Person? {
        val k = ContactsParse.phoneKey(phone)
        if (k.length < 7) return null
        return load(c).firstOrNull { p -> p.phones.any { ContactsParse.phoneKey(it) == k } }
    }

    // নতুন মেসেজ: গোনা বাড়ে; তথ্য (নাম/নম্বর/ঠিকানা) বা নম্বর-নামের প্রেরক থাকলে ব্যক্তি তৈরি/হালনাগাদ
    @Synchronized
    fun onMessage(c: Context, pkg: String, sender: String, text: String, isGroup: Boolean) {
        if (isGroup) return
        val s = sender.trim()
        if (s.isEmpty()) return
        val now = System.currentTimeMillis()
        val ak = (Store.grp(pkg) ?: pkg) + "|" + s.lowercase()
        val cnt = loadCnt(c)
        val o = cnt.optJSONObject(ak) ?: JSONObject().put("n", 0)
        o.put("n", o.optInt("n") + 1).put("t", now)
        cnt.put(ak, o)
        saveCnt(c, cnt)

        val phones = LinkedHashSet<String>()
        ContactsParse.senderPhone(s)?.let { phones.add(it) }
        phones.addAll(ContactsParse.phones(text))
        val name = ContactsParse.name(text)
        val addr = ContactsParse.address(text)
        val list = load(c)
        var p = list.firstOrNull { it.aliases.contains(ak) }
            ?: phones.firstNotNullOfOrNull { ph -> val k = ContactsParse.phoneKey(ph); list.firstOrNull { x -> x.phones.any { ContactsParse.phoneKey(it) == k } } }
        if (p == null && phones.isEmpty() && name == null && addr == null) return
        if (p == null) {
            p = Person(now.toString(), "", ArrayList(), "", ArrayList(), 0, 0L, now)
            list.add(p)
        }
        if (!p.aliases.contains(ak)) p.aliases.add(ak)
        for (ph in phones) if (p.phones.none { ContactsParse.phoneKey(it) == ContactsParse.phoneKey(ph) }) p.phones.add(ph)
        if (name != null) p.name = name
        if (addr != null) p.addr = addr
        save(c, list)
    }

    // ধাপ ২ (কল): নম্বর ধরে ফোনের সংখ্যা বাড়ায়
    @Synchronized
    fun onCall(c: Context, phone: String): Person? {
        val k = ContactsParse.phoneKey(phone)
        if (k.length < 7) return null
        val list = load(c)
        val p = list.firstOrNull { x -> x.phones.any { ContactsParse.phoneKey(it) == k } } ?: return null
        p.calls += 1
        p.lastCall = System.currentTimeMillis()
        save(c, list)
        return p
    }

    @Synchronized
    fun update(c: Context, id: String, name: String, phones: List<String>, addr: String) {
        val list = load(c)
        val p = list.firstOrNull { it.id == id } ?: return
        p.name = name.trim()
        p.addr = addr.trim()
        p.phones.clear()
        for (x in phones) { val n = ContactsParse.normPhone(x) ?: continue; if (p.phones.none { ContactsParse.phoneKey(it) == ContactsParse.phoneKey(n) }) p.phones.add(n) }
        save(c, list)
    }

    @Synchronized
    fun delete(c: Context, id: String) {
        val list = load(c)
        list.removeAll { it.id == id }
        save(c, list)
    }

    @Synchronized
    fun clear(c: Context) {
        Store.sp(c).edit().remove(KP).remove(KC).apply()
    }
}
