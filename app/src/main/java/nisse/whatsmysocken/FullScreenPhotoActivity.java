package nisse.whatsmysocken;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

public class FullScreenPhotoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_photo);

        String path = getIntent().getStringExtra("path");
        com.github.chrisbanes.photoview.PhotoView photoView = findViewById(R.id.photo_view);

        Glide.with(this).load(path).into(photoView);
    }
}