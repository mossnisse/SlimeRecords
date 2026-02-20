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
    private LocationViewModel viewModel;
    private int currentLocationCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // 1. Observe Export Status
        viewModel.getExportStatus().observe(this, state -> {
            if (state == null) return;
            updateUiForState(state);
        });

        // 2. Observe Item Count
        viewModel.getLocationCount().observe(this, count -> {
            this.currentLocationCount = (count != null) ? count : 0;
            // Only update status text if we are idle
            if (viewModel.getExportStatus().getValue() == LocationViewModel.ExportState.IDLE) {
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            }
        });

        // 3. Start Export Button
        binding.btnStartUsbExport.setOnClickListener(v -> {
            if (currentLocationCount > 0) {
                viewModel.startExport();
            } else {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. Share Button (The logic you mentioned)
        binding.btnShareExport.setOnClickListener(v -> {
            Uri zipUri = viewModel.getLastExportUri();
            if (zipUri != null) {
                shareZip(zipUri);
            } else {
                Toast.makeText(this, "Export file not found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUiForState(LocationViewModel.ExportState state) {
        boolean isLoading = (state == LocationViewModel.ExportState.LOADING);

        binding.btnStartUsbExport.setEnabled(!isLoading);
        binding.exportProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        // Only show share button if export was successful
        binding.btnShareExport.setVisibility(state == LocationViewModel.ExportState.SUCCESS ? View.VISIBLE : View.GONE);

        switch (state) {
            case IDLE -> binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            case LOADING -> binding.tvExportStatus.setText("Creating ZIP file...");
            case SUCCESS -> binding.tvExportStatus.setText("Export finished! File saved to Downloads.");
            case ERROR -> binding.tvExportStatus.setText("An error occurred during export.");
        }
    }

    /**
     * The Share Logic kept in the Activity
     */
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