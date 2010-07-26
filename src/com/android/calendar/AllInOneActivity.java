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

package com.android.calendar;

import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.SelectCalendars.SelectCalendarsFragment;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class AllInOneActivity extends Activity implements EventHandler,
        OnSharedPreferenceChangeListener {
    private static final String TAG = "AllInOneActivity";
    private static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    private static CalendarController mController;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);

        // Must be the first to register so that this activity can modify the
        // list the event handlers during dispatching.
        mController.registerEventHandler(this);

        setContentView(R.layout.all_in_one);

        // Get time from intent or icicle
        long timeMillis;
        if (icicle != null) {
            timeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
        } else {
            timeMillis = Utils.timeFromIntentInMillis(getIntent());
        }

        initFragments(timeMillis, Utils.getViewTypeFromIntentAndSharedPref(this));

        // Listen for changes that would require this to be refreshed
        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //FRAG_TODO save highlighted days of the week;
        Utils.setDefaultView(this, mController.getViewType());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong(BUNDLE_KEY_RESTORE_TIME, mController.getTime());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void initFragments(long timeMillis, int viewType) {
        FragmentTransaction ft = openFragmentTransaction();

        boolean multipane = (getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_XLARGE) != 0;

        if (multipane) {
            Fragment miniMonthFrag = new MonthFragment(false, timeMillis);
            ft.replace(R.id.mini_month, miniMonthFrag);
            mController.registerEventHandler((EventHandler) miniMonthFrag);

            Fragment selectCalendarsFrag = new SelectCalendarsFragment();
            ft.replace(R.id.calendar_list, selectCalendarsFrag);
        } else {
            findViewById(R.id.mini_month).setVisibility(View.GONE);
            findViewById(R.id.calendar_list).setVisibility(View.GONE);
        }

        setMainPane(ft, R.id.main_pane, viewType, timeMillis, true);

        ft.commit(); // this needs to be after setMainPane()

        Time t = new Time();
        t.set(timeMillis);
        mController.sendEvent(this, EventType.GO_TO, t, null, -1, viewType);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.all_in_one_title_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Time t = null;
        int viewType = ViewType.CURRENT;
        switch (item.getItemId()) {
            case R.id.action_refresh:
                mController.refreshCalendars();
                return true;
            case R.id.action_day:
                viewType = ViewType.DAY;
                break;
            case R.id.action_week:
                viewType = ViewType.WEEK;
                break;
            case R.id.action_month:
                viewType = ViewType.MONTH;
                break;
            case R.id.action_agenda:
                viewType = ViewType.AGENDA;
                break;
            case R.id.action_today:
                viewType = ViewType.CURRENT;
                t = new Time();
                t.setToNow();
                break;
            case R.id.action_create_event:
                mController.sendEventRelatedEvent(this, EventType.CREATE_EVENT, -1, 0, 0, 0, 0);
                return true;
            case R.id.action_manage_calendars:
                mController.sendEvent(this, EventType.LAUNCH_MANAGE_CALENDARS, null, null, 0, 0);
                return true;
            case R.id.action_settings:
                mController.sendEvent(this, EventType.LAUNCH_SETTINGS, null, null, 0, 0);
                return true;
            default:
                return false;
        }
        mController.sendEvent(this, EventType.GO_TO, t, null, -1, viewType);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(CalendarPreferenceActivity.KEY_WEEK_START_DAY)) {
            initFragments(mController.getTime(), mController.getViewType());
        }
    }

    private void setMainPane(FragmentTransaction ft, int viewId, int viewType,
            long timeMillis, boolean force) {
        if(!force && mController.getPreviousViewType() == viewType) {
            return;
        }

        // Deregister old view
        Fragment frag = findFragmentById(viewId);
        if (frag != null) {
            mController.deregisterEventHandler((EventHandler) frag);
        }

        // Create new one
        switch (viewType) {
            case ViewType.AGENDA:
                frag = new AgendaFragment(timeMillis);
                break;
            case ViewType.DAY:
                frag = new DayFragment(timeMillis, 1);
                break;
            case ViewType.WEEK:
                frag = new DayFragment(timeMillis, 7);
                break;
            case ViewType.MONTH:
                frag = new MonthFragment(false, timeMillis);
                break;
            default:
                throw new IllegalArgumentException(
                        "Must be Agenda, Day, Week, or Month ViewType, not " + viewType);
        }

        boolean doCommit = false;
        if (ft == null) {
            doCommit = true;
            ft = openFragmentTransaction();
        }

        ft.replace(viewId, frag);
        mController.registerEventHandler((EventHandler) frag);

        if (doCommit) {
            ft.commit();
        }
    }

    private void setTitleInActionBar(EventInfo event) {
        if (event.eventType != EventType.GO_TO) {
            return;
        }

        long start = event.startTime.toMillis(false /* use isDst */);
        long end = start;

        if (event.endTime != null) {
            end = event.endTime.toMillis(false /* use isDst */);
        }
        String msg = DateUtils.formatDateRange(this, start, end, DateUtils.FORMAT_SHOW_DATE);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setTitle(msg);
        }
    }

    // EventHandler Interface
    public long getSupportedEventTypes() {
        return EventType.GO_TO;
    }

    // EventHandler Interface
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            // Set title bar
            setTitleInActionBar(event);

            setMainPane(null, R.id.main_pane, event.viewType,
                    event.startTime.toMillis(false), false);

            // FRAG_TODO only for XL screen
            if (event.viewType == ViewType.MONTH) {
                // hide minimonth and calendar frag
                // show agenda view
            } else {
                // show minimonth and calendar frag
            }
        }
    }

    // EventHandler Interface
    public void eventsChanged() {
    }

    // EventHandler Interface
    public boolean getAllDay() {
        return false;
    }

    // EventHandler Interface
    public long getSelectedTime() {
        return 0;
    }

    // EventHandler Interface
    public void goTo(Time time, boolean animate) {
    }

    // EventHandler Interface
    public void goToToday() {
    }
}
