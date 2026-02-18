package nisse.whatsmysocken;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
// Import the generated binding class
import nisse.whatsmysocken.databinding.ActivityExportBinding;

public class ExportActivity extends AppCompatActivity {
    private ActivityExportBinding binding;
    private LocationViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private int currentLocationCount = 0;
    private LocationViewModel.ExportState currentState = LocationViewModel.ExportState.IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Binding
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // Observe Location Count
        viewModel.getLocationCount().observe(this, count -> {
            this.currentLocationCount = (count != null) ? count : 0;
            if (currentState == LocationViewModel.ExportState.IDLE) {
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            }
        });

        // Observe Export Status
        disposables.add(viewModel.getExportStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    this.currentState = state;
                    updateUiForState(state);
                    /*
                    // Trigger sharing only when we hit SUCCESS
                    if (state == LocationViewModel.ExportState.SUCCESS) {
                        Uri lastUri = viewModel.getLastExportUri();
                        if (lastUri != null) {
                            viewModel.shareExportedZip(this, lastUri);
                        }
                    }*/
                }, throwable -> {
                    Log.e("Export", "Error: " + throwable.getMessage());
                    updateUiForState(LocationViewModel.ExportState.ERROR);
                }));

        // Set Click Listener
        binding.btnStartUsbExport.setOnClickListener(v -> {
            if (currentLocationCount > 0) {
                viewModel.startExport();
            } else {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUiForState(LocationViewModel.ExportState state) {
        boolean isLoading = (state == LocationViewModel.ExportState.LOADING);

        // Access views directly via binding
        binding.btnStartUsbExport.setEnabled(!isLoading);
        binding.exportProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnShareExport.setVisibility(state == LocationViewModel.ExportState.SUCCESS ? View.VISIBLE : View.GONE);

        switch (state) {
            case IDLE ->
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            case LOADING ->
                binding.tvExportStatus.setText("Zipping files... you can leave this screen.");
            case SUCCESS -> {
                binding.tvExportStatus.setText("Export Complete! Check 'Downloads'.");
                binding.btnShareExport.setOnClickListener(v -> {
                    Uri lastUri = viewModel.getLastExportUri();
                    if (lastUri != null) {
                        viewModel.shareExportedZip(this, lastUri);
                    }
                });
            }
            case ERROR ->
                binding.tvExportStatus.setText("Export Failed. Please try again.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
        binding = null; // Clean up binding reference
    }
}