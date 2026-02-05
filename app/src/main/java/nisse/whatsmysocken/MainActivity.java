package nisse.whatsmysocken;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
//import android.location.LocationManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class MainActivity extends AppCompatActivity  {
    private LocationManager locationManager;
    private FusedLocationProviderClient fusedClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private static final int PERMISSION_ID = 42;
    private boolean isSearching = false;
    private Location currentBestLocation;
    private Button button;
    private TextView ctextview;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,     // desired interval
                1000                                 // interval in ms
        )
                .setMinUpdateIntervalMillis(500)             // fastest interval
                .setMinUpdateDistanceMeters(1)               // min distance
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;

                Location location = result.getLastLocation();
                if (location == null) return;

                // Keep the best accuracy
                if (currentBestLocation == null ||
                        location.getAccuracy() < currentBestLocation.getAccuracy()) {

                    currentBestLocation = location;
                    int acc = (int) Math.ceil(location.getAccuracy());
                    ctextview.setText("Accuracy: " + acc + "m\nKeep waiting for better precision or press STOP to save.");
                }
            }
        };

        // Initialize UI
        button = findViewById(R.id.check_button);
        ctextview = findViewById(R.id.ctextview);
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        button.setOnClickListener(view -> {
            if (isSearching) {
                stopLocationUpdates(true);
            } else {
                startLocationSearch();
            }
        });
    }

    private void startLocationSearch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
            return;
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setTitle("GPS Disabled")
                    .setMessage("Please enable GPS to get an more precise location.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        isSearching = true;
        currentBestLocation = null;
        button.setText("STOP");
        ctextview.setText("Waiting for satellites...");

        fusedClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    private void stopLocationUpdates(boolean shouldTransition) {
        fusedClient.removeLocationUpdates(locationCallback);
        isSearching = false;
        button.setText("Find Location");

        if (shouldTransition && currentBestLocation != null) {
            openDetailScreen();
        } else {
            ctextview.setText("");
        }
    }

    private void openDetailScreen() {
        Intent intent = new Intent(this, LocationDetailActivity.class);
        intent.putExtra("lat", currentBestLocation.getLatitude());
        intent.putExtra("lon", currentBestLocation.getLongitude());
        intent.putExtra("acc", currentBestLocation.getAccuracy());
        intent.putExtra("is_new", true);
        startActivity(intent);

        ctextview.setText(""); // Clear text after moving to next screen
    }

    private void checkLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs location access to show where you are.")
                    .setPositiveButton("OK", (d, id) -> requestLocationPermission())
                    .show();
        } else {
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_ID && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ctextview.setText("Permission granted! Press the button again.");
        }
    }

    // --- Lifecycle & Menu ---

    @Override
    protected void onPause() {
        super.onPause();
        if (isSearching) stopLocationUpdates(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.saved_locations) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}