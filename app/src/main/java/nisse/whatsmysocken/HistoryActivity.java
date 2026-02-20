package nisse.whatsmysocken;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Note: No RxJava imports needed anymore!

public class HistoryActivity extends AppCompatActivity {
    private LocationAdapter adapter;
    private LocationViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Setup UI
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LocationAdapter();
        recyclerView.setAdapter(adapter);

        // Setup ViewModel
        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // Observe the Paging LiveData (Untangled)
        viewModel.historyLiveData.observe(this, pagingData ->
            adapter.submitData(getLifecycle(), pagingData)
        );

        // Click Listeners
        setupListeners();
    }

    private void setupListeners() {
        adapter.setOnItemLongClickListener(item ->
                new AlertDialog.Builder(this)
                        .setTitle("Delete Location")
                        .setMessage("Are you sure you want to delete this record and its photos?")
                        .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteLocationWithPhotos(item))
                        .setNegativeButton("Cancel", null)
                        .show()
        );

        adapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(this, LocationDetailActivity.class);
            intent.putExtra("location_id", item.location.id);
            intent.putExtra("is_new", false);
            startActivity(intent);
        });
    }
}