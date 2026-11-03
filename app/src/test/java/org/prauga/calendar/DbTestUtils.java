/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.prauga.calendar;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

/**
 * A helper class for creating and wiring fake implementations of db classes, like
 */
class DbTestUtils {
    private final FakeContext context;
    private final FakeSharedPreferences sharedPreferences;

    class FakeContext extends ContextWrapper {
        private final Resources resources;
        private SharedPreferences sharedPreferences;

        FakeContext(Resources resources) {
            super(RuntimeEnvironment.getApplication());
            this.resources = resources;
        }

        @Override
        public Resources getResources() {
            return resources;
        }

        public void setSharedPreferences(SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            if (sharedPreferences != null) {
                return sharedPreferences;
            } else {
                return super.getSharedPreferences(name, mode);
            }
        }
    }

    public static class FakeContentProvider extends ContentProvider {
        private static ArrayList<String> queryResult = null;

        @Override
        public Bundle call(String method, String request, Bundle args) {
            return null;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 1;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        /**
         * Set the mocked results to return from a query call.
         */
        public static void setQueryResult(ArrayList<String> result) {
            queryResult = result;
        }

        @Override
        public final Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String orderBy) {
            ArrayList<String> result = (queryResult == null) ?
                    new ArrayList<String>() : queryResult;
            String[] columns = new String[result.size()];
            for (int i = 0; i < columns.length; i++) {
                columns[i] = "c" + i;
            }
            MatrixCursor cursor = new MatrixCursor(columns);
            if (!result.isEmpty()) {
                cursor.addRow(result.toArray());
            }
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public boolean onCreate() {
            return true;
        }
    }

    public DbTestUtils(Resources resources) {
        this.context = new FakeContext(resources);
        this.sharedPreferences = new FakeSharedPreferences();
        context.setSharedPreferences(sharedPreferences);
    }

    public void addProvider(String authority) {
        Robolectric.buildContentProvider(FakeContentProvider.class).create(authority);
    }

    public ContentResolver getContentResolver() {
        return context.getContentResolver();
    }

    public FakeContext getContext() {
        return context;
    }

    public FakeSharedPreferences getMockSharedPreferences() {
        return sharedPreferences;
    }
}
