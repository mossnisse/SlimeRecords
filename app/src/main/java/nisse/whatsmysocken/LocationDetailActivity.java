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
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.AppDatabase;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;
import nisse.whatsmysocken.data.SpatialResolver;

public class LocationDetailActivity extends AppCompatActivity {
    private double lat, lon;
    private float accuracy;
    private boolean isNew, isSaved = false;
    private EditText noteInput;
    private String currentPhotoPath;
    private final List<String> tempPhotoPaths = new ArrayList<>();
    private PhotoAdapter photoAdapter;
    private LocationRecord currentRecord;
    private LocationViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_detail);

        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        initUI();

        isNew = getIntent().getBooleanExtra("is_new", false);
        if (isNew) {
            setupNewLocation();
        } else {
            setupExistingLocation();
        }

        // Single observer for both Save and Update completion
        viewModel.getSaveOperationFinished().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                Toast.makeText(this, isNew ? "Saved!" : "Updated!", Toast.LENGTH_SHORT).show();
                isSaved = true;
                finish();
            }
        });
    }

    private void initUI() {
        noteInput = findViewById(R.id.detail_note_input);
        findViewById(R.id.btn_save_detail).setOnClickListener(v -> onCommitClicked());
        findViewById(R.id.btn_cancel_detail).setOnClickListener(v -> finish());
        findViewById(R.id.btn_take_photo_detail).setOnClickListener(v -> dispatchTakePictureIntent());

        findViewById(R.id.btn_open_map).setOnClickListener(v -> openLantmaterietMap());

        Button btnMap = findViewById(R.id.btn_open_map);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Check if the setting is enabled (default to true)
        boolean showMapLink = prefs.getBoolean("show_map_link", true);

        if (showMapLink) {
            btnMap.setVisibility(View.VISIBLE);
            btnMap.setOnClickListener(v -> openLantmaterietMap());
        } else {
            btnMap.setVisibility(View.GONE);
        }

        RecyclerView rvGallery = findViewById(R.id.rv_photo_gallery);
        rvGallery.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        photoAdapter = new PhotoAdapter(tempPhotoPaths, new PhotoAdapter.OnPhotoListener() {
            @Override
            public void onPhotoClick(String path) {
                Intent intent = new Intent(LocationDetailActivity.this, FullScreenPhotoActivity.class);
                intent.putExtra("path", path);
                startActivity(intent);
            }

            @Override
            public void onPhotoLongClick(int position) {
                confirmPhotoDeletion(position);
            }
        });
        rvGallery.setAdapter(photoAdapter);
    }

    private void setupNewLocation() {
        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        accuracy = getIntent().getFloatExtra("acc", 0);

        // Initial display with "Loading..." text
        displayFormattedCoordinates("Loading...", "Loading...");

        // Wait for DB, then trigger lookup
        performSpatialLookup();
    }

    private void performSpatialLookup() {
        Coordinates here = new Coordinates(lat, lon);
        Coordinates sweref = here.convertToSweref99TMFromWGS84();
        int n = (int)Math.round(sweref.getNorth());
        int e = (int)Math.round(sweref.getEast());

        AppDatabase.getDbExecutor().execute(() -> {
            // Simple, clean singleton access
            SpatialResolver resolver = SpatialResolver.getInstance(getApplicationContext());
            String prov = resolver.getRegionName(n, e, false);
            String dist = resolver.getRegionName(n, e, true);

            runOnUiThread(() ->
                displayFormattedCoordinates(prov, dist)
            );
        });
    }

    private void setupExistingLocation() {
        long id = getIntent().getLongExtra("location_id", -1);
        ((Button)findViewById(R.id.btn_save_detail)).setText("Save Changes");
        ((Button)findViewById(R.id.btn_cancel_detail)).setText("Back");
        findViewById(R.id.btn_take_photo_detail).setVisibility(View.GONE);

        // This only observes the record from the DB
        viewModel.getLocationWithPhotos(id).observe(this, item -> {
            if (item == null) return;

            currentRecord = item.location;
            lat = currentRecord.latitude;
            lon = currentRecord.longitude;
            accuracy = currentRecord.accuracy;
            noteInput.setText(currentRecord.note);

            tempPhotoPaths.clear();
            for (PhotoRecord p : item.photos) tempPhotoPaths.add(p.filePath);
            photoAdapter.notifyDataSetChanged();

            // First, show the text with placeholders
            displayFormattedCoordinates("Loading...", "Loading...");

            // ONLY trigger the lookup if we haven't resolved it yet
            // or every time the coordinates change.
            performSpatialLookup();
        });
    }

    private void onCommitClicked() {
        String note = noteInput.getText().toString();
        if (isNew) {
            String localTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocationRecord record = new LocationRecord(lat, lon, System.currentTimeMillis(), accuracy, localTime, note);
            viewModel.saveLocationWithPhotos(record, tempPhotoPaths);
        } else if (currentRecord != null) {
            currentRecord.note = note;
            viewModel.updateLocation(currentRecord);
        }
    }

    private void confirmPhotoDeletion(int position) {
        String path = tempPhotoPaths.get(position);
        new AlertDialog.Builder(this)
                .setTitle("Remove Photo")
                .setMessage("Delete this photo permanently?")
                .setPositiveButton("Delete", (d, w) -> {
                    FileUtils.deleteFileAtPath(path);
                    tempPhotoPaths.remove(position);
                    photoAdapter.notifyItemRemoved(position);
                    if (!isNew) viewModel.deletePhotoByPath(path);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void displayFormattedCoordinates(String province, String district) {
        TextView tvCoords = findViewById(R.id.tv_detail_coords);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Accuracy: ").append((int) Math.ceil(accuracy)).append("m\n");

        DecimalFormat dc = new DecimalFormat("0.00000");
        if (prefs.getBoolean("show_wgs84", true)) {
            sb.append("WGS84: ").append(dc.format(lat)).append(", ").append(dc.format(lon)).append("\n");
        }

        Coordinates here = new Coordinates(lat, lon);

        if (prefs.getBoolean("show_rt90", true)) {
            Coordinates rt90 = here.convertToRT90FromWGS84();
            sb.append("RT90: ").append((int)Math.round(rt90.getNorth())).append(", ").append((int)Math.round(rt90.getEast())).append("\n");
        }

        Coordinates sweref = here.convertToSweref99TMFromWGS84();
        if (prefs.getBoolean("show_sweref", false)) {
            sb.append("SWEREF99tm: ").append((int)Math.round(sweref.getNorth())).append(", ").append((int)Math.round(sweref.getEast())).append("\n");
        }

        if (prefs.getBoolean("show_rubin", true)) {
            sb.append("RUBIN: ").append(here.convertToRT90FromWGS84().getRUBINfromRT90()).append("\n");
        }

        if (prefs.getBoolean("show_date", true)) {
            sb.append("Date: ").append(LocalDate.now().toString()).append("\n");
        }

        // --- Spatial Lookup Logic ---
        if (prefs.getBoolean("show_province", true)) {
            sb.append("Province: ").append(province != null ? province : "Not found").append("\n");
        }

        if (prefs.getBoolean("show_district", true)) {
            sb.append("District: ").append(district != null ? district : "Not found").append("\n");
        }

        tvCoords.setText(sb.toString());
    }

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
        if (success) {
            tempPhotoPaths.add(currentPhotoPath);
            photoAdapter.notifyItemInserted(tempPhotoPaths.size() - 1);
            ((Button)findViewById(R.id.btn_take_photo_detail)).setText("Add Photo (" + tempPhotoPaths.size() + ")");
        }
    });

    private void dispatchTakePictureIntent() {
        try {
            File photoFile = createImageFile();
            Uri photoURI = FileProvider.getUriForFile(this, "nisse.whatsmysocken.fileprovider", photoFile);
            takePictureLauncher.launch(photoURI);
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES));
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openLantmaterietMap() {
        // Get the text from the EditText
        String noteText = noteInput.getText().toString().trim();

        // Fallback if the note is empty so the marker isn't "Untitled"
        if (noteText.isEmpty()) {
            noteText = "Saved Location";
        } else if (noteText.length() > 10) {
            noteText = noteText.substring(0, 10) + "...";
        }

        // URL Encode the text (replaces spaces with %20, etc.)
        String encodedNote;
        try {
            encodedNote = java.net.URLEncoder.encode(noteText, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedNote = "Location"; // Fallback
        }

        Coordinates here = new Coordinates(lat, lon);
        Coordinates sweref = here.convertToSweref99TMFromWGS84();

        // Add the %s for the name parameter
        String url = String.format(java.util.Locale.US,
                "https://minkarta.lantmateriet.se/plats/3006/v2.0/?e=%d&n=%d&z=11&mapprofile=karta&name=%s",
                (int) sweref.getEast(),
                (int) sweref.getNorth(),
                encodedNote);

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && isNew && !isSaved) {
            for (String path : tempPhotoPaths) FileUtils.deleteFileAtPath(path);
        }
    }
}