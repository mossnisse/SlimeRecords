package nisse.whatsmysocken;

import android.os.Bundle;
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

        // 1. Initialize Binding
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // 2. Observe Location Count
        viewModel.getLocationCount().observe(this, count -> {
            this.currentLocationCount = (count != null) ? count : 0;
            if (currentState == LocationViewModel.ExportState.IDLE) {
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            }
        });

        // 3. Observe Export Status
        disposables.add(viewModel.getExportStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    this.currentState = state;
                    updateUiForState(state);
                }));

        // 4. Set Click Listener
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

        switch (state) {
            case IDLE:
                binding.tvExportStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
                break;
            case LOADING:
                binding.tvExportStatus.setText("Zipping files... you can leave this screen.");
                break;
            case SUCCESS:
                binding.tvExportStatus.setText("Export Complete! Check 'Downloads'.");
                break;
            case ERROR:
                binding.tvExportStatus.setText("Export Failed. Please try again.");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
        binding = null; // Clean up binding reference
    }
}