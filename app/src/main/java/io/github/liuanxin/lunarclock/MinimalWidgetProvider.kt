package io.github.liuanxin.lunarclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val PREFS_NAME = "minimal_widget"
private const val KEY_LAST_RENDERED_DATE = "last_rendered_date"
private const val ACTION_REFRESH_DATE = "io.github.liuanxin.lunarclock.action.REFRESH_DATE"
private const val REQUEST_REFRESH_DATE = 1001

private val WEEK_NAMES = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

// 收到这些广播时重新渲染微件
private val REFRESH_ACTIONS = setOf(
    Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_REFRESH_DATE
)

// 这些广播无视"今天已渲染"强制刷新
private val FORCE_ACTIONS = setOf(
    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED
)

// 0~2 天倒计时前缀，节气与节日共用
private val DAY_PREFIX = arrayOf("今天", "明天", "后天")

class MinimalWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == null || action !in REFRESH_ACTIONS) {
            super.onReceive(context, intent)
            return
        }
        val now = Calendar.getInstance()
        scheduleNextDateRefresh(context, now)
        val last = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_RENDERED_DATE, null)
        if (dateKey(now) == last && action !in FORCE_ACTIONS) {
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MinimalWidgetProvider::class.java))
        if (ids != null && ids.isNotEmpty()) {
            forceUpdateAppWidgets(context, manager, ids, now)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val now = Calendar.getInstance()
        forceUpdateAppWidgets(context, appWidgetManager, appWidgetIds, now)
        scheduleNextDateRefresh(context, now)
    }

    override fun onEnabled(context: Context) = scheduleNextDateRefresh(context, Calendar.getInstance())

    override fun onDisabled(context: Context) = cancelNextDateRefresh(context)

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        val now = Calendar.getInstance()
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, now, appWidgetId, newOptions))
        saveLastRenderedDate(context, now)
        scheduleNextDateRefresh(context, now)
    }
}

private fun buildViews(context: Context, now: Calendar, widgetId: Int, options: Bundle?): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_layout)
    val year = now.get(Calendar.YEAR)
    val month = now.get(Calendar.MONTH) + 1
    val day = now.get(Calendar.DAY_OF_MONTH)
    val weekName = WEEK_NAMES[now.get(Calendar.DAY_OF_WEEK) - 1]

    views.setTextViewText(R.id.date_text, String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, day))
    val tip = dayTip(now)
    views.setTextViewText(R.id.week_text, if (tip == null) weekName else "$weekName · $tip")
    val lunar = Lunar.format(now)
    views.setTextViewText(R.id.lunar_text, "${lunar.ganzhiYear}-${lunar.date}")
    views.setCharSequence(R.id.time_clock, "setFormat12Hour", "HH:mm")
    views.setCharSequence(R.id.time_clock, "setFormat24Hour", "HH:mm")
    views.setString(R.id.time_clock, "setTimeZone", TimeZone.getDefault().id)
    applyTextSizes(views, options)

    views.setOnClickPendingIntent(
        R.id.calendar_zone,
        PendingIntent.getActivity(context, widgetId * 2, calendarIntent(now.timeInMillis), pendingIntentFlags())
    )
    views.setOnClickPendingIntent(
        R.id.time_clock,
        PendingIntent.getActivity(
            context, widgetId * 2 + 2,
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            pendingIntentFlags()
        )
    )
    return views
}

private fun forceUpdateAppWidgets(context: Context, manager: AppWidgetManager, ids: IntArray, now: Calendar) {
    for (id in ids) {
        manager.updateAppWidget(id, buildViews(context, now, id, manager.getAppWidgetOptions(id)))
    }
    saveLastRenderedDate(context, now)
}

private fun saveLastRenderedDate(context: Context, now: Calendar) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_LAST_RENDERED_DATE, dateKey(now)).apply()
}

private fun dateKey(now: Calendar) = String.format(
    Locale.CHINA, "%04d%02d%02d",
    now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
)

private fun scheduleNextDateRefresh(context: Context, now: Calendar) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDateRefreshTime(now), refreshDatePendingIntent(context))
}

private fun nextDateRefreshTime(now: Calendar): Long {
    val next = now.clone() as Calendar
    next.add(Calendar.DAY_OF_MONTH, 1)
    next.set(Calendar.HOUR_OF_DAY, 0)
    next.set(Calendar.MINUTE, 0)
    next.set(Calendar.SECOND, 3)
    next.set(Calendar.MILLISECOND, 0)
    return next.timeInMillis
}

private fun cancelNextDateRefresh(context: Context) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    am.cancel(refreshDatePendingIntent(context))
}

private fun refreshDatePendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MinimalWidgetProvider::class.java).setAction(ACTION_REFRESH_DATE)
    return PendingIntent.getBroadcast(context, REQUEST_REFRESH_DATE, intent, pendingIntentFlags())
}

private fun calendarIntent(timeMillis: Long): Intent {
    val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(timeMillis.toString()).build()
    return Intent(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun pendingIntentFlags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

// 按微件尺寸缩放各行字号，高度足够才显示日期/农历/星期
private fun applyTextSizes(views: RemoteViews, options: Bundle?) {
    val minWidth = readOption(options, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
    val minHeight = readOption(options, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
    val height = maxOf(minHeight, readOption(options, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight))

    val widthScale = clamp(minWidth / 300f, 0.86f, 1.14f)
    val sideScale = minOf(widthScale, clamp(height / 112f, 0.86f, 1.22f))
    val timeScale = minOf(clamp(minWidth / 300f, 0.88f, 1.12f), clamp(height / 112f, 0.92f, 1.36f))

    views.setTextViewTextSize(R.id.date_text, TypedValue.COMPLEX_UNIT_SP, clamp(14f * sideScale, 11f, 17f))
    views.setTextViewTextSize(R.id.week_text, TypedValue.COMPLEX_UNIT_SP, clamp(12f * sideScale, 10f, 15f))
    views.setTextViewTextSize(R.id.time_clock, TypedValue.COMPLEX_UNIT_SP, clamp(82f * timeScale, 68f, 104f))
    views.setTextViewTextSize(R.id.lunar_text, TypedValue.COMPLEX_UNIT_SP, clamp(13f * sideScale, 10f, 16f))
    views.setViewVisibility(R.id.calendar_zone, if (height >= 92) View.VISIBLE else View.GONE)
}

private fun readOption(options: Bundle?, key: String, fallback: Int): Int {
    val v = options?.getInt(key, fallback) ?: fallback
    return if (v > 0) v else fallback
}

private fun clamp(v: Float, min: Float, max: Float) = maxOf(min, minOf(max, v))

// 今天起 0~2 天内的节日 + 节气，按日期顺序拼成提示；都没有返回 null
// 例：「今天国庆·中秋 · 明天处暑」；同名(清明节气=清明)自动去重，节日在前
private fun dayTip(now: Calendar): String? {
    val parts = mutableListOf<String>()
    for (delta in 0..2) {
        val cal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, delta) }
        val names = LinkedHashSet<String>()
        names.addAll(Festival.namesOf(cal))
        SolarTerm.nameOf(cal)?.let { names.add(it) }
        if (names.isNotEmpty()) {
            parts.add(DAY_PREFIX[delta] + names.joinToString("·"))
        }
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private data class LunarText(val date: String, val ganzhiYear: String)

private data class LunarDate(val year: Int, val month: Int, val day: Int, val leap: Boolean)

// 通用天文计算：儒略日、太阳黄经、定朔，全部以北京时间(东经120°)为准
private object Astro {
    // 公历转儒略日(0h UT)
    fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = Math.floor(y / 100.0).toInt()
        val b = 2 - a + Math.floor(a / 4.0).toInt()
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    // 儒略日转公历 {年, 月, 日}
    fun fromJulianDay(jd: Double): IntArray {
        val shifted = jd + 0.5
        val z = Math.floor(shifted).toInt()
        val f = shifted - z
        val a = if (z < 2299161) z else {
            val alpha = Math.floor((z - 1867216.25) / 36524.25).toInt()
            z + 1 + alpha - Math.floor(alpha / 4.0).toInt()
        }
        val b = a + 1524
        val c = Math.floor((b - 122.1) / 365.25).toInt()
        val d = Math.floor(365.25 * c).toInt()
        val e = Math.floor((b - d) / 30.6001).toInt()
        val day = Math.floor(b - d - Math.floor(30.6001 * e) + f).toInt()
        val month = if (e < 14) e - 1 else e - 13
        val year = if (month > 2) c - 4716 else c - 4715
        return intArrayOf(year, month, day)
    }

    // 公历(北京)民用日序号
    fun dayOrd(y: Int, m: Int, d: Int) = (julianDay(y, m, d) + 0.5).toLong()

    // 天文瞬时(JD, 力学时)归算到北京民用日序号：TT - ΔT 得 UT，再 +8h
    fun instantOrd(jd: Double): Long {
        val yr = fromJulianDay(jd)[0]
        val g = fromJulianDay(jd - deltaT(yr.toDouble()) / 86400.0 + 8.0 / 24.0)
        return dayOrd(g[0], g[1], g[2])
    }

    // 太阳视黄经(度)，Meeus 简化公式
    fun solarLongitude(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val mr = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(mr) +
            (0.019993 - 0.000101 * t) * Math.sin(2 * mr) + 0.000289 * Math.sin(3 * mr)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val r = (l0 + c - 0.00569 - 0.00478 * Math.sin(omega)) % 360.0
        return if (r < 0) r + 360.0 else r
    }

    // 牛顿迭代求太阳视黄经达到 targetDeg 的最近时刻(JD, TT)
    fun solveSolarLongitude(guessJd: Double, targetDeg: Double): Double {
        var jd = guessJd
        repeat(8) {
            val diff = ((targetDeg - solarLongitude(jd) + 180) % 360 + 360) % 360 - 180
            jd += diff / 0.98565
        }
        return jd
    }

    // 黄经 targetDeg(节气/中气)对应的北京民用日序号
    fun solarTermOrd(targetDeg: Double, guessJd: Double) = instantOrd(solveSolarLongitude(guessJd, targetDeg))

    // 第 k 个合朔时刻 JDE(TT)，Meeus 第49章
    fun newMoonJde(k: Int): Double {
        val t = k / 1236.85
        var jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t * t -
            0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = Math.toRadians(2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t * t * t)
        val mp = Math.toRadians(201.5643 + 385.81693528 * k + 0.0107582 * t * t +
            0.00001238 * t * t * t - 0.000000058 * t * t * t * t)
        val f = Math.toRadians(160.7108 + 390.67050284 * k - 0.0016118 * t * t -
            0.00000227 * t * t * t + 0.000000011 * t * t * t * t)
        val om = Math.toRadians(124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t * t * t)
        jde += -0.40720 * Math.sin(mp) + 0.17241 * e * Math.sin(m) + 0.01608 * Math.sin(2 * mp) +
            0.01039 * Math.sin(2 * f) + 0.00739 * e * Math.sin(mp - m) - 0.00514 * e * Math.sin(mp + m) +
            0.00208 * e * e * Math.sin(2 * m) - 0.00111 * Math.sin(mp - 2 * f) - 0.00057 * Math.sin(mp + 2 * f) +
            0.00056 * e * Math.sin(2 * mp + m) - 0.00042 * Math.sin(3 * mp) + 0.00042 * e * Math.sin(m + 2 * f) +
            0.00038 * e * Math.sin(m - 2 * f) - 0.00024 * e * Math.sin(2 * mp - m) - 0.00017 * Math.sin(om) -
            0.00007 * Math.sin(mp + 2 * m) + 0.00004 * Math.sin(2 * mp - 2 * f) + 0.00004 * Math.sin(3 * m) +
            0.00003 * Math.sin(mp + m - 2 * f) + 0.00003 * Math.sin(2 * mp + 2 * f) - 0.00003 * Math.sin(mp + m + 2 * f) +
            0.00003 * Math.sin(mp - m + 2 * f) - 0.00002 * Math.sin(mp - m - 2 * f) - 0.00002 * Math.sin(3 * mp + m) +
            0.00002 * Math.sin(4 * mp)
        val av = doubleArrayOf(
            299.77 + 0.107408 * k - 0.009173 * t * t, 251.88 + 0.016321 * k, 251.83 + 26.651886 * k,
            349.42 + 36.412478 * k, 84.66 + 18.206239 * k, 141.74 + 53.303771 * k, 207.14 + 2.453732 * k,
            154.84 + 7.306860 * k, 34.52 + 27.261239 * k, 207.19 + 0.121824 * k, 291.34 + 1.844379 * k,
            161.72 + 24.198154 * k, 239.56 + 25.513099 * k, 331.55 + 3.592518 * k
        )
        val ac = doubleArrayOf(
            0.000325, 0.000165, 0.000164, 0.000126, 0.000110, 0.000062, 0.000060,
            0.000056, 0.000047, 0.000042, 0.000040, 0.000037, 0.000035, 0.000023
        )
        for (i in av.indices) { jde += ac[i] * Math.sin(Math.toRadians(av[i])) }
        return jde
    }

    // 第 k 个合朔的北京民用日序号
    fun newMoonOrd(k: Int) = instantOrd(newMoonJde(k))

    // <= targetOrd 的最近合朔序号 k
    fun newMoonIndexOnOrBefore(targetOrd: Long): Int {
        var k = Math.round((targetOrd - 2451550.5) / 29.530588861).toInt()
        while (newMoonOrd(k) > targetOrd) k--
        while (newMoonOrd(k + 1) <= targetOrd) k++
        return k
    }

    // ΔT(秒)，Espenak & Meeus 2006 分段多项式，覆盖 1900-2200
    fun deltaT(y: Double): Double = when {
        y < 1920 -> (y - 1900).let { t -> -2.79 + 1.494119 * t - 0.0598939 * t * t + 0.0061966 * t * t * t - 0.000197 * t * t * t * t }
        y < 1941 -> (y - 1920).let { t -> 21.20 + 0.84493 * t - 0.076100 * t * t + 0.0020936 * t * t * t }
        y < 1961 -> (y - 1950).let { t -> 29.07 + 0.407 * t - t * t / 233.0 + t * t * t / 2547.0 }
        y < 1986 -> (y - 1975).let { t -> 45.45 + 1.067 * t - t * t / 260.0 - t * t * t / 718.0 }
        y < 2005 -> (y - 2000).let { t -> 63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t * t * t + 0.000651814 * t * t * t * t + 0.00002373599 * t * t * t * t * t }
        y < 2050 -> (y - 2000).let { t -> 62.92 + 0.32217 * t + 0.005589 * t * t }
        y < 2150 -> -20 + 32 * Math.pow((y - 1820) / 100.0, 2.0) - 0.5628 * (2150 - y)
        else -> -20 + 32 * Math.pow((y - 1820) / 100.0, 2.0)
    }
}

// 农历：定朔 + 中气置闰，覆盖 1900-2200，输出干支、生肖、月、日
private object Lunar {
    private val GAN = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val ZHI = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val ANIMALS = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")
    private val MONTHS = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val DAYS = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    fun format(solar: Calendar): LunarText {
        val l = convert(solar)
        val ganzhi = GAN[(l.year - 4).mod(10)] + ZHI[(l.year - 4).mod(12)]
        val animal = ANIMALS[(l.year - 4).mod(12)]
        val month = (if (l.leap) "闰" else "") + MONTHS[l.month - 1] + "月"
        return LunarText(month + DAYS[l.day - 1], "$ganzhi($animal)")
    }

    // 返回农历 {月, 日, 是否闰月(0/1)}，供节日查询
    fun monthDay(solar: Calendar): IntArray {
        val l = convert(solar)
        return intArrayOf(l.month, l.day, if (l.leap) 1 else 0)
    }

    private fun convert(solar: Calendar): LunarDate {
        val y = solar.get(Calendar.YEAR)
        val m = solar.get(Calendar.MONTH) + 1
        val d = solar.get(Calendar.DAY_OF_MONTH)
        val dOrd = Astro.dayOrd(y, m, d)

        // 含冬至的朔月为十一月，确定目标日所在「岁」的起冬至与归属年
        val winterThis = winterOrd(y)
        val anchorYear = if (dOrd >= winterThis) y else y - 1
        val w0 = if (dOrd >= winterThis) winterThis else winterOrd(y - 1)
        val k0 = Astro.newMoonIndexOnOrBefore(w0)
        val monthCount = Astro.newMoonIndexOnOrBefore(winterOrd(anchorYear + 1)) - k0

        // 一岁含 13 个朔月则置闰，闰第一个不含中气的月
        var leapPos = -1
        if (monthCount == 13) {
            for (i in 1 until monthCount) {
                if (!hasZhongqi(k0 + i)) { leapPos = i; break }
            }
        }

        // 从十一月起顺序排月名，定位目标日所在的朔月
        val kt = Astro.newMoonIndexOnOrBefore(dOrd)
        var monthName = 11
        var nextName = 11
        var lunarYear = anchorYear
        var leap = false
        for (i in 0..kt - k0) {
            if (i == leapPos) {
                leap = true // 闰月沿用上一个月名，不推进
            } else {
                leap = false
                monthName = nextName
                nextName = nextName % 12 + 1
                if (monthName == 1) lunarYear = anchorYear + 1
            }
        }
        val day = (dOrd - Astro.newMoonOrd(kt) + 1).toInt()
        return LunarDate(lunarYear, monthName, day, leap)
    }

    // 含冬至(黄经 270°)的北京民用日序号
    private fun winterOrd(year: Int) = Astro.solarTermOrd(270.0, Astro.julianDay(year, 12, 22) + 0.5)

    // 朔月 [k, k+1) 内是否含中气(黄经为 30 的倍数)
    private fun hasZhongqi(k: Int): Boolean {
        val inst = Astro.newMoonJde(k)
        val nextZhongqi = Math.ceil(Astro.solarLongitude(inst) / 30.0 - 1e-9) * 30.0
        return Astro.solarTermOrd(nextZhongqi % 360, inst) < Astro.newMoonOrd(k + 1)
    }
}

// 二十四节气
private object SolarTerm {
    // 按太阳黄经排列，index = 黄经 / 15，0 = 春分(0°)
    private val NAMES = arrayOf(
        "春分", "清明", "谷雨", "立夏", "小满", "芒种",
        "夏至", "小暑", "大暑", "立秋", "处暑", "白露",
        "秋分", "寒露", "霜降", "立冬", "小雪", "大雪",
        "冬至", "小寒", "大寒", "立春", "雨水", "惊蛰"
    )

    // cal 当天若是某个节气，返回节气名，否则 null
    fun nameOf(cal: Calendar): String? {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val ord = Astro.dayOrd(y, m, d)
        val guessJd = Astro.julianDay(y, m, d) + 0.5
        val base = Math.floor(Astro.solarLongitude(guessJd) / 15.0).toInt()
        for (k in base..base + 1) {
            if (Astro.solarTermOrd((k % 24) * 15.0, guessJd) == ord) return NAMES[k % 24]
        }
        return null
    }
}

// 节日：公历按月日、农历按农历月日，名称用简称
private object Festival {
    // key = 月 * 100 + 日
    private val SOLAR = mapOf(
        101 to "元旦", 308 to "妇女节", 501 to "五一",
        601 to "六一", 910 to "教师节", 1001 to "国庆"
    )

    // key = 农历月 * 100 + 农历日
    private val LUNAR = mapOf(
        101 to "春节", 115 to "元宵", 505 to "端午",
        707 to "七夕", 815 to "中秋", 909 to "重阳", 1208 to "腊八"
    )

    // cal 当天的所有节日名（公历 + 农历，可能同天多个，如国庆+中秋）
    fun namesOf(cal: Calendar): List<String> {
        val names = mutableListOf<String>()
        SOLAR[(cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)]?.let { names.add(it) }
        val lunar = Lunar.monthDay(cal)
        if (lunar[2] == 0) {
            LUNAR[lunar[0] * 100 + lunar[1]]?.let { names.add(it) }
        }
        // 除夕：次日为正月初一
        val next = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val nl = Lunar.monthDay(next)
        if (nl[0] == 1 && nl[1] == 1 && nl[2] == 0) names.add("除夕")
        return names
    }
}
