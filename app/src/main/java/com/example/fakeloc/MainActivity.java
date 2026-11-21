
package com.example.fakeloc;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

public class MainActivity extends Activity {

    private MapView map;
    private Marker marker;
    private GeoPoint selected;
    private boolean running = false;

    private Button startButton;
    private TextView info, status;
    private EditText coordinates;
    private SharedPreferences prefs;

    private static final int REQUEST_PERMISSIONS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("fakeloc", MODE_PRIVATE);

        double lat = Double.longBitsToDouble(
                prefs.getLong("lat", Double.doubleToLongBits(30.0444)));

        double lon = Double.longBitsToDouble(
                prefs.getLong("lon", Double.doubleToLongBits(31.2357)));

        selected = new GeoPoint(lat, lon);

        Configuration.getInstance().load(
                this, getSharedPreferences("osm", MODE_PRIVATE));

        Configuration.getInstance().setUserAgentValue(
                getPackageName());

        requestAppPermissions();
        buildInterface();
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;

        java.util.ArrayList<String> list = new java.util.ArrayList<>();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!list.isEmpty()) {
            requestPermissions(
                    list.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void buildInterface() {

        FrameLayout root = new FrameLayout(this);

        map = new MapView(this);

        map.setTileSource(new XYTileSource(
                "CartoLight",
                0, 19, 256, ".png",
                new String[]{
                        "https://a.basemaps.cartocdn.com/light_all/",
                        "https://b.basemaps.cartocdn.com/light_all/",
                        "https://c.basemaps.cartocdn.com/light_all/"
                }
        ));

        map.setMultiTouchControls(true);
        map.getController().setZoom(15);
        map.getController().setCenter(selected);

        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        marker = new Marker(map);
        marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM);
        marker.setPosition(selected);

        map.getOverlays().add(new MapEventsOverlay(
                new MapEventsReceiver() {
                    @Override
                    public boolean singleTapConfirmedHelper(GeoPoint point) {
                        selectLocation(point, true);
                        return true;
                    }

                    @Override
                    public boolean longPressHelper(GeoPoint point) {
                        return false;
                    }
                }
        ));

        map.getOverlays().add(marker);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 20, 24, 24);
        panel.setBackgroundColor(Color.WHITE);

        info = new TextView(this);
        info.setTextColor(Color.BLACK);
        info.setTextSize(17);

        status = new TextView(this);
        status.setTextColor(Color.DKGRAY);
        status.setTextSize(14);
        status.setText("الحالة: متوقف");

        coordinates = new EditText(this);
        coordinates.setSingleLine(true);
        coordinates.setTextColor(Color.BLACK);
        coordinates.setHintTextColor(Color.GRAY);
        coordinates.setHint("Latitude, Longitude");

        Button goButton = new Button(this);
        goButton.setText("انتقال للإحداثيات");

        goButton.setOnClickListener(v -> {
            try {
                String[] values = coordinates.getText()
                        .toString()
                        .trim()
                        .split("[,،\\s]+");

                if (values.length != 2) {
                    throw new IllegalArgumentException();
                }

                double lat = Double.parseDouble(values[0]);
                double lon = Double.parseDouble(values[1]);

                if (!validCoordinates(lat, lon)) {
                    throw new IllegalArgumentException();
                }

                selectLocation(new GeoPoint(lat, lon), true);

            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "تأكد من كتابة الإحداثيات بصورة صحيحة",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        startButton = new Button(this);
        startButton.setText("ابدأ الموقع التجريبي");

        startButton.setOnClickListener(v -> toggleService());

        panel.addView(info);
        panel.addView(status);
        panel.addView(coordinates);
        panel.addView(goButton);
        panel.addView(startButton);

        FrameLayout.LayoutParams lp =
                new FrameLayout.LayoutParams(-1, -2);

        lp.gravity = Gravity.BOTTOM;

        root.addView(panel, lp);

        setContentView(root);

        selectLocation(selected, false);
    }

    private boolean validCoordinates(double lat, double lon) {
        return !Double.isNaN(lat)
                && !Double.isNaN(lon)
                && !Double.isInfinite(lat)
                && !Double.isInfinite(lon)
                && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180;
    }

    private void selectLocation(GeoPoint point, boolean center) {

        if (!validCoordinates(
                point.getLatitude(),
                point.getLongitude())) return;

        selected = point;

        marker.setPosition(selected);

        if (center) {
            map.getController().animateTo(selected);
        }

        String value = String.format(
                Locale.US,
                "%.6f, %.6f",
                selected.getLatitude(),
                selected.getLongitude()
        );

        info.setText("الإحداثيات: " + value);

        coordinates.setText(value);

        prefs.edit()
                .putLong("lat",
                        Double.doubleToLongBits(selected.getLatitude()))
                .putLong("lon",
                        Double.doubleToLongBits(selected.getLongitude()))
                .apply();

        map.invalidate();

        if (running) {
            sendStart();
        }
    }

    private boolean hasLocationPermission() {

        if (Build.VERSION.SDK_INT < 23) return true;

        return checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean mockAllowed() {

        try {
            AppOpsManager ops =
                    (AppOpsManager) getSystemService(APP_OPS_SERVICE);

            return ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    getPackageName()
            ) == AppOpsManager.MODE_ALLOWED;

        } catch (Exception e) {
            return false;
        }
    }

    private void sendStart() {

        Intent intent = new Intent(this, MockService.class);

        intent.putExtra("lat", selected.getLatitude());
        intent.putExtra("lon", selected.getLongitude());

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void toggleService() {

        if (!running) {

            if (!hasLocationPermission()) {
                requestAppPermissions();
                Toast.makeText(
                        this,
                        "اسمح بصلاحية الموقع أولاً",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            if (!mockAllowed()) {
                Toast.makeText(
                        this,
                        "اختر التطبيق من خيارات المطور كمصدر موقع تجريبي",
                        Toast.LENGTH_LONG
                ).show();

                try {
                    startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (Exception ignored) {}

                return;
            }

            try {
                sendStart();
                running = true;

                startButton.setText("إيقاف الموقع التجريبي");
                status.setText("الحالة: قيد التشغيل");

            } catch (Exception e) {
                Toast.makeText(
                        this,
                        "تعذر تشغيل الخدمة",
                        Toast.LENGTH_LONG
                ).show();
            }

        } else {

            Intent intent = new Intent(this, MockService.class);
            intent.setAction("STOP");

            startService(intent);

            running = false;

            startButton.setText("ابدأ الموقع التجريبي");
            status.setText("الحالة: متوقف");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (map != null) {
            map.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (map != null) {
            map.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (map != null) {
            map.onDetach();
        }

        super.onDestroy();
    }
}
