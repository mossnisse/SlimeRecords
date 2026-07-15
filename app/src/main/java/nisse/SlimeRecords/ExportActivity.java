package nisse.SlimeRecords;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import nisse.SlimeRecords.databinding.ActivityExportBinding;

public class ExportActivity extends AppCompatActivity {
    // Maps dropdown positions to formats; must stay in the same order as R.array.export_formats.
    private static final ExportFormat[] FORMAT_OPTIONS = {
            ExportFormat.STANDARD_CSV, ExportFormat.EXCEL_CSV, ExportFormat.ARTPORTALEN};

    private ActivityExportBinding binding;
    private ExportViewModel exportViewModel;
    private int currentLocationCount = 0;
    private int selectedFormatPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            selectedFormatPosition = savedInstanceState.getInt("selected_format_position", 0);
        }
        if (selectedFormatPosition < 0 || selectedFormatPosition >= FORMAT_OPTIONS.length) {
            selectedFormatPosition = 0;
        }

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
            ExportViewModel.ExportState currentState = exportViewModel.getExportStatus().getValue();
            if (currentState == ExportViewModel.ExportState.IDLE) {
                // Standard refresh of the count message
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            }
        });

        MaterialAutoCompleteTextView formatDropdown = binding.editExportFormat;

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.export_formats, android.R.layout.simple_list_item_1);

        formatDropdown.setAdapter(adapter);

        formatDropdown.setText(adapter.getItem(selectedFormatPosition).toString(), false);
        // Track the selection by position instead of parsing the (translatable) label text.
        formatDropdown.setOnItemClickListener((parent, view, position, id) ->
                selectedFormatPosition = position);

        binding.btnStartUsbExport.setOnClickListener(v -> {
            if (currentLocationCount > 0) {
                exportViewModel.startExport(FORMAT_OPTIONS[selectedFormatPosition]);
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

    void updateUiForState(ExportViewModel.ExportState state) {
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selected_format_position", selectedFormatPosition);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
