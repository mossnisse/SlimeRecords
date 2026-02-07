package nisse.whatsmysocken;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportActivity extends AppCompatActivity {
    private List<LocationWithPhotos> dataToExport;
    private Button btnExport;
    private ProgressBar progressBar;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        tvStatus = findViewById(R.id.tv_export_status);
        btnExport = findViewById(R.id.btn_start_usb_export);
        progressBar = findViewById(R.id.export_progress);

        LocationViewModel viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // Fetch all data immediately so it's ready
        viewModel.getAllDataForExport().observe(this, data -> {
            this.dataToExport = data;
            if (data != null) {
                tvStatus.setText("Found " + data.size() + " locations to export.");
            }
        });

        btnExport.setOnClickListener(v -> startZipExport());
    }

    private void startZipExport() {
        if (dataToExport == null || dataToExport.isEmpty()) return;

        btnExport.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Zipping files... please wait.");

        // We use a background thread for the ZIP operation
        new Thread(() -> {
            boolean success = performZip();
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnExport.setEnabled(true);
                if (success) {
                    tvStatus.setText("Export Complete! Check your 'Downloads' folder.");
                    Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText("Export Failed.");
                }
            });
        }).start();
    }

    private boolean performZip() {
        String zipName = "WhatsMySocken_" + System.currentTimeMillis() + ".zip";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;

        try (OutputStream os = getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            // 1. Add CSV
            ZipEntry csvEntry = new ZipEntry("data.csv");
            zos.putNextEntry(csvEntry);
            zos.write(FileUtils.generateCsv(dataToExport).getBytes());
            zos.closeEntry();

            // 2. Add Photos
            for (LocationWithPhotos item : dataToExport) {
                for (PhotoRecord photo : item.photos) {
                    File file = new File(photo.filePath);
                    if (file.exists()) {
                        ZipEntry entry = new ZipEntry("photos/" + file.getName());
                        zos.putNextEntry(entry);
                        copyFile(file, zos);
                        zos.closeEntry();
                    }
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void copyFile(File file, OutputStream out) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }
}
