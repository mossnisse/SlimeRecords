package nisse.whatsmysocken;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.location.Location;
import android.location.LocationManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private LocationManager locationManager;
    private static final int PERMISSION_ID = 42;
    private boolean isSearching = false;
    private boolean haveFoundLocation = false;
    private Location currentBestLocation;
    private Button button;
    private TextView ctextview;
    private EditText noteInput;
    private Button saveButton;
    private Button btnTakePhoto;
    private double lastLat, lastLon; // To "remember" the location when Save is clicked
    private String currentPhotoPath;
    private List<String> tempPhotoPaths = new ArrayList<>();

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
        }

        if (id == R.id.saved_locations) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startLocationSearch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
            return;
        }
        isSearching = true;
        button.setText("STOP");
        ctextview.setText("Waiting for satellites...");
        noteInput.setText("");
        saveButton.setVisibility(View.GONE);
        noteInput.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.GONE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, this);
    }

    private void stopLocationUpdates() {
        locationManager.removeUpdates(this);
        isSearching = false;
        button.setText("Find Location");
        ctextview.setText("");
        if (haveFoundLocation) {
            getTheLocation();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.check_button);
        ctextview = findViewById(R.id.ctextview);
        noteInput = findViewById(R.id.note_input);
        saveButton = findViewById(R.id.save_button);
        btnTakePhoto = findViewById(R.id.btn_take_photo);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        androidx.appcompat.widget.Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isSearching) {
                    stopLocationUpdates();
                } else {
                    startLocationSearch();
                }
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String note = noteInput.getText().toString();
                saveData(lastLat, lastLon, note);

                // Clear the input and disable button after saving
                noteInput.setText("");
                saveButton.setVisibility(View.GONE);
                noteInput.setVisibility(View.GONE);

                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(intent);
            }
        });

        btnTakePhoto.setOnClickListener(v -> {
            dispatchTakePictureIntent();
        });
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Location Permission Needed");
                builder.setMessage("This app needs Location permission, to show your location is the idea behind the program");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        requestLocationPermission();
                    }
                });
                builder.create();
                builder.show();
            } else {
                requestLocationPermission();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted! Tell the user to press the button again
                TextView ctextview = findViewById(R.id.ctextview);
                ctextview.setText("Permission granted! Press the button again.");
            }
        }
    }
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ID);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentBestLocation = location; // Save this!
        int acc = (int) Math.ceil(location.getAccuracy());
        ctextview.setText("Accuracy: " + acc + "m");
        haveFoundLocation = true;
    }

    public void getTheLocation() {
        locationManager.removeUpdates(this);
        Location location = currentBestLocation;

        if (location == null) {
            ctextview.setText("Location not found yet. Wait for better accuracy.");
            return;
        }

        // Get user preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showWgs84 = prefs.getBoolean("show_wgs84", true);
        boolean showRt90 = prefs.getBoolean("show_rt90", true);
        boolean showSweref = prefs.getBoolean("show_sweref", false);
        boolean showRubin = prefs.getBoolean("show_rubin", true);
        boolean showDate = prefs.getBoolean("show_date", true);

        double lat = location.getLatitude();
        double lon = location.getLongitude();
        StringBuilder sb = new StringBuilder();
        sb.append("Accuracy: ").append((int) Math.ceil(location.getAccuracy())).append("m\n");

        // Conditionally add coordinates
        if (showWgs84) {
            DecimalFormat dc = new DecimalFormat("0.00000");
            sb.append("WGS84: ").append(dc.format(lat)).append(", ").append(dc.format(lon)).append("\n");
        }

        Coordinates here = new Coordinates(lat, lon);

        if (showRt90) {
            Coordinates rt90 = here.convertToRT90FromWGS84();
            sb.append("RT90: ").append((int)Math.round(rt90.getNorth()))
                    .append(", ").append((int)Math.round(rt90.getEast())).append("\n");
        }

        if (showSweref) {
            Coordinates sweref = here.convertToSweref99TMFromWGS84();
            sb.append("SWEREF99tm: ").append((int)Math.round(sweref.getNorth()))
                    .append(", ").append((int)Math.round(sweref.getEast())).append("\n");
        }

        if (showRubin) {
            Coordinates rt90ForRubin = here.convertToRT90FromWGS84();
            sb.append("RUBIN: ").append(rt90ForRubin.getRUBINfromRT90()).append("\n");
        }

        if (showDate) {
            LocalDate date = LocalDate.now();
            sb.append("Date: ").append(date.toString()).append("\n");
        }

        ctextview.setText(sb.toString());

        this.lastLat = lat;
        this.lastLon = lon;

        saveButton.setVisibility(View.VISIBLE);
        noteInput.setVisibility(View.VISIBLE);
        btnTakePhoto.setVisibility(View.VISIBLE);
        haveFoundLocation = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isSearching) {
            stopLocationUpdates();
        }
    }

    private void saveData(double lat, double lon, String note) {
        // Capture the context and the data safely before entering the thread
        Context appContext = getApplicationContext();
        List<String> pathsToSave = new ArrayList<>(tempPhotoPaths);
        tempPhotoPaths.clear();

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);

                // Save the location
                LocationRecord record = new LocationRecord(lat, lon, System.currentTimeMillis(), note);
                long newLocationId = db.locationDao().insertLocation(record);

                // Save photos if there are any
                if (!pathsToSave.isEmpty()) {
                    for (String path : pathsToSave) {
                        db.locationDao().insertPhoto(new PhotoRecord(newLocationId, path));
                    }
                }

                // Toast on UI thread
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(appContext, "Saved successfully!", android.widget.Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace(); // This prints the error to your Logcat!
            }
        }).start();
    }

    // Initialize the launcher
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success) {
                    // Add the path to our list so we can save it later
                    tempPhotoPaths.add(currentPhotoPath);

                    // Optional: Show a toast or update UI to show 1 photo added
                    android.widget.Toast.makeText(this, "Photo added!", android.widget.Toast.LENGTH_SHORT).show();
                    btnTakePhoto.setText("Take Photo (" + tempPhotoPaths.size() + ")");
                }
            });

    // Function to trigger the camera
    private void dispatchTakePictureIntent() {
        File photoFile = null;
        try {
            photoFile = createImageFile(); // Helper to create a .jpg in app storage
        } catch (IOException ex) {
            // Error occurred while creating the File
        }

        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this,
                    "nisse.whatsmysocken.fileprovider",
                    photoFile);
            currentPhotoPath = photoFile.getAbsolutePath();
            takePictureLauncher.launch(photoURI);
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }
}

