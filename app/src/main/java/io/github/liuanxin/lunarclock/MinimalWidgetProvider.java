package io.github.liuanxin.lunarclock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class MinimalWidgetProvider extends AppWidgetProvider {
    private static final String[] WEEK_NAMES = {
            "周日", "周一", "周二", "周三", "周四", "周五", "周六"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, MinimalWidgetProvider.class);
            int[] widgetIds = manager.getAppWidgetIds(component);
            if (widgetIds != null && widgetIds.length > 0) {
                onUpdate(context, manager, widgetIds);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Calendar now = Calendar.getInstance();
        for (int widgetId : appWidgetIds) {
            Bundle options = appWidgetManager.getAppWidgetOptions(widgetId);
            appWidgetManager.updateAppWidget(widgetId, buildViews(context, now, widgetId, options));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId,
            Bundle newOptions
    ) {
        appWidgetManager.updateAppWidget(
                appWidgetId,
                buildViews(context, Calendar.getInstance(), appWidgetId, newOptions)
        );
    }

    private RemoteViews buildViews(Context context, Calendar now, int widgetId, Bundle options) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;
        int day = now.get(Calendar.DAY_OF_MONTH);
        String weekName = WEEK_NAMES[now.get(Calendar.DAY_OF_WEEK) - 1];

        views.setTextViewText(
                R.id.date_text,
                String.format(Locale.CHINA, "%04d-%02d-%02d", year, month, day)
        );
        views.setTextViewText(R.id.week_text, weekName);
        LunarUtil.LunarText lunarText = LunarUtil.format(now);
        views.setTextViewText(R.id.lunar_text, lunarText.ganzhiYear + "-" + lunarText.date);
        views.setCharSequence(R.id.time_clock, "setFormat12Hour", "HH:mm");
        views.setCharSequence(R.id.time_clock, "setFormat24Hour", "HH:mm");
        views.setString(R.id.time_clock, "setTimeZone", TimeZone.getDefault().getID());
        applyTextSizes(views, options);

        views.setOnClickPendingIntent(
                R.id.calendar_zone,
                PendingIntent.getActivity(
                        context,
                        widgetId * 2,
                        calendarIntent(now.getTimeInMillis()),
                        pendingIntentFlags()
                )
        );
        views.setOnClickPendingIntent(
                R.id.time_clock,
                PendingIntent.getActivity(
                        context,
                        widgetId * 2 + 2,
                        new Intent(AlarmClock.ACTION_SHOW_ALARMS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        pendingIntentFlags()
                )
        );
        return views;
    }

    private static void applyTextSizes(RemoteViews views, Bundle options) {
        WidgetSize size = WidgetSize.from(options);
        views.setTextViewTextSize(R.id.date_text, TypedValue.COMPLEX_UNIT_SP, size.dateSp);
        views.setTextViewTextSize(R.id.week_text, TypedValue.COMPLEX_UNIT_SP, size.weekSp);
        views.setTextViewTextSize(R.id.time_clock, TypedValue.COMPLEX_UNIT_SP, size.timeSp);
        views.setTextViewTextSize(R.id.lunar_text, TypedValue.COMPLEX_UNIT_SP, size.lunarSp);
        views.setViewVisibility(R.id.calendar_zone, size.showDetails ? View.VISIBLE : View.GONE);
    }

    private static Intent calendarIntent(long timeMillis) {
        Uri uri = CalendarContract.CONTENT_URI
                .buildUpon()
                .appendPath("time")
                .appendPath(String.valueOf(timeMillis))
                .build();
        return new Intent(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    private static final class WidgetSize {
        final float dateSp;
        final float weekSp;
        final float timeSp;
        final float lunarSp;
        final boolean showDetails;

        private WidgetSize(
                float dateSp,
                float weekSp,
                float timeSp,
                float lunarSp,
                boolean showDetails
        ) {
            this.dateSp = dateSp;
            this.weekSp = weekSp;
            this.timeSp = timeSp;
            this.lunarSp = lunarSp;
            this.showDetails = showDetails;
        }

        static WidgetSize from(Bundle options) {
            int minWidth = readOption(options, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
            int minHeight = readOption(options, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40);
            int maxHeight = readOption(options, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight);
            int height = Math.max(minHeight, maxHeight);

            boolean showDetails = height >= 92;
            float widthScale = clamp(minWidth / 300f, 0.86f, 1.14f);
            float sideHeightScale = clamp(height / 112f, 0.86f, 1.22f);
            float timeHeightScale = clamp(height / 112f, 0.92f, 1.36f);
            float sideScale = Math.min(widthScale, sideHeightScale);
            float timeScale = Math.min(clamp(minWidth / 300f, 0.88f, 1.12f), timeHeightScale);

            return new WidgetSize(
                    clamp(14f * sideScale, 11f, 17f),
                    clamp(12f * sideScale, 10f, 15f),
                    clamp(82f * timeScale, 68f, 104f),
                    clamp(13f * sideScale, 10f, 16f),
                    showDetails
            );
        }

        private static int readOption(Bundle options, String key, int fallback) {
            if (options == null) {
                return fallback;
            }
            int value = options.getInt(key, fallback);
            return value > 0 ? value : fallback;
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

    }

    private static final class LunarUtil {
        private static final long[] LUNAR_INFO = {
                0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
                0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
                0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
                0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
                0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
                0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
                0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
                0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
                0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
                0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
                0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
                0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
                0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
                0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
                0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
                0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
                0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
                0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
                0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
                0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
                0x0d520
        };

        private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
        private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
        private static final String[] ANIMALS = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};
        private static final String[] MONTHS = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
        private static final String[] DAYS = {
                "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
                "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
        };
        private static final long MILLIS_PER_DAY = 86_400_000L;

        static LunarText format(Calendar solar) {
            LunarDate lunar = convert(solar);
            String ganzhi = GAN[floorMod(lunar.year - 4, 10)] + ZHI[floorMod(lunar.year - 4, 12)];
            String animal = ANIMALS[floorMod(lunar.year - 4, 12)];
            String month = (lunar.leapMonth ? "闰" : "") + MONTHS[lunar.month - 1] + "月";
            String day = DAYS[lunar.day - 1];
            return new LunarText(month + day, ganzhi + "(" + animal + ")");
        }

        private static final class LunarText {
            final String date;
            final String ganzhiYear;

            LunarText(String date, String ganzhiYear) {
                this.date = date;
                this.ganzhiYear = ganzhiYear;
            }
        }

        private static LunarDate convert(Calendar solar) {
            Calendar base = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.CHINA);
            base.clear();
            base.set(1900, Calendar.JANUARY, 31, 0, 0, 0);

            Calendar target = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.CHINA);
            target.clear();
            target.set(
                    solar.get(Calendar.YEAR),
                    solar.get(Calendar.MONTH),
                    solar.get(Calendar.DAY_OF_MONTH),
                    0,
                    0,
                    0
            );

            int offset = (int) ((target.getTimeInMillis() - base.getTimeInMillis()) / MILLIS_PER_DAY);
            if (offset < 0) {
                return new LunarDate(1900, 1, 1, false);
            }

            int year = 1900;
            while (year < 2101) {
                int daysOfYear = yearDays(year);
                if (offset < daysOfYear) {
                    break;
                }
                offset -= daysOfYear;
                year++;
            }

            int leapMonth = leapMonth(year);
            boolean isLeap = false;
            int month = 1;
            while (month <= 12) {
                int daysOfMonth = isLeap ? leapDays(year) : monthDays(year, month);
                if (offset < daysOfMonth) {
                    break;
                }
                offset -= daysOfMonth;

                if (leapMonth == month && !isLeap) {
                    isLeap = true;
                } else {
                    if (isLeap) {
                        isLeap = false;
                    }
                    month++;
                }
            }
            return new LunarDate(year, Math.min(month, 12), offset + 1, isLeap);
        }

        private static int yearDays(int year) {
            int sum = 348;
            int bit = 0x8000;
            long info = LUNAR_INFO[year - 1900];
            while (bit > 0x8) {
                if ((info & bit) != 0) {
                    sum++;
                }
                bit >>= 1;
            }
            return sum + leapDays(year);
        }

        private static int leapDays(int year) {
            if (leapMonth(year) == 0) {
                return 0;
            }
            return (LUNAR_INFO[year - 1900] & 0x10000L) != 0 ? 30 : 29;
        }

        private static int leapMonth(int year) {
            return (int) (LUNAR_INFO[year - 1900] & 0xfL);
        }

        private static int monthDays(int year, int month) {
            return (LUNAR_INFO[year - 1900] & (0x10000L >> month)) == 0 ? 29 : 30;
        }

        private static int floorMod(int value, int divisor) {
            int result = value % divisor;
            return result < 0 ? result + divisor : result;
        }

        private static final class LunarDate {
            final int year;
            final int month;
            final int day;
            final boolean leapMonth;

            LunarDate(int year, int month, int day, boolean leapMonth) {
                this.year = year;
                this.month = month;
                this.day = day;
                this.leapMonth = leapMonth;
            }
        }
    }
}
