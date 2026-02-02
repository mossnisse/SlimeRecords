package nisse.whatsmysocken;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LocationDetailActivity extends AppCompatActivity {
    private double lat, lon;
    //private float accuracy;
    private boolean isNew;
    private EditText noteInput;
    private String currentPhotoPath;
    private List<String> tempPhotoPaths = new ArrayList<>();
    private PhotoAdapter photoAdapter;
    private RecyclerView rvGallery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_detail);

        // Initialize Views
        noteInput = findViewById(R.id.detail_note_input);
        Button btnSave = findViewById(R.id.btn_save_detail);
        Button btnCancel = findViewById(R.id.btn_cancel_detail);
        Button btnPhoto = findViewById(R.id.btn_take_photo_detail);

        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        isNew = getIntent().getBooleanExtra("is_new", false);

        if (!isNew) {
            btnSave.setVisibility(View.GONE);
            btnPhoto.setVisibility(View.GONE);
            btnCancel.setText("Back");

            String existingNote = getIntent().getStringExtra("note");
            noteInput.setText(existingNote);
            noteInput.setEnabled(false);
        }

        displayFormattedCoordinates();
        // Use the variables instead of finding them again
        btnSave.setOnClickListener(v -> saveAndExit());
        btnCancel.setOnClickListener(v -> finish());
        btnPhoto.setOnClickListener(v -> dispatchTakePictureIntent());

        rvGallery = findViewById(R.id.rv_photo_gallery);

        // Set up horizontal layout
        rvGallery.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        if (!isNew) {
            // Mode: VIEWING
            tempPhotoPaths = getIntent().getStringArrayListExtra("photo_paths");
            if (tempPhotoPaths == null) tempPhotoPaths = new ArrayList<>();
        }

        // Initialize adapter with our list (either empty or passed from history)
        photoAdapter = new PhotoAdapter(tempPhotoPaths, new PhotoAdapter.OnPhotoListener() {
            @Override
            public void onPhotoClick(String path) {
                Intent intent = new Intent(LocationDetailActivity.this, FullScreenPhotoActivity.class);
                intent.putExtra("path", path);
                startActivity(intent);
            }

            @Override
            public void onPhotoLongClick(int position) {
                String pathToDelete = tempPhotoPaths.get(position);

                new AlertDialog.Builder(LocationDetailActivity.this)
                        .setTitle("Remove Photo")
                        .setMessage("Do you want to remove this photo from this location?")
                        .setPositiveButton("Remove", (dialog, which) -> {

                            // 1. Update the UI list immediately
                            tempPhotoPaths.remove(position);
                            photoAdapter.notifyItemRemoved(position);

                            // 2. If it's a saved location, delete from Database
                            if (!isNew) {
                                new Thread(() -> {
                                    AppDatabase.getInstance(getApplicationContext())
                                            .locationDao().deletePhotoByPath(pathToDelete);
                                }).start();
                            }

                            // 3. Update the button text if it's visible
                            Button btnPhoto = findViewById(R.id.btn_take_photo_detail);
                            if (btnPhoto.getVisibility() == View.VISIBLE) {
                                btnPhoto.setText("Add Photo (" + tempPhotoPaths.size() + ")");
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        rvGallery.setAdapter(photoAdapter);
    }

    private void saveAndExit() {
        String note = noteInput.getText().toString();
        float accuracy = getIntent().getFloatExtra("acc", 0);
        // Get raw Unix time
        long unixTime = System.currentTimeMillis();
        // Get formatted Local Time
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String localTimeStr = now.format(formatter);

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            LocationRecord record = new LocationRecord(lat, lon, unixTime, accuracy, localTimeStr, note);
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
                    tempPhotoPaths.add(currentPhotoPath);
                    photoAdapter.notifyItemInserted(tempPhotoPaths.size() - 1);
                    rvGallery.scrollToPosition(tempPhotoPaths.size() - 1);

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
