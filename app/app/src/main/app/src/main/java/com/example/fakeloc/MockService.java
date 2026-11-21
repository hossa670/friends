package com.example.fakeloc;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.*;
import android.widget.Toast;

public class MockService extends Service {
    static final String[] PROVIDERS = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
    LocationManager lm;
    Handler h = new Handler(Looper.getMainLooper());
    double lat, lon;
    boolean active = false;
    Runnable tick = new Runnable() {
        public void run() { push(); h.postDelayed(this, 1000); }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i == null || "STOP".equals(i.getAction())) { cleanup(); stopForeground(true); stopSelf(); return START_NOT_STICKY; }
        lat = i.getDoubleExtra("lat", 0);
        lon = i.getDoubleExtra("lon", 0);
        startFg();
        if (!active) {
            if (!setup()) { stopForeground(true); stopSelf(); return START_NOT_STICKY; }
            active = true;
            h.post(tick);
        }
        return START_STICKY;
    }

    void startFg() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("fakeloc", "الموقع الوهمي", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "fakeloc") : new Notification.Builder(this);
        Notification n = b.setContentTitle("الموقع الوهمي شغال").setContentText(lat + ", " + lon)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(1, n);
    }

    boolean setup() {
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            for (String p : PROVIDERS) {
                try { lm.removeTestProvider(p); } catch (Exception ignored) {}
                lm.addTestProvider(p, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
                lm.setTestProviderEnabled(p, true);
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "اختار التطبيق ده كـ Mock location app من خيارات المطور", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    void push() {
        for (String p : PROVIDERS) {
            try {
                Location l = new Location(p);
                l.setLatitude(lat); l.setLongitude(lon);
                l.setAccuracy(3f); l.setAltitude(0);
                l.setTime(System.currentTimeMillis());
                l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                lm.setTestProviderLocation(p, l);
            } catch (Exception ignored) {}
        }
    }

    void cleanup() {
        h.removeCallbacks(tick);
        if (lm != null) for (String p : PROVIDERS) { try { lm.removeTestProvider(p); } catch (Exception ignored) {} }
        active = false;
    }

    @Override public void onDestroy() { cleanup(); super.onDestroy(); }
}
