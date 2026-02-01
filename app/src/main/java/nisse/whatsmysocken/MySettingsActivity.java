package nisse.whatsmysocken;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MySettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // You MUST set the content view first!
        setContentView(R.layout.activity_settings);

        // Now you can swap in the fragment
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new MySettingsFragment())
                .commit();

        // Optional: Add a back button in the toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    // This makes the back button in the toolbar actually work
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

