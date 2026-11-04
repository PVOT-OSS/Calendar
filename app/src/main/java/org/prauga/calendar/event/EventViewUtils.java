/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.prauga.calendar.event;

import android.content.Context;
import android.content.res.Resources;

import org.prauga.calendar.R;

public class EventViewUtils {
    private EventViewUtils() {
    }

    // Constructs a label given an arbitrary number of minutes. For example,
    // if the given minutes is 63, then this returns the string "63 minutes".
    // As another example, if the given minutes is 120, then this returns
    // "2 hours".
    // if minute is < 0, returns `None`
    public static String constructReminderLabel(Context context, int minutes, boolean abbrev) {
        Resources resources = context.getResources();

        if (minutes < 0) {
            return resources.getString(R.string.no_snooze_label);
        }

        int value, resId;

        if (minutes == Integer.MIN_VALUE) {
            value = 0;
            resId = R.string.no_reminder_label;

            String format = resources.getString(resId, value);
            return String.format(format, value);

        } else if (minutes % 60 != 0 || minutes == 0) {
            value = minutes;
            if (abbrev) {
                resId = R.plurals.Nmins;
            } else {
                resId = R.plurals.Nminutes;
            }
        } else if (minutes % (24 * 60) != 0) {
            value = minutes / 60;
            resId = R.plurals.Nhours;
        } else if (minutes % (7 * 24 * 60) != 0) {
            value = minutes / (24 * 60);
            resId = R.plurals.Ndays;
        } else {
            value = minutes / (7 * 24 * 60);
            resId = R.plurals.Nweeks;
        }

        String format = resources.getQuantityString(resId, value);
        return String.format(format, value);
    }
}
