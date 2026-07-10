package nisse.SlimeRecords;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.UserDatabase;
import nisse.SlimeRecords.databinding.ActivityPrintBinding;

public class PrintActivity extends AppCompatActivity {
    private ExportViewModel exportViewModel;
    private ActivityPrintBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPrintBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);

        // Standard Save
        binding.btnGenerateLabel.setOnClickListener(v -> exportSpecimenLabels(false));

        // Save and Share
        binding.btnShareLabel.setOnClickListener(v -> exportSpecimenLabels(true));
    }

    private void exportSpecimenLabels(boolean shouldShare) {
        // Parse the optional Collection nr range before touching the DB
        final Integer fromNr = parseNullableInt(binding.inputRangeFrom.getText().toString());
        final Integer toNr = parseNullableInt(binding.inputRangeTo.getText().toString());

        if (fromNr != null && toNr != null && fromNr > toNr) {
            Toast.makeText(this, "\"From nr\" must not be greater than \"To nr\".", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnGenerateLabel.setEnabled(false);
        binding.btnShareLabel.setEnabled(false);

        LiveData<List<ObservationRecord>> specimens = exportViewModel.getSpecimenLocations();
        specimens.observe(this, new androidx.lifecycle.Observer<List<ObservationRecord>>() {
            @Override
            public void onChanged(List<ObservationRecord> list) {
                // Remove observer immediately so it only runs once per click
                specimens.removeObserver(this);

                List<ObservationRecord> filtered = filterByCollectionNr(list, fromNr, toNr);

                if (filtered.isEmpty()) {
                    boolean hasRange = (fromNr != null || toNr != null);
                    String msg = hasRange ? "No specimens with a Collection nr in that range."
                            : "No specimens found!";
                    Toast.makeText(PrintActivity.this, msg, Toast.LENGTH_SHORT).show();
                    binding.btnGenerateLabel.setEnabled(true);
                    binding.btnShareLabel.setEnabled(true);
                    return;
                }

                // Move heavy file operations to background thread
                UserDatabase.getDbExecutor().execute(() -> {
                    String htmlContent = SpecimenLabelBuilder.generateFullReport(PrintActivity.this, filtered);
                    Uri uri = saveFileAndGetUri(htmlContent);

                    // Switch back to Main Thread for UI updates
                    runOnUiThread(() -> {
                        if (uri == Uri.EMPTY) {
                            Toast.makeText(PrintActivity.this, "Failed to save labels", Toast.LENGTH_SHORT).show();
                        } else if (shouldShare) {
                            shareFile(uri);
                        } else {
                            Toast.makeText(PrintActivity.this, "Labels saved to Downloads", Toast.LENGTH_LONG).show();
                        }
                        binding.btnGenerateLabel.setEnabled(true);
                        binding.btnShareLabel.setEnabled(true);
                    });
                });
            }
        });
    }

    /** Parses trimmed user input into an Integer, or null if empty/invalid. */
    private Integer parseNullableInt(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (TextUtils.isEmpty(trimmed)) return null;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Keeps only specimens whose Collection nr (specimenNr) is a whole number
     * within [min, max]. A null bound means that end is unbounded; if both are
     * null the list is returned unchanged. Specimens with a missing or
     * non-numeric Collection nr are excluded whenever a bound is supplied.
     */
    private List<ObservationRecord> filterByCollectionNr(List<ObservationRecord> list, Integer min, Integer max) {
        if (list == null) return new ArrayList<>();
        if (min == null && max == null) return list;

        List<ObservationRecord> filtered = new ArrayList<>();
        for (ObservationRecord r : list) {
            if (r.attributes == null || r.attributes.specimenNr == null) continue;
            try {
                int nr = Integer.parseInt(r.attributes.specimenNr.trim());
                if (min != null && nr < min) continue;
                if (max != null && nr > max) continue;
                filtered.add(r);
            } catch (NumberFormatException e) {
                // Non-numeric Collection nr cannot be range-matched; skip it.
            }
        }
        return filtered;
    }

    private Uri saveFileAndGetUri(String htmlContent) {
        String fileName = "Specimen_Labels_" + System.currentTimeMillis() + ".html";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        try {
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(htmlContent.getBytes(StandardCharsets.UTF_8));
                    return uri;
                }
            }
        } catch (IOException e) {
            Log.e("Print", "Failed save", e);
        }
        return Uri.EMPTY;
    }

    private void shareFile(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        // Good practice: Add a subject line for email shares
        intent.putExtra(Intent.EXTRA_SUBJECT, "Specimen Labels Export");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Specimen Labels"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}