package nisse.SlimeRecords;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import nisse.SlimeRecords.data.LocationDao;
import nisse.SlimeRecords.data.ObservationRecord;

public class ExportViewModel extends AndroidViewModel {
    public enum ExportState { IDLE, LOADING, SUCCESS, ERROR }

    private final LocationDao locationDao;
    private final MutableLiveData<ExportState> exportStatus =
            new MutableLiveData<>(ExportState.IDLE);
    private Uri lastExportUri;

    public ExportViewModel(@NonNull Application application) {
        super(application);
        locationDao = AppDependencies.get().locationDao(application);
    }

    public void startExport(String displayFormat) {
        startExport(ExportFormat.fromDisplayName(displayFormat));
    }

    public void startExport(ExportFormat format) {
        if (exportStatus.getValue() == ExportState.LOADING) return;
        exportStatus.setValue(ExportState.LOADING);

        AppDependencies.get().executor().execute(() -> {
            Uri uri = null;
            try {
                Context context = getApplication();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                        "SlimeRecords_" + AppDependencies.get().currentTimeMillis() + ".zip");
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                uri = context.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Failed to create MediaStore entry");

                List<RecordWithPhotos> records = locationDao.getAllLocationsWithPhotosSync();
                try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IOException("Failed to open export destination");
                    new ExportArchiveWriter().write(output, records, format,
                            loadAsset("metadata_template.txt"));
                }

                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);
                lastExportUri = uri;
                exportStatus.postValue(ExportState.SUCCESS);
            } catch (Exception exception) {
                Log.e("Export", "Critical export failure", exception);
                if (uri != null) {
                    try {
                        getApplication().getContentResolver().delete(uri, null, null);
                    } catch (Exception ignored) {
                    }
                }
                exportStatus.postValue(ExportState.ERROR);
            }
        });
    }

    private String loadAsset(String name) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getApplication().getAssets().open(name), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        } catch (IOException exception) {
            Log.e("Export", "Could not load metadata template", exception);
            return "Metadata template missing.";
        }
        return text.toString();
    }

    public LiveData<List<ObservationRecord>> getSpecimenLocations() {
        return locationDao.getSpecimenLocations();
    }

    public LiveData<ExportState> getExportStatus() {
        return exportStatus;
    }

    public Uri getLastExportUri() {
        return lastExportUri;
    }
}
