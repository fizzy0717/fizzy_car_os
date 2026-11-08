package com.fizzyos.launcher;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.text.TextUtils;

/** Reads only the user-visible Google Maps navigation notification. */
public class NavigationNotificationListener extends NotificationListenerService {
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !MAPS_PACKAGE.equals(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        Bundle extras = notification.extras;
        CharSequence titleValue = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textValue = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextValue = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence subTextValue = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);

        String title = titleValue == null ? "" : titleValue.toString();
        String body = bigTextValue != null ? bigTextValue.toString()
                : (textValue == null ? "" : textValue.toString());
        if (TextUtils.isEmpty(body) && subTextValue != null) body = subTextValue.toString();

        SharedPreferences prefs = getSharedPreferences("fizzy_navigation", MODE_PRIVATE);
        prefs.edit().putString("title", title).putString("body", body)
                .putLong("updated_at", System.currentTimeMillis()).apply();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !MAPS_PACKAGE.equals(sbn.getPackageName())) return;
        // Keep the last navigation text briefly; the launcher reads it on resume.
    }
}
