package nisse.SlimeRecords;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nisse.SlimeRecords.coords.CoordSystem;
import nisse.SlimeRecords.coords.Coordinates;
import nisse.SlimeRecords.coords.UTMResult;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.PhotoRecord;
import nisse.SlimeRecords.data.SpeciesAttributes;
import nisse.SlimeRecords.data.SpeciesReferenceWithAccepted;
import nisse.SlimeRecords.databinding.ActivityRecordDetailBinding;

public class RecordDetailActivity extends AppCompatActivity {
    private double lat, lon;
    private float accuracy;
    private double altitude;
    private boolean isNew, isSaved = false;
    private ActivityRecordDetailBinding binding;
    private String currentPhotoPath;
    private final List<PhotoRecord> currentPhotos = new ArrayList<>();
    private PhotoAdapter photoAdapter;
    private ArrayAdapter<String> localityAdapter;
    private ObservationRecord currentRecord;
    private SearchViewModel searchViewModel;
    private HistoryViewModel historyViewModel;
    private String latestCollectorName = "";
    private String currentCountryCode = "";
    private Integer selectedDyntaxaID = null;
    private boolean showPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        historyViewModel = new ViewModelProvider(this).get(HistoryViewModel.class);

        initUI();
        initAutocomplete();
        setupObservers();

        isNew = getIntent().getBooleanExtra("is_new", false);
        if (isNew) setupNewLocation();
        else setupExistingLocation();
    }

    private void setupObservers() {
        historyViewModel.getOperationFinished().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                Toast.makeText(this, isNew ? "Saved!" : "Updated!", Toast.LENGTH_SHORT).show();
                isSaved = true;
                finish();
            }
        });

        searchViewModel.getCountryResult().observe(this, name -> binding.editCountry.setText(name));

        searchViewModel.getCountryCodeResult().observe(this, code -> {
            if (code != null) {
                this.currentCountryCode = code;
            }
        });

        searchViewModel.getProvinceResult().observe(this, name -> binding.editProvince.setText(name));

        searchViewModel.getDistrictResult().observe(this, name -> binding.editDistrict.setText(name));

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
        binding.btnSaveDetail.setOnClickListener(v -> onSaveClicked());
        binding.btnCancelDetail.setOnClickListener(v -> finish());
        binding.btnTakePhotoDetail.setOnClickListener(v -> launchCamera());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        binding.layoutTaxonName.setVisibility(prefs.getBoolean("show_taxon_name_field", true) ? View.VISIBLE : View.GONE);
        binding.layoutLocality.setVisibility(prefs.getBoolean("show_locality_field", true) ? View.VISIBLE : View.GONE);
        binding.layoutCountry.setVisibility(prefs.getBoolean("show_country", true) ? View.VISIBLE : View.GONE);
        binding.layoutProvince.setVisibility(prefs.getBoolean("show_province", true) ? View.VISIBLE : View.GONE);
        binding.layoutDistrict.setVisibility(prefs.getBoolean("show_district", true) ? View.VISIBLE : View.GONE);
        binding.layoutSubstrate.setVisibility(prefs.getBoolean("show_substrate_field", true) ? View.VISIBLE : View.GONE);
        binding.layoutHabitat.setVisibility(prefs.getBoolean("show_habitat_field", true) ? View.VISIBLE : View.GONE);
        binding.layoutCollector.setVisibility(prefs.getBoolean("show_collector_field", true) ? View.VISIBLE : View.GONE);
        binding.layoutOrganismQuantity.setVisibility(prefs.getBoolean("show_organism_quantity_field", false) ? View.VISIBLE : View.GONE);
        binding.layoutLifeStage.setVisibility(prefs.getBoolean("show_life_stage_field", false) ? View.VISIBLE : View.GONE);
        binding.layoutSex.setVisibility(prefs.getBoolean("show_sex_field", false) ? View.VISIBLE : View.GONE);
        binding.layoutActivity.setVisibility(prefs.getBoolean("show_activity_field", false) ? View.VISIBLE : View.GONE);
        binding.layoutSamplingProtocol.setVisibility(prefs.getBoolean("show_sampling_protocol_field", false) ? View.VISIBLE : View.GONE);
        boolean showMapLink = prefs.getBoolean("show_map_link", true);
        binding.btnOpenMap.setVisibility(showMapLink ? View.VISIBLE : View.GONE);
        binding.btnOpenMap.setOnClickListener(v -> openLantmaterietMap());

        boolean showIsSpecimen = prefs.getBoolean("show_is_specimen", true);
        binding.checkboxIsSpecimen.setVisibility(showIsSpecimen ? View.VISIBLE : View.GONE);

        binding.checkboxIsSpecimen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.tvSpecimenNr.setVisibility((isChecked && showIsSpecimen) ? View.VISIBLE : View.GONE);

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

        photoAdapter = new PhotoAdapter(currentPhotos, new PhotoAdapter.OnPhotoListener() {
            @Override
            public void onPhotoClick(PhotoRecord photo) {
                Intent intent = new Intent(RecordDetailActivity.this, FullScreenPhotoActivity.class);
                intent.putExtra("path", photo.filePath); // Pass the path string to the viewer
                startActivity(intent);
            }

            @Override
            public void onPhotoLongClick(int position) {
                confirmPhotoDeletion(position);
            }
        });
        binding.rvPhotoGallery.setAdapter(photoAdapter);
    }

    private void loadLocalitySuggestions() {
        // 0,0 is a valid coordinate (Null Island), but usually means 'unset' in this context.
        if (lat == 0 && lon == 0) return;

        // Use a single observer for the lifetime of the activity
        historyViewModel.getSortedNearbyLocalities(lat, lon).observe(this, suggestions -> {
            if (suggestions == null || localityAdapter == null) return;

            // Save current selection/cursor position if needed
            String currentText = binding.editLocality.getText().toString().trim();

            localityAdapter.setNotifyOnChange(false); // Stop flickering
            localityAdapter.clear();

            for (String s : suggestions) {
                // Only add if it's not exactly what's already there
                if (!s.equalsIgnoreCase(currentText)) {
                    localityAdapter.add(s);
                }
            }

            localityAdapter.notifyDataSetChanged();
        });
    }

    private void setupNewLocation() {
        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);
        accuracy = getIntent().getFloatExtra("acc", 0);
        altitude = getIntent().getDoubleExtra("altitude", 0);

        onCoordinatesLoaded(true); // Perform full lookup
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

            binding.inputNote.setText(currentRecord.note); // Set General Notes
            binding.editLocality.setText(currentRecord.locality);
            binding.editCountry.setText(currentRecord.country);
            binding.editProvince.setText(currentRecord.province);
            binding.editDistrict.setText(currentRecord.district);
            this.currentCountryCode = currentRecord.countryCode;

            // Set flexible attributes
            if (currentRecord.attributes != null) {
                SpeciesAttributes a = currentRecord.attributes;

                binding.inputTaxonName.setText(a.taxonName);
                binding.inputSubstrate.setText(a.substrate);
                binding.inputHabitat.setText(a.habitat);
                binding.inputCollector.setText(a.collector);

                if (a.organismQuantity != null) {
                    binding.inputOrganismQuantity.setText(String.valueOf(a.organismQuantity));
                } else {
                    binding.inputOrganismQuantity.setText("");
                }

                binding.inputLifeStage.setText(a.lifeStage);
                binding.inputSex.setText(a.sex);
                binding.inputActivity.setText(a.activity);
                binding.inputSamplingProtocol.setText(a.samplingProtocol);

                binding.checkboxIsSpecimen.setChecked(a.isSpecimen);
                binding.tvSpecimenNr.setVisibility(a.isSpecimen ? View.VISIBLE : View.GONE);
                binding.tvSpecimenNr.setText("No: " + (a.specimenNr != null ? a.specimenNr : "--"));
            }
            if (showPhoto) {
                currentPhotos.clear();
                currentPhotos.addAll(item.photos);
                photoAdapter.notifyDataSetChanged();
                binding.rvPhotoGallery.setVisibility(View.VISIBLE);
            } else {
                binding.rvPhotoGallery.setVisibility(View.GONE);
            }
            onCoordinatesLoaded(false);
        });
    }

    private void onCoordinatesLoaded(boolean isNewRecord) {
        displayFormattedCoordinates();
        loadLocalitySuggestions();

        // ONLY perform the API spatial lookup (Country/Province/District) for NEW records
        if (isNewRecord) {
            searchViewModel.performFullSpatialLookup(lat, lon);
        }
    }

    private void onSaveClicked() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get UI text values first
        String noteText = binding.inputNote.getText().toString().trim();
        String localityText = binding.editLocality.getText().toString().trim();
        String countryText = binding.editCountry.getText().toString().trim();
        String provinceText = binding.editProvince.getText().toString().trim();
        String districtText = binding.editDistrict.getText().toString().trim();

        // Initialize or retrieve existing attributes to preserve hidden fields
        SpeciesAttributes attrs = (currentRecord != null && currentRecord.attributes != null)
                ? currentRecord.attributes
                : new SpeciesAttributes();

        // ONLY overwrite the attribute if the field was visible/active
        if (prefs.getBoolean("show_taxon_name_field", true)) {
            attrs.taxonName = binding.inputTaxonName.getText().toString().trim();
            attrs.dyntaxaID = selectedDyntaxaID;
        }

        if (prefs.getBoolean("show_substrate_field", true)) {
            attrs.substrate = binding.inputSubstrate.getText().toString().trim();
        }

        if (prefs.getBoolean("show_habitat_field", true)) {
            attrs.habitat = binding.inputHabitat.getText().toString().trim();
        }

        if (prefs.getBoolean("show_organism_quantity_field", false)) {
            String q = binding.inputOrganismQuantity.getText().toString().trim();
            try {
                attrs.organismQuantity = q.isEmpty() ? null : Integer.parseInt(q);
            } catch (NumberFormatException e) { /* Keep existing value in attrs */ }
        }

        if (prefs.getBoolean("show_life_stage_field", false)) {
            attrs.lifeStage = binding.inputLifeStage.getText().toString().trim();
        }

        if (prefs.getBoolean("show_sex_field", false)) {
            attrs.sex = binding.inputSex.getText().toString().trim();
        }

        if (prefs.getBoolean("show_activity_field", false)) {
            attrs.activity = binding.inputActivity.getText().toString().trim();
        }

        if (prefs.getBoolean("show_sampling_protocol_field", false)) {
            attrs.samplingProtocol = binding.inputSamplingProtocol.getText().toString().trim();
        }

        String collectorToSave = attrs.collector; // Default to what's already in the record (if editing)

        if (isNew && (collectorToSave == null || collectorToSave.isEmpty())) {
            collectorToSave = latestCollectorName;
        }

        if (prefs.getBoolean("show_collector_field", true)) {
            String uiText = binding.inputCollector.getText().toString().trim();
            if (!uiText.isEmpty()) {
                collectorToSave = uiText;
            }
        }
        attrs.collector = collectorToSave;

        // Specimen logic
        if (prefs.getBoolean("show_is_specimen", true)) {
            attrs.isSpecimen = binding.checkboxIsSpecimen.isChecked();
            if (attrs.isSpecimen) {
                String rawText = binding.tvSpecimenNr.getText().toString();
                String nrText = rawText.replace("No: ", "").trim();
                attrs.specimenNr = nrText;

                if (isNew) {
                    try {
                        // Only increment if it's actually a number
                        int currentNr = Integer.parseInt(nrText);
                        prefs.edit().putString("last_specimen_number", String.valueOf(currentNr + 1)).apply();
                    } catch (NumberFormatException e) {
                        Log.e("LocationDetail", "Could not parse specimen number: " + nrText);
                    }
                }
            }
        }

        // Save or Update Execution
        if (isNew) {
            String localTime = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            currentRecord = new ObservationRecord(lat, lon, altitude, System.currentTimeMillis(), accuracy, localTime, noteText);
        } else {
            currentRecord.note = noteText;
        }

        // Assign geo-fields directly to the Record object
        currentRecord.locality = localityText;
        currentRecord.country = countryText;
        currentRecord.province = provinceText;
        currentRecord.district = districtText;
        currentRecord.countryCode = this.currentCountryCode;

        currentRecord.attributes = attrs;

        // Persistence
        List<String> pathsToSave = new ArrayList<>();
        for (PhotoRecord p : currentPhotos) {
            pathsToSave.add(p.filePath);
        }
        if (isNew) {
            historyViewModel.saveLocationWithPhotos(currentRecord, pathsToSave);
        } else {
            historyViewModel.updateLocation(currentRecord);
        }
        // Update the recent collectors list
        if (collectorToSave != null && !collectorToSave.isEmpty()) {
            historyViewModel.updateRecentCollector(collectorToSave);
        }
    }

    private void confirmPhotoDeletion(int position) {
        PhotoRecord photoToDelete = currentPhotos.get(position);

        new AlertDialog.Builder(this)
                .setTitle("Remove Photo")
                .setMessage("Delete this photo from this record?")
                .setPositiveButton("Delete", (d, w) -> {
                    currentPhotos.remove(position);
                    photoAdapter.notifyItemRemoved(position);

                    if (!isNew && photoToDelete.id != 0) {
                        // This is an existing record in the DB
                        historyViewModel.deletePhoto(photoToDelete);
                    } else {
                        // This is a fresh photo taken during this session, delete file directly
                        FileUtils.deleteFileAtPath(photoToDelete.filePath);
                    }

                    binding.btnTakePhotoDetail.setText(currentPhotos.isEmpty() ?
                            "Take Photo" : "Add Photo (" + currentPhotos.size() + ")");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void displayFormattedCoordinates() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Accuracy: ").append((int) Math.ceil(accuracy)).append("m\n");

        if(prefs.getBoolean("show_altitude", true)) {
            sb.append("Altitude: ").append(Math.round(altitude)).append(" m\n");
        }
        if (prefs.getBoolean("show_wgs84", true)) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            DecimalFormat dc = new DecimalFormat("0.00000", symbols);
            sb.append("WGS84: ").append(dc.format(lat)).append(", ").append(dc.format(lon)).append("\n");
        }
        Coordinates here = new Coordinates(lat, lon);
        if (prefs.getBoolean("show_rt90", true)) {
            Coordinates rt90 = here.toProjected(CoordSystem.RT90);
            sb.append("RT90: ").append((int)Math.round(rt90.getNorth())).append(", ").append((int)Math.round(rt90.getEast())).append("\n");
        }

        if (prefs.getBoolean("show_sweref", false)) {
            Coordinates sweref = here.toProjected(CoordSystem.SWEREF99TM);
            sb.append("SWEREF99tm: ").append((int)Math.round(sweref.getNorth())).append(", ").append((int)Math.round(sweref.getEast())).append("\n");
        }

        if (prefs.getBoolean("show_rubin", true)) {
            sb.append("RUBIN: ").append(here.toProjected(CoordSystem.RT90).toRUBIN(false)).append("\n");
        }

        if (prefs.getBoolean("show_DMS", true)) {
            sb.append("DMS: ").append(here.getLatDMS()).append(" ").append(here.getLonDMS()).append("\n");
        }

        if (prefs.getBoolean("show_UTM", true)) {
            UTMResult utm = here.toUTM();
            sb.append("UTM: ").append(utm.toString()).append("\n");
        }

        if (prefs.getBoolean("show_MGRS", true)) {
            UTMResult utm = here.toUTM();
            sb.append("MGRS: ").append(here.toMGRS()).append("\n");
        }

        if (prefs.getBoolean("show_date", true)) {
            String dateToShow = isNew ? LocalDate.now().toString() : currentRecord.localTime;
            sb.append("Date: ").append(dateToShow).append("\n");
        }

        binding.tvDetailCoords.setText(sb.toString());
    }

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
        new ActivityResultContracts.TakePicture(), success -> {
            if (success) {
                // Create a temporary PhotoRecord (id will be 0 until saved to DB)
                PhotoRecord newPhoto = new PhotoRecord(0, currentPhotoPath);
                currentPhotos.add(newPhoto);

                photoAdapter.notifyItemInserted(currentPhotos.size() - 1);
                binding.btnTakePhotoDetail.setText("Add Photo (" + currentPhotos.size() + ")");
            }
        });

    private void launchCamera() {
        try {
            File photoFile = createImageFile();
            Uri photoURI = FileProvider.getUriForFile(this, "nisse.SlimeRecords.fileprovider", photoFile);
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
        String noteText = binding.inputNote.getText().toString().trim();
        if (noteText.isEmpty()) noteText = "Saved Location";
        else if (noteText.length() > 10) noteText = noteText.substring(0, 10) + "...";

        String encodedNote;
        try {
            encodedNote = java.net.URLEncoder.encode(noteText, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedNote = "Location";
        }

        Coordinates here = new Coordinates(lat, lon);
        Coordinates sweref = here.toProjected(CoordSystem.SWEREF99TM);

        String url = String.format(java.util.Locale.US,
                "https://minkarta.lantmateriet.se/plats/3006/v2.0/?e=%d&n=%d&z=11&mapprofile=karta&name=%s",
                (int) sweref.getEast(),
                (int) sweref.getNorth(),
                encodedNote);

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void setupSpeciesAutocomplete() {
        AutoCompleteTextView speciesInput = binding.inputTaxonName;

        // Set threshold to 1 so it starts immediately
        speciesInput.setThreshold(1);

        // Use a custom adapter that doesn't filter locally, this ensures that whatever the DB returns is actually shown
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
                for (SpeciesReferenceWithAccepted item : entities) {
                    String original = item.getName();
                    String accepted = item.acceptedName;

                    if (item.getIsSynonym() == 1 && accepted != null && !original.equalsIgnoreCase(accepted)) {
                        displayList.add(original + " -> " + accepted);
                    } else {
                        displayList.add(original);
                    }
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
                if (speciesInput.hasFocus()) {
                    selectedDyntaxaID = null;
                }

                String query = s.toString().trim();
                if (query.length() >= 1) {
                    // Get target language from prefs to pass to the search
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(RecordDetailActivity.this);
                    String targetLang = prefs.getString("preferred_species_language", "sv");

                    searchViewModel.findSpecies(query, targetLang);
                }
            }
        });

        speciesInput.setOnItemClickListener((parent, view, position, id) -> {
            List<SpeciesReferenceWithAccepted> currentSuggestions = searchViewModel.getSpeciesSuggestions().getValue();

            if (currentSuggestions != null && position < currentSuggestions.size()) {
                SpeciesReferenceWithAccepted selected = currentSuggestions.get(position);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                boolean autoResolve = prefs.getBoolean("auto_resolve_synonyms", true);
                String targetLang = prefs.getString("preferred_species_language", "sv");

                // Logic: If it's a synonym or wrong language, use the 'acceptedName' from our POJO
                if ((selected.getIsSynonym() == 1 && autoResolve) || !selected.species.language.equals(targetLang)) {
                    if (selected.acceptedName != null) {
                        speciesInput.setText(selected.acceptedName);
                    } else {
                        // If no translation found, fall back to current
                        speciesInput.setText(selected.getName());
                    }
                } else {
                    speciesInput.setText(selected.getName());
                }
                selectedDyntaxaID = selected.getTaxonID();
                speciesInput.setSelection(speciesInput.getText().length());
            }
        });
    }

    private void initAutocomplete() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String lang = prefs.getString("preferred_species_language", "sv");

        // Static Dropdowns (from strings.xml arrays)
        // We pick the array resource based on the user's preferred language
        setupStaticDropdown(binding.inputLifeStage, lang.equals("sv") ? R.array.life_stage_sv_values : R.array.life_stage_en_values);
        setupStaticDropdown(binding.inputSex, lang.equals("sv") ? R.array.gender_sv_values : R.array.gender_en_values);
        setupStaticDropdown(binding.inputActivity, lang.equals("sv") ? R.array.activity_sv_values : R.array.activity_en_values);
        setupStaticDropdown(binding.inputSamplingProtocol, lang.equals("sv") ? R.array.method_sv_values : R.array.method_en_values);
        setupStaticDropdown(binding.inputSubstrate, lang.equals("sv") ? R.array.substrate_sv_values : R.array.substrate_en_values);

        // Setup Locality (The one that gets updated later with nearby suggestions)
        localityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        binding.editLocality.setAdapter(localityAdapter);
        binding.editLocality.setThreshold(0);

        // Keep the UX consistent with other dropdowns
        binding.editLocality.setOnClickListener(v -> {
            if (localityAdapter.getCount() > 0) binding.editLocality.showDropDown();
        });

        // setup Species Search (The complex one with DB lookups)
        setupSpeciesAutocomplete();

        // Setup Collector (Recent names)
        binding.inputCollector.setOnClickListener(v -> binding.inputCollector.showDropDown());
        binding.inputCollector.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) binding.inputCollector.showDropDown();
        });
    }

    private void setupStaticDropdown(AutoCompleteTextView textView, int arrayResId) {
        String[] items = getResources().getStringArray(arrayResId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, items);

        textView.setAdapter(adapter);

        // Ensure the dropdown shows immediately when clicked or focused
        textView.setOnClickListener(v -> textView.showDropDown());
        textView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                textView.showDropDown();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && isNew && !isSaved) {
            for (PhotoRecord p : currentPhotos) FileUtils.deleteFileAtPath(p.filePath);
        }
    }
}