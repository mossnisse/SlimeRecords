package nisse.whatsmysocken;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ExportActivity extends AppCompatActivity {
    private Button btnExport;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private LocationViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // Local variable to hold the count for display logic
    private int currentLocationCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        initViews();
        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // Observe the Location Count (Updates automatically)
        viewModel.getLocationCount().observe(this, count -> {
            this.currentLocationCount = (count != null) ? count : 0;
            // Only update text if NOT loading
            if (!viewModel.isExporting()) {
                tvStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
            }
        });

        // Observe the Export Status
        disposables.add(viewModel.getExportStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateUiForState));

        // Start Export
        btnExport.setOnClickListener(v -> {
            if (currentLocationCount > 0) {
                viewModel.startExport();
            } else {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUiForState(LocationViewModel.ExportState state) {
        boolean isLoading = (state == LocationViewModel.ExportState.LOADING);

        btnExport.setEnabled(!isLoading);
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);

        switch (state) {
            case IDLE:
                tvStatus.setText(getString(R.string.export_ready_format, currentLocationCount));
                break;
            case LOADING:
                tvStatus.setText("Zipping files... you can leave this screen.");
                break;
            case SUCCESS:
                tvStatus.setText("Export Complete! Check 'Downloads'.");
                Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show();
                break;
            case ERROR:
                tvStatus.setText("Export Failed. Please try again.");
                break;
        }
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_export_status);
        btnExport = findViewById(R.id.btn_start_usb_export);
        progressBar = findViewById(R.id.export_progress);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}