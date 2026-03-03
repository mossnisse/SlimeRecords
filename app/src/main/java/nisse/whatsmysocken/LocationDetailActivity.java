package nisse.whatsmysocken;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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
import nisse.whatsmysocken.data.SpeciesReferenceEntity;
import nisse.whatsmysocken.databinding.ActivityLocationDetailBinding;

public class LocationDetailActivity extends AppCompatActivity {
    private double lat, lon;
    private float accuracy;
    private double altitude;
    private boolean isNew, isSaved = false;
    private ActivityLocationDetailBinding binding;
    private String currentPhotoPath;
    private final List<String> tempPhotoPaths = new ArrayList<>();
    private PhotoAdapter photoAdapter;
    private ArrayAdapter<String> localityAdapter;
    private LocationRecord currentRecord;
    private SearchViewModel searchViewModel;
    private HistoryViewModel historyViewModel;
    private String currentProvince = "Loading...";
    private String currentDistrict = "Loading...";
    private String latestCollectorName = "";
    private Integer selectedDyntaxaID = null;
    private boolean showPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        historyViewModel = new ViewModelProvider(this).get(HistoryViewModel.class);

        initUI();
        initLocalityView();
        setupObservers();
        setupSpeciesAutocomplete();

        isNew = getIntent().getBooleanExtra("is_new", false);
        if (isNew) {
            setupNewLocation();
        } else {
            setupExistingLocation();
        }
    }

    private void setupObservers() {
        // Corrected: observe standard LiveData
        historyViewModel.getOperationFinished().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                Toast.makeText(this, isNew ? "Saved!" : "Updated!", Toast.LENGTH_SHORT).show();
                isSaved = true;
                finish();
            }
        });

        searchViewModel.getProvinceResult().observe(this, name -> {
            currentProvince = name;
            refreshCoordinateDisplay();
        });

        searchViewModel.getDistrictResult().observe(this, name -> {
            currentDistrict = name;
            refreshCoordinateDisplay();
        });

        // Fix: Explicitly typing the list to resolve "isEmpty" errors
        historyViewModel.getRecentCollectors().observe(this, (List<String> list) -> {
            if (list != null && !list.isEmpty()) {
                latestCollectorName = list.get(0);

                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, list);
                binding.inputCollector.setAdapter(adapter);

                if (isNew && binding.inputCollector.getText().toString().isEmpty()) {
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
        boolean showLocality = prefs.getBoolean( "show_locality_description", true);
        binding.editLocalityDescription.setVisibility(showLocality ? View.VISIBLE : View.GONE);
        boolean showSubstrate = prefs.getBoolean("show_substrate_field", true);
        binding.inputSubstrate.setVisibility(showSubstrate ? View.VISIBLE : View.GONE);
        boolean showHabitat = prefs.getBoolean("show_habitat_field", true);
        binding.inputHabitat.setVisibility(showHabitat ? View.VISIBLE : View.GONE);
        boolean showCollector = prefs.getBoolean("show_collector_field", true);
        binding.inputCollector.setVisibility(showCollector ? View.VISIBLE : View.GONE);
        boolean showIsCollection = prefs.getBoolean("show_is_collection", true);
        binding.checkboxIsSpecimen.setVisibility(showIsCollection ? View.VISIBLE : View.GONE);
        boolean showMapLink = prefs.getBoolean("show_map_link", true);
        binding.btnOpenMap.setVisibility(showMapLink ? View.VISIBLE : View.GONE);
        binding.btnOpenMap.setOnClickListener(v -> openLantmaterietMap());

        binding.checkboxIsSpecimen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.tvSpecimenNr.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            if (isNew && isChecked) {
                String nextNrStr = prefs.getString("last_specimen_number", "1");
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

        showPhoto = prefs.getBoolean("show_photo", true);
        binding.btnTakePhotoDetail.setVisibility(showPhoto ? View.VISIBLE : View.GONE);
        // photo gallery
        binding.rvPhotoGallery.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvPhotoGallery.setVisibility(showPhoto ? View.VISIBLE : View.GONE);

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

    private void initLocalityView() {
        AutoCompleteTextView editLocality = binding.editLocalityDescription;

        // Initialize the adapter
        localityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        editLocality.setAdapter(localityAdapter);

        // Allow the dropdown to show even without typing
        editLocality.setThreshold(0);

        editLocality.setOnClickListener(v -> {
            if (localityAdapter.getCount() > 0) {
                editLocality.showDropDown();
            }
        });

        editLocality.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && localityAdapter.getCount() > 0) {
                editLocality.showDropDown();
            }
        });
    }

    private void loadLocalitySuggestions() {
        // Only fetch if we actually have coordinates
        if (lat == 0 && lon == 0) return;

        historyViewModel.getNearbyLocalitySuggestions(lat, lon).observe(this, suggestions -> {
            if (suggestions != null && localityAdapter != null) {
                localityAdapter.clear();
                // Filter out the current text to avoid suggesting what's already typed
                String currentText = binding.editLocalityDescription.getText().toString();
                for (String s : suggestions) {
                    if (!s.equals(currentText)) localityAdapter.add(s);
                }
                localityAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupNewLocation() {
        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        accuracy = getIntent().getFloatExtra("acc", 0);
        altitude = getIntent().getDoubleExtra("altitude", 0);
        triggerSpatialLookups();
        refreshCoordinateDisplay();
        loadLocalitySuggestions();
    }

    private void setupExistingLocation() {
        long id = getIntent().getLongExtra("location_id", -1);
        binding.btnSaveDetail.setText("Save Changes");
        binding.btnCancelDetail.setText("Back");
        binding.btnTakePhotoDetail.setVisibility(View.GONE);

        historyViewModel.getLocationWithPhotos(id).observe(this, item -> {
            if (item == null) return;

            currentRecord = item.location;
            lat = currentRecord.latitude;
            lon = currentRecord.longitude;
            accuracy = currentRecord.accuracy;
            altitude = currentRecord.altitude;

            // Set General Note
            binding.detailNoteInput.setText(currentRecord.note);
            binding.editLocalityDescription.setText(currentRecord.localityDescription);

            // Set flexible attributes
            if (currentRecord.attributes != null) {
                binding.inputSpecies.setText(currentRecord.attributes.species);
                binding.inputSubstrate.setText(currentRecord.attributes.substrate);
                binding.inputHabitat.setText(currentRecord.attributes.habitat);
                binding.inputCollector.setText(currentRecord.attributes.collector);
                binding.checkboxIsSpecimen.setChecked(currentRecord.attributes.isSpecimen);
                binding.tvSpecimenNr.setVisibility(currentRecord.attributes.isSpecimen ? View.VISIBLE : View.GONE);
                binding.tvSpecimenNr.setText("No: " + (currentRecord.attributes.specimenNr != null ?
                        currentRecord.attributes.specimenNr : "--"));
            }
            if (showPhoto) {
                tempPhotoPaths.clear();
                for (PhotoRecord p : item.photos) tempPhotoPaths.add(p.filePath);
                photoAdapter.notifyDataSetChanged();
                binding.rvPhotoGallery.setVisibility(View.VISIBLE);
            } else {
                binding.rvPhotoGallery.setVisibility(View.GONE);
            }

            triggerSpatialLookups();
            refreshCoordinateDisplay();
            loadLocalitySuggestions();
        });
    }

    private void onCommitClicked() {
        String noteText = binding.detailNoteInput.getText().toString().trim();
        String localityText = binding.editLocalityDescription.getText().toString().trim();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SpeciesAttributes attrs;
        if (isNew || currentRecord == null || currentRecord.attributes == null) {
            attrs = new SpeciesAttributes();
        } else {
            attrs = currentRecord.attributes;
        }

        // Logic for updating attributes...
        if (prefs.getBoolean("show_species_field", true)) {
            attrs.species = binding.inputSpecies.getText().toString().trim();
            attrs.dyntaxaID = selectedDyntaxaID; // Save the captured ID here!
        }
        if (prefs.getBoolean("show_substrate_field", true)) attrs.substrate = binding.inputSubstrate.getText().toString().trim();
        if (prefs.getBoolean("show_habitat_field", true)) attrs.habitat = binding.inputHabitat.getText().toString().trim();

        String collector;
        if (prefs.getBoolean("show_collector_field", true)) {
            collector = binding.inputCollector.getText().toString().trim();
        } else {
            collector = latestCollectorName;
        }
        attrs.collector = collector;

        //if (prefs.getBoolean("show_locality_description", true)) attrs.localityDescription = binding.editLocalityDescription.getText().toString();
        attrs.isSpecimen = binding.checkboxIsSpecimen.isChecked();

        if (attrs.isSpecimen) {
            String nrText = binding.tvSpecimenNr.getText().toString().replace("No: ", "").trim();
            attrs.specimenNr = nrText;
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

            LocationRecord record = new LocationRecord(lat, lon, altitude, System.currentTimeMillis(), accuracy, localTime, noteText);
            record.localityDescription = localityText;
            record.attributes = attrs;
            historyViewModel.saveLocationWithPhotos(record, tempPhotoPaths);
        } else if (currentRecord != null) {
            currentRecord.note = noteText;
            currentRecord.localityDescription = localityText;
            currentRecord.attributes = attrs;
            historyViewModel.updateLocation(currentRecord);
        }

        // Fix: Call matching ViewModel method name
        if (collector != null && !collector.isEmpty()) {
            historyViewModel.updateRecentCollector(collector);
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
                    // Fix: ViewModel method now matches
                    if (!isNew) historyViewModel.deletePhotoByPath(path);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void displayFormattedCoordinates(String province, String district) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Accuracy: ").append((int) Math.ceil(accuracy)).append("m\n");

        if(prefs.getBoolean("show_altitude", true)) {
            sb.append("Altitude: ").append(Math.round(altitude)).append(" m\n");
        }
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
            String dateToShow = isNew ? LocalDate.now().toString() : currentRecord.localTime;
            sb.append("Date: ").append(dateToShow).append("\n");
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
        if (prefs.getBoolean("show_province", true)) searchViewModel.fetchProvinceName(lat, lon);
        else currentProvince = null;

        if (prefs.getBoolean("show_district", true)) searchViewModel.fetchDistrictName(lat, lon);
        else currentDistrict = null;
    }

    private void setupLocalityAutocomplete(double currentLat, double currentLon) {
        // Corrected ID to match the XML change above
        AutoCompleteTextView editLocality = binding.editLocalityDescription;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        editLocality.setAdapter(adapter);

        double delta = 0.018;
        // Calling the renamed ViewModel method
        historyViewModel.getNearbyLocalitySuggestions(
                currentLat - delta,
                currentLon - delta
        ).observe(this, suggestions -> {
            if (suggestions != null) {
                adapter.clear();
                adapter.addAll(suggestions);
                adapter.notifyDataSetChanged();
            }
        });

        editLocality.setOnClickListener(v -> editLocality.showDropDown());
        // Also show when focus is gained
        editLocality.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) editLocality.showDropDown();
        });
    }

    private void setupSpeciesAutocomplete() {
        AutoCompleteTextView speciesInput = binding.inputSpecies;

        // Set threshold to 1 so it starts immediately
        speciesInput.setThreshold(1);

        // Use a custom adapter that doesn't filter locally
        // This ensures that whatever the DB returns is actually shown
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line) {
            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        // We do nothing here because the DB already filtered
                        return null;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        // Do nothing
                    }
                };
            }
        };

        speciesInput.setAdapter(adapter);

        searchViewModel.getSpeciesSuggestions().observe(this, entities -> {
            if (entities != null) {
                List<String> displayList = new ArrayList<>();
                for (SpeciesReferenceEntity e : entities) {
                    StringBuilder sb = new StringBuilder();

                    // Add the name
                    sb.append(e.name);

                    // Add a hint if it's a synonym
                    if (e.isSynonym == 1) {
                        sb.append(" (synonym)");
                    }

                    // Add the group if available
                    if (e.taxonGroup != null && !e.taxonGroup.isEmpty()) {
                        sb.append(" [").append(e.taxonGroup).append("]");
                    }

                    displayList.add(sb.toString());
                }

                adapter.clear();
                adapter.addAll(displayList);
                adapter.notifyDataSetChanged();
            }
        });

        speciesInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Only reset the ID if the change came from the user typing (keyboard)
                // AutoCompleteTextView triggers this even when we call .setText()
                if (speciesInput.hasFocus()) {
                    selectedDyntaxaID = null;
                }

                String query = s.toString().trim();
                if (query.length() >= 1) {
                    searchViewModel.findSpecies(query);
                }
            }
        });

        speciesInput.setOnItemClickListener((parent, view, position, id) -> {
            List<SpeciesReferenceEntity> currentSuggestions = searchViewModel.getSpeciesSuggestions().getValue();
            if (currentSuggestions != null && position < currentSuggestions.size()) {
                SpeciesReferenceEntity selected = currentSuggestions.get(position);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                boolean autoResolve = prefs.getBoolean("auto_resolve_synonyms", true);
                String targetLang = prefs.getString("preferred_species_language", "sv");

                // If it's a synonym OR we want to translate from Latin to Swedish (or vice versa)
                if (autoResolve || !selected.language.equals(targetLang)) {
                    // Find the accepted name in the target language
                    searchViewModel.resolveAcceptedName(selected.taxonID, targetLang).observe(this, accepted -> {
                        if (accepted != null) {
                            speciesInput.setText(accepted.name);
                            selectedDyntaxaID = accepted.taxonID;
                        } else {
                            // Fallback if no translation exists
                            speciesInput.setText(selected.name);
                            selectedDyntaxaID = selected.taxonID;
                        }
                        speciesInput.setSelection(speciesInput.getText().length());
                    });
                } else {
                    speciesInput.setText(selected.name);
                    selectedDyntaxaID = selected.taxonID;
                    speciesInput.setSelection(selected.name.length());
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && isNew && !isSaved) {
            for (String path : tempPhotoPaths) FileUtils.deleteFileAtPath(path);
        }
    }
}