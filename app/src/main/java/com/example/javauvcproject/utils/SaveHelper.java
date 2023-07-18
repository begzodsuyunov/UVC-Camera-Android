package com.example.javauvcproject.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import com.example.javauvcproject.R;
import com.serenegiant.utils.UVCUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SaveHelper {
    private static final String AUTHORITY = "com.example.javauvcproject.fileprovider";

    private static String getBaseStoragePath(Context context) {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                + File.separator + context.getString(R.string.app_name);
    }

    private static File getOutputDirectory(Context context) {
        String parentPath = getBaseStoragePath(context) + File.separator
                + new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date())
                + File.separator;
        File folder = new File(parentPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static File getSavePhotoFile(Context context) {
        File outputDirectory = getOutputDirectory(context);
        return new File(outputDirectory,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg");
    }

    public static Uri getSavePhotoUri(Context context) {
        File photoFile = getSavePhotoFile(context);
        return FileProvider.getUriForFile(context, AUTHORITY, photoFile);
    }

    public static File getSaveVideoFile(Context context) {
        File outputDirectory = getOutputDirectory(context);
        return new File(outputDirectory,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4");
    }

    public static Uri getSaveVideoUri(Context context) {
        File videoFile = getSaveVideoFile(context);
        return FileProvider.getUriForFile(context, AUTHORITY, videoFile);
    }
}