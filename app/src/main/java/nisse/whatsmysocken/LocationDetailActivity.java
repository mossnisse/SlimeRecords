package nisse.whatsmysocken;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LocationDetailActivity extends AppCompatActivity {
    private double lat, lon;
    private long locationId = -1;
    private boolean isNew;
    private EditText noteInput;
    private String currentPhotoPath;
    private List<String> tempPhotoPaths = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_detail);

        noteInput = findViewById(R.id.detail_note_input);
        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        isNew = getIntent().getBooleanExtra("is_new", false);

        if (!isNew) {
            // Logic for viewing existing location would go here
            locationId = getIntent().getLongExtra("location_id", -1);
            noteInput.setText(getIntent().getStringExtra("note"));
        }

        displayFormattedCoordinates();

        findViewById(R.id.btn_save_detail).setOnClickListener(v -> saveAndExit());
        findViewById(R.id.btn_cancel_detail).setOnClickListener(v -> finish());
        findViewById(R.id.btn_take_photo_detail).setOnClickListener(v -> dispatchTakePictureIntent());
    }

    private void saveAndExit() {
        String note = noteInput.getText().toString();
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            LocationRecord record = new LocationRecord(lat, lon, System.currentTimeMillis(), note);
            long id = db.locationDao().insertLocation(record);

            for (String path : tempPhotoPaths) {
                db.locationDao().insertPhoto(new PhotoRecord(id, path));
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
                finish(); // Go back to previous screen
            });
        }).start();
    }

    private void displayFormattedCoordinates() {
        TextView tvCoords = findViewById(R.id.tv_detail_coords);
        float accuracy = getIntent().getFloatExtra("acc", 0);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showWgs84 = prefs.getBoolean("show_wgs84", true);
        boolean showRt90 = prefs.getBoolean("show_rt90", true);
        boolean showSweref = prefs.getBoolean("show_sweref", false);
        boolean showRubin = prefs.getBoolean("show_rubin", true);
        boolean showDate = prefs.getBoolean("show_date", true);

        StringBuilder sb = new StringBuilder();
        sb.append("Accuracy: ").append((int) Math.ceil(accuracy)).append("m\n");

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
            sb.append("Date: ").append(LocalDate.now().toString()).append("\n");
        }

        tvCoords.setText(sb.toString());
    }

    //Take photos code
    // The Launcher: This handles the result when the camera app closes
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success) {
                    // Add the path to our list for saving to the DB later
                    tempPhotoPaths.add(currentPhotoPath);

                    // Optional: Update the button text to show how many photos are "pending"
                    Button btnPhoto = findViewById(R.id.btn_take_photo_detail);
                    btnPhoto.setText("Add Photo (" + tempPhotoPaths.size() + ")");
                }
            });

    // The Dispatcher: This sets up the file and launches the camera
    private void dispatchTakePictureIntent() {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
        }

        if (photoFile != null) {
            // Use the same authority string as your Manifest
            Uri photoURI = FileProvider.getUriForFile(this,
                    "nisse.whatsmysocken.fileprovider",
                    photoFile);

            // We don't save to the list yet, only if the user actually takes the photo
            takePictureLauncher.launch(photoURI);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save the path to the class-level variable
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }
}
