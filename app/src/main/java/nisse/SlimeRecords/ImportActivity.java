package nisse.SlimeRecords;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import nisse.SlimeRecords.databinding.ActivityImportBinding;

public class ImportActivity extends AppCompatActivity {
    private ActivityImportBinding binding;
    private ImportViewModel importViewModel;
    private OnBackPressedCallback backPressedCallback;
    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument() {
                @NonNull
                @Override
                public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
                    Intent intent = super.createIntent(context, input);
                    // Pre-select Documents folder on Android 8.0+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Uri docsUri = DocumentsContract.buildDocumentUri(
                                "com.android.externalstorage.documents", "primary:Documents");
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, docsUri);
                    }
                    return intent;
                }
            }, uri -> {
                if (uri != null) {
                    ImportViewModel.DuplicateStrategy strategy;
                    int checkedId = binding.rgStrategy.getCheckedRadioButtonId();

                    if (checkedId == R.id.rbReplace) {
                        strategy = ImportViewModel.DuplicateStrategy.REPLACE;
                    } else if (checkedId == R.id.rbKeepBoth) {
                        strategy = ImportViewModel.DuplicateStrategy.KEEP_BOTH;
                    } else {
                        strategy = ImportViewModel.DuplicateStrategy.SKIP;
                    }

                    importViewModel.startImport(uri, strategy);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // This code runs only when the callback is enabled
                Toast.makeText(ImportActivity.this,
                        "Import in progress, please wait...", Toast.LENGTH_SHORT).show();
            }
        };

        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);

        importViewModel.getImportStatus().observe(this, state -> {
            boolean isLoading = (state == ImportViewModel.ImportState.LOADING);

            // If loading, the callback is ENABLED (intercepts back button)
            // If not loading, the callback is DISABLED (back button works normally)
            backPressedCallback.setEnabled(isLoading);

            binding.btnSelectFile.setEnabled(!isLoading);
            binding.rgStrategy.setEnabled(!isLoading);
            for (int i = 0; i < binding.rgStrategy.getChildCount(); i++) {
                binding.rgStrategy.getChildAt(i).setEnabled(!isLoading);
            }

            binding.importProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.tvImportStatus.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) binding.tvImportStatus.setText("Importing data, please wait...");

            // Completion Dialog
            if (state == ImportViewModel.ImportState.SUCCESS) {
                String summary = importViewModel.getStatusMessage().getValue();
                new AlertDialog.Builder(this)
                        .setTitle("Import Complete")
                        .setMessage(summary)
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> finish())
                        .show();
            }
        });

        binding.btnSelectFile.setOnClickListener(v ->
                // Accept ZIPs, CSVs, and Plain text (sometimes CSVs are typed as text/plain)
                filePickerLauncher.launch(new String[]{
                        "application/zip",
                        "text/comma-separated-values",
                        "text/csv",
                        "text/plain"
                }));

        // Observe Status (Loading/Success/Error)
        importViewModel.getImportStatus().observe(this, state -> {
            boolean isLoading = (state == ImportViewModel.ImportState.LOADING);

            // Disable interaction during loading
            binding.btnSelectFile.setEnabled(!isLoading);
            binding.rgStrategy.setEnabled(!isLoading); // Don't let them change strategy mid-way
            for (int i = 0; i < binding.rgStrategy.getChildCount(); i++) {
                binding.rgStrategy.getChildAt(i).setEnabled(!isLoading);
            }

            binding.importProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.tvImportStatus.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) binding.tvImportStatus.setText("Importing data, please wait...");

            if (state == ImportViewModel.ImportState.SUCCESS) {
                String summary = importViewModel.getStatusMessage().getValue();
                new AlertDialog.Builder(this)
                        .setTitle("Import Complete")
                        .setMessage(summary)
                        .setCancelable(false) // Force them to click OK
                        .setPositiveButton("OK", (d, w) -> finish())
                        .show();
            }
        });

        // Observe specific error messages
        importViewModel.getStatusMessage().observe(this, message -> {
            // Only Toast if there's an error; Success summary is handled by the AlertDialog above
            if (importViewModel.getImportStatus().getValue() == ImportViewModel.ImportState.ERROR) {
                if (message != null && !message.isEmpty()) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}