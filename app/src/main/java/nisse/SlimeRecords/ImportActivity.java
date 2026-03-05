package nisse.SlimeRecords;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import nisse.SlimeRecords.databinding.ActivityImportBinding;

public class ImportActivity extends AppCompatActivity {
    private ActivityImportBinding binding;
    private ImportViewModel importViewModel;

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
                    importViewModel.startImport(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);

        binding.btnSelectFile.setOnClickListener(v ->
                filePickerLauncher.launch(new String[]{"application/zip"}));

        // Observe Status (Loading/Success/Error)
        importViewModel.getImportStatus().observe(this, state -> {
            boolean isLoading = (state == ImportViewModel.ImportState.LOADING);
            binding.btnSelectFile.setEnabled(!isLoading);
            binding.importProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);

            if (state == ImportViewModel.ImportState.SUCCESS) {
                Toast.makeText(this, "Import Successful", Toast.LENGTH_LONG).show();
                finish();
            }
        });

        // Observe specific error messages
        importViewModel.getStatusMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}