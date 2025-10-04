package com.example.rescuenet;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Display;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // ===== Permissions & Requests =====
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final int BLE_PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_CHECK_SETTINGS = 2000;

    // ===== WebView & Location =====
    private WebView webView;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Handler locationSettingsHandler;
    private Runnable locationSettingsRunnable;

    // ===== Connectivity =====
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // ===== BLE =====
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;
    private BluetoothGatt bluetoothGatt;

    // Replace with your actual service/characteristic UUIDs when you define them
    // (Temporary demo UUIDs for data notify/write; update to your GATT profile)
    private static final UUID SERVICE_UUID = UUID.fromString("0000181C-0000-1000-8000-00805F9B34FB"); // demo
    private static final UUID CHAR_WRITE_UUID = UUID.fromString("00002A58-0000-1000-8000-00805F9B34FB"); // demo
    private static final UUID CHAR_NOTIFY_UUID = UUID.fromString("00002A59-0000-1000-8000-00805F9B34FB"); // demo
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ===== WebView setup =====
        webView = findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient());
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        webView.addJavascriptInterface(new WebAppInterface(this), "Android");      // credentials, exports, device id
        webView.addJavascriptInterface(new AndroidBLEBridge(this), "AndroidBLE");  // BLE-only sync bridge

        webView.loadUrl("file:///android_asset/map.html");

        // ===== Location client =====
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Request permissions (location + BLE)
        requestCorePermissions();

        // Periodic check for location settings
        locationSettingsHandler = new Handler();
        locationSettingsRunnable = new Runnable() {
            @Override
            public void run() {
                checkLocationSettings();
                locationSettingsHandler.postDelayed(this, 10000); // every 10s
            }
        };
        locationSettingsHandler.post(locationSettingsRunnable);

        // Monitor connectivity (HTML shows banner)
        setupInternetMonitoring();

        // Init BLE adapter
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm != null ? bm.getAdapter() : null;
    }

    // ===== Permissions =====
    private void requestCorePermissions() {
        List<String> req = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            req.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                req.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                req.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            // (Optional) if you plan advertising:
            // if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) { ... }
        }
        if (!req.isEmpty()) {
            ActivityCompat.requestPermissions(this, req.toArray(new String[0]), BLE_PERMISSION_REQUEST_CODE);
        } else {
            // If already granted, start location flow
            checkLocationSettings();
        }
    }

    // ===== JS Interface: credentials, export, device id =====
    public class WebAppInterface {
        Context context;
        WebAppInterface(Context ctx) { this.context = ctx; }

        @JavascriptInterface
        public void saveCredentials(String username, String password) {
            SharedPreferences sp = context.getSharedPreferences("CampusMapPrefs", Context.MODE_PRIVATE);
            sp.edit().putString("username", username).putString("password", password).apply();
            runOnUiThread(() -> Toast.makeText(context, "Credentials saved!", Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String getUsername() {
            SharedPreferences sp = context.getSharedPreferences("CampusMapPrefs", Context.MODE_PRIVATE);
            return sp.getString("username", "");
        }

        @JavascriptInterface
        public String getPassword() {
            SharedPreferences sp = context.getSharedPreferences("CampusMapPrefs", Context.MODE_PRIVATE);
            return sp.getString("password", "");
        }

        @JavascriptInterface
        public String getDeviceId() {
            try {
                @SuppressLint("HardwareIds")
                String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                return id != null ? id : "unknown-device";
            } catch (Exception e) {
                return "unknown-device";
            }
        }

        // Save exported JSON payload to app's external files dir
        @JavascriptInterface
        public void saveExport(String json) {
            try {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (dir != null && (dir.exists() || dir.mkdirs())) {
                    String name = "dr_logs_export_" + System.currentTimeMillis() + ".json";
                    File out = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Saved: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed (dir).", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed.", Toast.LENGTH_SHORT).show());
            }
        }
    }

    // ===== JS Interface: BLE-only peer sync bridge =====
    public class AndroidBLEBridge extends @org.jspecify.annotations.NonNull Context {
        Context context;
        AndroidBLEBridge(Context ctx) { this.context = ctx; }

        @JavascriptInterface
        public void startScan() {
            if (!ensureBleReady()) return;

            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bleScanner == null) {
                runOnUiThread(() -> Toast.makeText(context, "BLE scanner unavailable", Toast.LENGTH_SHORT).show());
                return;
            }

            List<ScanFilter> filters = new ArrayList<>();
            // Optionally filter to your SERVICE_UUID
            // filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build());

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    BluetoothDevice device = result.getDevice();
                    // For simplicity, auto-connect to first seen device exposing our service (or any, if not filtering)
                    // In production, surface a device picker UI. For now, just connect once.
                }
            };

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bleScanner.startScan(filters, settings, scanCallback);
            runOnUiThread(() -> Toast.makeText(context, "BLE scanning…", Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void stopScan() {
            if (bleScanner != null && scanCallback != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                bleScanner.stopScan(scanCallback);
                runOnUiThread(() -> Toast.makeText(context, "BLE scan stopped", Toast.LENGTH_SHORT).show());
            }
        }

        // Connect by MAC address (deviceId). Your JS should call AndroidBLE.connect(deviceId)
        @JavascriptInterface
        public void connect(String deviceId) {
            if (!ensureBleReady()) return;
            if (deviceId == null || deviceId.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(context, "No deviceId provided", Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                BluetoothDevice dev = bluetoothAdapter.getRemoteDevice(deviceId);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                bluetoothGatt = dev.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                runOnUiThread(() -> Toast.makeText(context, "Connecting to " + deviceId, Toast.LENGTH_SHORT).show());
            } catch (IllegalArgumentException e) {
                runOnUiThread(() -> Toast.makeText(context, "Invalid deviceId", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void send(String jsonPayload) {
            if (bluetoothGatt == null || writeCharacteristic == null) {
                runOnUiThread(() -> Toast.makeText(context, "BLE not connected", Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                byte[] data = jsonPayload.getBytes(StandardCharsets.UTF_8);
                writeCharacteristic.setValue(data);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                boolean ok = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                runOnUiThread(() -> Toast.makeText(context, ok ? "BLE send ok" : "BLE send failed", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(context, "BLE send error", Toast.LENGTH_SHORT).show());
            }
        }

        private boolean ensureBleReady() {
            if (bluetoothAdapter == null) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "BLE not supported", Toast.LENGTH_SHORT).show());
                return false;
            }
            if (!bluetoothAdapter.isEnabled()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Enable Bluetooth", Toast.LENGTH_SHORT).show());
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                            BLE_PERMISSION_REQUEST_CODE);
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean bindService(@NonNull Intent service, @NonNull ServiceConnection conn, int flags) {
            return false;
        }

        @Override
        public int checkCallingOrSelfPermission(@NonNull String permission) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkCallingPermission(@NonNull String permission) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkCallingUriPermission(Uri uri, int modeFlags) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkPermission(@NonNull String permission, int pid, int uid) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkSelfPermission(@NonNull String permission) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkUriPermission(@Nullable Uri uri, @Nullable String readPermission, @Nullable String writePermission, int pid, int uid, int modeFlags) {
            return PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public void clearWallpaper() throws IOException {

        }

        @Override
        public Context createConfigurationContext(@NonNull Configuration overrideConfiguration) {
            return null;
        }

        @Override
        public Context createContextForSplit(String splitName) throws PackageManager.NameNotFoundException {
            return null;
        }

        @Override
        public Context createDeviceProtectedStorageContext() {
            return null;
        }

        @Override
        public Context createDisplayContext(@NonNull Display display) {
            return null;
        }

        @Override
        public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
            return null;
        }

        @Override
        public String[] databaseList() {
            return new String[0];
        }

        @Override
        public boolean deleteDatabase(String name) {
            return false;
        }

        @Override
        public boolean deleteFile(String name) {
            return false;
        }

        @Override
        public boolean deleteSharedPreferences(String name) {
            return false;
        }

        @Override
        public void enforceCallingOrSelfPermission(@NonNull String permission, @Nullable String message) {

        }

        @Override
        public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message) {

        }

        @Override
        public void enforceCallingPermission(@NonNull String permission, @Nullable String message) {

        }

        @Override
        public void enforceCallingUriPermission(Uri uri, int modeFlags, String message) {

        }

        @Override
        public void enforcePermission(@NonNull String permission, int pid, int uid, @Nullable String message) {

        }

        @Override
        public void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message) {

        }

        @Override
        public void enforceUriPermission(@Nullable Uri uri, @Nullable String readPermission, @Nullable String writePermission, int pid, int uid, int modeFlags, @Nullable String message) {

        }

        @Override
        public String[] fileList() {
            return new String[0];
        }

        @Override
        public Context getApplicationContext() {
            return null;
        }

        @Override
        public ApplicationInfo getApplicationInfo() {
            return null;
        }

        @Override
        public AssetManager getAssets() {
            return null;
        }

        @Override
        public File getCacheDir() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }

        @Override
        public File getCodeCacheDir() {
            return null;
        }

        @Override
        public ContentResolver getContentResolver() {
            return null;
        }

        @Override
        public File getDataDir() {
            return null;
        }

        @Override
        public File getDatabasePath(String name) {
            return null;
        }

        @Override
        public File getDir(String name, int mode) {
            return null;
        }

        @Nullable
        @Override
        public File getExternalCacheDir() {
            return null;
        }

        @Override
        public File[] getExternalCacheDirs() {
            return new File[0];
        }

        @Nullable
        @Override
        public File getExternalFilesDir(@Nullable String type) {
            return null;
        }

        @Override
        public File[] getExternalFilesDirs(String type) {
            return new File[0];
        }

        @Override
        public File[] getExternalMediaDirs() {
            return new File[0];
        }

        @Override
        public File getFileStreamPath(String name) {
            return null;
        }

        @Override
        public File getFilesDir() {
            return null;
        }

        @Override
        public Looper getMainLooper() {
            return null;
        }

        @Override
        public File getNoBackupFilesDir() {
            return null;
        }

        @Override
        public File getObbDir() {
            return null;
        }

        @Override
        public File[] getObbDirs() {
            return new File[0];
        }

        @Override
        public String getPackageCodePath() {
            return "";
        }

        @Override
        public PackageManager getPackageManager() {
            return null;
        }

        @Override
        public String getPackageName() {
            return "";
        }

        @Override
        public String getPackageResourcePath() {
            return "";
        }

        @Override
        public Resources getResources() {
            return null;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return null;
        }

        @Override
        public Object getSystemService(@NonNull String name) {
            return null;
        }

        @Nullable
        @Override
        public String getSystemServiceName(@NonNull Class<?> serviceClass) {
            return "";
        }

        @Override
        public Resources.Theme getTheme() {
            return null;
        }

        @Override
        public Drawable getWallpaper() {
            return null;
        }

        @Override
        public int getWallpaperDesiredMinimumHeight() {
            return 0;
        }

        @Override
        public int getWallpaperDesiredMinimumWidth() {
            return 0;
        }

        @Override
        public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {

        }

        @Override
        public boolean isDeviceProtectedStorage() {
            return false;
        }

        @Override
        public boolean moveDatabaseFrom(Context sourceContext, String name) {
            return false;
        }

        @Override
        public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
            return false;
        }

        @Override
        public FileInputStream openFileInput(String name) throws FileNotFoundException {
            return null;
        }

        @Override
        public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
            return null;
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return null;
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, @Nullable DatabaseErrorHandler errorHandler) {
            return null;
        }

        @Override
        public Drawable peekWallpaper() {
            return null;
        }

        @Nullable
        @Override
        public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter) {
            return null;
        }

        @Nullable
        @Override
        public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter, int flags) {
            return null;
        }

        @Nullable
        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, @Nullable String broadcastPermission, @Nullable Handler scheduler) {
            return null;
        }

        @Nullable
        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, @Nullable String broadcastPermission, @Nullable Handler scheduler, int flags) {
            return null;
        }

        @Override
        public void removeStickyBroadcast(Intent intent) {

        }

        @Override
        public void removeStickyBroadcastAsUser(Intent intent, UserHandle user) {

        }

        @Override
        public void revokeUriPermission(Uri uri, int modeFlags) {

        }

        @Override
        public void revokeUriPermission(String toPackage, Uri uri, int modeFlags) {

        }

        @Override
        public void sendBroadcast(Intent intent) {

        }

        @Override
        public void sendBroadcast(Intent intent, @Nullable String receiverPermission) {

        }

        @Override
        public void sendBroadcastAsUser(Intent intent, UserHandle user) {

        }

        @Override
        public void sendBroadcastAsUser(Intent intent, UserHandle user, @Nullable String receiverPermission) {

        }

        @Override
        public void sendOrderedBroadcast(Intent intent, @Nullable String receiverPermission) {

        }

        @Override
        public void sendOrderedBroadcast(@NonNull Intent intent, @Nullable String receiverPermission, @Nullable BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

        }

        @Override
        public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, @Nullable String receiverPermission, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

        }

        @Override
        public void sendStickyBroadcast(Intent intent) {

        }

        @Override
        public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {

        }

        @Override
        public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

        }

        @Override
        public void sendStickyOrderedBroadcastAsUser(Intent intent, UserHandle user, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

        }

        @Override
        public void setTheme(int resid) {

        }

        @Override
        public void setWallpaper(Bitmap bitmap) throws IOException {

        }

        @Override
        public void setWallpaper(InputStream data) throws IOException {

        }

        @Override
        public void startActivities(Intent[] intents) {

        }

        @Override
        public void startActivities(Intent[] intents, Bundle options) {

        }

        @Override
        public void startActivity(Intent intent) {

        }

        @Override
        public void startActivity(Intent intent, @Nullable Bundle options) {

        }

        @Nullable
        @Override
        public ComponentName startForegroundService(Intent service) {
            return null;
        }

        @Override
        public boolean startInstrumentation(@NonNull ComponentName className, @Nullable String profileFile, @Nullable Bundle arguments) {
            return false;
        }

        @Override
        public void startIntentSender(IntentSender intent, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) throws IntentSender.SendIntentException {

        }

        @Override
        public void startIntentSender(IntentSender intent, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, @Nullable Bundle options) throws IntentSender.SendIntentException {

        }

        @Nullable
        @Override
        public ComponentName startService(Intent service) {
            return null;
        }

        @Override
        public boolean stopService(Intent service) {
            return false;
        }

        @Override
        public void unbindService(@NonNull ServiceConnection conn) {

        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {

        }
    }

    // ===== GATT callback =====
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "BLE connected", Toast.LENGTH_SHORT).show());
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                gatt.discoverServices();
            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "BLE disconnected", Toast.LENGTH_SHORT).show());
                writeCharacteristic = null;
                notifyCharacteristic = null;
            }
        }

        @Override public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            BluetoothGattService svc = gatt.getService(SERVICE_UUID);
            if (svc != null) {
                writeCharacteristic = svc.getCharacteristic(CHAR_WRITE_UUID);
                notifyCharacteristic = svc.getCharacteristic(CHAR_NOTIFY_UUID);
                if (notifyCharacteristic != null) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    gatt.setCharacteristicNotification(notifyCharacteristic, true);
                    BluetoothGattDescriptor ccc = notifyCharacteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // CCCD
                    if (ccc != null) {
                        ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        gatt.writeDescriptor(ccc);
                    }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "BLE service ready", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Service UUID not found", Toast.LENGTH_SHORT).show());
            }
        }

        @Override public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (CHAR_NOTIFY_UUID.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                final String json = new String(value, StandardCharsets.UTF_8);
                // Call JS: window.onBlePayload(jsonString)
                runOnUiThread(() -> {
                    String esc = json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
                    webView.evaluateJavascript("window.onBlePayload('" + esc + "')", null);
                });
            }
        }
    };

    // ===== Connectivity monitoring =====
    private void setupInternetMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Internet connected", Toast.LENGTH_SHORT).show());
            }
            @Override public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showInternetDialog());
            }
        };
        connectivityManager.registerNetworkCallback(nr, networkCallback);
    }

    private void showInternetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Internet Connection Lost")
                .setMessage("It seems like your internet connection is off. Would you like to turn it on?")
                .setPositiveButton("Go to Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)))
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setCancelable(false)
                .show();
    }

    // ===== Location flow =====
    private void checkLocationSettings() {
        LocationRequest lr = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000).build();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(lr);
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> getLocationUpdates());
        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try { ((ResolvableApiException) e).startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS); }
                catch (Exception ignored) { }
            } else {
                Toast.makeText(MainActivity.this, "Location services unavailable", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getLocationUpdates() {
        LocationRequest lr = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000).build();

        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult locationResult) {
                Location loc = locationResult.getLastLocation();
                if (loc != null) updateMapLocation(loc.getLatitude(), loc.getLongitude());
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(lr, locationCallback, null);
    }

    private void updateMapLocation(double latitude, double longitude) {
        webView.evaluateJavascript("updateLocation(" + latitude + "," + longitude + ")", null);
    }

    // ===== Cleanup =====
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
        if (locationSettingsHandler != null) {
            locationSettingsHandler.removeCallbacks(locationSettingsRunnable);
        }
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        if (bleScanner != null && scanCallback != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bleScanner.stopScan(scanCallback);
        }
    }

    // ===== Permission results =====
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = true;
        for (int r : grantResults) granted &= (r == PackageManager.PERMISSION_GRANTED);

        if ((requestCode == LOCATION_PERMISSION_REQUEST_CODE || requestCode == BLE_PERMISSION_REQUEST_CODE) && granted) {
            checkLocationSettings();
        } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}
