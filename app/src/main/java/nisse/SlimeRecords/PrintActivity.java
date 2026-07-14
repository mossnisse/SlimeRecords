package nisse.SlimeRecords;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
        String fromText = binding.inputRangeFrom.getText().toString();
        String toText = binding.inputRangeTo.getText().toString();
        final Integer fromNr = parseNullableInt(fromText);
        final Integer toNr = parseNullableInt(toText);

        binding.inputRangeFrom.setError(null);
        binding.inputRangeTo.setError(null);
        if (isInvalidRangeBound(fromText)) {
            binding.inputRangeFrom.setError("Enter a whole number within the supported range.");
            return;
        }
        if (isInvalidRangeBound(toText)) {
            binding.inputRangeTo.setError("Enter a whole number within the supported range.");
            return;
        }

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
                    Uri savedUri;
                    try {
                        String htmlContent = SpecimenLabelBuilder.generateFullReport(PrintActivity.this, filtered);
                        savedUri = saveFileAndGetUri(htmlContent);
                    } catch (Exception e) {
                        // Never let the task die silently: that would crash the app
                        // and leave both buttons permanently disabled.
                        Log.e("Print", "Label generation failed", e);
                        savedUri = Uri.EMPTY;
                    }
                    final Uri uri = savedUri;

                    // Switch back to Main Thread for UI updates
                    runOnUiThread(() -> {
                        if (binding == null) return; // Activity destroyed while exporting
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
    static Integer parseNullableInt(String text) {
        return PrintRangeFilter.parseNullableInt(text);
    }

    static boolean isInvalidRangeBound(String text) {
        return PrintRangeFilter.isInvalidBound(text);
    }

    /**
     * Keeps only specimens whose Collection nr (specimenNr) is a whole number
     * within [min, max]. A null bound means that end is unbounded; if both are
     * null the list is returned unchanged. Specimens with a missing or
     * non-numeric Collection nr are excluded whenever a bound is supplied.
     */
    private List<ObservationRecord> filterByCollectionNr(List<ObservationRecord> list, Integer min, Integer max) {
        return PrintRangeFilter.filter(list, min, max);
    }

    private Uri saveFileAndGetUri(String htmlContent) {
        String fileName = "Specimen_Labels_" + System.currentTimeMillis() + ".html";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = null;
        try {
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IOException("Could not open output stream for " + uri);
                    os.write(htmlContent.getBytes(StandardCharsets.UTF_8));
                    return uri;
                }
            }
        } catch (Exception e) {
            Log.e("Print", "Failed save", e);
            // Remove the half-written entry so it doesn't linger in Downloads
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
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
