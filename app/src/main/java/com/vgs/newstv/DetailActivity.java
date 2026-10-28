package com.vgs.newstv;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

public class DetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        TextView titleView = findViewById(R.id.detailTitle);
        ImageView imageView = findViewById(R.id.detailImage);
        TextView descView = findViewById(R.id.detailDescription);

        String title = getIntent().getStringExtra("title");
        String image = getIntent().getStringExtra("image");
        String desc = getIntent().getStringExtra("description");

        titleView.setText(title);
        descView.setText(desc);

        if (image != null && !image.isEmpty()) {
            Glide.with(this).load(image).into(imageView);
        }
    }
}
