package nisse.whatsmysocken;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;

public class ExportActivity extends AppCompatActivity {
    private List<LocationWithPhotos> dataToExport;
    private Button btnExport;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private LocationViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        initViews();
        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // 1. Observe the data to be exported
        viewModel.getAllDataForExport().observe(this, data -> {
            this.dataToExport = data;
            if (data != null && viewModel.getExportStatus().blockingFirst() == LocationViewModel.ExportState.IDLE) {
                tvStatus.setText(getString(R.string.export_ready_format, data.size()));
            }
        });

        // 2. Observe the Export Status (The critical part for stability)
        disposables.add(viewModel.getExportStatus()
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(this::updateUiForState));

        btnExport.setOnClickListener(v -> {
            if (dataToExport != null && !dataToExport.isEmpty()) {
                viewModel.startExport(getApplicationContext(), dataToExport);
            }
        });
    }

    private void updateUiForState(LocationViewModel.ExportState state) {
        switch (state) {
            case IDLE:
                btnExport.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                break;
            case LOADING:
                btnExport.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                tvStatus.setText("Zipping files... you can leave this screen.");
                break;
            case SUCCESS:
                btnExport.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("Export Complete! Check 'Downloads'.");
                Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show();
                break;
            case ERROR:
                btnExport.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("Export Failed.");
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
        disposables.clear(); // Prevent memory leaks
    }
}
