package com.herohan.uvcdemo.fragment.pagefragments;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcdemo.MultiCameraNewActivity;
import com.herohan.uvcdemo.R;
import com.herohan.uvcdemo.fragment.DeviceListDialogFragment;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.util.concurrent.ConcurrentLinkedQueue;

public class MultiCameraFragment extends Fragment implements View.OnClickListener {

    private static final boolean DEBUG = true;
    private static final String TAG = MultiCameraNewActivity.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private ICameraHelper mCameraHelperLeft;
    private ICameraHelper mCameraHelperRight;

    private AspectRatioSurfaceView svCameraViewLeft;
    private AspectRatioSurfaceView svCameraViewRight;

    private UsbDevice mUsbDeviceLeft;
    private UsbDevice mUsbDeviceRight;
    private ConcurrentLinkedQueue<UsbDevice> mReadyUsbDeviceList = new ConcurrentLinkedQueue<>();
    private ConditionVariable mReadyDeviceConditionVariable = new ConditionVariable();

    private final Object mSync = new Object();

    private boolean mIsCameraLeftConnected = false;
    private boolean mIsCameraRightConnected = false;

    private DeviceListDialogFragment mDeviceListDialogLeft;
    private DeviceListDialogFragment mDeviceListDialogRight;

    private HandlerThread mHandlerThread;
    private Handler mAsyncHandler;

    private Button btnOpenCameraLeft;
    private Button btnCloseCameraLeft;
    private Button btnOpenCameraRight;
    private Button btnCloseCameraRight;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_multi_camera, container, false);
        btnOpenCameraLeft = view.findViewById(R.id.btnOpenCameraLeft);
        btnCloseCameraLeft = view.findViewById(R.id.btnCloseCameraLeft);
        btnOpenCameraRight = view.findViewById(R.id.btnOpenCameraRight);
        btnCloseCameraRight = view.findViewById(R.id.btnCloseCameraRight);
        svCameraViewRight = view.findViewById(R.id.svCameraViewRight);
        svCameraViewLeft = view.findViewById(R.id.svCameraViewLeft);



        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppCompatActivity activity = (AppCompatActivity) getActivity();

        // Set the title using the activity's ActionBar or Toolbar
        if (activity != null) {
            activity.setTitle(R.string.entry_multi_camera_new);
        }
        initViews();
        initListeners();

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mAsyncHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mHandlerThread.quitSafely();
        mAsyncHandler.removeCallbacksAndMessages(null);
    }

    private void initViews() {
        setCameraViewLeft();
        setCameraViewRight();
    }

    private void initListeners() {
        btnOpenCameraLeft.setOnClickListener(this);
        btnCloseCameraLeft.setOnClickListener(this);

        btnOpenCameraRight.setOnClickListener(this);
        btnCloseCameraRight.setOnClickListener(this);
    }

    private void setCameraViewLeft() {
        svCameraViewLeft.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        svCameraViewLeft.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelperLeft != null) {
                    mCameraHelperLeft.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelperLeft != null) {
                    mCameraHelperLeft.removeSurface(holder.getSurface());
                }
            }
        });
    }

    private void setCameraViewRight() {
        svCameraViewRight.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        svCameraViewRight.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelperRight != null) {
                    mCameraHelperRight.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelperRight != null) {
                    mCameraHelperRight.removeSurface(holder.getSurface());
                }
            }
        });
    }

    @Override
    public void onStart() {
        if (DEBUG) Log.d(TAG, "onStart:");
        super.onStart();
        initCameraHelper();
    }

    @Override
    public void onStop() {
        if (DEBUG) Log.d(TAG, "onStop:");
        super.onStop();
        clearCameraHelper();
    }

    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelperLeft == null) {
            mCameraHelperLeft = new CameraHelper();
            mCameraHelperLeft.setStateCallback(mStateListenerLeft);
        }

        if (mCameraHelperRight == null) {
            mCameraHelperRight = new CameraHelper();
            mCameraHelperRight.setStateCallback(mStateListenerRight);
        }
    }

    private void clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:");
        if (mCameraHelperLeft != null) {
            mCameraHelperLeft.release();
            mCameraHelperLeft = null;
        }

        if (mCameraHelperRight != null) {
            mCameraHelperRight.release();
            mCameraHelperRight = null;
        }
    }

    private void selectDeviceLeft(final UsbDevice device) {
        if (DEBUG) Log.v(TAG, "selectDeviceLeft:device=" + device.getDeviceName());
        mUsbDeviceLeft = device;

        mAsyncHandler.post(() -> {
            waitCanSelectDevice(device);

            if (mCameraHelperLeft != null) {
                mCameraHelperLeft.selectDevice(device);
            }
        });
    }

    private void selectDeviceRight(final UsbDevice device) {
        if (DEBUG) Log.v(TAG, "selectDeviceRight:device=" + device.getDeviceName());
        mUsbDeviceRight = device;

        mAsyncHandler.post(() -> {
            waitCanSelectDevice(device);

            if (mCameraHelperRight != null) {
                mCameraHelperRight.selectDevice(device);
            }
        });
    }

    /**
     * wait for only one camera need request permission
     *
     * @param device
     */
    private void waitCanSelectDevice(UsbDevice device) {
        mReadyUsbDeviceList.add(device);
        while (mReadyUsbDeviceList.size() > 1) {
            mReadyDeviceConditionVariable.block();
            mReadyDeviceConditionVariable.close();
        }
    }

    /**
     * remove ready camera that wait  for select
     *
     * @param device
     */
    private void removeSelectedDevice(UsbDevice device) {
        mReadyUsbDeviceList.remove(device);
        mReadyDeviceConditionVariable.open();
    }

    private final ICameraHelper.StateCallback mStateListenerLeft = new ICameraHelper.StateCallback() {
        private final String LOG_PREFIX = "ListenerLeft#";

        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onAttach:");
            synchronized (mSync) {
                if (mUsbDeviceLeft == null && !device.equals(mUsbDeviceRight)) {
                    selectDeviceLeft(device);
                }
            }
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onDeviceOpen:");
            if (mCameraHelperLeft != null && device.equals(mUsbDeviceLeft)) {
                UVCParam param = new UVCParam();
                param.setQuirks(UVCCamera.UVC_QUIRK_FIX_BANDWIDTH);
                mCameraHelperLeft.openCamera(param);
            }

            removeSelectedDevice(device);
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onCameraOpen:");
            if (mCameraHelperLeft != null && device.equals(mUsbDeviceLeft)) {
                mCameraHelperLeft.startPreview();

                Size size = mCameraHelperLeft.getPreviewSize();
                if (size != null) {
                    int width = size.width;
                    int height = size.height;
                    //auto aspect ratio
                    svCameraViewLeft.setAspectRatio(width, height);
                }

                mCameraHelperLeft.addSurface(svCameraViewLeft.getHolder().getSurface(), false);

                mIsCameraLeftConnected = true;
            }
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onCameraClose:");
            if (device.equals(mUsbDeviceLeft)) {
                if (mCameraHelperLeft != null) {
                    mCameraHelperLeft.removeSurface(svCameraViewLeft.getHolder().getSurface());
                }

                mIsCameraLeftConnected = false;
            }
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onDetach:");
            if (device.equals(mUsbDeviceLeft)) {
                mUsbDeviceLeft = null;
            }

            removeSelectedDevice(device);
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onCancel:");
            if (device.equals(mUsbDeviceLeft)) {
                mUsbDeviceLeft = null;
            }

            removeSelectedDevice(device);
        }
    };

    private final ICameraHelper.StateCallback mStateListenerRight = new ICameraHelper.StateCallback() {
        private final String LOG_PREFIX = "ListenerRight#";

        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onAttach:");
            synchronized (mSync) {
                if (mUsbDeviceRight == null && !device.equals(mUsbDeviceLeft)) {
                    selectDeviceRight(device);
                }
            }
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onDeviceOpen:");
            if (mCameraHelperRight != null && device.equals(mUsbDeviceRight)) {
                UVCParam param = new UVCParam();
                param.setQuirks(UVCCamera.UVC_QUIRK_FIX_BANDWIDTH);
                mCameraHelperRight.openCamera(param);
            }

            removeSelectedDevice(device);
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onCameraOpen:");
            if (mCameraHelperRight != null && device.equals(mUsbDeviceRight)) {
                mCameraHelperRight.startPreview();

                Size size = mCameraHelperRight.getPreviewSize();
                if (size != null) {
                    int width = size.width;
                    int height = size.height;
                    //auto aspect ratio
                    svCameraViewRight.setAspectRatio(width, height);
                }

                mCameraHelperRight.addSurface(svCameraViewRight.getHolder().getSurface(), false);

                mIsCameraRightConnected = true;
            }
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onCameraClose:");
            if (device.equals(mUsbDeviceRight)) {
                if (mCameraHelperRight != null) {
                    mCameraHelperRight.removeSurface(svCameraViewRight.getHolder().getSurface());
                }

                mIsCameraRightConnected = false;
            }
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onDetach:");
            if (device.equals(mUsbDeviceRight)) {
                mUsbDeviceRight = null;
            }

            removeSelectedDevice(device);
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, LOG_PREFIX + "onCancel:");
            if (device.equals(mUsbDeviceRight)) {
                mUsbDeviceRight = null;
            }

            removeSelectedDevice(device);
        }
    };

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnOpenCameraLeft) {
            // select a uvc device
            showDeviceListDialogLeft();
        } else if (v.getId() == R.id.btnCloseCameraLeft) {
            // close camera
            if (mCameraHelperLeft != null && mIsCameraLeftConnected) {
                mCameraHelperLeft.closeCamera();
            }
        } else if (v.getId() == R.id.btnOpenCameraRight) {
            // select a uvc device
            showDeviceListDialogRight();
        } else if (v.getId() == R.id.btnCloseCameraRight) {
            // close camera
            if (mCameraHelperRight != null && mIsCameraRightConnected) {
                mCameraHelperRight.closeCamera();
            }
        }
    }

    private void showDeviceListDialogLeft() {
        mDeviceListDialogLeft = new DeviceListDialogFragment(mCameraHelperLeft, mIsCameraLeftConnected ? mUsbDeviceLeft : null);
        mDeviceListDialogLeft.setOnDeviceItemSelectListener(usbDevice -> {
            if (mCameraHelperLeft != null && mIsCameraLeftConnected) {
                mCameraHelperLeft.closeCamera();
            }
            selectDeviceLeft(usbDevice);
        });

        mDeviceListDialogLeft.show(getChildFragmentManager(), "device_list_left");
    }

    private void showDeviceListDialogRight() {
        mDeviceListDialogRight = new DeviceListDialogFragment(mCameraHelperRight, mIsCameraRightConnected ? mUsbDeviceRight : null);
        mDeviceListDialogRight.setOnDeviceItemSelectListener(usbDevice -> {
            if (mCameraHelperRight != null && mIsCameraRightConnected) {
                mCameraHelperRight.closeCamera();
            }
            selectDeviceRight(usbDevice);
        });

        mDeviceListDialogRight.show(getChildFragmentManager(), "device_list_right");
    }

}