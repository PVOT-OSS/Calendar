# SPDX-FileCopyrightText: 2010 The Android Open Source Project
# SPDX-License-Identifier: Apache-2.0

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class org.prauga.calendar.selectcalendars.SelectCalendarsSyncFragment
-keep class com.android.calendar.OtherPreferences
-keep class com.android.calendar.AboutPreferences
-keep class org.prauga.calendar.settings.QuickResponsePreferences
-keepclassmembers class org.prauga.calendar.AllInOneActivity {
  *** setControlsOffset(...);
}
-keepclassmembers class org.prauga.calendar.selectcalendars.SelectVisibleCalendarsActivity {
  *** handleSelectSyncedCalendarsClicked(...);
}
-keepclassmembers class org.prauga.calendar.AllInOneActivity {
  *** handleSelectSyncedCalendarsClicked(...);
}
-keepclassmembers class org.prauga.calendar.AsyncQueryService {
  *** setTestHandler(...);
  *** getLastCancelableOperation(...);
}
-keepclassmembers class org.prauga.calendar.AsyncQueryServiceHelper$OperationInfo {
  *** equivalent(...);
}
-keepclassmembers class org.prauga.calendar.DayView {
  *** setAnimateDayHeight(...);
  *** setAnimateDayEventHeight(...);
  *** setMoreAllDayEventsTextAlpha(...);
  *** setExpandDayHeightIconRotation(...);
  *** setViewStartY(...);
  *** setAnimateTodayAlpha(...);
  *** setEventsAlpha(...);
  *** getEventsAlpha(...);
}
-keepclassmembers class org.prauga.calendar.month.MonthWeekEventsView {
  *** setAnimateTodayAlpha(...);
}
-keepclassmembers class org.prauga.calendar.event.EditEventHelper {
 *** updateRecurrenceRule(...);
 *** extractDomain(...);
}

-keep public class * extends android.app.Fragment {
  public <init>();
}

-keepclassmembers class * implements android.content.SharedPreferences$Editor {
  public *** apply();
}
