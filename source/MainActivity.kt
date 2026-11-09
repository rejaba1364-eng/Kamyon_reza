package com.reza.kamyon

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
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
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PREFS = "kamyon_reza"

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val database by lazy { KamyonDatabase(this) }
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
        migrateOldDataOnce()
        val scroll = ScrollView(this).apply { setBackgroundColor(paper) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(root)
        setContentView(scroll)
        showHome()
    }

    private fun migrateOldDataOnce() {
        if (prefs.getBoolean("v2_data_migrated", false)) return
        try {
            val old = JSONArray(prefs.getString("checklist_history", "[]") ?: "[]")
            for (i in 0 until old.length()) {
                val item = old.optJSONObject(i) ?: continue
                database.insertChecklist(item)
            }
            val today = Jalali.today()
            val legacyTypes = listOf("daily", "monthly", "yearly")
            legacyTypes.forEach { type ->
                val period = when (type) {
                    "daily" -> today
                    "monthly" -> today.substring(0, 7)
                    else -> today.substring(0, 4)
                }
                val oldPrefix = "check_${type}_"
                val newPrefix = draftPrefix(type, period)
                val itemCount = when (type) { "daily" -> 10; "monthly" -> 8; else -> 6 }
                for (i in 0 until itemCount) {
                    if (prefs.contains(oldPrefix + i) && !prefs.contains(newPrefix + i)) {
                        prefs.edit().putBoolean(newPrefix + i, prefs.getBoolean(oldPrefix + i, false)).apply()
                    }
                }
                val oldNote = "note_$type"
                if (prefs.contains(oldNote) && !prefs.contains(newPrefix + "note")) {
                    prefs.edit().putString(newPrefix + "note", prefs.getString(oldNote, "")).apply()
                }
            }
        } catch (_: Exception) {
            // A damaged legacy history must not prevent the app from opening.
        }
        prefs.edit().putBoolean("v2_data_migrated", true).apply()
    }

    private fun showHome() {
        clearScreen()
        addHeader()
        val today = Jalali.today()
        info("امروز: ${fa(today)}  |  تاریخ از ساعت و تقویم خود گوشی خوانده می‌شود.")
        sectionTitle("چک‌لیست‌ها")
        button("چک‌لیست روزانه  •  ${fa(today)}") { showChecklist("daily", today) }
        button("چک‌لیست ماهانه  •  ${fa(today.substring(0, 7))}") { showChecklist("monthly", today) }
        button("چک‌لیست سالانه  •  ${fa(today.substring(0, 4))}") { showChecklist("yearly", today) }
        button("انتخاب تاریخ از تقویم شمسی") { showJalaliCalendar(today, true) { chosen -> showChecklist("daily", chosen) } }

        sectionTitle("سوابق و نگهداری")
        button("بانک سوابق چک‌لیست و جست‌وجوی تاریخ") { showHistorySearch() }
        button("سوابق مراجعه و گارانتی") { showWarrantyList() }
        button("ثبت مراجعه جدید گارانتی") { showWarrantyForm() }
        button("یادآوری بیمه و معاینه فنی") { showDocumentReminders() }

        sectionTitle("اطلاعات خودرو")
        button("مشخصات خودرو و یادداشت تعمیرات") { showVehicleInfo() }
        button("اشتراک‌گذاری گزارش خودرو") { shareReport() }
        info("اطلاعات چک‌لیست‌ها و مراجعات در پایگاه داده داخلی گوشی ذخیره می‌شود و با به‌روزرسانی عادی برنامه باقی می‌ماند.")
    }

    private fun showChecklist(type: String, selectedDate: String) {
        clearScreen()
        addHeader()
        val normalizedDate = selectedDate.take(10)
        val period = when (type) {
            "daily" -> normalizedDate
            "monthly" -> normalizedDate.substring(0, 7)
            else -> normalizedDate.substring(0, 4)
        }
        val typeTitle = when (type) { "daily" -> "چک‌لیست روزانه"; "monthly" -> "چک‌لیست ماهانه"; else -> "چک‌لیست سالانه" }
        val items = checklistItems(type)
        val prefix = draftPrefix(type, period)
        sectionTitle(typeTitle)
        info(when (type) {
            "daily" -> "تاریخ انتخاب‌شده: ${fa(period)}"
            "monthly" -> "ماه انتخاب‌شده: ${fa(period)}"
            else -> "سال انتخاب‌شده: ${fa(period)}"
        })
        button("تغییر تاریخ از تقویم شمسی") {
            showJalaliCalendar(normalizedDate, true) { chosen -> showChecklist(type, chosen) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val boxes = mutableListOf<CheckBox>()
        items.forEachIndexed { index, label ->
            val cb = CheckBox(this).apply {
                text = label
                textSize = 15f
                setTextColor(ink)
                isChecked = prefs.getBoolean(prefix + index, false)
                buttonTintList = android.content.res.ColorStateList.valueOf(green)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(prefix + index, checked).apply() }
            }
            boxes.add(cb)
            card.addView(cb, matchWrap())
        }
        val note = EditText(this).apply {
            hint = "یادداشت این دوره..."
            textSize = 14f
            minLines = 2
            gravity = Gravity.TOP or Gravity.RIGHT
            setText(prefs.getString(prefix + "note", ""))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(paper)
        }
        note.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { prefs.edit().putString(prefix + "note", s?.toString() ?: "").apply() }
        })
        card.addView(note, matchWrap().apply { topMargin = dp(8) })
        root.addView(card, matchWrap().apply { topMargin = dp(8) })
        button("ثبت سابقه با تاریخ و ساعت") {
            val now = Calendar.getInstance()
            val entry = JSONObject().apply {
                put("title", typeTitle)
                put("key", type)
                put("period", period)
                put("date", if (type == "daily") period else normalizedDate)
                put("time", SimpleDateFormat("HH:mm", Locale.US).format(Date()))
                put("savedAt", System.currentTimeMillis())
                put("note", note.text.toString())
                val checks = JSONArray()
                items.forEachIndexed { index, label ->
                    checks.put(JSONObject().put("name", label).put("checked", boxes[index].isChecked))
                }
                put("checks", checks)
            }
            database.insertChecklist(entry)
            prefs.edit().putString(prefix + "note", note.text.toString()).apply()
            toast("سابقه ${fa(period)} ذخیره شد")
        }
        button("بازگشت به صفحه اصلی") { showHome() }
    }

    private fun checklistItems(type: String): List<String> = when (type) {
        "daily" -> listOf(
            "آب رادیاتور و مایع خنک‌کننده", "سطح روغن موتور", "وضعیت باتری و اتصالات",
            "باد و سلامت لاستیک‌ها", "شلنگ‌ها و اتصالات", "چراغ‌ها و راهنماها",
            "ترمز و ترمز دستی", "آینه‌ها و برف‌پاک‌کن", "نشتی روغن یا آب",
            "تجهیزات ایمنی و کپسول آتش‌نشانی"
        )
        "monthly" -> listOf(
            "بررسی فیلتر هوا", "بررسی روغن و فیلترها", "بازدید تسمه‌ها",
            "بررسی سیستم خنک‌کننده", "بازدید لنت و اتصالات ترمز",
            "بررسی جلوبندی و فرمان", "کنترل زاپاس و ابزار خودرو", "نظافت کابین و بدنه"
        )
        else -> listOf(
            "تمدید بیمه شخص ثالث", "انجام معاینه فنی", "بررسی مدارک و مجوزهای خودرو",
            "بررسی کارت سوخت", "بازدید کامل سیستم برق", "سرویس کامل موتور و گیربکس"
        )
    }

    private fun showJalaliCalendar(initial: String, allowFuture: Boolean, onPicked: (String) -> Unit) {
        val first = Jalali.parse(initial) ?: Jalali.parse(Jalali.today())!!
        var year = first[0]
        var month = first[1]
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val gridHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dialog = AlertDialog.Builder(this).setTitle("تقویم شمسی").setView(layout).setNegativeButton("بستن", null).create()
        val previous = Button(this).apply { text = "ماه قبل"; isAllCaps = false }
        val next = Button(this).apply { text = "ماه بعد"; isAllCaps = false }
        val heading = TextView(this).apply { gravity = Gravity.CENTER; textSize = 17f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) }
        nav.addView(previous, LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(heading, LinearLayout.LayoutParams(0, dp(44), 2f))
        nav.addView(next, LinearLayout.LayoutParams(0, dp(44), 1f))
        layout.addView(nav)
        layout.addView(gridHolder)
        fun render() {
            heading.text = "${Jalali.monthNames[month - 1]} ${fa(year.toString())}"
            gridHolder.removeAllViews()
            val daysHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
            Jalali.weekNames.forEach { day ->
                val label = TextView(this).apply { text = day; gravity = Gravity.CENTER; textSize = 11f; setTextColor(muted) }
                daysHeader.addView(label, LinearLayout.LayoutParams(0, dp(32), 1f))
            }
            gridHolder.addView(daysHeader)
            val gregorian = Jalali.toGregorian(String.format(Locale.US, "%04d/%02d/01", year, month))
            val offset = if (gregorian == null) 0 else {
                val c = Calendar.getInstance().apply { set(gregorian[0], gregorian[1] - 1, gregorian[2], 12, 0, 0) }
                c.get(Calendar.DAY_OF_WEEK) % 7 // Saturday is column zero.
            }
            val totalDays = Jalali.monthLength(year, month)
            var day = 1
            while (day <= totalDays) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                for (column in 0 until 7) {
                    val position = ((gridHolder.childCount - 1) * 7) + column
                    if (position < offset || day > totalDays) {
                        val blank = TextView(this)
                        row.addView(blank, LinearLayout.LayoutParams(0, dp(42), 1f))
                    } else {
                        val pickedDay = day++
                        val dayText = Button(this).apply {
                            text = fa(pickedDay.toString())
                            textSize = 13f
                            isAllCaps = false
                            setPadding(0, 0, 0, 0)
                            val chosen = String.format(Locale.US, "%04d/%02d/%02d", year, month, pickedDay)
                            val today = Jalali.today()
                            if (chosen == today) setTextColor(green)
                            setOnClickListener {
                                if (!allowFuture && chosen > today) toast("انتخاب تاریخ آینده مجاز نیست")
                                else { dialog.dismiss(); onPicked(chosen) }
                            }
                        }
                        row.addView(dayText, LinearLayout.LayoutParams(0, dp(42), 1f))
                    }
                }
                gridHolder.addView(row)
            }
        }
        previous.setOnClickListener { if (month == 1) { month = 12; year-- } else month--; render() }
        next.setOnClickListener { if (month == 12) { month = 1; year++ } else month++; render() }
        render()
        dialog.show()
    }

    private fun showHistorySearch() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val search = EditText(this).apply { hint = "جست‌وجوی تاریخ؛ نمونه ۱۴۰۵/۰۹ یا ۱۴۰۵/۰۹/۱۸"; isSingleLine = true }
        val filter = android.widget.Spinner(this)
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("همه سوابق", "روزانه", "ماهانه", "سالانه"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filter.adapter = adapter
        panel.addView(search, matchWrap())
        panel.addView(filter, matchWrap())
        AlertDialog.Builder(this).setTitle("بانک سوابق چک‌لیست").setView(panel)
            .setPositiveButton("جست‌وجو") { _, _ ->
                val type = when (filter.selectedItemPosition) { 1 -> "daily"; 2 -> "monthly"; 3 -> "yearly"; else -> "" }
                showHistoryResults(type, normalizeDigits(search.text.toString()).trim())
            }
            .setNeutralButton("نمایش همه") { _, _ -> showHistoryResults("", "") }
            .setNegativeButton("بازگشت", null).show()
    }

    private fun showHistoryResults(type: String, search: String) {
        val entries = database.checklistEntries(type, search)
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle("سوابق").setMessage("موردی با این فیلتر پیدا نشد.").setPositiveButton("باشه", null).show()
            return
        }
        val labels = entries.map { entry ->
            val checks = entry.optJSONArray("checks") ?: JSONArray()
            var completed = 0
            for (i in 0 until checks.length()) if (checks.optJSONObject(i)?.optBoolean("checked") == true) completed++
            "${entry.optString("title")} | ${fa(entry.optString("date"))} ${fa(entry.optString("time"))} | $completed/${checks.length()}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("سوابق؛ جدیدترین اول").setItems(labels) { _, index -> showChecklistDetails(entries[index]) }
            .setNegativeButton("بستن", null).show()
    }

    private fun showChecklistDetails(entry: JSONObject) {
        val out = StringBuilder()
        out.append(entry.optString("title")).append('\n')
        out.append("تاریخ: ").append(fa(entry.optString("date"))).append('\n')
        out.append("ساعت ثبت: ").append(fa(entry.optString("time"))).append("\n\n")
        val checks = entry.optJSONArray("checks") ?: JSONArray()
        for (i in 0 until checks.length()) {
            val item = checks.optJSONObject(i) ?: continue
            out.append(if (item.optBoolean("checked")) "✓ انجام شد" else "□ انجام نشد")
                .append(" - ").append(item.optString("name")).append('\n')
        }
        if (entry.optString("note").isNotBlank()) out.append("\nیادداشت:\n").append(entry.optString("note"))
        AlertDialog.Builder(this).setTitle("جزئیات سابقه").setMessage(out.toString()).setPositiveButton("بستن", null).show()
    }

    private fun showWarrantyForm() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0) }
        val visitDate = formField(panel, "تاریخ مراجعه شمسی (۱۴۰۵/۰۹/۱۸)", Jalali.today())
        val mileage = formField(panel, "کیلومتر خودرو هنگام مراجعه")
        val stage = formField(panel, "شماره مرحله گارانتی")
        val work = formField(panel, "کارهای انجام‌شده", "", false)
        val parts = formField(panel, "قطعات تعویض‌شده", "", false)
        val cost = formField(panel, "هزینه (اختیاری)")
        val dealer = formField(panel, "نام نمایندگی")
        val note = formField(panel, "توضیحات و یادداشت", "", false)
        val nextDate = formField(panel, "تاریخ سررسید بعدی (اختیاری)", "")
        val nextKm = formField(panel, "کیلومتر سررسید بعدی (اختیاری)", "")
        visitDate.isFocusable = false
        visitDate.setOnClickListener { showJalaliCalendar(visitDate.text.toString(), true) { visitDate.setText(it) } }
        nextDate.isFocusable = false
        nextDate.setOnClickListener { showJalaliCalendar(nextDate.text.toString().ifBlank { Jalali.today() }, true) { nextDate.setText(it) } }
        val formScroll = ScrollView(this).apply { addView(panel) }
        AlertDialog.Builder(this).setTitle("ثبت مراجعه گارانتی").setView(formScroll)
            .setPositiveButton("ذخیره مراجعه", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val date = normalizeDigits(visitDate.text.toString()).trim()
                        if (!Jalali.isValid(date)) {
                            toast("تاریخ مراجعه را از تقویم شمسی انتخاب کن")
                            return@setOnClickListener
                        }
                        val obj = JSONObject().apply {
                            put("visit_date", date); put("mileage", mileage.text.toString().trim())
                            put("stage", stage.text.toString().trim()); put("work", work.text.toString().trim())
                            put("parts", parts.text.toString().trim()); put("cost", cost.text.toString().trim())
                            put("dealer", dealer.text.toString().trim()); put("note", note.text.toString().trim())
                            put("next_date", normalizeDigits(nextDate.text.toString()).trim())
                            put("next_km", nextKm.text.toString().trim()); put("saved_at", System.currentTimeMillis())
                        }
                        database.insertWarranty(obj)
                        val futureDate = obj.optString("next_date")
                        prefs.edit().putString("warranty_next_date", futureDate).putString("warranty_next_km", obj.optString("next_km")).apply()
                        ReminderScheduler.schedule(this@MainActivity, "warranty", futureDate)
                        dialog.dismiss()
                        toast("مراجعه گارانتی ذخیره شد")
                        showWarrantyList()
                    }
                }
                dialog.show()
            }
    }

    private fun showWarrantyList(search: String = "") {
        val query = normalizeDigits(search).trim()
        val records = database.warrantyEntries().filter { item ->
            query.isBlank() || listOf("visit_date", "stage", "dealer", "mileage", "work", "parts", "note")
                .any { item.optString(it).contains(query, ignoreCase = true) }
        }
        if (records.isEmpty()) {
            val message = if (query.isBlank()) "هنوز مراجعه‌ای ثبت نشده است." else "موردی با این عبارت پیدا نشد."
            AlertDialog.Builder(this).setTitle("سوابق گارانتی").setMessage(message)
                .setPositiveButton("ثبت مراجعه") { _, _ -> showWarrantyForm() }
                .setNeutralButton("جست‌وجو") { _, _ -> showWarrantySearch() }
                .setNegativeButton("بازگشت", null).show()
            return
        }
        val labels = records.map { "${fa(it.optString("visit_date"))} | مرحله ${fa(it.optString("stage"))} | ${it.optString("dealer")}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("سوابق مراجعه گارانتی").setItems(labels) { _, which -> showWarrantyDetails(records[which]) }
            .setPositiveButton("ثبت مراجعه جدید") { _, _ -> showWarrantyForm() }
            .setNeutralButton("جست‌وجو") { _, _ -> showWarrantySearch() }.setNegativeButton("بستن", null).show()
    }

    private fun showWarrantySearch() {
        val input = EditText(this).apply { hint = "تاریخ، مرحله، نمایندگی یا کار انجام‌شده" }
        AlertDialog.Builder(this).setTitle("جست‌وجوی سوابق گارانتی").setView(input)
            .setPositiveButton("جست‌وجو") { _, _ -> showWarrantyList(input.text.toString()) }
            .setNegativeButton("لغو", null).show()
    }

    private fun showWarrantyDetails(item: JSONObject) {
        val out = StringBuilder()
        out.append("تاریخ مراجعه: ").append(fa(item.optString("visit_date"))).append('\n')
        out.append("کیلومتر: ").append(fa(item.optString("mileage"))).append('\n')
        out.append("مرحله: ").append(fa(item.optString("stage"))).append('\n')
        out.append("نمایندگی: ").append(item.optString("dealer")).append('\n')
        out.append("کارهای انجام‌شده: ").append(item.optString("work")).append('\n')
        out.append("قطعات تعویض‌شده: ").append(item.optString("parts")).append('\n')
        out.append("هزینه: ").append(fa(item.optString("cost"))).append('\n')
        out.append("توضیحات: ").append(item.optString("note")).append('\n')
        if (item.optString("next_date").isNotBlank()) out.append("سررسید بعدی: ").append(fa(item.optString("next_date"))).append('\n')
        if (item.optString("next_km").isNotBlank()) out.append("کیلومتر سررسید: ").append(fa(item.optString("next_km")))
        AlertDialog.Builder(this).setTitle("جزئیات مراجعه").setMessage(out.toString())
            .setNeutralButton("حذف این سابقه") { _, _ ->
                database.deleteWarranty(item.optLong("id"))
                val latest = database.warrantyEntries().firstOrNull()
                val nextDate = latest?.optString("next_date") ?: ""
                val nextKm = latest?.optString("next_km") ?: ""
                prefs.edit().putString("warranty_next_date", nextDate).putString("warranty_next_km", nextKm).apply()
                ReminderScheduler.schedule(this@MainActivity, "warranty", nextDate)
                toast("سابقه حذف شد")
                showWarrantyList()
            }.setPositiveButton("بستن", null).show()
    }

    private fun showDocumentReminders() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0) }
        val insurance = formField(panel, "تاریخ پایان بیمه شخص ثالث (شمسی)", prefs.getString("date_insurance", "") ?: "")
        val inspection = formField(panel, "تاریخ پایان معاینه فنی (شمسی)", prefs.getString("date_inspection", "") ?: "")
        listOf(insurance, inspection).forEach { field ->
            field.isFocusable = false
            field.setOnClickListener { showJalaliCalendar(field.text.toString().ifBlank { Jalali.today() }, true) { field.setText(it) } }
        }
        AlertDialog.Builder(this).setTitle("یادآوری مدارک؛ ۷ روز پیش از موعد").setView(panel)
            .setPositiveButton("ذخیره و تنظیم یادآوری") { _, _ ->
                val a = normalizeDigits(insurance.text.toString()).trim()
                val b = normalizeDigits(inspection.text.toString()).trim()
                if (a.isNotBlank() && !Jalali.isValid(a)) { toast("تاریخ بیمه معتبر نیست"); return@setPositiveButton }
                if (b.isNotBlank() && !Jalali.isValid(b)) { toast("تاریخ معاینه فنی معتبر نیست"); return@setPositiveButton }
                prefs.edit().putString("date_insurance", a).putString("date_inspection", b).apply()
                ReminderScheduler.schedule(this, "insurance", a)
                ReminderScheduler.schedule(this, "inspection", b)
                toast("تاریخ‌ها ذخیره شد")
            }.setNegativeButton("بستن", null).show()
    }

    private fun showVehicleInfo() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0) }
        val name = formField(panel, "نام خودرو یا مدل", prefs.getString("vehicle_name", "") ?: "")
        val plate = formField(panel, "شماره پلاک", prefs.getString("vehicle_plate", "") ?: "")
        val km = formField(panel, "کارکرد فعلی (کیلومتر)", prefs.getString("vehicle_km", "") ?: "")
        val notes = formField(panel, "یادداشت تعمیر و سرویس", prefs.getString("maintenance_notes", "") ?: "", false)
        notes.minLines = 3
        AlertDialog.Builder(this).setTitle("مشخصات خودرو").setView(panel)
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit().putString("vehicle_name", name.text.toString()).putString("vehicle_plate", plate.text.toString())
                    .putString("vehicle_km", km.text.toString()).putString("maintenance_notes", notes.text.toString()).apply()
                toast("اطلاعات خودرو ذخیره شد")
            }.setNegativeButton("بستن", null).show()
    }

    private fun shareReport() {
        val out = StringBuilder("گزارش خودرو - کامیون رضا\n")
        out.append("خودرو: ${prefs.getString("vehicle_name", "ثبت نشده")}\n")
        out.append("پلاک: ${prefs.getString("vehicle_plate", "ثبت نشده")}\n")
        out.append("کارکرد: ${prefs.getString("vehicle_km", "ثبت نشده")}\n")
        out.append("تعداد سوابق چک‌لیست: ${database.checklistEntries("", "").size}\n")
        out.append("تعداد مراجعات گارانتی: ${database.warrantyEntries().size}\n")
        out.append("یادداشت تعمیرات: ${prefs.getString("maintenance_notes", "")}\n")
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "گزارش خودرو"); putExtra(Intent.EXTRA_TEXT, out.toString()) }
        startActivity(Intent.createChooser(send, "اشتراک‌گذاری گزارش"))
    }

    private fun formField(parent: LinearLayout, hint: String, value: String = "", single: Boolean = true): EditText {
        val edit = EditText(this).apply {
            this.hint = hint
            textSize = 14f
            isSingleLine = single
            if (!single) minLines = 2
            setText(value)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(Color.WHITE)
        }
        parent.addView(edit, matchWrap().apply { topMargin = dp(5) })
        return edit
    }

    private fun addHeader() {
        text("کامیون رضا", 25f, Color.WHITE, true).also {
            it.setPadding(dp(16), dp(18), dp(16), dp(5)); it.setBackgroundColor(green); root.addView(it, matchWrap())
        }
        text("چک‌لیست و نگهداری خودرو  |  آفلاین", 13f, Color.WHITE).also {
            it.setPadding(dp(16), 0, dp(16), dp(16)); it.setBackgroundColor(green); root.addView(it, matchWrap())
        }
    }

    private fun clearScreen() { root.removeAllViews() }
    private fun sectionTitle(value: String) { root.addView(text(value, 18f, ink, true).apply { setPadding(0, dp(18), 0, dp(4)) }, matchWrap()) }
    private fun info(value: String) { root.addView(text(value, 13f, muted).apply { setPadding(dp(4), dp(5), dp(4), dp(5)) }, matchWrap()) }
    private fun button(label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label; textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(green)
            setOnClickListener { action() }
        }
        root.addView(b, matchWrap().apply { topMargin = dp(6) })
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.RIGHT
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun draftPrefix(type: String, period: String) = "draft_${type}_${period}_"
    private fun fa(value: String): String {
        val english = "0123456789"
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        return value.map { ch -> val i = english.indexOf(ch); if (i >= 0) persian[i] else ch }.joinToString("")
    }
    private fun normalizeDigits(value: String): String = value
        .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
        .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')

    override fun onDestroy() {
        super.onDestroy()
        // SQLiteOpenHelper manages its connection lifecycle; the database file remains on the device.
    }
}

private class KamyonDatabase(context: Context) : SQLiteOpenHelper(context, "kamyon_reza.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE checklist_history (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, title TEXT NOT NULL, period_key TEXT, entry_date TEXT NOT NULL, entry_time TEXT NOT NULL, saved_at INTEGER NOT NULL, note TEXT, checks_json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE warranty_visits (id INTEGER PRIMARY KEY AUTOINCREMENT, visit_date TEXT NOT NULL, mileage TEXT, stage TEXT, work TEXT, parts TEXT, cost TEXT, dealer TEXT, note TEXT, next_date TEXT, next_km TEXT, saved_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_checklist_date ON checklist_history(entry_date)")
        db.execSQL("CREATE INDEX idx_warranty_date ON warranty_visits(visit_date)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS idx_warranty_date ON warranty_visits(visit_date)")
    }
    fun insertChecklist(item: JSONObject) {
        val values = ContentValues().apply {
            put("type", item.optString("key", item.optString("type", "daily")))
            put("title", item.optString("title", "چک‌لیست"))
            put("period_key", item.optString("period", item.optString("date", "")))
            put("entry_date", item.optString("date", ""))
            put("entry_time", item.optString("time", ""))
            put("saved_at", item.optLong("savedAt", item.optLong("saved_at", System.currentTimeMillis())))
            put("note", item.optString("note", ""))
            put("checks_json", (item.optJSONArray("checks") ?: JSONArray()).toString())
        }
        writableDatabase.insert("checklist_history", null, values)
    }
    fun checklistEntries(type: String, search: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val db = readableDatabase
        val cursor = db.query("checklist_history", null, null, null, null, null, "saved_at DESC")
        cursor.use {
            while (it.moveToNext()) {
                val rowType = it.string("type")
                val date = it.string("entry_date")
                if (type.isNotBlank() && rowType != type) continue
                if (search.isNotBlank() && !date.contains(search)) continue
                result.add(JSONObject().apply {
                    put("id", it.long("id")); put("key", rowType); put("title", it.string("title"))
                    put("period", it.string("period_key")); put("date", date); put("time", it.string("entry_time"))
                    put("savedAt", it.long("saved_at")); put("note", it.string("note")); put("checks", JSONArray(it.string("checks_json")))
                })
            }
        }
        return result
    }
    fun insertWarranty(item: JSONObject) {
        val values = ContentValues().apply {
            put("visit_date", item.optString("visit_date")); put("mileage", item.optString("mileage"))
            put("stage", item.optString("stage")); put("work", item.optString("work")); put("parts", item.optString("parts"))
            put("cost", item.optString("cost")); put("dealer", item.optString("dealer")); put("note", item.optString("note"))
            put("next_date", item.optString("next_date")); put("next_km", item.optString("next_km")); put("saved_at", item.optLong("saved_at"))
        }
        writableDatabase.insert("warranty_visits", null, values)
    }
    fun warrantyEntries(): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val cursor = readableDatabase.query("warranty_visits", null, null, null, null, null, "saved_at DESC")
        cursor.use {
            while (it.moveToNext()) result.add(JSONObject().apply {
                put("id", it.long("id")); put("visit_date", it.string("visit_date")); put("mileage", it.string("mileage"))
                put("stage", it.string("stage")); put("work", it.string("work")); put("parts", it.string("parts"))
                put("cost", it.string("cost")); put("dealer", it.string("dealer")); put("note", it.string("note"))
                put("next_date", it.string("next_date")); put("next_km", it.string("next_km")); put("saved_at", it.long("saved_at"))
            })
        }
        return result
    }
    fun deleteWarranty(id: Long) { writableDatabase.delete("warranty_visits", "id=?", arrayOf(id.toString())) }
    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
}

object ReminderScheduler {
    fun schedule(context: Context, kind: String, jalaliDate: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val request = when (kind) { "insurance" -> 5101; "inspection" -> 5102; else -> 5103 }
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("kind", kind)
        val pending = PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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

private object Jalali {
    val monthNames = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val weekNames = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    fun today(): String {
        val now = Calendar.getInstance()
        return fromGregorian(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    }
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
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
    }
    fun parse(input: String): IntArray? {
        val parts = input.trim().replace('-', '/').split('/')
        if (parts.size < 2) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = if (parts.size > 2) parts[2].toIntOrNull() ?: 1 else 1
        if (y !in 1300..1500 || m !in 1..12 || d !in 1..31) return null
        return intArrayOf(y, m, d)
    }
    fun monthLength(year: Int, month: Int): Int {
        if (month in 1..6) return 31
        if (month in 7..11) return 30
        val leapDay = String.format(Locale.US, "%04d/12/30", year)
        val gregorian = toGregorian(leapDay) ?: return 29
        return if (fromGregorian(gregorian[0], gregorian[1], gregorian[2]) == leapDay) 30 else 29
    }
    fun isValid(input: String): Boolean {
        val p = parse(input) ?: return false
        if (p[2] !in 1..monthLength(p[0], p[1])) return false
        val greg = toGregorian(input) ?: return false
        return fromGregorian(greg[0], greg[1], greg[2]) == String.format(Locale.US, "%04d/%02d/%02d", p[0], p[1], p[2])
    }
    fun toGregorian(input: String): IntArray? {
        val normalized = input.trim().replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
            .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9').replace('-', '/')
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
            d--; gy += 100 * (d / 36524); d %= 36524
            if (d >= 365) d++
        }
        gy += 4 * (d / 1461); d %= 1461
        if (d >= 366) { leap = false; d--; gy += d / 365; d %= 365 }
        val monthDays = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && d >= monthDays[gm]) { d -= monthDays[gm]; gm++ }
        if (gm !in 0..11) return null
        return intArrayOf(gy, gm + 1, d + 1)
    }
}

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
