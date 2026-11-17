package com.rahcare.autoreply

// মেসেজের লেখা থেকে নাম, নম্বর ও ঠিকানা বের করা। কোনো অ্যান্ড্রয়েড ক্লাস লাগে না।
// নিয়ম ইচ্ছে করেই কড়া: স্পষ্ট লেবেল ("আমার নাম", "নাম:", "ঠিকানা:", "মোবাইল:") না থাকলে নাম/ঠিকানা ধরা হয় না, ভুল তথ্য জমার চেয়ে না জমা ভালো।
object ContactsParse {
    private val BN = "০১২৩৪৫৬৭৮৯"
    private val AR = "٠١٢٣٤٥٦٧٨٩"

    fun digits(s: String): String {
        val b = StringBuilder(s.length)
        for (ch in s) {
            val i = BN.indexOf(ch)
            val j = AR.indexOf(ch)
            b.append(if (i >= 0) ('0' + i) else if (j >= 0) ('0' + j) else ch)
        }
        return b.toString()
    }

    // নম্বর সাজানো: বাংলাদেশি হলে 01XXXXXXXXX, বিদেশি হলে +কোড…; নম্বর না হলে null
    fun normPhone(raw: String): String? {
        val s = digits(raw).trim()
        val plus = s.startsWith("+")
        var d = s.filter { it.isDigit() }
        if (d.length < 7 || d.length > 15) return null
        var intl = plus
        if (d.startsWith("00")) { d = d.substring(2); intl = true }
        if (d.startsWith("880") && d.length == 13) return "0" + d.substring(3)
        if (d.length == 10 && d.startsWith("1") && !intl) return "0$d"
        if (d.length == 11 && d.startsWith("01") && !intl) return d
        return if (intl) "+$d" else if (d.length >= 8) d else null
    }

    // মেলানোর চাবি: নম্বরের শেষ ১০ অঙ্ক (+৮৮০১৭… ও ০১৭… একই ধরা হয়)
    fun phoneKey(p: String): String {
        val d = p.filter { it.isDigit() }
        return if (d.length > 10) d.takeLast(10) else d
    }

    // প্রেরকের নামের ঘরটাই যদি নম্বর হয় (সেভ না করা নম্বর)
    fun senderPhone(sender: String): String? {
        val s = digits(sender).trim()
        if (!Regex("^[+\\d\\s()\\-]{7,22}$").matches(s)) return null
        return normPhone(s)
    }

    private val bdRx = Regex("(?<!\\d)(?:\\+?88[\\s-]?)?01[3-9](?:[\\s-]?\\d){8}(?!\\d)")
    private val intlRx = Regex("(?<![\\d+])\\+\\d{1,3}(?:[\\s-]?\\d){7,12}(?!\\d)")

    fun phones(text: String): List<String> {
        val t = digits(text)
        val out = LinkedHashSet<String>()
        val used = ArrayList<IntRange>()
        for (m in bdRx.findAll(t)) { normPhone(m.value)?.let { out.add(it); used.add(m.range) } }
        for (m in intlRx.findAll(t)) {
            if (used.any { it.first <= m.range.last && m.range.first <= it.last }) continue
            normPhone(m.value)?.let { out.add(it) }
        }
        return out.toList()
    }

    private val L = "[\\p{L}\\p{M}]"
    private val strongRx = Regex("(?<!$L)(?:আমার\\s*নাম(?:টি)?|my\\s*name(?:\\s*is)?)(?:ঃ|(?!$L))\\s*(?:হলো|হল|হয়|হচ্ছে|is)?\\s*[:：ঃ=\\-–]*\\s*([^\\n\\r]{2,80})", RegexOption.IGNORE_CASE)
    private val labelRx = Regex("(?<!$L)(?:নাম|name)\\s*[:：ঃ=\\-–]+\\s*([^\\n\\r]{2,80})", RegexOption.IGNORE_CASE)
    private val nameStop = Regex("\\s+(?:ঠিকানা|address|মোবাইল|নাম্বার|নম্বর|নাম্বর|ফোন|phone|mobile|number|এবং|আর|আমি|আমার|বাসা|থাকি|জেলা|গ্রাম|এলাকা|থেকে|and|my|i\\s)", RegexOption.IGNORE_CASE)
    private val badName = setOf("কি", "কী", "কত", "কেন", "কোথায়", "কেমন", "ki", "what", "is", "the", "না", "নেই")

    fun name(text: String): String? {
        val t = digits(text)
        for (rx in listOf(strongRx, labelRx)) {
            for (m in rx.findAll(t)) {
                var v = text.substring(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1)
                v = v.split(Regex("[,.،।;:!?()\\d০-৯]"))[0]
                nameStop.find(v)?.let { v = v.substring(0, it.range.first) }
                v = v.trim().trim('-', '–', '"', '\'', ' ')
                val w = v.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (w.isEmpty() || w.size > 4 || v.length < 2) continue
                if (badName.contains(v.lowercase())) continue
                return w.joinToString(" ")
            }
        }
        return null
    }

    private val addrRx = Regex("(?<!$L)(?:ঠিকানা|address|addr)(?:ঃ|(?!$L))\\s*(?:হলো|হল|হচ্ছে|is)?\\s*[:：ঃ=\\-–]*\\s*([^\\n\\r]{4,160})", RegexOption.IGNORE_CASE)
    private val addrStop = Regex("\\s+(?:(?:মোবাইল|ফোন|phone|mobile)|(?:নাম্বার|নম্বর|নাম্বর|number)\\s*(?:[:ঃ=\\-–+]|\\d)|(?:নাম|name)\\s*[:ঃ])", RegexOption.IGNORE_CASE)

    fun address(text: String): String? {
        val t = digits(text)
        val m = addrRx.find(t) ?: return null
        var v = text.substring(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1)
        addrStop.find(digits(v))?.let { v = v.substring(0, it.range.first) }
        v = v.trim().trim('-', '–', ',', ' ', '।', '.')
        if (v.length < 4 || digits(v).all { it.isDigit() || it == ' ' }) return null
        return v
    }
}
