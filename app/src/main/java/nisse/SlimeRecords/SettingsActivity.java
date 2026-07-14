package nisse.SlimeRecords;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // You MUST set the content view first!
        setContentView(R.layout.activity_settings);

        // Only add the fragment on first creation; on rotation the
        // FragmentManager restores the existing one (keeping scroll position).
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }

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