
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

import java.util.Locale;

public class MockService extends Service {

    private static final String CHANNEL_ID = "fakeloc";
    private static final int NOTIFICATION_ID = 1;

    private static final String[] PROVIDERS = {
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
    };

    private LocationManager locationManager;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private double lat;
    private double lon;

    private boolean active = false;
    private boolean providersReady = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!active) return;

            push();

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null ||
                "STOP".equals(intent.getAction())) {

            stopServiceSafely();
            return START_NOT_STICKY;
        }

        double newLat = intent.getDoubleExtra("lat", 0);
        double newLon = intent.getDoubleExtra("lon", 0);

        if (!validCoordinates(newLat, newLon)) {
            stopServiceSafely();
            return START_NOT_STICKY;
        }

        lat = newLat;
        lon = newLon;

        try {

            startForegroundNotification();

            if (!providersReady) {

                if (!setupProviders()) {
                    stopServiceSafely();
                    return START_NOT_STICKY;
                }

                providersReady = true;
            }

            if (!active) {
                active = true;
                handler.post(tick);
            } else {
                push();
            }

            updateNotification();

        } catch (Exception e) {

            stopServiceSafely();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    private boolean validCoordinates(double latitude,
                                     double longitude) {

        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !Double.isInfinite(latitude)
                && !Double.isInfinite(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(NOTIFICATION_SERVICE);

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "خدمة الموقع التجريبي",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "حالة تشغيل خدمة الموقع التجريبي");

            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {

        String coordinates = String.format(
                Locale.US,
                "%.6f, %.6f",
                lat,
                lon
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("الموقع التجريبي يعمل")
                .setContentText(coordinates)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void startForegroundNotification() {

        createNotificationChannel();

        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }
    }

    private void updateNotification() {

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);

        manager.notify(
                NOTIFICATION_ID,
                buildNotification()
        );
    }

    private boolean setupProviders() {

        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

        if (locationManager == null) return false;

        try {

            for (String provider : PROVIDERS) {

                try {
                    locationManager.removeTestProvider(provider);
                } catch (Exception ignored) {}

                locationManager.addTestProvider(
                        provider,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                );

                locationManager.setTestProviderEnabled(
                        provider,
                        true
                );
            }

            return true;

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "تعذر إعداد مزود الموقع التجريبي",
                    Toast.LENGTH_LONG
            ).show();

            return false;
        }
    }

    private void push() {

        if (locationManager == null || !providersReady) return;

        for (String provider : PROVIDERS) {

            try {

                Location location = new Location(provider);

                location.setLatitude(lat);
                location.setLongitude(lon);
                location.setAccuracy(3f);
                location.setAltitude(0);

                location.setTime(
                        System.currentTimeMillis()
                );

                location.setElapsedRealtimeNanos(
                        SystemClock.elapsedRealtimeNanos()
                );

                locationManager.setTestProviderLocation(
                        provider,
                        location
                );

            } catch (Exception e) {

                android.util.Log.e(
                        "FakeLoc",
                        "Location update failed: " + provider,
                        e
                );
            }
        }
    }

    private void stopServiceSafely() {

        active = false;

        handler.removeCallbacks(tick);

        if (locationManager != null && providersReady) {

            for (String provider : PROVIDERS) {

                try {
                    locationManager.setTestProviderEnabled(
                            provider,
                            false
                    );
                } catch (Exception ignored) {}

                try {
                    locationManager.removeTestProvider(provider);
                } catch (Exception ignored) {}
            }
        }

        providersReady = false;

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {

        stopServiceSafely();

        super.onDestroy();
    }
}
