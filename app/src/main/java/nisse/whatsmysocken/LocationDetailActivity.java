package nisse.whatsmysocken;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;
import nisse.whatsmysocken.data.SpeciesAttributes;
import nisse.whatsmysocken.databinding.ActivityLocationDetailBinding;

public class LocationDetailActivity extends AppCompatActivity {
    private double lat, lon;
    private float accuracy;
    private boolean isNew, isSaved = false;
    private ActivityLocationDetailBinding binding; // ViewBinding object
    private String currentPhotoPath;
    private final List<String> tempPhotoPaths = new ArrayList<>();
    private PhotoAdapter photoAdapter;
    private LocationRecord currentRecord;
    private LocationViewModel viewModel;
    private String currentProvince = "Loading...";
    private String currentDistrict = "Loading...";
    private String latestCollectorName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        initUI();
        setupObservers();

        isNew = getIntent().getBooleanExtra("is_new", false);
        if (isNew) {
            setupNewLocation();
        } else {
            setupExistingLocation();
        }
    }

    private void setupObservers() {
        viewModel.getSaveOperationFinished().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                Toast.makeText(this, isNew ? "Saved!" : "Updated!", Toast.LENGTH_SHORT).show();
                isSaved = true;
                finish();
            }
        });

        viewModel.getProvinceResult().observe(this, name -> {
            currentProvince = name;
            refreshCoordinateDisplay();
        });

        viewModel.getDistrictResult().observe(this, name -> {
            currentDistrict = name;
            refreshCoordinateDisplay();
        });

        viewModel.getRecentCollectors().observe(this, list -> {
            if (list != null && !list.isEmpty()) {
                latestCollectorName = list.get(0); // The most recent one

                // Update the dropdown adapter
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, list);
                binding.inputCollector.setAdapter(adapter);

                // FIX: Only auto-fill if it's a NEW record and the field is currently empty
                if (isNew && binding.inputCollector.getText().toString().isEmpty()) {
                    // Use 'false' to prevent the dropdown menu from popping up immediately
                    binding.inputCollector.setText(latestCollectorName, false);
                }
            }
        });
    }

    private void initUI() {
        binding.btnSaveDetail.setOnClickListener(v -> onCommitClicked());
        binding.btnCancelDetail.setOnClickListener(v -> finish());
        binding.btnTakePhotoDetail.setOnClickListener(v -> dispatchTakePictureIntent());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean showSpecies = prefs.getBoolean("show_species_field", true);
        binding.inputSpecies.setVisibility(showSpecies ? View.VISIBLE : View.GONE);
        boolean showSubstrate = prefs.getBoolean("show_substrate_field", true);
        binding.inputSubstrate.setVisibility(showSubstrate ? View.VISIBLE : View.GONE);
        boolean showCollector = prefs.getBoolean("show_collector_field", true);
        binding.inputCollector.setVisibility(showCollector ? View.VISIBLE : View.GONE);
        boolean showLocality = prefs.getBoolean( "show_locality_description", true);
        binding.inputLocality.setVisibility(showLocality ? View.VISIBLE : View.GONE);
        boolean showIsCollection = prefs.getBoolean("show_is_collection", true);
        binding.checkboxIsSpecimen.setVisibility(showIsCollection ? View.VISIBLE : View.GONE);
        boolean showMapLink = prefs.getBoolean("show_map_link", true);
        binding.btnOpenMap.setVisibility(showMapLink ? View.VISIBLE : View.GONE);
        binding.btnOpenMap.setOnClickListener(v -> openLantmaterietMap());

        binding.checkboxIsSpecimen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.tvSpecimenNr.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            if (isNew && isChecked) {
                // Settings store it as a String, but our code might have stored it as an Int.
                // This helper handles both to prevent crashes.
                String nextNrStr = "";
                try {
                    nextNrStr = prefs.getString("last_specimen_number", "1");
                } catch (ClassCastException e) {
                    // If it was saved as an int previously, convert it
                    nextNrStr = String.valueOf(prefs.getInt("last_specimen_number", 1));
                }
                binding.tvSpecimenNr.setText("No: " + nextNrStr);
            }
        });

        // to get the combobox for the collector field logic to work
        binding.inputCollector.setOnClickListener(v -> {
            // Show the dropdown when the field is clicked, even if empty
            binding.inputCollector.showDropDown();
        });

        binding.inputCollector.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.inputCollector.showDropDown();
            }
        });

        // photo gallery
        binding.rvPhotoGallery.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

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
        binding.rvPhotoGallery.setAdapter(photoAdapter);
    }

    private void setupNewLocation() {
        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        accuracy = getIntent().getFloatExtra("acc", 0);
        triggerSpatialLookups();
        refreshCoordinateDisplay();
    }

    private void setupExistingLocation() {
        long id = getIntent().getLongExtra("location_id", -1);
        binding.btnSaveDetail.setText("Save Changes");
        binding.btnCancelDetail.setText("Back");
        binding.btnTakePhotoDetail.setVisibility(View.GONE);

        viewModel.getLocationWithPhotos(id).observe(this, item -> {
            if (item == null) return;

            currentRecord = item.location;
            lat = currentRecord.latitude;
            lon = currentRecord.longitude;
            accuracy = currentRecord.accuracy;

            // Set General Note
            binding.detailNoteInput.setText(currentRecord.note);

            // Set flexible attributes
            if (currentRecord.attributes != null) {
                binding.inputSpecies.setText(currentRecord.attributes.species);
                binding.inputSubstrate.setText(currentRecord.attributes.substrate);
                binding.inputCollector.setText(currentRecord.attributes.collector);
                binding.inputLocality.setText(currentRecord.attributes.localityDescription);
                binding.checkboxIsSpecimen.setChecked(currentRecord.attributes.isSpecimen);
                binding.checkboxIsSpecimen.setChecked(currentRecord.attributes.isSpecimen);
                binding.tvSpecimenNr.setVisibility(currentRecord.attributes.isSpecimen ? View.VISIBLE : View.GONE);
                binding.tvSpecimenNr.setText("No: " + (currentRecord.attributes.specimenNr != null ?
                        currentRecord.attributes.specimenNr : "--"));
            }

            tempPhotoPaths.clear();
            for (PhotoRecord p : item.photos) tempPhotoPaths.add(p.filePath);
            photoAdapter.notifyDataSetChanged();

            triggerSpatialLookups();
            refreshCoordinateDisplay();
        });
    }

    private void onCommitClicked() {
        String noteText = binding.detailNoteInput.getText().toString().trim();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get or create the attributes object
        SpeciesAttributes attrs;
        if (isNew || currentRecord == null || currentRecord.attributes == null) {
            attrs = new SpeciesAttributes();
        } else {
            // Reuse existing attributes so hidden fields aren't wiped
            attrs = currentRecord.attributes;
        }

        // Only update fields if they are currently active in settings
        if (prefs.getBoolean("show_species_field", true)) {
            attrs.species = binding.inputSpecies.getText().toString();
        }

        if (prefs.getBoolean("show_substrate_field", true)) {
            attrs.substrate = binding.inputSubstrate.getText().toString();
        }

        String collector;
        boolean isCollectorVisible = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("show_collector_field", true);
        if (isCollectorVisible) {
            collector = binding.inputCollector.getText().toString().trim();
        } else {
            collector = latestCollectorName;
        }
        attrs.collector = collector;

        if (prefs.getBoolean("show_locality_description", true)) {
            attrs.localityDescription = binding.inputLocality.getText().toString();
        }

        // Specimen toggle and number should generally always be preserved or handled
        attrs.isSpecimen = binding.checkboxIsSpecimen.isChecked();

        if (attrs.isSpecimen) {
            // Extract the number from the text "No: 5" -> "5"
            String nrText = binding.tvSpecimenNr.getText().toString().replace("No: ", "").trim();
            attrs.specimenNr = nrText;

            // Save this back to preferences as the new "last used" number
            try {
                int currentNr = Integer.parseInt(nrText);
                prefs.edit().putString("last_specimen_number", String.valueOf(currentNr + 1)).apply();
            } catch (NumberFormatException ignored) {}
        } else {
            attrs.specimenNr = null;
        }

        if (isNew) {
            String localTime = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            LocationRecord record = new LocationRecord(lat, lon, System.currentTimeMillis(), accuracy, localTime, noteText);
            record.attributes = attrs;
            viewModel.saveLocationWithPhotos(record, tempPhotoPaths);
        } else if (currentRecord != null) {
            currentRecord.note = noteText;
            currentRecord.attributes = attrs; // attrs now contains old data + new edits
            viewModel.updateLocation(currentRecord);
        }
        if (collector != null && !collector.isEmpty()) {
            viewModel.updateRecentCollector(collector);
        }
    }

    private void refreshCoordinateDisplay() {
        displayFormattedCoordinates(currentProvince, currentDistrict);
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

        if (prefs.getBoolean("show_sweref", false)) {
            Coordinates sweref = here.convertToSweref99TMFromWGS84();
            sb.append("SWEREF99tm: ").append((int)Math.round(sweref.getNorth())).append(", ").append((int)Math.round(sweref.getEast())).append("\n");
        }

        if (prefs.getBoolean("show_rubin", true)) {
            sb.append("RUBIN: ").append(here.convertToRT90FromWGS84().getRUBINfromRT90()).append("\n");
        }

        if (prefs.getBoolean("show_date", true)) {
            sb.append("Date: ").append(LocalDate.now().toString()).append("\n");
        }

        if (prefs.getBoolean("show_province", true)) {
            sb.append("Province: ").append(province != null ? province : "Not found").append("\n");
        }

        if (prefs.getBoolean("show_district", true)) {
            sb.append("District: ").append(district != null ? district : "Not found").append("\n");
        }

        binding.tvDetailCoords.setText(sb.toString());
    }

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
        if (success) {
            tempPhotoPaths.add(currentPhotoPath);
            photoAdapter.notifyItemInserted(tempPhotoPaths.size() - 1);
            binding.btnTakePhotoDetail.setText("Add Photo (" + tempPhotoPaths.size() + ")");
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
        String noteText = binding.detailNoteInput.getText().toString().trim();
        if (noteText.isEmpty()) noteText = "Saved Location";
        else if (noteText.length() > 10) noteText = noteText.substring(0, 10) + "...";

        String encodedNote;
        try {
            encodedNote = java.net.URLEncoder.encode(noteText, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedNote = "Location";
        }

        Coordinates here = new Coordinates(lat, lon);
        Coordinates sweref = here.convertToSweref99TMFromWGS84();

        String url = String.format(java.util.Locale.US,
                "https://minkarta.lantmateriet.se/plats/3006/v2.0/?e=%d&n=%d&z=11&mapprofile=karta&name=%s",
                (int) sweref.getEast(),
                (int) sweref.getNorth(),
                encodedNote);

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void triggerSpatialLookups() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("show_province", true)) viewModel.fetchProvinceName(lat, lon);
        else currentProvince = null;

        if (prefs.getBoolean("show_district", true)) viewModel.fetchDistrictName(lat, lon);
        else currentDistrict = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && isNew && !isSaved) {
            for (String path : tempPhotoPaths) FileUtils.deleteFileAtPath(path);
        }
    }
}