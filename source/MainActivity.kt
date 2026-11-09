package com.reza.kamyon

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PREFS = "kamyon_reza"

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val green = Color.rgb(23, 107, 91)
    private val ink = Color.rgb(34, 48, 45)
    private val muted = Color.rgb(100, 116, 111)
    private val paper = Color.rgb(244, 247, 246)
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 76, 66)
        window.navigationBarColor = paper
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
        }
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(paper)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(root)
        setContentView(scroll)
        buildScreen()
    }

    private fun buildScreen() {
        text("کامیون رضا", 27f, Color.WHITE, true).also {
            it.setPadding(dp(18), dp(22), dp(18), dp(4))
            it.setBackgroundColor(green)
            root.addView(it, matchWrap())
        }
        text("چک‌لیست و نگهداری خودرو | قابل استفاده بدون اینترنت", 13f, Color.WHITE).also {
            it.setPadding(dp(18), 0, dp(18), dp(18))
            it.setBackgroundColor(green)
            root.addView(it, matchWrap())
        }

        sectionTitle("مشخصات خودرو")
        val vehicle = editField("نام خودرو یا مدل", "vehicle_name")
        val plate = editField("شماره پلاک", "vehicle_plate")
        val km = editField("کارکرد فعلی (کیلومتر)", "vehicle_km")
        button("ذخیره مشخصات خودرو") {
            saveField("vehicle_name", vehicle)
            saveField("vehicle_plate", plate)
            saveField("vehicle_km", km)
            toast("مشخصات خودرو ذخیره شد")
        }

        sectionTitle("چک‌لیست‌های سرویس")
        checklist("بررسی روزانه", "daily", listOf(
            "آب رادیاتور و مایع خنک‌کننده", "سطح روغن موتور", "وضعیت باتری و اتصالات",
            "باد و سلامت لاستیک‌ها", "شلنگ‌ها و اتصالات", "چراغ‌ها و راهنماها",
            "ترمز و ترمز دستی", "آینه‌ها و برف‌پاک‌کن", "نشتی روغن یا آب",
            "تجهیزات ایمنی و کپسول آتش‌نشانی"
        ))
        checklist("سرویس ماهانه", "monthly", listOf(
            "بررسی فیلتر هوا", "بررسی روغن و فیلترها", "بازدید تسمه‌ها",
            "بررسی سیستم خنک‌کننده", "بازدید لنت و اتصالات ترمز",
            "بررسی جلوبندی و فرمان", "کنترل زاپاس و ابزار خودرو", "نظافت کابین و بدنه"
        ))
        checklist("موارد سالانه", "yearly", listOf(
            "تمدید بیمه شخص ثالث", "انجام معاینه فنی", "بررسی مدارک و مجوزهای خودرو",
            "بررسی کارت سوخت", "بازدید کامل سیستم برق", "سرویس کامل موتور و گیربکس"
        ))
        button("مشاهده سوابق چک‌لیست") { chooseHistoryType() }

        sectionTitle("یادآوری مدارک")
        text("تاریخ‌ها را به شمسی وارد کن؛ نمونه: ۱۴۰۵/۰۹/۲۰. یادآوری ۷ روز قبل تنظیم می‌شود.", 13f, muted)
        val insurance = editField("تاریخ پایان بیمه شخص ثالث (شمسی)", "date_insurance")
        val inspection = editField("تاریخ پایان معاینه فنی (شمسی)", "date_inspection")
        button("ذخیره تاریخ‌ها و تنظیم یادآوری") {
            val a = insurance.text.toString().trim()
            val b = inspection.text.toString().trim()
            if (a.isNotEmpty() && Jalali.toGregorian(a) == null) {
                toast("تاریخ بیمه را مثل ۱۴۰۵/۰۹/۲۰ وارد کن")
                return@button
            }
            if (b.isNotEmpty() && Jalali.toGregorian(b) == null) {
                toast("تاریخ معاینه فنی را مثل ۱۴۰۵/۰۹/۲۰ وارد کن")
                return@button
            }
            prefs.edit().putString("date_insurance", a).putString("date_inspection", b).apply()
            ReminderScheduler.schedule(this, "insurance", a)
            ReminderScheduler.schedule(this, "inspection", b)
            toast("تاریخ‌ها ذخیره شد؛ یادآوری ۷ روز قبل تنظیم شد")
        }

        sectionTitle("یادداشت تعمیر و سرویس")
        val notes = EditText(this).apply {
            hint = "شرح تعمیر، قطعه تعویض‌شده، هزینه و توضیحات..."
            minLines = 4
            gravity = Gravity.TOP or Gravity.RIGHT
            textSize = 15f
            setText(prefs.getString("maintenance_notes", ""))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(notes, matchWrap().apply { topMargin = dp(8) })
        button("ذخیره یادداشت") {
            prefs.edit().putString("maintenance_notes", notes.text.toString()).apply()
            toast("یادداشت ذخیره شد")
        }
        button("اشتراک‌گذاری گزارش خودرو") { shareReport() }
        text("اطلاعات روی همین گوشی ذخیره می‌شود. برای نگهداری نسخه پشتیبان، گزارش را با پیام‌رسان یا فایل‌ها به اشتراک بگذار.", 12f, muted).also {
            root.addView(it, matchWrap())
        }
    }

    private fun checklist(title: String, key: String, items: List<String>) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        card.addView(text(title, 18f, green, true), matchWrap())
        val boxes = mutableListOf<CheckBox>()
        items.forEachIndexed { index, label ->
            val id = "check_${key}_$index"
            val cb = CheckBox(this).apply {
                text = label
                textSize = 15f
                setTextColor(ink)
                isChecked = prefs.getBoolean(id, false)
                buttonTintList = android.content.res.ColorStateList.valueOf(green)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(id, checked).apply()
                }
            }
            boxes.add(cb)
            card.addView(cb, matchWrap())
        }
        val note = EditText(this).apply {
            hint = "یادداشت این نوبت..."
            textSize = 14f
            setText(prefs.getString("note_$key", ""))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(paper)
        }
        card.addView(note, matchWrap().apply { topMargin = dp(6) })
        val save = Button(this).apply {
            text = "ثبت و ذخیره با تاریخ"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(green)
            setOnClickListener {
                val checkedCount = boxes.count { it.isChecked }
                if (checkedCount == 0) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("ثبت چک‌لیست")
                        .setMessage("هیچ موردی تیک نخورده است. می‌خواهی همین وضعیت را به‌عنوان سابقه ذخیره کنی؟")
                        .setNegativeButton("بازگشت", null)
                        .setPositiveButton("ثبت سابقه") { _, _ ->
                            saveChecklistEntry(title, key, items, boxes, note)
                        }
                        .show()
                } else {
                    saveChecklistEntry(title, key, items, boxes, note)
                }
            }
        }
        card.addView(save, matchWrap().apply { topMargin = dp(8) })
        root.addView(card, matchWrap().apply { topMargin = dp(8) })
    }

    private fun saveChecklistEntry(
        title: String,
        key: String,
        items: List<String>,
        boxes: List<CheckBox>,
        note: EditText
    ) {
        val now = Calendar.getInstance()
        val date = Jalali.fromGregorian(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val entry = JSONObject().apply {
            put("title", title)
            put("key", key)
            put("date", date)
            put("time", time)
            put("savedAt", System.currentTimeMillis())
            put("note", note.text.toString())
            val checks = JSONArray()
            items.forEachIndexed { index, item ->
                checks.put(JSONObject().put("name", item).put("checked", boxes[index].isChecked))
            }
            put("checks", checks)
        }
        val history = readHistory()
        history.put(entry)
        prefs.edit().putString("checklist_history", history.toString()).apply()

        boxes.forEachIndexed { index, box ->
            box.isChecked = false
            prefs.edit().putBoolean("check_${key}_$index", false).apply()
        }
        note.setText("")
        prefs.edit().putString("note_$key", "").apply()
        toast("سابقه $title در تاریخ ${persianDigits(date)} ذخیره شد")
    }

    private fun readHistory(): JSONArray {
        return try {
            JSONArray(prefs.getString("checklist_history", "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun chooseHistoryType() {
        val options = arrayOf("همه سوابق", "روزانه", "ماهانه", "سالانه")
        AlertDialog.Builder(this)
            .setTitle("انتخاب نوع سوابق")
            .setItems(options) { _, which ->
                val key = when (which) {
                    1 -> "daily"
                    2 -> "monthly"
                    3 -> "yearly"
                    else -> ""
                }
                showChecklistHistory(key)
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun showChecklistHistory(filterKey: String) {
        val all = readHistory()
        val entries = (0 until all.length())
            .mapNotNull { index -> all.optJSONObject(index) }
            .filter { filterKey.isBlank() || it.optString("key") == filterKey }
            .sortedByDescending { it.optLong("savedAt") }

        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("سوابق چک‌لیست")
                .setMessage("برای این بخش هنوز سابقه‌ای ثبت نشده است.")
                .setPositiveButton("باشه", null)
                .show()
            return
        }

        val labels = entries.map { entry ->
            val checks = entry.optJSONArray("checks") ?: JSONArray()
            var done = 0
            for (i in 0 until checks.length()) {
                if (checks.optJSONObject(i)?.optBoolean("checked", false) == true) done++
            }
            "${entry.optString("title")} | ${persianDigits(entry.optString("date"))} - " +
                "${persianDigits(entry.optString("time"))} | $done از ${checks.length()} مورد"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("سوابق ثبت‌شده؛ جدیدترین اول")
            .setItems(labels) { _, which -> showHistoryDetails(entries[which]) }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun showHistoryDetails(entry: JSONObject) {
        val details = StringBuilder()
        details.append(entry.optString("title")).append('\n')
        details.append("تاریخ: ").append(persianDigits(entry.optString("date"))).append('\n')
        details.append("ساعت: ").append(persianDigits(entry.optString("time"))).append("\n\n")
        val checks = entry.optJSONArray("checks") ?: JSONArray()
        for (i in 0 until checks.length()) {
            val item = checks.optJSONObject(i) ?: continue
            val mark = if (item.optBoolean("checked", false)) "✓ انجام شد" else "□ انجام نشد"
            details.append(mark).append(" - ").append(item.optString("name")).append('\n')
        }
        val note = entry.optString("note")
        if (note.isNotBlank()) details.append("\nیادداشت:\n").append(note)

        AlertDialog.Builder(this)
            .setTitle("جزئیات سابقه")
            .setMessage(details.toString())
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun persianDigits(value: String): String {
        val latin = "0123456789"
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        return value.map { ch ->
            val index = latin.indexOf(ch)
            if (index >= 0) persian[index] else ch
        }.joinToString("")
    }

    private fun sectionTitle(value: String) {
        val t = text(value, 20f, ink, true)
        t.setPadding(0, dp(22), 0, dp(5))
        root.addView(t, matchWrap())
    }

    private fun editField(hintText: String, key: String): EditText {
        val e = EditText(this).apply {
            hint = hintText
            textSize = 15f
            isSingleLine = true
            setText(prefs.getString(key, ""))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(e, matchWrap().apply { topMargin = dp(7) })
        return e
    }

    private fun saveField(key: String, e: EditText) {
        prefs.edit().putString(key, e.text.toString()).apply()
    }

    private fun button(label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(green)
            setOnClickListener { action() }
        }
        root.addView(b, matchWrap().apply { topMargin = dp(8) })
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.RIGHT
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun matchWrap() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()

    private fun shareReport() {
        val out = StringBuilder("گزارش خودرو - کامیون رضا\n")
        out.append("خودرو: ${prefs.getString("vehicle_name", "ثبت نشده")}\n")
        out.append("پلاک: ${prefs.getString("vehicle_plate", "ثبت نشده")}\n")
        out.append("کارکرد: ${prefs.getString("vehicle_km", "ثبت نشده")}\n\n")
        val groups = listOf(
            Triple("بررسی روزانه", "daily", listOf(
                "آب رادیاتور و مایع خنک‌کننده", "سطح روغن موتور", "وضعیت باتری و اتصالات",
                "باد و سلامت لاستیک‌ها", "شلنگ‌ها و اتصالات", "چراغ‌ها و راهنماها",
                "ترمز و ترمز دستی", "آینه‌ها و برف‌پاک‌کن", "نشتی روغن یا آب",
                "تجهیزات ایمنی و کپسول آتش‌نشانی"
            )),
            Triple("سرویس ماهانه", "monthly", listOf(
                "بررسی فیلتر هوا", "بررسی روغن و فیلترها", "بازدید تسمه‌ها",
                "بررسی سیستم خنک‌کننده", "بازدید لنت و اتصالات ترمز",
                "بررسی جلوبندی و فرمان", "کنترل زاپاس و ابزار خودرو", "نظافت کابین و بدنه"
            )),
            Triple("موارد سالانه", "yearly", listOf(
                "تمدید بیمه شخص ثالث", "انجام معاینه فنی", "بررسی مدارک و مجوزهای خودرو",
                "بررسی کارت سوخت", "بازدید کامل سیستم برق", "سرویس کامل موتور و گیربکس"
            ))
        )
        groups.forEach { (label, key, items) ->
            out.append("\n$label\n")
            items.forEachIndexed { index, item ->
                val mark = if (prefs.getBoolean("check_${key}_$index", false)) "✓" else "□"
                out.append("$mark $item\n")
            }
            out.append("یادداشت: ${prefs.getString("note_$key", "")}\n")
        }
        out.append("بیمه شخص ثالث: ${prefs.getString("date_insurance", "ثبت نشده")}\n")
        out.append("معاینه فنی: ${prefs.getString("date_inspection", "ثبت نشده")}\n")
        out.append("\nیادداشت تعمیرات:\n${prefs.getString("maintenance_notes", "")}")
        out.append("\n\nتعداد سوابق چک‌لیست ثبت‌شده: ${readHistory().length()}")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "گزارش خودرو")
            putExtra(Intent.EXTRA_TEXT, out.toString())
        }
        startActivity(Intent.createChooser(send, "اشتراک‌گذاری گزارش"))
    }
}

object ReminderScheduler {
    fun schedule(context: Context, kind: String, jalaliDate: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val request = if (kind == "insurance") 5101 else 5102
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("kind", kind)
        val pending = PendingIntent.getBroadcast(
            context, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pending)
        if (jalaliDate.isBlank()) return
        val gregorian = Jalali.toGregorian(jalaliDate) ?: return
        val calendar = Calendar.getInstance().apply {
            set(gregorian[0], gregorian[1] - 1, gregorian[2], 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -7)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
    }
}

object Jalali {
    fun fromGregorian(gyInput: Int, gm: Int, gd: Int): String {
        val gDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy = gyInput
        var jy = if (gy > 1600) 979 else 0
        if (gy > 1600) gy -= 1600 else gy -= 621
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gDays[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
    }

    fun toGregorian(input: String): IntArray? {
        val normalized = input.trim()
            .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
            .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
            .replace('-', '/')
        val p = normalized.split('/')
        if (p.size != 3) return null
        val jy = p[0].toIntOrNull() ?: return null
        val jm = p[1].toIntOrNull() ?: return null
        val jd = p[2].toIntOrNull() ?: return null
        if (jy !in 1300..1500 || jm !in 1..12 || jd !in 1..31) return null
        var days = 365 * (jy - 979) + ((jy - 979) / 33) * 8 + (((jy - 979) % 33) + 3) / 4
        for (m in 1 until jm) days += if (m <= 6) 31 else 30
        days += jd - 1 + 79
        var gy = 1600 + 400 * (days / 146097)
        var d = days % 146097
        var leap = true
        if (d >= 36525) {
            d--
            gy += 100 * (d / 36524)
            d %= 36524
            if (d >= 365) d++
        }
        gy += 4 * (d / 1461)
        d %= 1461
        if (d >= 366) {
            leap = false
            d--
            gy += d / 365
            d %= 365
        }
        val monthDays = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && d >= monthDays[gm]) {
            d -= monthDays[gm]
            gm++
        }
        if (gm !in 0..11) return null
        return intArrayOf(gy, gm + 1, d + 1)
    }
}
