package com.example.javauvcproject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.javauvcproject.databinding.ActivityMainBinding;
import com.example.javauvcproject.fragments.CustomPreviewFragment;
import com.example.javauvcproject.fragments.MultiCameraFragment;
import com.example.javauvcproject.fragments.RecordVideoFragment;
import com.example.javauvcproject.fragments.TakePictureFragment;
import com.example.javauvcproject.fragments.supportingfragments.CameraControlsDialogFragment;
import com.example.javauvcproject.fragments.supportingfragments.DeviceListDialogFragment;
import com.example.javauvcproject.fragments.supportingfragments.VideoFormatDialogFragment;
import com.example.javauvcproject.utils.SaveHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.VideoCapture;
import com.serenegiant.opengl.renderer.MirrorMode;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.utils.FileUtils;
import com.serenegiant.utils.UriHelper;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final boolean DEBUG = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    /**
     * Camera preview width
     */
    private int mPreviewWidth = DEFAULT_WIDTH;
    /**
     * Camera preview height
     */
    private int mPreviewHeight = DEFAULT_HEIGHT;
    private UsbDevice mUsbDevice;

    private int mPreviewRotation = 0;

    private ICameraHelper mCameraHelper;

    private AspectRatioSurfaceView mCameraViewMain;
    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 102;

    private CameraControlsDialogFragment mControlsDialog;
    private VideoFormatDialogFragment mVideoFormatDialog;
    private Button btnOpenCamera;
    private Button btnCloseCamera;
    private Handler mAsyncHandler;
    private final Object mSync = new Object();

    private HandlerThread mHandlerThread;

    private boolean mIsCameraConnected = false;
    private ConcurrentLinkedQueue<UsbDevice> mReadyUsbDeviceList = new ConcurrentLinkedQueue<>();
    private ConditionVariable mReadyDeviceConditionVariable = new ConditionVariable();
    private ActivityMainBinding mBinding;

    private DeviceListDialogFragment mDeviceListDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());


        // Inflate the layout for this fragment

        // Check camera and storage permissions
        // Check camera and storage permissions
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
        };

        List<String> permissionList = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
            }
        }

        if (!permissionList.isEmpty()) {
            // Request the necessary permissions
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), REQUEST_CODE_WRITE_EXTERNAL_STORAGE);
        } else {
            // All permissions granted, proceed with initializing the camera
            initCamera();
        }

        setTitle(R.string.entry_custom_preview);
        initViews();

//        mHandlerThread = new HandlerThread(TAG);
//        mHandlerThread.start();
//        mAsyncHandler = new Handler(mHandlerThread.getLooper());
    }
    private void initCamera() {
        if (!mIsCameraConnected) {
            clearCameraHelper();
        }
        initCameraHelper();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();

        mHandlerThread.quitSafely();
        mAsyncHandler.removeCallbacksAndMessages(null);
    }
    @Override
    public void onStart() {
        super.onStart();
        initCameraHelper();
    }
    private void initViews() {
        mBinding.svCameraViewMain.setAspectRatio(mPreviewWidth, mPreviewHeight);
        mCameraViewMain = mBinding.svCameraViewMain;
        mCameraViewMain.setAspectRatio(mPreviewWidth, mPreviewHeight);
        mBinding.svCameraViewMain.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                // Handle surface changes if needed
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(holder.getSurface());
                }
            }
        });

        mBinding.btnOpenCamera.setOnClickListener(this);
        mBinding.btnCloseCamera.setOnClickListener(this);
        mBinding.btnCaptureVideo.setOnClickListener(this);
        initCameraHelper();

    }
    private void removeSelectedDevice(UsbDevice device) {
        mReadyUsbDeviceList.remove(device);
        mReadyDeviceConditionVariable.open();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_custom_preview, menu);
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mIsCameraConnected) {
            menu.findItem(R.id.action_video_format).setVisible(true);
            menu.findItem(R.id.action_rotate_90_CW).setVisible(true);
            menu.findItem(R.id.action_rotate_90_CCW).setVisible(true);
            menu.findItem(R.id.action_flip_horizontally).setVisible(true);
            menu.findItem(R.id.action_flip_vertically).setVisible(true);
        } else {
            menu.findItem(R.id.action_video_format).setVisible(false);
            menu.findItem(R.id.action_rotate_90_CW).setVisible(false);
            menu.findItem(R.id.action_rotate_90_CCW).setVisible(false);
            menu.findItem(R.id.action_flip_horizontally).setVisible(false);
            menu.findItem(R.id.action_flip_vertically).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_video_format) {
            showVideoFormatDialog();
        } else if (id == R.id.action_rotate_90_CW) {
            rotateBy(90);
        } else if (id == R.id.action_rotate_90_CCW) {
            rotateBy(-90);
        } else if (id == R.id.action_flip_horizontally) {
            flipHorizontally();
        } else if (id == R.id.action_flip_vertically) {
            flipVertically();
        }

        return true;
    }

    private void showDeviceListDialog() {


        mDeviceListDialog = new DeviceListDialogFragment(mCameraHelper, mIsCameraConnected ? mUsbDevice : null);
        mDeviceListDialog.setOnDeviceItemSelectListener(usbDevice -> {
            if (mCameraHelper != null && mIsCameraConnected) {
                mCameraHelper.closeCamera();
            }
            mUsbDevice = usbDevice;
            selectDevice(mUsbDevice);
        });

        mDeviceListDialog.show(getSupportFragmentManager(), "device_list");
    }
    private void showVideoFormatDialog() {
        if (mVideoFormatDialog != null && mVideoFormatDialog.isAdded()) {
            return;
        }

        mVideoFormatDialog = new VideoFormatDialogFragment(
                mCameraHelper.getSupportedFormatList(),
                mCameraHelper.getPreviewSize());

        mVideoFormatDialog.setOnVideoFormatSelectListener(size -> {
            if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                mCameraHelper.stopPreview();
                mCameraHelper.setPreviewSize(size);
                mCameraHelper.startPreview();

                resizePreviewView(size);
            }
        });

        mVideoFormatDialog.show(getSupportFragmentManager(), "video_format_dialog");
    }

    private void resizePreviewView(Size size) {
        // Update the preview size
        mPreviewWidth = size.width;
        mPreviewHeight = size.height;
        // Set the aspect ratio of SurfaceView to match the aspect ratio of the camera
        mBinding.svCameraViewMain.setAspectRatio(mPreviewWidth, mPreviewHeight);
    }

    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
            //setCustomVideoCaptureConfig();
        }
    }


//    private void setCustomVideoCaptureConfig() {
//        mCameraHelper.setVideoCaptureConfig(
//                mCameraHelper.getVideoCaptureConfig()
//                        .setAudioCaptureEnable(false)
//                        .setBitRate((int) (1024 * 1024 * 25 * 0.25))
//                        .setVideoFrameRate(25)
//                        .setIFrameInterval(1));
//    }
    private void closeAllDialogFragment() {
        if (mControlsDialog != null && mControlsDialog.isAdded()) {
            mControlsDialog.dismiss();
        }
        if (mDeviceListDialog != null && mDeviceListDialog.isAdded()) {
            mDeviceListDialog.dismiss();
        }
//        if (mFormatDialog != null && mFormatDialog.isAdded()) {
//            mFormatDialog.dismiss();
//        }
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
        if (mCameraHelper != null) {
            mCameraHelper.selectDevice(device);
        }
    }
    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        private final String LOG_PREFIX = "ListenerRight#";

        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onAttach:");

            synchronized (mSync) {
                selectDevice(device);

            }

        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:");
            //mCameraHelper.openCamera();

            if (mCameraHelper != null && device.equals(mUsbDevice)) {
                UVCParam param = new UVCParam();
                param.setQuirks(UVCCamera.UVC_QUIRK_FIX_BANDWIDTH);
                mCameraHelper.openCamera(param);

                mCameraHelper.setButtonCallback(new IButtonCallback() {
                    @Override
                    public void onButton(int button, int state) {
                        Toast.makeText(MainActivity.this, "onButton(button=" + button + "; " +
                                "state=" + state + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            removeSelectedDevice(device);
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:");
            if (mCameraHelper != null && device.equals(mUsbDevice)) {

                mCameraHelper.startPreview();

                Size size = mCameraHelper.getPreviewSize();
                if (size != null) {
                    resizePreviewView(size);
                }

                mCameraHelper.addSurface(mBinding.svCameraViewMain.getHolder().getSurface(), false);
                mIsCameraConnected = true;
            }
            invalidateOptionsMenu();


        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:");

            if (device.equals(mUsbDevice)) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
                }

                mIsCameraConnected = false;
                invalidateOptionsMenu();
                closeAllDialogFragment();
            }


        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:");
            if (device.equals(mCameraHelper)) {
                mUsbDevice = null;
            }

            removeSelectedDevice(device);
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel:");
            if (device.equals(mUsbDevice)) {
                mUsbDevice = null;
            }

            removeSelectedDevice(device);
        }

    };

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnOpenCamera) {
            // Camera permission is already checked in onCreate
            // Proceed with opening the camera
            showDeviceListDialog();
        } else if (v.getId() == R.id.btnCloseCamera) {
            // Close camera
            if (mCameraHelper != null && mIsCameraConnected) {
                mCameraHelper.closeCamera();
            }
        } else if (v.getId() == R.id.btnCaptureVideo) {
            // All permissions granted, proceed with recording video
            if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                if (mCameraHelper.isRecording()) {
                    stopRecord();
                } else {
                    startRecord();
                }
            } else {
                Toast.makeText(this, "Please open the camera first.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void rotateBy(int angle) {
        mPreviewRotation += angle;
        mPreviewRotation %= 360;
        if (mPreviewRotation < 0) {
            mPreviewRotation += 360;
        }

        if (mCameraHelper != null) {
            mCameraHelper.setPreviewConfig(
                    mCameraHelper.getPreviewConfig().setRotation(mPreviewRotation));
        }
    }

    private void flipHorizontally() {
        if (mCameraHelper != null) {
            mCameraHelper.setPreviewConfig(
                    mCameraHelper.getPreviewConfig().setMirror(MirrorMode.MIRROR_HORIZONTAL));
        }
    }

    private void flipVertically() {
        if (mCameraHelper != null) {
            mCameraHelper.setPreviewConfig(
                    mCameraHelper.getPreviewConfig().setMirror(MirrorMode.MIRROR_VERTICAL));
        }
    }

    private void startRecord() {
        Log.d(TAG, "startRecord: Recording started");
        File videoFile = SaveHelper.getSaveVideoFile(this);
        File videoDir = videoFile.getParentFile(); // Get the parent directory
        if (videoDir != null && !videoDir.exists()) {
            boolean created = videoDir.mkdirs(); // Create the parent directory if it doesn't exist
            if (!created) {
                Log.e(TAG, "Failed to create video directory");
                return;
            }
        }
        Uri videoUri = SaveHelper.getSaveVideoUri(this);
        VideoCapture.OutputFileOptions options = new VideoCapture.OutputFileOptions.Builder(videoFile).build();
        mCameraHelper.startRecording(options, new VideoCapture.OnVideoCaptureCallback() {
            @Override
            public void onStart() {
                Log.d(TAG, "onStart: Recording started callback");
                Toast.makeText(MainActivity.this, "recording ...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                Log.d(TAG, "onVideoSaved: Video saved");
                Toast.makeText(
                        MainActivity.this,
                        "Video saved at: " + videoUri.getPath(),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                Log.e(TAG, "onError: Video recording error: " + message, cause);
                Toast.makeText(MainActivity.this, "Video recording error: " + message, Toast.LENGTH_LONG).show();
            }
        });

        mBinding.btnCaptureVideo.setColorFilter(0x7fff0000);
    }

    private void stopRecord() {
        if (mCameraHelper != null) {
            mCameraHelper.stopRecording();
        }
    }


}