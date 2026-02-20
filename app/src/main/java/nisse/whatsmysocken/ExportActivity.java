package nisse.whatsmysocken;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import nisse.whatsmysocken.databinding.ActivityExportBinding;

public class ExportActivity extends AppCompatActivity {
    private ActivityExportBinding binding;
    private ExportViewModel exportViewModel;
    private int currentLocationCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);

        // Observe Export Status
        exportViewModel.getExportStatus().observe(this, state -> {
            if (state == null) return;
            updateUiForState(state);
        });

        HistoryViewModel historyViewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        // Observe Item Count
        historyViewModel.getLocationCount().observe(this, count -> {
            this.currentLocationCount = (count != null) ? count : 0;
            // Only update status text if we are idle
            if (exportViewModel.getExportStatus().getValue() == ExportViewModel.ExportState.IDLE) {
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            }
        });

        // Start Export Button
        binding.btnStartUsbExport.setOnClickListener(v -> {
            if (currentLocationCount > 0) {
                exportViewModel.startExport();
            } else {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            }
        });

        // Share Button (The logic you mentioned)
        binding.btnShareExport.setOnClickListener(v -> {
            Uri zipUri = exportViewModel.getLastExportUri();
            if (zipUri != null) {
                shareZip(zipUri);
            } else {
                Toast.makeText(this, "Export file not found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUiForState(ExportViewModel.ExportState state) {
        boolean isLoading = (state == ExportViewModel.ExportState.LOADING);

        binding.btnStartUsbExport.setEnabled(!isLoading);
        binding.exportProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        // Only show share button if export was successful
        binding.btnShareExport.setVisibility(state == ExportViewModel.ExportState.SUCCESS ? View.VISIBLE : View.GONE);

        switch (state) {
            case IDLE -> binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            case LOADING -> binding.tvExportStatus.setText("Creating ZIP file...");
            case SUCCESS -> binding.tvExportStatus.setText("Export finished! File saved to Downloads.");
            case ERROR -> binding.tvExportStatus.setText("An error occurred during export.");
        }
    }

    private void shareZip(Uri zipUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, zipUri);

        // Grant temporary read permission to the receiving app
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        intent.putExtra(Intent.EXTRA_SUBJECT, "WhatsMySocken Export: " + System.currentTimeMillis());

        startActivity(Intent.createChooser(intent, "Share ZIP via..."));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}