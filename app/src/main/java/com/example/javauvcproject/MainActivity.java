package com.example.javauvcproject;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

import static java.lang.String.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.FFmpeg;
import com.example.javauvcproject.databinding.ActivityMainBinding;
import com.example.javauvcproject.supportingfragments.CameraControlsDialogFragment;
import com.example.javauvcproject.supportingfragments.DeviceListDialogFragment;
import com.example.javauvcproject.supportingfragments.VideoFormatDialogFragment;
import com.example.javauvcproject.utils.SaveHelper;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.VideoCapture;
import com.serenegiant.opengl.renderer.MirrorMode;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final boolean DEBUG = true;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;
    private static final String TIME_FORMAT_PATTERN = "HH:mm:ss";
    private SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT_PATTERN, Locale.getDefault());
    /**
     * Camera preview width
     */
    private int mPreviewWidth = DEFAULT_WIDTH;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerUI();
            timerHandler.postDelayed(this, 1000); // Update every second
        }
    };
    /**
     * Camera preview height
     */
    private int mPreviewHeight = DEFAULT_HEIGHT;
    private UsbDevice mUsbDevice;

    private int mPreviewRotation = 0;

    private ICameraHelper mCameraHelper;
    private long remainingTime;

    private AspectRatioSurfaceView mCameraViewMain;
    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 102;

    private CameraControlsDialogFragment mControlsDialog;
    private VideoFormatDialogFragment mVideoFormatDialog;

    private final Object mSync = new Object();

    private boolean mIsCameraConnected = false;
    private ConcurrentLinkedQueue<UsbDevice> mReadyUsbDeviceList = new ConcurrentLinkedQueue<>();
    private ConditionVariable mReadyDeviceConditionVariable = new ConditionVariable();
    private ActivityMainBinding mBinding;
    private boolean isRecording = false;
    private Timer videoTimer;
    private long videoStartTime;
    private static final long VIDEO_DURATION = 60000; // 1 minute in milliseconds
    private Handler mHandler = new Handler();
    private Runnable mStopRecordRunnable;
    private Runnable mStartRecordRunnable;
    private HandlerThread ffmpegThread;
    private Handler ffmpegHandler;
    private String timeDuration;
    private Handler recordingHandler = new Handler();
    private DeviceListDialogFragment mDeviceListDialog;

    private static final String FONT_FILE_PATH = "android.resource://com.example.javauvcproject/" + R.font.times;
    private static final String OVERLAY_IMAGE_PATH = "file:android.resource://com.example.javauvcproject/drawable/images.jpg";

    private static final int OSD_FONT_SIZE = 36;
    private static final int OSD_POSITION_X = 10;
    private static final int OSD_POSITION_Y = 10;
    private static final String OSD_FONT_COLOR = "white";
    private static final String OSD_BOX_COLOR = "black@0.5";


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
        InitializingRunnable();
        copyFFmpegBinaryToInternalStorage();
        // Initialize FFmpeg thread and handler
        ffmpegThread = new HandlerThread("FFmpegThread");
        ffmpegThread.start();
        ffmpegHandler = new Handler(ffmpegThread.getLooper());
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

        timerHandler.removeCallbacksAndMessages(null); // Stop the timer to avoid memory leaks
        // Stop the FFmpeg thread
        if (ffmpegThread != null) {
            ffmpegThread.quitSafely();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        initCameraHelper();
        timerHandler.postDelayed(timerRunnable, 1000); // Start the timer to update UI every second

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

    private void closeAllDialogFragment() {
        if (mControlsDialog != null && mControlsDialog.isAdded()) {
            mControlsDialog.dismiss();
        }
        if (mDeviceListDialog != null && mDeviceListDialog.isAdded()) {
            mDeviceListDialog.dismiss();
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
        if (mCameraHelper != null) {
            mCameraHelper.selectDevice(device);
        }
    }


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
            recordingHandler.removeCallbacksAndMessages(mStartRecordRunnable);
            recordingHandler.removeCallbacksAndMessages(mStopRecordRunnable);

            if (mCameraHelper != null) {
                if (mCameraHelper.isRecording()) {
                    // Stop recording when the button is clicked again
                    stopRecord();
                } else {
                    // Start recording
                    starting1MinRecording();
                }
            }

        }
    }

    private void InitializingRunnable() {
        mStopRecordRunnable = () -> {
            stopRecord();
            recordingHandler.postDelayed(mStartRecordRunnable, 300L);
            //File videoFile = SaveHelper.getSaveVideoFile(MainActivity.this, videoStartTime);
            File videoFile = SaveHelper.getSaveVideoFile(MainActivity.this, videoStartTime);
            String currentDate = new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(new Date());

        };

        mStartRecordRunnable = () -> {
            startRecord();
            recordingHandler.postDelayed(mStopRecordRunnable, 60_000 - 300L);

        };
    }


    private void starting1MinRecording() {
        //Calculate the start time of the current video segment aligned to the nearest minute
        Calendar calendarSec = Calendar.getInstance();

        // Calculate the remaining time to the next minute
        long calendarSecond = calendarSec.get(Calendar.SECOND);
        long calendarMili = calendarSec.get(Calendar.MILLISECOND);
        long delay = (60 - calendarSecond) * 1000;
        System.out.println(calendarMili + "starting milli");

        startRecord();
        // Start the timer to automatically stop recording after the remaining time
        recordingHandler.postDelayed(mStopRecordRunnable, delay);
    }

    private void startRecord() {
        // Check if the camera is opened and not already recording
        if (mCameraHelper != null && mCameraHelper.isCameraOpened() && !mCameraHelper.isRecording()) {
            Log.d(TAG, "startRecord: Recording started");

            // Calculate the start time of the current video segment aligned to the nearest minute
            long currentTime = System.currentTimeMillis();
            videoStartTime = (currentTime / VIDEO_DURATION) * VIDEO_DURATION;


            recordingHandler.post(() -> {
                File videoFile = SaveHelper.getSaveVideoFile(MainActivity.this, videoStartTime);
                File videoDir = videoFile.getParentFile(); // Get the parent directory

                Uri videoUri = SaveHelper.getSaveVideoUri(MainActivity.this, videoStartTime);
                VideoCapture.OutputFileOptions options = new VideoCapture.OutputFileOptions.Builder(videoFile).build();

                if (videoDir != null && !videoDir.exists()) {
                    boolean created = videoDir.mkdirs(); // Create the parent directory if it doesn't exist
                    if (!created) {
                        Log.e(TAG, "Failed to create video directory");
                        return;
                    }
                }
                //mCameraHelper.stopRecording();

// Save the recorded video file with the overlay permanently using FFmpeg


                mCameraHelper.startRecording(options, new VideoCapture.OnVideoCaptureCallback() {
                    @Override
                    public void onStart() {
                        Log.d(TAG, "onStart: Recording started callback" + videoStartTime);
                        Toast.makeText(MainActivity.this, "recording ...", Toast.LENGTH_SHORT).show();
                        mBinding.btnCaptureVideo.setColorFilter(0x7fff0000);
                        System.out.println(videoStartTime + "starting video");

                        long currentTime = System.currentTimeMillis();
                        long elapsedTime = currentTime - videoStartTime;
                        remainingTime = VIDEO_DURATION - elapsedTime;
                    }


                    @Override
                    public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                        Log.d(TAG, "onVideoSaved: Video saved");
                        Toast.makeText(
                                MainActivity.this,
                                "Video saved at: " + videoUri.getPath(),
                                Toast.LENGTH_SHORT).show();
                        System.out.println("saved video");
                        // Execute FFmpeg command to add time stamp overlay and fix "moov atom not found" issue

                    }

                    @Override
                    public void onError(int videoCaptureError, @NonNull String message,
                                        @Nullable Throwable cause) {
                        Log.e(TAG, "onError: Video recording error: " + message, cause);
                        Toast.makeText(MainActivity.this, "Video recording error: " + message, Toast.LENGTH_LONG).show();

                    }
                });
            });
        }

    }
    private String copyTextToInternalStorage() {
        String textContent = "This is a custom text overlay"; // Replace this with the desired text
        String textFileName = "custom_text.txt";
        String textFilePath = getFilesDir() + File.separator + textFileName;

        try {
            FileOutputStream outputStream = new FileOutputStream(textFilePath);
            outputStream.write(textContent.getBytes());
            outputStream.close();
            return textFilePath;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    private void stopRecord() {
        if (mCameraHelper != null && mCameraHelper.isRecording()) {
            // Stop recording
            mCameraHelper.stopRecording();

            File videoFile = SaveHelper.getSaveVideoFile(MainActivity.this, videoStartTime);
            File videoDir = videoFile.getParentFile(); // Get the parent directory
            Uri videoUri = SaveHelper.getSaveVideoUri(MainActivity.this, videoStartTime);

//            if (videoDir != null && !videoDir.exists()) {
//                boolean created = videoDir.mkdirs(); // Create the parent directory if it doesn't exist
//                if (!created) {
//                    Log.e(TAG, "Failed to create video directory");
//                    return;
//                }
//            }




            mBinding.btnCaptureVideo.setColorFilter(0x00000000);
//            isRecording = false;
            Calendar calendarSec = Calendar.getInstance();

            // Calculate the remaining time to the next minute
            long calendarSecond = calendarSec.get(Calendar.SECOND);
            long calendarMili = calendarSec.get(Calendar.MILLISECOND);
            long delay = (60 - calendarSecond) * 1000;

            System.out.println(calendarSecond + "stopping");

        }
    }
    private void copyFFmpegBinaryToInternalStorage() {
        String ffmpegBinaryPath = getDir("ffmpeg", Context.MODE_PRIVATE) + File.separator + "ffmpeg";

        try {
            InputStream inputStream = getResources().openRawResource(R.raw.ffmpeg); // Assuming the binary file in res/raw is named "ffmpeg"
            File ffmpegFile = new File(ffmpegBinaryPath);

            FileOutputStream outputStream = new FileOutputStream(ffmpegFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            inputStream.close();
            outputStream.close();

            // Set executable permission on the copied FFmpeg binary
            ffmpegFile.setExecutable(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTimerUI() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - videoStartTime;
        long remainingTime = VIDEO_DURATION - elapsedTime;

        if (elapsedTime >= VIDEO_DURATION) {
            // The video recording duration has ended
            mBinding.tvVideoCurrentTime.setText("00:00:00");
            stopRecord();
            return;
        }

        // Calculate the seconds, minutes, and hours for elapsed and remaining time
        long elapsedSeconds = elapsedTime / 1000;
        long elapsedMinutes = elapsedSeconds / 60;
        long elapsedHours = elapsedMinutes / 60;

        long remainingSeconds = remainingTime / 1000;
        long remainingMinutes = remainingSeconds / 60;
        long remainingHours = remainingMinutes / 60;

        // Format the time strings to display in the TextView
        String formattedElapsedTime = format(Locale.getDefault(), "%02d:%02d:%02d", elapsedHours, elapsedMinutes % 60, elapsedSeconds % 60);
        String formattedRemainingTime = format(Locale.getDefault(), "%02d:%02d:%02d", remainingHours, remainingMinutes % 60, remainingSeconds % 60);

        // Update the TextView with the formatted time strings
        mBinding.tvVideoCurrentTime.setText(formattedElapsedTime);
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
                stopRecord();

                invalidateOptionsMenu();
                closeAllDialogFragment();
                mBinding.btnCaptureVideo.setColorFilter(0x00000000);

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


}