package com.example.javauvcproject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.javauvcproject.fragments.CustomPreviewFragment;
import com.example.javauvcproject.fragments.MultiCameraFragment;
import com.example.javauvcproject.fragments.RecordVideoFragment;
import com.example.javauvcproject.fragments.TakePictureFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BottomNavigationView
        .OnNavigationItemSelectedListener {
    private static final int REQUEST_CAMERA_PERMISSION = 101;

    BottomNavigationView bottomNavigationView;

    CustomPreviewFragment customPreviewFragment;
    MultiCameraFragment multiCameraFragment;
    RecordVideoFragment recordVideoFragment;
    TakePictureFragment takePictureFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        customPreviewFragment = new CustomPreviewFragment();
        multiCameraFragment = new MultiCameraFragment();
        recordVideoFragment = new RecordVideoFragment();
        takePictureFragment = new TakePictureFragment();

        bottomNavigationView
                = findViewById(R.id.bottomNavigationView);

        bottomNavigationView
                .setOnNavigationItemSelectedListener(this);
        bottomNavigationView.setSelectedItemId(R.id.custom_cam);
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            // Permission already granted
            switchToCustomCamera();
        }
    }
    private void switchToCustomCamera() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flFragment, customPreviewFragment)
                .commit();
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public boolean
    onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.custom_cam) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, customPreviewFragment)
                    .commit();
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return true;
        } else if (itemId == R.id.multi_cam) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, multiCameraFragment)
                    .commit();
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return true;
        } else if (itemId == R.id.take_picture) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, takePictureFragment)
                    .commit();
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return true;
        } else if (itemId == R.id.video_cam) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, recordVideoFragment)
                    .commit();
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                switchToCustomCamera();
            } else {
                // Permission denied
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

}