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
import androidx.lifecycle.ViewModelProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import nisse.SlimeRecords.data.LocationRecord;
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
        // Since getSpecimenLocationsWithPhotos is LiveData, we can get its current value
        // or observe it once. For button clicks, we often just want the current state.
        binding.btnGenerateLabel.setEnabled(false);
        binding.btnShareLabel.setEnabled(false);
        exportViewModel.getSpecimenLocations().observe(this, new androidx.lifecycle.Observer<List<LocationRecord>>() {
            @Override
            public void onChanged(List<LocationRecord> list) {
                // Remove observer immediately so it only runs once per click
                exportViewModel.getSpecimenLocations().removeObserver(this);

                if (list == null || list.isEmpty()) {
                    Toast.makeText(PrintActivity.this, "No specimens found!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Move heavy file operations to background thread
                UserDatabase.getDbExecutor().execute(() -> {
                    String htmlContent = LabelHtmlGenerator.generateFullReport(PrintActivity.this, list);
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