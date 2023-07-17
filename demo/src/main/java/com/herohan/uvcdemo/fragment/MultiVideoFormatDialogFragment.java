package com.herohan.uvcdemo.fragment;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.herohan.uvcdemo.R;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class MultiVideoFormatDialogFragment extends DialogFragment {
    private static final String RESOLUTION_SEPARATOR = "x";


    private List<Integer> mTypeList = new ArrayList<>();
    private LinkedHashMap<String, List<Integer>> mResolutionMap = new LinkedHashMap<>();
    private List<String> mResolutionList = new ArrayList<>();
    private List<Integer> mFrameRateList = new ArrayList<>();

    private LinkedHashMap<Integer, String> mTypeAndNameMap = new LinkedHashMap<>();
    private LinkedHashMap<Integer, LinkedHashMap<String, List<Integer>>> mTypeAndResolutionMap = new LinkedHashMap<>();

    private VideoFormatDialogFragment.OnVideoFormatSelectListener mOnVideoFormatSelectListener;

    private Spinner spVideoFormatFormat;
    private Spinner spVideoFormatResolution;
    private Spinner spVideoFormatFrameRate;
    private List<Format> mFormatListLeft;
    private List<Format> mFormatListRight;
    private Size mSizeLeft;
    private Size mSizeRight;

    public MultiVideoFormatDialogFragment(List<Format> formatListLeft, List<Format> formatListRight, Size sizeLeft, Size sizeRight) {
        mFormatListLeft = formatListLeft;
        mFormatListRight = formatListRight;
        mSizeLeft = sizeLeft.clone();
        mSizeRight = sizeRight.clone();
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.fragment_video_format,null);
        spVideoFormatFormat = view.findViewById(R.id.spVideoFormatFormat);
        spVideoFormatResolution = view.findViewById(R.id.spVideoFormatResolution);
        spVideoFormatFrameRate = view.findViewById(R.id.spVideoFormatFrameRate);

        updateDialogUI();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(R.string.video_format_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.video_format_ok_button, (dialog, which) -> {
            if (mOnVideoFormatSelectListener != null) {
                mOnVideoFormatSelectListener.onFormatSelect(mSizeLeft);
                mOnVideoFormatSelectListener.onFormatSelect(mSizeRight);

            }
            dismiss();
        });
        builder.setNegativeButton(R.string.video_format_cancel_button, (dialog, which) -> {
            dismiss();
        });
        return builder.create();
    }

    private void updateDialogUI() {
        fetchSpinnerData(mFormatListLeft, mSizeLeft, UVCCamera.UVC_VS_FRAME_UNCOMPRESSED);
        fetchSpinnerData(mFormatListRight, mSizeRight, UVCCamera.UVC_VS_FRAME_MJPEG);
        showAllSpinner();
        setListeners();
    }

    private void fetchSpinnerData(List<Format> formatList, Size size, int frameType) {
        for (Format format : formatList) {
            if (format.type == UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED && frameType == UVCCamera.UVC_VS_FRAME_UNCOMPRESSED) {
                int type = UVCCamera.UVC_VS_FRAME_UNCOMPRESSED;
                mTypeAndNameMap.put(type, getString(R.string.video_format_format_yuv));
                mTypeAndResolutionMap.put(type, new LinkedHashMap<String, List<Integer>>());
            } else if (format.type == UVCCamera.UVC_VS_FORMAT_MJPEG && frameType == UVCCamera.UVC_VS_FRAME_MJPEG) {
                int type = UVCCamera.UVC_VS_FRAME_MJPEG;
                mTypeAndNameMap.put(type, getString(R.string.video_format_format_mjped));
                mTypeAndResolutionMap.put(type, new LinkedHashMap<String, List<Integer>>());
            }
            for (Format.Descriptor descriptor : format.frameDescriptors) {
                if (descriptor.type == frameType) {
                    LinkedHashMap<String, List<Integer>> resolutionAndFpsMap = mTypeAndResolutionMap.get(descriptor.type);
                    List<Integer> fpsList = new ArrayList<>();
                    for (Format.Interval interval : descriptor.intervals) {
                        fpsList.add(interval.fps);
                    }
                    resolutionAndFpsMap.put(descriptor.width + RESOLUTION_SEPARATOR + descriptor.height, fpsList);
                }
            }
        }
    }

    private void showAllSpinner() {
        // Format Spinner
        refreshFormatSpinner();

        // Resolution Spinner
        refreshResolutionSpinner();

        // Frame Rate Spinner
        refreshFrameRateSpinner();
    }

    private void refreshFormatSpinner() {
        List<String> formatTextList = new ArrayList<>(mTypeAndNameMap.values());
        ArrayAdapter<String> formatAdapter = new ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, formatTextList);
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVideoFormatFormat.setAdapter(formatAdapter);
        mTypeList = new ArrayList<>(mTypeAndNameMap.keySet());
        spVideoFormatFormat.setSelection(mTypeList.indexOf(mSizeLeft.type));
        spVideoFormatFormat.setSelection(mTypeList.indexOf(mSizeRight.type));

    }

    private void refreshResolutionSpinner() {
        mResolutionMap = mTypeAndResolutionMap.get(mSizeLeft.type); // Changed to use mSizeLeft.type instead of mSizeRight.type
        List<String> resolutionTextList = new ArrayList<>(mResolutionMap.keySet());
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, resolutionTextList);
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVideoFormatResolution.setAdapter(resolutionAdapter);
        mResolutionList = new ArrayList<>(mResolutionMap.keySet());
        String resolution = mSizeLeft.width + RESOLUTION_SEPARATOR + mSizeLeft.height; // Changed to use mSizeLeft instead of mSizeRight
        int index = mResolutionList.indexOf(resolution);
        if (index == -1) {
            index = 0;
            String[] resolutions = mResolutionList.get(index).split(RESOLUTION_SEPARATOR);
            mSizeLeft.width = Integer.parseInt(resolutions[0]);
            mSizeLeft.height = Integer.parseInt(resolutions[1]);
            mSizeRight.width = Integer.parseInt(resolutions[0]);
            mSizeRight.height = Integer.parseInt(resolutions[1]);
        }
        spVideoFormatResolution.setSelection(index);
    }

    private void refreshFrameRateSpinner() {
        // Get the selected format type
        int selectedFormatType = spVideoFormatFormat.getSelectedItemPosition() != -1 ? mTypeList.get(spVideoFormatFormat.getSelectedItemPosition()) : -1;

        // Get the selected resolution
        String selectedResolution = spVideoFormatResolution.getSelectedItemPosition() != -1 ? mResolutionList.get(spVideoFormatResolution.getSelectedItemPosition()) : null;

        if (selectedFormatType != -1 && selectedResolution != null) {
            // Get the Frame Rate list for the selected resolution
            mFrameRateList = mResolutionMap.get(selectedResolution);
            ArrayAdapter<Integer> rateAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, mFrameRateList);
            rateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spVideoFormatFrameRate.setAdapter(rateAdapter);

            // Set the selected Frame Rate for the left camera
            int leftCameraFps = mSizeLeft.fps;
            int leftCameraFpsIndex = mFrameRateList.indexOf(leftCameraFps);
            if (leftCameraFpsIndex == -1) {
                leftCameraFpsIndex = 0;
                mSizeLeft.fps = mFrameRateList.get(leftCameraFpsIndex);
            }
            spVideoFormatFrameRate.setSelection(leftCameraFpsIndex);

            // Set the selected Frame Rate for the right camera
            int rightCameraFps = mSizeRight.fps;
            int rightCameraFpsIndex = mFrameRateList.indexOf(rightCameraFps);
            if (rightCameraFpsIndex == -1) {
                rightCameraFpsIndex = 0;
                mSizeRight.fps = mFrameRateList.get(rightCameraFpsIndex);
            }
            spVideoFormatFrameRate.setSelection(rightCameraFpsIndex);
        }
    }

    private void setListeners() {
        // Set listener of Format
        spVideoFormatFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectType = mTypeList.get(position);
                if (selectType != mSizeLeft.type) {
                    mSizeLeft.type = selectType;
                    mSizeRight.type = selectType; // Apply the selected format to both cameras
                    refreshResolutionSpinner();
                    refreshFrameRateSpinner();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        // Set listener of Resolution
        spVideoFormatResolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String[] resolutions = mResolutionList.get(position).split(RESOLUTION_SEPARATOR);
                int width = Integer.parseInt(resolutions[0]);
                int height = Integer.parseInt(resolutions[1]);
                if (mSizeLeft.width != width || mSizeLeft.height != height) {
                    mSizeLeft.width = width;
                    mSizeLeft.height = height;
                    mSizeRight.width = width;
                    mSizeRight.height = height;
                    refreshFrameRateSpinner();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        // Set listener of Format Rate
        spVideoFormatFrameRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int fps = mFrameRateList.get(position);
                if (mSizeLeft.fps != fps) {
                    mSizeLeft.fps = fps;
                    mSizeRight.fps = fps;
                    refreshFrameRateSpinner();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public void setOnVideoFormatSelectListener(VideoFormatDialogFragment.OnVideoFormatSelectListener listener) {
        this.mOnVideoFormatSelectListener = listener;
    }

    public interface OnVideoFormatSelectListener {
        void onFormatSelect(Size size);
    }
}