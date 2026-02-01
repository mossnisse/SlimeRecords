package nisse.whatsmysocken;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
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

public class MainActivity extends AppCompatActivity implements LocationListener {
    private LocationManager locationManager;
    private static final int PERMISSION_ID = 42;
    private boolean isSearching = false;
    private Location currentBestLocation;
    private Button button;
    private TextView ctextview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
            return;
        }

        isSearching = true;
        currentBestLocation = null; // Clear previous results
        button.setText("STOP");
        ctextview.setText("Waiting for satellites...");
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, this);
    }

    private void stopLocationUpdates(boolean shouldTransition) {
        locationManager.removeUpdates(this);
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

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentBestLocation = location;
        int acc = (int) Math.ceil(location.getAccuracy());
        ctextview.setText("Accuracy: " + acc + "m\nKeep waiting for better precision or press STOP to save.");
    }

    // --- Permissions Logic ---

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
            startActivity(new Intent(this, MySettingsActivity.class));
            return true;
        } else if (id == R.id.saved_locations) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}