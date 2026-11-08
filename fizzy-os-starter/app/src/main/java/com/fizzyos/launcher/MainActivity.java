package com.fizzyos.launcher;

import android.Manifest;
import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    private static final String PREFS = "fizzy_settings";
    private static final String THEME_KEY = "theme";
    private static final int SLOT_COUNT = 4;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 4101;
    private static final String[] DEFAULT_NAMES = {"Maps", "Spotify", "YouTube", "Phone"};
    private static final String[] DEFAULT_SYMBOLS = {"⌖", "♫", "▶", "☎"};

    private boolean lightTheme;
    private int background, panel, panelAlt, foreground, secondary, border, accent;
    private SharedPreferences preferences;
    private TextView clockView;
    private TextView dateView;
    private TextView wifiView;
    private TextView bluetoothView;
    private WebView mapWebView;
    private TextView navigationInfoView;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private static final int REQUEST_LOCATION_PERMISSION = 4102;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        lightTheme = preferences.getBoolean(THEME_KEY, true);
        buildHome();
        requestBluetoothPermissionIfNeeded();
        requestLocationPermissionIfNeeded();
    }

    private void requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            updateConnectivityStatus();
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (hasLocationPermission()) {
                startLocationUpdates();
            } else {
                Toast.makeText(this,
                        "Location permission denied. Live location is unavailable.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION_PERMISSION);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (mapWebView == null || !hasLocationPermission()) return;
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;

        try {
            Location best = null;
            for (String provider : locationManager.getProviders(true)) {
                try {
                    Location candidate = locationManager.getLastKnownLocation(provider);
                    if (candidate != null && (best == null
                            || candidate.getTime() > best.getTime())) {
                        best = candidate;
                    }
                } catch (SecurityException ignored) { }
            }
            if (best != null) sendLocationToMap(best);

            if (locationListener == null) {
                locationListener = location -> sendLocationToMap(location);
            }
            locationManager.removeUpdates(locationListener);
            for (String provider : locationManager.getProviders(true)) {
                try {
                    locationManager.requestLocationUpdates(
                            provider, 5000L, 8f, locationListener, Looper.getMainLooper());
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (SecurityException ignored) {
            Toast.makeText(this, "Location permission is unavailable.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendLocationToMap(Location location) {
        if (mapWebView == null || location == null) return;
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        mapWebView.post(() -> mapWebView.evaluateJavascript(
                "window.setDeviceLocation(" + lat + "," + lon + ");", null));
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException ignored) { }
        }
    }

    private class MapBridge {
        @JavascriptInterface
        public void openGoogleMapsFromMap() {
            runOnUiThread(() -> openGoogleMaps("", true));
        }
    }

    private void openGoogleMaps(String destination, boolean fullView) {
        try {
            Intent intent;
            if (fullView || destination == null || destination.trim().isEmpty()) {
                intent = getPackageManager().getLaunchIntentForPackage(
                        "com.google.android.apps.maps");
                if (intent == null) {
                    throw new IllegalStateException("Google Maps is unavailable");
                }
            } else {
                String encoded = URLEncoder.encode(destination.trim(), "UTF-8");
                Uri uri = Uri.parse("google.navigation:q=" + encoded + "&mode=d");
                intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setPackage("com.google.android.apps.maps");
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Google Maps is not installed or enabled on this device.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        updateConnectivityStatus();
        clockHandler.removeCallbacks(clockRunnable);
        clockHandler.post(clockRunnable);
        if (hasLocationPermission()) startLocationUpdates();
        updateNavigationInfo();
    }

    private void updateNavigationInfo() {
        if (navigationInfoView == null) return;
        SharedPreferences navPrefs = getSharedPreferences("fizzy_navigation", MODE_PRIVATE);
        String title = navPrefs.getString("title", "");
        String body = navPrefs.getString("body", "");
        if (title.isEmpty() && body.isEmpty()) {
            navigationInfoView.setText("Tap map to open Google Maps");
        } else {
            String combined = (title + "  " + body).trim();
            navigationInfoView.setText(combined.length() > 95
                    ? combined.substring(0, 92) + "..." : combined);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockRunnable);
        stopLocationUpdates();
    }

    private void updateClock() {
        if (clockView == null || dateView == null) return;
        Date now = new Date();
        clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        dateView.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now));
    }

    private void updateConnectivityStatus() {
        if (wifiView != null) {
            boolean wifiConnected = false;
            ConnectivityManager manager =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager != null) {
                Network active = manager.getActiveNetwork();
                NetworkCapabilities caps = active == null ? null
                        : manager.getNetworkCapabilities(active);
                wifiConnected = caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            }
            wifiView.setText(wifiConnected ? "Wi-Fi" : "Wi-Fi off");
            wifiView.setTextColor(wifiConnected ? accent : secondary);
        }

        if (bluetoothView != null) {
            boolean bluetoothEnabled = false;
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    try {
                        bluetoothEnabled = adapter.isEnabled();
                    } catch (SecurityException ignored) {
                        bluetoothEnabled = false;
                    }
                }
            }
            bluetoothView.setText(bluetoothEnabled ? "Bluetooth" : "Bluetooth off");
            bluetoothView.setTextColor(bluetoothEnabled ? accent : secondary);
        }
    }

    private void applyTheme() {
        if (lightTheme) {
            background = Color.rgb(235, 243, 250);
            panel = Color.rgb(250, 253, 255);
            panelAlt = Color.rgb(225, 237, 247);
            foreground = Color.rgb(36, 51, 66);
            secondary = Color.rgb(104, 122, 139);
            border = Color.rgb(210, 224, 237);
            accent = Color.rgb(112, 174, 220);
        } else {
            // Dark Maroon: warm burgundy surfaces with a muted rose accent.
            background = Color.rgb(35, 19, 25);
            panel = Color.rgb(59, 32, 41);
            panelAlt = Color.rgb(48, 25, 34);
            foreground = Color.rgb(249, 241, 243);
            secondary = Color.rgb(194, 164, 173);
            border = Color.rgb(91, 55, 66);
            accent = Color.rgb(190, 126, 145);
        }
    }

    private void buildHome() {
        applyTheme();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(10));
        root.setBackgroundColor(background);
        root.addView(buildStatusBar(), new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyParams.topMargin = dp(8);
        root.addView(body, bodyParams);
        body.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(76), -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, -1, 1f);
        contentParams.leftMargin = dp(10);
        body.addView(content, contentParams);

        LinearLayout map = card();
        map.setPadding(0, 0, 0, 0);
        map.setClipToOutline(true);
        map.setBackground(drawable(panelAlt, border, 24));

        FrameLayout mapFrame = new FrameLayout(this);
        mapWebView = new WebView(this);
        mapWebView.setBackgroundColor(panelAlt);
        mapWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mapWebView.setVerticalScrollBarEnabled(false);
        mapWebView.setHorizontalScrollBarEnabled(false);
        WebSettings mapSettings = mapWebView.getSettings();
        mapSettings.setJavaScriptEnabled(true);
        mapSettings.setDomStorageEnabled(true);
        mapSettings.setAllowFileAccess(true);
        mapSettings.setAllowContentAccess(false);
        mapWebView.addJavascriptInterface(new MapBridge(), "FizzyMaps");
        mapWebView.setWebViewClient(new WebViewClient());
        mapFrame.addView(mapWebView, new FrameLayout.LayoutParams(-1, -1));

        navigationInfoView = text("Tap map to open Google Maps", 11, true, foreground);
        navigationInfoView.setGravity(Gravity.CENTER_VERTICAL);
        navigationInfoView.setPadding(dp(10), dp(7), dp(10), dp(7));
        navigationInfoView.setBackground(drawable(panel, border, 12));
        FrameLayout.LayoutParams navInfoParams = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        navInfoParams.bottomMargin = dp(10);
        mapFrame.addView(navigationInfoView, navInfoParams);

        map.addView(mapFrame, new LinearLayout.LayoutParams(-1, -1));
        map.setOnClickListener(v -> openGoogleMaps());
        mapWebView.loadUrl("file:///android_asset/map.html");
        updateNavigationInfo();

        content.addView(map, new LinearLayout.LayoutParams(0, -1, 0.64f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 0.36f);
        rightParams.leftMargin = dp(10);
        content.addView(right, rightParams);
        right.addView(buildMusicWidget(), new LinearLayout.LayoutParams(-1, 0, 1.15f));
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(-1, 0, 0.8f);
        weatherParams.topMargin = dp(8);
        right.addView(buildWeatherWidget(), weatherParams);
        LinearLayout.LayoutParams prayerParams = new LinearLayout.LayoutParams(-1, 0, 1.15f);
        prayerParams.topMargin = dp(8);
        right.addView(buildPrayerWidget(), prayerParams);
        setContentView(root);
    }

    private View buildStatusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(16), 0);
        bar.setBackground(drawable(panel, border, 20));

        LinearLayout leftGroup = new LinearLayout(this);
        leftGroup.setOrientation(LinearLayout.HORIZONTAL);
        leftGroup.setGravity(Gravity.CENTER_VERTICAL);

        clockView = text("--:--", 21, true, foreground);
        dateView = text("--/--/----", 13, false, secondary);

        leftGroup.addView(clockView);
        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(-2, -2);
        dateParams.leftMargin = dp(14);
        leftGroup.addView(dateView, dateParams);
        bar.addView(leftGroup);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout rightGroup = new LinearLayout(this);
        rightGroup.setOrientation(LinearLayout.HORIZONTAL);
        rightGroup.setGravity(Gravity.CENTER_VERTICAL);

        wifiView = text("Wi-Fi off", 12, true, secondary);
        bluetoothView = text("Bluetooth off", 12, true, secondary);

        LinearLayout.LayoutParams wifiParams =
                new LinearLayout.LayoutParams(-2, -2);
        wifiParams.leftMargin = dp(12);
        rightGroup.addView(wifiView, wifiParams);

        LinearLayout.LayoutParams bluetoothParams =
                new LinearLayout.LayoutParams(-2, -2);
        bluetoothParams.leftMargin = dp(18);
        rightGroup.addView(bluetoothView, bluetoothParams);

        TextView settings = text("⚙", 22, true, foreground);
        settings.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams settingsParams =
                new LinearLayout.LayoutParams(dp(42), dp(42));
        settingsParams.leftMargin = dp(18);
        rightGroup.addView(settings, settingsParams);
        settings.setOnClickListener(v -> showSettings());

        bar.addView(rightGroup);
        updateClock();
        updateConnectivityStatus();
        return bar;
    }

    private View buildSidebar() {
        LinearLayout rail = card();
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        rail.setPadding(dp(6), dp(8), dp(6), dp(8));

        // Five equal slots: four shortcuts plus the fixed All Apps button.
        // Weight-based sizing keeps every tile the same height on different displays.
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            addSidebarButton(rail, slot);
        }

        TextView allApps = text("▦", 29, true, foreground);
        allApps.setGravity(Gravity.CENTER);
        allApps.setIncludeFontPadding(false);
        allApps.setBackground(drawable(panelAlt, border, 17));

        LinearLayout.LayoutParams allAppsParams =
                new LinearLayout.LayoutParams(-1, 0, 1f);
        allAppsParams.topMargin = dp(4);
        allAppsParams.bottomMargin = dp(4);
        rail.addView(allApps, allAppsParams);
        allApps.setOnClickListener(v -> showAppDrawer());

        return rail;
    }

    private void addSidebarButton(LinearLayout rail, int slot) {
        String pkg = preferences.getString(packageKey(slot), "");
        String cls = preferences.getString(classKey(slot), "");
        String label = preferences.getString(labelKey(slot), DEFAULT_NAMES[slot]);

        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER);
        button.setBackground(drawable(panelAlt, border, 17));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 0, 1f);
        p.topMargin = dp(4);
        p.bottomMargin = dp(4);
        rail.addView(button, p);

        Drawable icon = null;
        if (!pkg.isEmpty() && !cls.isEmpty()) {
            try {
                icon = getPackageManager().getActivityIcon(new ComponentName(pkg, cls));
            } catch (PackageManager.NameNotFoundException ignored) {
                preferences.edit().remove(packageKey(slot)).remove(classKey(slot)).apply();
            }
        }

        if (icon != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.addView(image, new LinearLayout.LayoutParams(dp(30), dp(30)));
        } else {
            TextView fallback = text(DEFAULT_SYMBOLS[slot], 25, true, foreground);
            fallback.setGravity(Gravity.CENTER);
            button.addView(fallback);
        }

        button.setContentDescription(label);
        button.setOnClickListener(v -> launchSidebarApp(slot));
        button.setOnLongClickListener(v -> {
            showSidebarSlotPicker(slot);
            return true;
        });
    }

    private void launchSidebarApp(int slot) {
        String pkg = preferences.getString(packageKey(slot), "");
        String cls = preferences.getString(classKey(slot), "");
        if (pkg.isEmpty() || cls.isEmpty()) {
            if (slot == 0) {
                openGoogleMaps();
            } else {
                showSidebarSlotPicker(slot);
            }
            return;
        }
        Intent launch = new Intent();
        launch.setClassName(pkg, cls);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(this, "Application unavailable. Choose another app.",
                    Toast.LENGTH_SHORT).show();
            showSidebarSlotPicker(slot);
        }
    }

    private void openGoogleMaps() {
        openGoogleMaps("", true);
    }

    private String packageKey(int slot) { return "sidebar_" + slot + "_package"; }
    private String classKey(int slot) { return "sidebar_" + slot + "_class"; }
    private String labelKey(int slot) { return "sidebar_" + slot + "_label"; }

    private List<ResolveInfo> getInstalledLauncherApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        Collections.sort(apps, (a, b) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        return apps;
    }

    private void showSidebarSlotPicker(int slot) {
        List<ResolveInfo> apps = getInstalledLauncherApps();
        PackageManager pm = getPackageManager();

        if (apps.isEmpty()) {
            Toast.makeText(this, "No launchable applications found", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentLabel = preferences.getString(
                labelKey(slot), DEFAULT_NAMES[slot]);

        String[] names = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).loadLabel(pm).toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("Change Shortcut " + (slot + 1)
                        + "  •  Current: " + currentLabel)
                .setItems(names, (dialog, which) -> {
                    ResolveInfo selected = apps.get(which);
                    String pkg = selected.activityInfo.packageName;
                    String cls = selected.activityInfo.name;
                    String label = selected.loadLabel(pm).toString();

                    preferences.edit()
                            .putString(packageKey(slot), pkg)
                            .putString(classKey(slot), cls)
                            .putString(labelKey(slot), label)
                            .apply();

                    buildHome();
                    Toast.makeText(this, label + " added to sidebar",
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Reset to Default", (dialog, which) -> {
                    preferences.edit()
                            .remove(packageKey(slot))
                            .remove(classKey(slot))
                            .remove(labelKey(slot))
                            .apply();

                    buildHome();
                    Toast.makeText(this,
                            "Shortcut " + (slot + 1) + " reset to default",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View buildMusicWidget() {
        LinearLayout box = card();
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(text("♫  NOW PLAYING", 12, true, secondary));
        box.addView(text("Music", 21, true, foreground));
        box.addView(text("Media controls will appear here", 13, false, secondary));
        return box;
    }

    private View buildWeatherWidget() {
        LinearLayout box = card();
        box.setPadding(dp(16), dp(10), dp(16), dp(10));
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(text("WEATHER", 12, true, secondary));
        box.addView(text("Weather", 20, true, foreground));
        box.addView(text("Location and temperature", 13, false, secondary));
        return box;
    }

    private View buildPrayerWidget() {
        LinearLayout box = card();
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(text("WAKTU SOLAT", 12, true, secondary));
        box.addView(text("Prayer times", 20, true, foreground));
        box.addView(text("Prayer schedule is not connected yet", 13, false, secondary));
        box.addView(text("Subuh   Zohor   Asar   Maghrib   Isyak", 11, true, foreground));
        return box;
    }

    private void showAppDrawer() {
        applyTheme();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(14), dp(8), dp(14), dp(6));
        main.setBackground(drawable(
                Color.argb(246, Color.red(background), Color.green(background),
                        Color.blue(background)),
                Color.argb(150, Color.red(border), Color.green(border), Color.blue(border)),
                24));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Applications", 23, true, foreground);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView close = text("✕", 23, true, foreground);
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(46), dp(42)));
        main.addView(header);
        close.setOnClickListener(v -> dialog.dismiss());

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(foreground);
        search.setHintTextColor(secondary);
        search.setHint("Search applications...");
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(drawable(
                Color.argb(170, Color.red(panel), Color.green(panel), Color.blue(panel)),
                Color.argb(145, Color.red(border), Color.green(border), Color.blue(border)),
                15));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(42));
        searchParams.topMargin = dp(5);
        main.addView(search, searchParams);

        HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
        horizontalScroll.setHorizontalScrollBarEnabled(false);
        horizontalScroll.setVerticalScrollBarEnabled(false);
        horizontalScroll.setFillViewport(true);
        horizontalScroll.setSmoothScrollingEnabled(true);
        horizontalScroll.setClipToPadding(false);
        horizontalScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout pagesContainer = new LinearLayout(this);
        pagesContainer.setOrientation(LinearLayout.HORIZONTAL);
        horizontalScroll.addView(pagesContainer,
                new HorizontalScrollView.LayoutParams(-1, -1));

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(5);
        main.addView(horizontalScroll, scrollParams);

        TextView pageHint = text("Swipe left or right to browse apps", 11, false, secondary);
        pageHint.setGravity(Gravity.CENTER);
        main.addView(pageHint, new LinearLayout.LayoutParams(-1, dp(20)));

        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = getInstalledLauncherApps();

        Runnable updatePages = () -> {
            pagesContainer.removeAllViews();
            String query = search.getText().toString()
                    .toLowerCase(Locale.ROOT).trim();

            java.util.ArrayList<ResolveInfo> filtered = new java.util.ArrayList<>();
            for (ResolveInfo app : apps) {
                String appName = app.loadLabel(pm).toString();
                if (appName.toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(app);
                }
            }

            int pageWidth = horizontalScroll.getWidth();
            if (pageWidth <= 0) {
                pageWidth = getResources().getDisplayMetrics().widthPixels - dp(28);
            }

            if (filtered.isEmpty()) {
                LinearLayout emptyPage = new LinearLayout(this);
                emptyPage.setGravity(Gravity.CENTER);
                emptyPage.addView(text("No applications found", 15, false, secondary));
                pagesContainer.addView(emptyPage,
                        new LinearLayout.LayoutParams(pageWidth, -1));
                pageHint.setText("");
                horizontalScroll.scrollTo(0, 0);
                return;
            }

            int screenHeightDp = (int) (getResources().getDisplayMetrics().heightPixels
                    / getResources().getDisplayMetrics().density);
            final int rows = screenHeightDp >= 480 ? 4 : 3;
            final int columns = 5;
            final int appsPerPage = rows * columns;
            int pageCount = (filtered.size() + appsPerPage - 1) / appsPerPage;

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                LinearLayout page = new LinearLayout(this);
                page.setOrientation(LinearLayout.VERTICAL);
                page.setPadding(dp(2), dp(3), dp(2), dp(3));

                GridLayout grid = new GridLayout(this);
                grid.setColumnCount(columns);
                grid.setRowCount(rows);
                grid.setUseDefaultMargins(false);
                page.addView(grid, new LinearLayout.LayoutParams(-1, -1));

                int first = pageIndex * appsPerPage;
                int last = Math.min(first + appsPerPage, filtered.size());

                for (int i = first; i < last; i++) {
                    ResolveInfo app = filtered.get(i);
                    String appName = app.loadLabel(pm).toString();

                    LinearLayout item = new LinearLayout(this);
                    item.setOrientation(LinearLayout.VERTICAL);
                    item.setGravity(Gravity.CENTER);
                    item.setPadding(dp(3), dp(3), dp(3), dp(3));
                    item.setBackground(drawable(
                            Color.argb(62, Color.red(panel), Color.green(panel),
                                    Color.blue(panel)),
                            Color.argb(75, Color.red(border), Color.green(border),
                                    Color.blue(border)),
                            15));

                    ImageView icon = new ImageView(this);
                    icon.setImageDrawable(app.loadIcon(pm));
                    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int iconSize = rows == 4 ? 36 : 40;
                    item.addView(icon, new LinearLayout.LayoutParams(
                            dp(iconSize), dp(iconSize)));

                    TextView name = text(appName, 11, false, foreground);
                    name.setGravity(Gravity.CENTER);
                    name.setMaxLines(2);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams nameParams =
                            new LinearLayout.LayoutParams(-1, dp(30));
                    nameParams.topMargin = dp(2);
                    item.addView(name, nameParams);

                    GridLayout.LayoutParams itemParams = new GridLayout.LayoutParams();
                    itemParams.width = 0;
                    itemParams.height = 0;
                    itemParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    itemParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    itemParams.setMargins(dp(3), dp(3), dp(3), dp(3));
                    grid.addView(item, itemParams);

                    item.setOnClickListener(v -> {
                        Intent launch = new Intent();
                        launch.setClassName(app.activityInfo.packageName,
                                app.activityInfo.name);
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(launch);
                            dialog.dismiss();
                        } catch (Exception e) {
                            Toast.makeText(this, "Unable to open application",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                pagesContainer.addView(page,
                        new LinearLayout.LayoutParams(pageWidth, -1));
            }

            pageHint.setText(pageCount > 1
                    ? "Swipe left or right  •  " + pageCount + " pages"
                    : "All applications");
            horizontalScroll.scrollTo(0, 0);
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                horizontalScroll.post(updatePages);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        dialog.setContentView(main);
        dialog.setCancelable(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        horizontalScroll.post(updatePages);
    }

    private void showSettings() {
        String[] options = {"Appearance", "Customize sidebar"};
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showAppearanceSettings();
                    else showSidebarCustomization();
                })
                .show();
    }

    private void showAppearanceSettings() {
        String[] themes = {"Light Sky", "Dark Maroon"};
        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setSingleChoiceItems(themes, lightTheme ? 0 : 1, (dialog, which) -> {
                    lightTheme = (which == 0);
                    preferences.edit().putBoolean(THEME_KEY, lightTheme).apply();
                    dialog.dismiss();
                    buildHome();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSidebarCustomization() {
        String[] slots = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            String label = preferences.getString(labelKey(i), DEFAULT_NAMES[i]);
            slots[i] = "Shortcut " + (i + 1) + "  •  " + label;
        }
        new AlertDialog.Builder(this)
                .setTitle("Customize sidebar")
                .setItems(slots, (dialog, which) -> showSidebarSlotPicker(which))
                .setNegativeButton("Close", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(drawable(panel, border, 22));
        return box;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable drawable(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

