package com.example.fakeloc;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    MapCanvas map;
    boolean running = false;
    Button btn; TextView info; EditText box;
    double lat = 30.0444, lon = 31.2357;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Build.VERSION.SDK_INT >= 23) {
            String[] perms = Build.VERSION.SDK_INT >= 33
                    ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(perms, 1);
        }
        FrameLayout root = new FrameLayout(this);
        map = new MapCanvas(this);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

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
                select(Double.parseDouble(a[0]), Double.parseDouble(a[1]));
            } catch (Exception e) { Toast.makeText(this, "اكتب lat, lon صح", Toast.LENGTH_SHORT).show(); }
        });
        btn = new Button(this); btn.setText("ابدأ الموقع الوهمي");
        btn.setOnClickListener(v -> toggle());
        panel.addView(info); panel.addView(box); panel.addView(go); panel.addView(btn);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        root.addView(panel, lp);
        setContentView(root);
        select(lat, lon);
    }

    void select(double la, double lo) {
        lat = la; lon = lo;
        info.setText(String.format(Locale.US, "%.6f, %.6f", lat, lon));
        map.setPoint(la, lo);
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
        i.putExtra("lat", lat); i.putExtra("lon", lon);
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

    static class MapCanvas extends View {
        double px = 0.5, py = 0.5;
        Paint land = new Paint(), road = new Paint(), water = new Paint(), pin = new Paint(), pinDot = new Paint();
        MapCanvas(android.content.Context c) {
            super(c);
            land.setColor(Color.rgb(238, 241, 232));
            road.setColor(Color.WHITE);
            water.setColor(Color.rgb(169, 211, 240));
            pin.setColor(Color.rgb(26, 115, 232));
            pinDot.setColor(Color.WHITE);
            setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                    px = e.getX() / getWidth(); py = e.getY() / getHeight();
                    invalidate();
                    MainActivity act = (MainActivity) getContext();
                    double la = 30.12 - py * 0.17, lo = 31.15 + px * 0.25;
                    act.select(la, lo);
                }
                return true;
            });
        }
        void setPoint(double la, double lo) {
            py = (30.12 - la) / 0.17; px = (lo - 31.15) / 0.25;
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(Color.rgb(238, 241, 232));
            for (int i = 0; i <= 10; i++) {
                c.drawLine(i * w / 10f, 0, i * w / 10f, h, road);
                c.drawLine(0, i * h / 10f, w, i * h / 10f, road);
            }
            Path wp = new Path();
            wp.moveTo(0, h * 0.35f);
            wp.cubicTo(w * 0.3f, h * 0.25f, w * 0.5f, h * 0.45f, w, h * 0.4f);
            wp.lineTo(w, h * 0.55f);
            wp.cubicTo(w * 0.5f, h * 0.6f, w * 0.3f, h * 0.4f, 0, h * 0.5f);
            wp.close();
            c.drawPath(wp, water);
            float cx = (float) (px * w), cy = (float) (py * h);
            pin.setAlpha(60);
            c.drawCircle(cx, cy, 60, pin);
            pin.setAlpha(255);
            c.drawCircle(cx, cy, 22, pin);
            c.drawCircle(cx, cy, 16, pinDot);
            c.drawCircle(cx, cy, 11, pin);
        }
    }
}
