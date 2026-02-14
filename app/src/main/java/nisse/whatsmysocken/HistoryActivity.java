package nisse.whatsmysocken;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class HistoryActivity extends AppCompatActivity {
    private LocationAdapter adapter;
    private final CompositeDisposable mDisposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LocationAdapter();
        recyclerView.setAdapter(adapter);

        LocationViewModel viewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        // Subscribe to the Flowable
        mDisposable.add(viewModel.historyFlow
                .observeOn(AndroidSchedulers.mainThread()) // Ensure UI updates happen on main thread
                .subscribe(pagingData ->
                    adapter.submitData(getLifecycle(), pagingData)
                , throwable ->
                    Log.e("HistoryActivity", "Error loading paging data", throwable)
                ));

        // Keep your listeners as they were
        adapter.setOnItemLongClickListener(item ->
            new AlertDialog.Builder(this)
                    .setTitle("Delete Location")
                    .setMessage("Are you sure?")
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the subscription to prevent memory leaks
        mDisposable.clear();
    }
}