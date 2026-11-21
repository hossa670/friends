package com.example.fakeloc;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class MainActivity extends Activity {
    MapView map; Marker marker; GeoPoint sel = new GeoPoint(30.0444, 31.2357);
    boolean running = false; Button btn; TextView info; EditText box;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        Configuration.getInstance().load(this, getSharedPreferences("osm", 0));
        Configuration.getInstance().setUserAgentValue("HossaFriendsLocation/1.0 (hossa670)");
        if (Build.VERSION.SDK_INT >= 23) {
            String[] perms = Build.VERSION.SDK_INT >= 33
                    ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(perms, 1);
        }
        FrameLayout root = new FrameLayout(this);
        map = new MapView(this);
    map.setTileSource(new org.osmdroid.tileprovider.tilesource.XYTileSource("CartoLight", 0, 19, 256, ".png", new String[]{"https://a.basemaps.cartocdn.com/light_all/","https://b.basemaps.cartocdn.com/light_all/","https://c.basemaps.cartocdn.com/light_all/"}));
        map.setMultiTouchControls(true);
        map.getController().setZoom(13.0);
        map.getController().setCenter(sel);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setPosition(sel);
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            public boolean singleTapConfirmedHelper(GeoPoint p) { select(p, false); return true; }
            public boolean longPressHelper(GeoPoint p) { return false; }
        }));
        map.getOverlays().add(marker);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);
        panel.setPadding(32, 24, 32, 32);
        info = new TextView(this); info.setTextColor(Color.BLACK); info.setTextSize(16);
        box = new EditText(this); box.setHint("lat, lon  مثال: 30.0444, 31.2357"); box.setSingleLine(true);
        box.setTextColor(Color.BLACK); box.setHintTextColor(Color.GRAY);
        Button go = new Button(this); go.setText("روح للإحداثيات");
        go.setOnClickListener(v -> {
            try {
                String[] a = box.getText().toString().split("[,،\\s]+");
                select(new GeoPoint(Double.parseDouble(a[0]), Double.parseDouble(a[1])), true);
            } catch (Exception e) { Toast.makeText(this, "اكتب lat, lon صح", Toast.LENGTH_SHORT).show(); }
        });
        btn = new Button(this); btn.setText("ابدأ الموقع الوهمي");
        btn.setOnClickListener(v -> toggle());
        panel.addView(info); panel.addView(box); panel.addView(go); panel.addView(btn);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        root.addView(panel, lp);
        setContentView(root);
        select(sel, false);
    }

    void select(GeoPoint p, boolean center) {
        sel = p; marker.setPosition(p);
        if (center) map.getController().animateTo(p);
        info.setText(String.format(java.util.Locale.US, "%.6f, %.6f", p.getLatitude(), p.getLongitude()));
        map.invalidate();
        if (running) sendStart();
    }

    boolean mockAllowed() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            return ops.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    void sendStart() {
        Intent i = new Intent(this, MockService.class);
        i.putExtra("lat", sel.getLatitude()); i.putExtra("lon", sel.getLongitude());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    void toggle() {
        if (!running) {
            if (!mockAllowed()) {
                Toast.makeText(this, "فعّل خيارات المطور واختار التطبيق ده في Select mock location app", Toast.LENGTH_LONG).show();
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); } catch (Exception ignored) {}
                return;
            }
            sendStart(); running = true; btn.setText("إيقاف الموقع الوهمي");
        } else {
            Intent i = new Intent(this, MockService.class); i.setAction("STOP"); startService(i);
            running = false; btn.setText("ابدأ الموقع الوهمي");
        }
    }

    @Override protected void onResume() { super.onResume(); map.onResume(); }
    @Override protected void onPause() { super.onPause(); map.onPause(); }
}
