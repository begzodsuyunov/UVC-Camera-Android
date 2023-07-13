package com.herohan.uvcdemo;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.herohan.uvcdemo.fragment.pagefragments.CustomPreviewFragment;
import com.herohan.uvcdemo.fragment.pagefragments.HomeFragment;
import com.herohan.uvcdemo.fragment.pagefragments.MultiCameraFragment;
import com.herohan.uvcdemo.fragment.pagefragments.RecordVideoFragment;
import com.herohan.uvcdemo.fragment.pagefragments.TakePictureFragment;
import com.hjq.permissions.XXPermissions;

import java.util.ArrayList;
import java.util.List;

public class EntryActivity extends AppCompatActivity implements BottomNavigationView
        .OnNavigationItemSelectedListener{
    BottomNavigationView bottomNavigationView;

    CustomPreviewFragment customPreviewFragment;
    HomeFragment homeFragment;
    MultiCameraFragment multiCameraFragment;
    RecordVideoFragment recordVideoFragment;
    TakePictureFragment takePictureFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);


        customPreviewFragment = new CustomPreviewFragment();
        homeFragment = new HomeFragment();
        multiCameraFragment = new MultiCameraFragment();
        recordVideoFragment = new RecordVideoFragment();
        takePictureFragment = new TakePictureFragment();

        bottomNavigationView
                = findViewById(R.id.bottomNavigationView);

        bottomNavigationView
                .setOnNavigationItemSelectedListener(this);
        bottomNavigationView.setSelectedItemId(R.id.home);
    }

    @Override
    public boolean
    onNavigationItemSelected(@NonNull MenuItem item)
    {

        switch (item.getItemId()) {
            case R.id.home:
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.flFragment, homeFragment)
                        .commit();
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;

            case R.id.custom_cam:
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.flFragment, customPreviewFragment)
                        .commit();
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;
            case R.id.multi_cam:
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.flFragment, multiCameraFragment)
                        .commit();
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;

            case R.id.take_picture:
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.flFragment, takePictureFragment)
                        .commit();
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;
            case R.id.video_cam:
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.flFragment, recordVideoFragment)
                        .commit();
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;
        }
        return false;
    }

}