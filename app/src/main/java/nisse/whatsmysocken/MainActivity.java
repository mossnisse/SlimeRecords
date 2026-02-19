package nisse.whatsmysocken;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import nisse.whatsmysocken.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private FusedLocationProviderClient fusedClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private LocationViewModel viewModel;
    private ActivityMainBinding binding;
    private boolean isSearching = false; // Fixed: Added missing variable
    private boolean wasSearchingBeforePause = false;

    // Menu
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
        } else if (id == R.id.action_import) {
            startActivity(new Intent(this, ImportActivity.class));
        } else if (id == R.id.action_print) {
            startActivity(new Intent(this, PrintActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    // permissions and settings
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkLocationSettings();
                } else {
                    if (binding != null) { binding.ctextview.setText("Permission denied. Cannot search.");}
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    startLocationUpdates();
                } else {
                    binding.ctextview.setText("Location services must be enabled.");
                }
            });

    private void handlePermissionRationale() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Needed")
                    .setMessage("This app needs location access to find your 'socken' and coordinates.")
                    .setPositiveButton("OK", (d, id) ->
                            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    )
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // Direct request
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void checkLocationSettings() {
        LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();

        LocationServices.getSettingsClient(this)
                .checkLocationSettings(request)
                .addOnSuccessListener(this, response -> {
                    // GPS is already ON
                    startLocationUpdates();
                })
                .addOnFailureListener(this, e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            // GPS is OFF, show the system dialog to turn it on
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            IntentSenderRequest isr = new IntentSenderRequest.Builder(resolvable.getResolution()).build();
                            settingsLauncher.launch(isr);
                        } catch (Exception sendEx) {
                            binding.ctextview.setText("Error opening location settings.");
                        }
                    } else {
                        binding.ctextview.setText("Location settings are not satisfied on this device.");
                    }
                });
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // No more findViewById! Access views via binding.
        setSupportActionBar(binding.myToolbar);

        binding.checkButton.setOnClickListener(v -> {
            if (isSearching) stopLocationUpdates(true);
            else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    checkLocationSettings();
                } else {
                    handlePermissionRationale();
                }
            }
        });

        setupLocationLogic();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isSearching) {
            // SET THIS FIRST so onResume knows to restart
            wasSearchingBeforePause = true;
            stopLocationUpdates(false);
            Toast.makeText(this, "GPS search paused for battery safety", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If we were searching when the user left, start again automatically
        if (wasSearchingBeforePause) {
            // Check permission again (standard practice as user could have revoked it)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
            wasSearchingBeforePause = false; // Reset the flag
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        isSearching = true;
        //viewModel.setCurrentBestLocation(null);

        // Concise view updates
        binding.checkButton.setText("STOP");
        binding.ctextview.setText("Acquiring GPS signal...");

        fusedClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    private void setupLocationLogic() {
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;

                Location bestSoFar = viewModel.getCurrentBestLocation();

                // Check if this is our first location OR if the new one is more accurate
                if (bestSoFar == null || location.getAccuracy() < bestSoFar.getAccuracy()) {
                    viewModel.setCurrentBestLocation(location); // This is now thread-safe

                    int acc = (int) Math.ceil(location.getAccuracy());
                    binding.ctextview.setText("Accuracy: " + acc + "m\nPress STOP when precision is sufficient.");
                }
            }
        };
    }

    private void stopLocationUpdates(boolean shouldTransition) {
        fusedClient.removeLocationUpdates(locationCallback);
        isSearching = false;

        // This resets the button enabled state and default text
        binding.checkButton.setEnabled(true);
        if (!isSearching) {
            binding.ctextview.setText("Ready to search.");
            binding.checkButton.setText("Find Location");
        }

        Location best = viewModel.getCurrentBestLocation();

        if (shouldTransition && best != null) {
            Intent intent = new Intent(this, LocationDetailActivity.class);
            intent.putExtra("lat", best.getLatitude());
            intent.putExtra("lon", best.getLongitude());
            intent.putExtra("acc", best.getAccuracy());
            intent.putExtra("altitude", best.getAltitude());
            intent.putExtra("is_new", true);
            startActivity(intent);
        } else if (shouldTransition) { // shouldTransition is true but best is null
            binding.ctextview.setText("No location acquired. Try again.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}