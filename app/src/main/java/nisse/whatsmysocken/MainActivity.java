package nisse.whatsmysocken;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity {
    private FusedLocationProviderClient fusedClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private boolean isSearching = false;
    private Location currentBestLocation;
    private Button button;
    private TextView ctextview;

    // --- Modern Result Launchers ---

    // Handles the Location Permission request
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkLocationSettings();
                } else {
                    ctextview.setText("Permission denied. Cannot search for location.");
                }
            });

    // Handles the "Turn on GPS" system dialog result
    private final ActivityResultLauncher<IntentSenderRequest> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    startLocationUpdates();
                } else {
                    ctextview.setText("Location services are required for high accuracy.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .setMinUpdateDistanceMeters(1)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;

                if (currentBestLocation == null || location.getAccuracy() < currentBestLocation.getAccuracy()) {
                    currentBestLocation = location;
                    int acc = (int) Math.ceil(location.getAccuracy());
                    ctextview.setText("Accuracy: " + acc + "m\nKeep waiting for better precision or press STOP to save.");
                }
            }
        };

        button = findViewById(R.id.check_button);
        ctextview = findViewById(R.id.ctextview);
        setSupportActionBar(findViewById(R.id.my_toolbar));

        button.setOnClickListener(view -> {
            if (isSearching) stopLocationUpdates(true);
            else startLocationSearch();
        });
    }

    private void startLocationSearch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            checkLocationSettings();
        } else {
            handlePermissionRationale();
        }
    }

    private void handlePermissionRationale() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Needed")
                    .setMessage("This app needs location access to find your 'socken'.")
                    .setPositiveButton("OK", (d, id) -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
                    .show();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void checkLocationSettings() {
        LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest).build();

        Task<com.google.android.gms.location.LocationSettingsResponse> task =
                LocationServices.getSettingsClient(this).checkLocationSettings(request);

        task.addOnSuccessListener(this, response -> startLocationUpdates());

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    IntentSenderRequest isr = new IntentSenderRequest.Builder(resolvable.getResolution()).build();
                    settingsLauncher.launch(isr);
                } catch (Exception sendEx) {
                    // Ignore
                }
            }
        });
    }

    private void startLocationUpdates() {
        isSearching = true;
        currentBestLocation = null;
        button.setText("STOP");
        ctextview.setText("Waiting for satellites...");
        // API 29+ still requires the check, but we know it's granted by now
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
        }
    }

    private void stopLocationUpdates(boolean shouldTransition) {
        fusedClient.removeLocationUpdates(locationCallback);
        isSearching = false;
        button.setText("Find Location");

        if (shouldTransition && currentBestLocation != null) {
            Intent intent = new Intent(this, LocationDetailActivity.class);
            intent.putExtra("lat", currentBestLocation.getLatitude());
            intent.putExtra("lon", currentBestLocation.getLongitude());
            intent.putExtra("acc", currentBestLocation.getAccuracy());
            intent.putExtra("is_new", true);
            startActivity(intent);
        }
        ctextview.setText("");
    }

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
        } else if (id == R.id.saved_locations) {
            startActivity(new Intent(this, HistoryActivity.class));
        } else if (id == R.id.action_export) {
            startActivity(new Intent(this, ExportActivity.class));
        }
        return true;
    }
}