package com.herohan.uvcdemo.fragment.pagefragments;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcdemo.CustomPreviewActivity;
import com.herohan.uvcdemo.R;
import com.herohan.uvcdemo.fragment.CameraControlsDialogFragment;
import com.herohan.uvcdemo.fragment.DeviceListDialogFragment;
import com.herohan.uvcdemo.fragment.VideoFormatDialogFragment;
import com.serenegiant.opengl.renderer.MirrorMode;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.util.concurrent.ConcurrentLinkedQueue;

public class CustomPreviewFragment extends Fragment implements View.OnClickListener {
    private static final boolean DEBUG = true;
    private static final String TAG = CustomPreviewActivity.class.getSimpleName();

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

    private DeviceListDialogFragment mDeviceListDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Initializ e mCameraHelper and other necessary variables
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_custom_preview, container, false);

        btnOpenCamera = view.findViewById(R.id.btnOpenCamera);
        btnCloseCamera = view.findViewById(R.id.btnCloseCamera);
        mCameraViewMain = view.findViewById(R.id.svCameraViewMain);


        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppCompatActivity activity = (AppCompatActivity) getActivity();

        // Set the title using the activity's ActionBar or Toolbar
        if (activity != null) {
            activity.setTitle(R.string.entry_custom_preview);
        }
        initViews();

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mAsyncHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onDetach() {
        super.onDetach();
        clearCameraHelper();
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
//
//    @Override
//    public void onStop() {
//        super.onStop();
//        clearCameraHelper();
//    }

    private void initViews() {
        mCameraViewMain.setAspectRatio(mPreviewWidth, mPreviewHeight);

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

        btnOpenCamera.setOnClickListener(this);
        btnCloseCamera.setOnClickListener(this);
    }

    private void removeSelectedDevice(UsbDevice device) {
        mReadyUsbDeviceList.remove(device);
        mReadyDeviceConditionVariable.open();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_custom_preview, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
            menu.findItem(R.id.action_control).setVisible(true);
            menu.findItem(R.id.action_video_format).setVisible(true);
            menu.findItem(R.id.action_rotate_90_CW).setVisible(true);
            menu.findItem(R.id.action_rotate_90_CCW).setVisible(true);
            menu.findItem(R.id.action_flip_horizontally).setVisible(true);
            menu.findItem(R.id.action_flip_vertically).setVisible(true);
        } else {
            menu.findItem(R.id.action_control).setVisible(false);
            menu.findItem(R.id.action_video_format).setVisible(false);
            menu.findItem(R.id.action_rotate_90_CW).setVisible(false);
            menu.findItem(R.id.action_rotate_90_CCW).setVisible(false);
            menu.findItem(R.id.action_flip_horizontally).setVisible(false);
            menu.findItem(R.id.action_flip_vertically).setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_control) {
            showCameraControlsDialog();
        } else if (id == R.id.action_video_format) {
            showVideoFormatDialog();
        } else if (id == R.id.action_rotate_90_CW) {
            rotateBy(90);
        } else if (id == R.id.action_rotate_90_CCW) {
            rotateBy(-90);
        } else if (id == R.id.action_flip_horizontally) {
            flipHorizontally();
        } else if (id == R.id.action_flip_vertically) {
            flipVertically();
        } else if (id == R.id.action_device) {
            showDeviceListDialog();

        }

        return true;
    }

    private void showCameraControlsDialog() {
        if (mControlsDialog == null) {
            mControlsDialog = new CameraControlsDialogFragment(mCameraHelper);
        }

        // When DialogFragment is not showing
        if (!mControlsDialog.isAdded()) {
            mControlsDialog.show(getChildFragmentManager(), "controls_dialog");
        }
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

        mDeviceListDialog.show(getChildFragmentManager(), "device_list");
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

        mVideoFormatDialog.show(getChildFragmentManager(), "video_format_dialog");
    }

    private void resizePreviewView(Size size) {
        // Update the preview size
        mPreviewWidth = size.width;
        mPreviewHeight = size.height;
        // Set the aspect ratio of SurfaceView to match the aspect ratio of the camera
        mCameraViewMain.setAspectRatio(mPreviewWidth, mPreviewHeight);
    }

    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

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

                mCameraHelper.addSurface(mCameraViewMain.getHolder().getSurface(), false);
                mIsCameraConnected = true;
            }
            requireActivity().invalidateOptionsMenu();


        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:");

            if (device.equals(mUsbDevice)) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
                }

                mIsCameraConnected = false;
                requireActivity().invalidateOptionsMenu();
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
            // select a uvc device
            showDeviceListDialog();
        } else if (v.getId() == R.id.btnCloseCamera) {
            // close camera
            if (mCameraHelper != null && mIsCameraConnected) {
                mCameraHelper.closeCamera();
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


}