package nisse.SlimeRecords;

import android.app.Application;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import nisse.SlimeRecords.data.LocationDao;

public class ImportViewModel extends AndroidViewModel {
    public enum ImportState { IDLE, LOADING, SUCCESS, ERROR }

    private final LocationDao locationDao;
    private final MutableLiveData<ImportState> importStatus =
            new MutableLiveData<>(ImportState.IDLE);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");

    public ImportViewModel(@NonNull Application application) {
        super(application);
        locationDao = AppDependencies.get().locationDao(application);
    }

    public void startImport(Uri sourceUri, ImportProcessor.DuplicateStrategy strategy) {
        if (importStatus.getValue() == ImportState.LOADING) return;
        importStatus.setValue(ImportState.LOADING);
        statusMessage.setValue("");

        AppDependencies.get().executor().execute(() -> {
            File temporaryFile = new File(getApplication().getCacheDir(), "import_temp");
            try (ParcelFileDescriptor descriptor = getApplication().getContentResolver()
                    .openFileDescriptor(sourceUri, "r");
                 FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
                 FileOutputStream output = new FileOutputStream(temporaryFile)) {
                FileUtils.copy(input, output);
                output.flush();

                File photoDirectory = getApplication()
                        .getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                ImportProcessor processor = new ImportProcessor(
                        locationDao, () -> AppDependencies.get().currentTimeMillis());
                ImportResult result = processor.process(temporaryFile, photoDirectory, strategy);
                for (String error : result.errors) {
                    Log.w("Import", "Row failed: " + error);
                }
                statusMessage.postValue(result.toString());
                importStatus.postValue(ImportState.SUCCESS);
            } catch (Exception exception) {
                Log.e("Import", "Processing failed", exception);
                statusMessage.postValue("Import failed: " + exception.getLocalizedMessage());
                importStatus.postValue(ImportState.ERROR);
            } finally {
                if (temporaryFile.exists()) temporaryFile.delete();
            }
        });
    }

    public LiveData<ImportState> getImportStatus() {
        return importStatus;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }
}
