package nisse.whatsmysocken;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {
    private LocationAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter with empty list first
        adapter = new LocationAdapter();
        recyclerView.setAdapter(adapter);

        // Get the ViewModel
        LocationViewModel viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // Observe the LiveData
        viewModel.getAllLocations().observe(this, locationsWithPhotos -> {
            if (locationsWithPhotos != null) {
                adapter.setLocations(locationsWithPhotos);
                // notifyDataSetChanged() is no longer needed!
            }
        });

        adapter.setOnItemLongClickListener(item -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Location")
                    .setMessage("Are you sure? This will also delete all associated photos.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        viewModel.deleteLocationWithPhotos(item);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        adapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(this, LocationDetailActivity.class);
            // Pass the data we already have
            /*
            intent.putExtra("lat", item.location.latitude);
            intent.putExtra("lon", item.location.longitude);
            intent.putExtra("note", item.location.note);
            intent.putExtra("acc", item.location.accuracy);*/
            intent.putExtra("location_id", item.location.id);
            intent.putExtra("is_new", false); // Important: tells detail screen NOT to save a new record
            /*
            // If you want to show photos in DetailActivity, pass the paths
            ArrayList<String> paths = new ArrayList<>();
            for (PhotoRecord p : item.photos) {
                paths.add(p.filePath);
            }
            intent.putStringArrayListExtra("photo_paths", paths);

             */

            startActivity(intent);
        });
    }
}