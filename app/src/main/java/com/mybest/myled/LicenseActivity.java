package com.mybest.myled;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ImageButton;

public class LicenseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(e -> {
            finish();
        });
    }
}