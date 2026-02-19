package nisse.whatsmysocken;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nisse.whatsmysocken.databinding.ActivityPrintBinding;

public class PrintActivity extends AppCompatActivity {
    private LocationViewModel viewModel;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityPrintBinding binding = ActivityPrintBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // Standard Save
        binding.btnGenerateLabel.setOnClickListener(v -> exportSpecimenLabels(false));

        // Save and Share
        binding.btnShareLabel.setOnClickListener(v -> exportSpecimenLabels(true));
    }

    private void exportSpecimenLabels(boolean shouldShare) {
        disposables.add(viewModel.getSpecimenLocationsWithPhotos()
                .firstOrError()
                .subscribeOn(Schedulers.io())
                .map(list -> {
                    if (list.isEmpty()) return null;
                    return LabelHtmlGenerator.generateFullReport(this, list);
                })
                .map(htmlContent -> {
                    if (htmlContent == null) return Uri.EMPTY;
                    return saveFileAndGetUri(htmlContent); // Now returns URI
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(uri -> {
                    if (uri == Uri.EMPTY) {
                        Toast.makeText(this, "No specimens found!", Toast.LENGTH_SHORT).show();
                    } else if (shouldShare) {
                        shareFile(uri);
                    } else {
                        Toast.makeText(this, "Labels saved to Downloads", Toast.LENGTH_LONG).show();
                    }
                }, throwable ->
                    Log.e("Print", "Error", throwable)
                ));
    }

    // Modified saveFile to run on the background thread and return a status string
    private Uri saveFileAndGetUri(String htmlContent) {
        String fileName = "Specimen_Labels_" + System.currentTimeMillis() + ".html";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(htmlContent.getBytes(StandardCharsets.UTF_8));
                return uri;
            } catch (IOException e) {
                Log.e("Print", "Failed save", e);
            }
        }
        return Uri.EMPTY;
    }

    private void shareFile(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/html");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Specimen Labels"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear(); // Important to prevent memory leaks
    }
}