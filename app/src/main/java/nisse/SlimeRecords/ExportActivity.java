package nisse.SlimeRecords;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import nisse.SlimeRecords.databinding.ActivityExportBinding;

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
            int oldCount = this.currentLocationCount;
            this.currentLocationCount = (count != null) ? count : 0;

            // Check if the count changed while we were in a SUCCESS state
            ExportViewModel.ExportState currentState = exportViewModel.getExportStatus().getValue();

            if (currentState == ExportViewModel.ExportState.SUCCESS && oldCount != currentLocationCount) {
                // Data changed! The old export is now "stale".
                // We don't reset the ViewModel, but we tell the UI to show the Ready message again.
                updateUiForState(ExportViewModel.ExportState.IDLE);
            }
            else if (currentState == ExportViewModel.ExportState.IDLE) {
                // Standard refresh of the count message
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            }
        });

        MaterialAutoCompleteTextView formatDropdown = (MaterialAutoCompleteTextView) binding.editExportFormat;

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.export_formats, android.R.layout.simple_list_item_1);

        formatDropdown.setAdapter(adapter);

        formatDropdown.setText(adapter.getItem(0).toString(), false);

        binding.btnStartUsbExport.setOnClickListener(v -> {
            if (currentLocationCount > 0) {
                // Retrieve text from the AutoCompleteTextView
                String selectedFormat = formatDropdown.getText().toString();
                exportViewModel.startExport(selectedFormat);
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
        binding.btnShareExport.setVisibility(state == ExportViewModel.ExportState.SUCCESS ? View.VISIBLE : View.GONE);

        switch (state) {
            case IDLE -> binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            case LOADING -> binding.tvExportStatus.setText(R.string.export_loading);
            case SUCCESS -> binding.tvExportStatus.setText(R.string.export_success);
            case ERROR -> binding.tvExportStatus.setText(R.string.export_error);
        }
    }

    private void shareZip(Uri zipUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, zipUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Use a human-readable date for the subject
        String date = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date());
        intent.putExtra(Intent.EXTRA_SUBJECT, "SlimeRecords Export - " + date);
        intent.putExtra(Intent.EXTRA_TEXT, "Attached is the data export including photos.");

        startActivity(Intent.createChooser(intent, "Send Export..."));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}