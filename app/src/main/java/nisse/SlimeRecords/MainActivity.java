package nisse.SlimeRecords;

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
import androidx.preference.PreferenceManager;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import nisse.SlimeRecords.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    /**
     * The location flow is always in exactly one of these states, so a single
     * field replaces flags that would otherwise have to be kept in sync from
     * every exit path.
     */
    private enum LocationFlow { IDLE, CHECKING_SETTINGS, UPDATES_ACTIVE }

    private ActivityMainBinding binding;
    private SearchViewModel viewModel;
    private FusedLocationProviderClient fusedClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private LocationFlow locationFlow = LocationFlow.IDLE;

    // --- Permission Launchers ---
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkLocationSettings();
                } else {
                    abortLocationSearch("Permission denied. Cannot search.");
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    startLocationUpdates();
                } else {
                    abortLocationSearch("Location services must be enabled.");
                }
            });

    // Activity Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        setSupportActionBar(binding.myToolbar);

        // setupLocationLogic
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdateAgeMillis(0)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location newLocation = result.getLastLocation();
                if (newLocation == null) return;

                Location best = viewModel.getCurrentBestLocation();

                float distanceFromBest = best == null ? 0 : best.distanceTo(newLocation);
                // Order fixes by the monotonic elapsed-realtime clock: getTime()
                // is wall-clock and the fused provider mixes GPS- and device-
                // sourced timestamps, which can appear to run backwards.
                if (best == null || LocationSelection.shouldReplace(
                        best.getAccuracy(), best.getElapsedRealtimeNanos() / 1_000_000L,
                        newLocation.getAccuracy(), newLocation.getElapsedRealtimeNanos() / 1_000_000L,
                        distanceFromBest)) {
                    viewModel.setCurrentBestLocation(newLocation);

                    int acc = (int) Math.ceil(newLocation.getAccuracy());
                    binding.tvStatus.setText("Accuracy: " + acc + "m\nPress STOP when precision is sufficient.");
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
                binding.tvStatus.setText("Initializing GPS...");
                viewModel.setUserWantsSearching(true);
            } else {
                // Flip the intent first: its observer resets the status text, so the
                // outcome message from stopLocationUpdates(true) must come after it.
                viewModel.setUserWantsSearching(false);
                stopLocationUpdates(true);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Pause GPS but do NOT change user intent
        fusedClient.removeLocationUpdates(locationCallback);
        locationFlow = LocationFlow.IDLE;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Boolean wants = viewModel.getUserWantsSearching().getValue();
        if (wants != null && wants) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                checkLocationSettings();
            }
        }
    }

    private boolean userWantsSearch() {
        return Boolean.TRUE.equals(viewModel.getUserWantsSearching().getValue());
    }

    private void startLocationUpdates() {
        // The user-intent check matters because this is reached from async
        // callbacks (settings check, resolution dialog) that may resolve after
        // the user has already pressed STOP.
        if (!userWantsSearch()
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                || locationFlow == LocationFlow.UPDATES_ACTIVE) return;

        binding.tvStatus.setText("Acquiring GPS signal...");
        locationFlow = LocationFlow.UPDATES_ACTIVE;
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper())
                .addOnFailureListener(this, e -> abortLocationSearch("Could not start location updates."));
    }

    private void stopLocationUpdates(boolean shouldTransition) {
        fusedClient.removeLocationUpdates(locationCallback);
        locationFlow = LocationFlow.IDLE;
        binding.tvStatus.setText("Ready to search.");

        Location best = viewModel.getCurrentBestLocation();
        if (shouldTransition && best != null) {
            Intent intent = new Intent(this, RecordDetailActivity.class);
            intent.putExtra("lat", best.getLatitude());
            intent.putExtra("lon", best.getLongitude());
            intent.putExtra("acc", best.getAccuracy());
            intent.putExtra("has_altitude", best.hasAltitude());
            if (best.hasAltitude()) intent.putExtra("altitude", best.getAltitude());
            intent.putExtra("is_new", true);
            startActivity(intent);
        } else if (shouldTransition) {
            binding.tvStatus.setText("No location acquired. Try again.");
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
                    .setNegativeButton("Cancel", (d, id) ->
                            abortLocationSearch("Location permission is required to search."))
                    .setOnCancelListener(d ->
                            abortLocationSearch("Location permission is required to search."))
                    .show();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void checkLocationSettings() {
        if (locationFlow != LocationFlow.IDLE) return;
        locationFlow = LocationFlow.CHECKING_SETTINGS;

        LocationSettingsRequest request = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();

        LocationServices.getSettingsClient(this)
                .checkLocationSettings(request)
                .addOnSuccessListener(this, response -> {
                    locationFlow = LocationFlow.IDLE;
                    startLocationUpdates();
                })
                .addOnFailureListener(this, e -> {
                    locationFlow = LocationFlow.IDLE;
                    // The user may have pressed STOP while the check was in
                    // flight; don't surface a dialog they no longer asked for.
                    if (!userWantsSearch()) return;
                    if (e instanceof ResolvableApiException) {
                        try {
                            IntentSenderRequest isr =
                                    new IntentSenderRequest.Builder(((ResolvableApiException) e).getResolution()).build();
                            settingsLauncher.launch(isr);
                        } catch (Exception error) {
                            abortLocationSearch("Error opening location settings.");
                        }
                    } else {
                        abortLocationSearch("Location settings are not satisfied on this device.");
                    }
                });
    }

    private void abortLocationSearch(String message) {
        fusedClient.removeLocationUpdates(locationCallback);
        locationFlow = LocationFlow.IDLE;
        viewModel.setUserWantsSearching(false);
        // The observer above resets the generic status first; keep the useful
        // failure reason visible after the state has returned to idle.
        binding.tvStatus.setText(message);
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
