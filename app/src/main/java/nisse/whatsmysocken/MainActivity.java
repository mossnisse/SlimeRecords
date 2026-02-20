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
    private ActivityMainBinding binding;
    private SearchViewModel viewModel;
    private FusedLocationProviderClient fusedClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    // --- Permission Launchers ---
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkLocationSettings();
                } else {
                    binding.ctextview.setText("Permission denied. Cannot search.");
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

    // Activity Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        setSupportActionBar(binding.myToolbar);

        // setupLocationLogic
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;

                Location best = viewModel.getCurrentBestLocation();
                if (best == null || location.getAccuracy() < best.getAccuracy()) {
                    viewModel.setCurrentBestLocation(location);

                    int acc = (int) Math.ceil(location.getAccuracy());
                    binding.ctextview.setText("Accuracy: " + acc + "m\nPress STOP when precision is sufficient.");
                }
            }
        };

        // setupObservers
        viewModel.getUserWantsSearching().observe(this, wantsSearching -> {
            if (wantsSearching) {
                binding.checkButton.setText("STOP");
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    checkLocationSettings();
                } else {
                    handlePermissionRationale();   // <-- NOW IT GETS CALLED
                }
            } else {
                binding.checkButton.setText("Find Location");
                stopLocationUpdates(false);
            }

        });
        // setupButton
        binding.checkButton.setOnClickListener(v -> {
            Boolean current = viewModel.getUserWantsSearching().getValue();
            boolean isStarting = (current == null || !current);

            if (isStarting) {
                viewModel.setCurrentBestLocation(null); // Clear old results for the new search
                binding.ctextview.setText("Initializing GPS...");
            } else {
                stopLocationUpdates(true);
            }
            viewModel.setUserWantsSearching(isStarting);
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Pause GPS but do NOT change user intent
        fusedClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Boolean wants = viewModel.getUserWantsSearching().getValue();
        if (wants != null && wants) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        binding.ctextview.setText("Acquiring GPS signal...");
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    private void stopLocationUpdates(boolean shouldTransition) {
        fusedClient.removeLocationUpdates(locationCallback);
        binding.ctextview.setText("Ready to search.");

        Location best = viewModel.getCurrentBestLocation();
        if (shouldTransition && best != null) {
            Intent intent = new Intent(this, LocationDetailActivity.class);
            intent.putExtra("lat", best.getLatitude());
            intent.putExtra("lon", best.getLongitude());
            intent.putExtra("acc", best.getAccuracy());
            intent.putExtra("altitude", best.getAltitude());
            intent.putExtra("is_new", true);
            startActivity(intent);
        } else if (shouldTransition) {
            binding.ctextview.setText("No location acquired. Try again.");
        }
    }

    // Permissions & Settings
    private void handlePermissionRationale() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Needed")
                    .setMessage("This app needs location access to find your 'socken' and coordinates.")
                    .setPositiveButton("OK", (d, id) ->
                            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void checkLocationSettings() {
        LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();

        LocationServices.getSettingsClient(this)
                .checkLocationSettings(request)
                .addOnSuccessListener(this, response -> startLocationUpdates())
                .addOnFailureListener(this, e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            IntentSenderRequest isr =
                                    new IntentSenderRequest.Builder(((ResolvableApiException) e).getResolution()).build();
                            settingsLauncher.launch(isr);
                        } catch (Exception ignored) {
                            binding.ctextview.setText("Error opening location settings.");
                        }
                    } else {
                        binding.ctextview.setText("Location settings are not satisfied on this device.");
                    }
                });
    }

    // Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) startActivity(new Intent(this, SettingsActivity.class));
        else if (id == R.id.saved_locations) startActivity(new Intent(this, HistoryActivity.class));
        else if (id == R.id.action_export) startActivity(new Intent(this, ExportActivity.class));
        else if (id == R.id.action_import) startActivity(new Intent(this, ImportActivity.class));
        else if (id == R.id.action_print) startActivity(new Intent(this, PrintActivity.class));
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
