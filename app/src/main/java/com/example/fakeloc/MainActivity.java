package com.example.fakeloc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class MainActivity extends Activity {
    MapView map;
    Marker marker;
    GeoPoint sel = new GeoPoint(30.0444, 31.2357);
    boolean running = false;
    boolean isSatellite = false;
    Button btn;
    Button mapTypeBtn;
    TextView info;
    EditText box;
    OnlineTileSourceBase streetTiles;
    OnlineTileSourceBase satelliteTiles;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);

        Configuration.getInstance().load(this, getSharedPreferences("map_prefs", 0));
        Configuration.getInstance().setUserAgentValue("MapNavigator/1.3");
        File mapBase = new File(getCacheDir(), "map_cache");
        File mapTiles = new File(mapBase, "tiles");
        mapTiles.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(mapBase);
        Configuration.getInstance().setOsmdroidTileCache(mapTiles);

        if (Build.VERSION.SDK_INT >= 23) {
            String[] perms = Build.VERSION.SDK_INT >= 33
                    ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(perms, 1);
        }

        streetTiles = createGoogleTileSource("GoogleStreets", "m");
        satelliteTiles = createGoogleTileSource("GoogleSatellite", "y");

        FrameLayout root = new FrameLayout(this);
        map = new MapView(this);
        map.setTileSource(streetTiles);
        map.setUseDataConnection(true);
        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);
        map.getController().setCenter(sel);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setPosition(sel);
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                select(p, false);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));
        map.getOverlays().add(marker);

        mapTypeBtn = new Button(this);
        mapTypeBtn.setText("قمر صناعي");
        mapTypeBtn.setOnClickListener(v -> toggleMapType());
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-2, -2);
        topLp.gravity = Gravity.TOP | Gravity.END;
        topLp.setMargins(24, 48, 24, 24);
        root.addView(mapTypeBtn, topLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);
        panel.setPadding(28, 20, 28, 28);

        info = new TextView(this);
        info.setTextColor(Color.BLACK);
        info.setTextSize(15);

        box = new EditText(this);
        box.setHint("اكتب اسم المكان أو الإحداثيات (lat, lon)");
        box.setSingleLine(true);
        box.setTextColor(Color.BLACK);
        box.setHintTextColor(Color.GRAY);

        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnWeight = new LinearLayout.LayoutParams(0, -2, 1f);

        Button go = new Button(this);
        go.setText("بحث");
        go.setOnClickListener(v -> searchOrGo());
        actionsRow.addView(go, btnWeight);

        Button saveBtn = new Button(this);
        saveBtn.setText("حفظ");
        saveBtn.setOnClickListener(v -> promptSaveFavorite());
        actionsRow.addView(saveBtn, btnWeight);

        Button favBtn = new Button(this);
        favBtn.setText("المفضلة");
        favBtn.setOnClickListener(v -> showFavorites());
        actionsRow.addView(favBtn, btnWeight);

        btn = new Button(this);
        btn.setText("تثبيت الموقع");
        btn.setOnClickListener(v -> toggle());

        panel.addView(info);
        panel.addView(box);
        panel.addView(actionsRow);
        panel.addView(btn);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        root.addView(panel, lp);
        setContentView(root);
        select(sel, false);
    }

    OnlineTileSourceBase createGoogleTileSource(String name, String layer) {
        return new OnlineTileSourceBase(
                name,
                0, 20, 256, ".png",
                new String[]{
                        "https://mt0.google.com/vt/lyrs=" + layer + "&hl=ar&x=",
                        "https://mt1.google.com/vt/lyrs=" + layer + "&hl=ar&x=",
                        "https://mt2.google.com/vt/lyrs=" + layer + "&hl=ar&x=",
                        "https://mt3.google.com/vt/lyrs=" + layer + "&hl=ar&x="
                }) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl()
                        + MapTileIndex.getX(pMapTileIndex)
                        + "&y=" + MapTileIndex.getY(pMapTileIndex)
                        + "&z=" + MapTileIndex.getZoom(pMapTileIndex);
            }
        };
    }

    void toggleMapType() {
        isSatellite = !isSatellite;
        if (isSatellite) {
            map.setTileSource(satelliteTiles);
            mapTypeBtn.setText("شوارع");
        } else {
            map.setTileSource(streetTiles);
            mapTypeBtn.setText("قمر صناعي");
        }
        map.invalidate();
    }

    void searchOrGo() {
        String q = box.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "اكتب اسم مكان أو إحداثيات", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] parts = q.split("[,،\\s]+");
        if (parts.length == 2) {
            try {
                double lat = Double.parseDouble(parts[0]);
                double lon = Double.parseDouble(parts[1]);
                if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                    select(new GeoPoint(lat, lon), true);
                    map.getController().setZoom(16.0);
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        Toast.makeText(this, "جاري البحث...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = "https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=ar&q="
                        + URLEncoder.encode(q, "UTF-8");
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MapNavigator/1.3");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONArray arr = new JSONArray(sb.toString());
                if (arr.length() > 0) {
                    JSONObject obj = arr.getJSONObject(0);
                    double lat = obj.getDouble("lat");
                    double lon = obj.getDouble("lon");
                    String name = obj.optString("display_name", q);
                    runOnUiThread(() -> {
                        select(new GeoPoint(lat, lon), true);
                        map.getController().setZoom(16.0);
                        Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "لم يتم العثور على المكان", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    JSONArray getFavorites() {
        try {
            SharedPreferences prefs = getSharedPreferences("map_prefs", 0);
            return new JSONArray(prefs.getString("favorites", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    void saveFavorites(JSONArray arr) {
        getSharedPreferences("map_prefs", 0).edit().putString("favorites", arr.toString()).apply();
    }

    void promptSaveFavorite() {
        EditText nameInput = new EditText(this);
        nameInput.setHint("اكتب اسم للمكان (مثال: البيت، الشغل)");
        new AlertDialog.Builder(this)
                .setTitle("حفظ المكان في المفضلة")
                .setView(nameInput)
                .setPositiveButton("حفظ", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = String.format(Locale.US, "%.4f, %.4f", sel.getLatitude(), sel.getLongitude());
                    }
                    try {
                        JSONArray arr = getFavorites();
                        JSONObject item = new JSONObject();
                        item.put("name", name);
                        item.put("lat", sel.getLatitude());
                        item.put("lon", sel.getLongitude());
                        arr.put(item);
                        saveFavorites(arr);
                        Toast.makeText(this, "تم الحفظ في المفضلة", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    void showFavorites() {
        JSONArray arr = getFavorites();
        if (arr.length() == 0) {
            Toast.makeText(this, "المفضلة فاضية حالياً", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            names[i] = item != null ? item.optString("name", "مكان " + (i + 1)) : "مكان " + (i + 1);
        }
        new AlertDialog.Builder(this)
                .setTitle("الأماكن المفضلة")
                .setItems(names, (d, which) -> {
                    JSONObject item = arr.optJSONObject(which);
                    if (item != null) {
                        select(new GeoPoint(item.optDouble("lat"), item.optDouble("lon")), true);
                        map.getController().setZoom(16.0);
                    }
                })
                .setNeutralButton("حذف مكان", (d, w) -> showDeleteFavorite(arr, names))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    void showDeleteFavorite(JSONArray arr, String[] names) {
        new AlertDialog.Builder(this)
                .setTitle("اختر المكان لحذفه")
                .setItems(names, (d, which) -> {
                    JSONArray updated = new JSONArray();
                    for (int i = 0; i < arr.length(); i++) {
                        if (i != which) updated.put(arr.opt(i));
                    }
                    saveFavorites(updated);
                    Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    void select(GeoPoint p, boolean center) {
        sel = p;
        marker.setPosition(p);
        if (center) map.getController().animateTo(p);
        info.setText(String.format(Locale.US, "%.6f, %.6f", p.getLatitude(), p.getLongitude()));
        map.invalidate();
        if (running) sendStart();
    }

    boolean mockAllowed() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            return ops.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    void sendStart() {
        Intent i = new Intent(this, MockService.class);
        i.putExtra("lat", sel.getLatitude());
        i.putExtra("lon", sel.getLongitude());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    void toggle() {
        if (!running) {
            if (!mockAllowed()) {
                Toast.makeText(this, "فعّل خيارات المطور واختار تطبيق الخرائط في Select mock location app", Toast.LENGTH_LONG).show();
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (Exception ignored) {
                }
                return;
            }
            sendStart();
            running = true;
            btn.setText("إيقاف التثبيت");
        } else {
            Intent i = new Intent(this, MockService.class);
            i.setAction("STOP");
            startService(i);
            running = false;
            btn.setText("تثبيت الموقع");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}
