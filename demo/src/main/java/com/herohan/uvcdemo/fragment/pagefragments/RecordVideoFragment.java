package com.herohan.uvcdemo.fragment.pagefragments;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.VideoCapture;
import com.herohan.uvcdemo.BasicPreviewActivity;
import com.herohan.uvcdemo.R;
import com.herohan.uvcdemo.RecordVideoActivity;
import com.serenegiant.usb.Size;
import com.serenegiant.utils.FileUtils;
import com.serenegiant.utils.UriHelper;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.io.File;


public class RecordVideoFragment extends Fragment implements View.OnClickListener {
    private static final boolean DEBUG = true;
    private static final String TAG = BasicPreviewActivity.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private ICameraHelper mCameraHelper;

    private AspectRatioSurfaceView mCameraViewMain;

    private ImageButton bthCaptureVideo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record_video, container, false);

        bthCaptureVideo = view.findViewById(R.id.bthCaptureVideo);
        mCameraViewMain = view.findViewById(R.id.svCameraViewMain);


        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        AppCompatActivity activity = (AppCompatActivity) getActivity();

        // Set the title using the activity's ActionBar or Toolbar
        if (activity != null) {
            activity.setTitle(R.string.entry_record_video);
        }
    }


    private void initViews() {
        mCameraViewMain.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        mCameraViewMain.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(holder.getSurface());
                }
            }
        });

        bthCaptureVideo.setOnClickListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        initCameraHelper();
    }

    @Override
    public void onStop() {
        super.onStop();
        clearCameraHelper();
    }

    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

    private void clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:");
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void selectDevice(final UsbDevice device) {
        if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.getDeviceName());
        mCameraHelper.selectDevice(device);
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach:");
            selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:");
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:");

            mCameraHelper.startPreview();

            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                int width = size.width;
                int height = size.height;
                //auto aspect ratio
                mCameraViewMain.setAspectRatio(width, height);
            }

            mCameraHelper.addSurface(mCameraViewMain.getHolder().getSurface(), false);
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:");

            if (mCameraHelper != null) {
                mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
            }
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel:");
        }

    };

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bthCaptureVideo) {
            if (mCameraHelper != null) {
                if (mCameraHelper.isRecording()) {
                    stopRecord();
                } else {
                    startRecord();
                }
            }
        }
    }

    private void startRecord() {
        File file = FileUtils.getCaptureFile(requireContext(), Environment.DIRECTORY_MOVIES, ".mp4");
        VideoCapture.OutputFileOptions options =
                new VideoCapture.OutputFileOptions.Builder(file).build();

//        ContentValues contentValues = new ContentValues();
//        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO");
//        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
//
//        VideoCapture.OutputFileOptions options = new VideoCapture.OutputFileOptions.Builder(
//                getContentResolver(),
//                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
//                contentValues).build();

        mCameraHelper.startRecording(options, new VideoCapture.OnVideoCaptureCallback() {
            @Override
            public void onStart() {
            }

            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                Toast.makeText(
                        requireContext(),
                        "save \"" + UriHelper.getPath(requireContext(), outputFileResults.getSavedUri()) + "\"",
                        Toast.LENGTH_SHORT).show();

                bthCaptureVideo.setColorFilter(0);
            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();

                bthCaptureVideo.setColorFilter(0);
            }
        });

        bthCaptureVideo.setColorFilter(0x7fff0000);
    }

    private void stopRecord() {
        if (mCameraHelper != null) {
            mCameraHelper.stopRecording();
        }
    }
}