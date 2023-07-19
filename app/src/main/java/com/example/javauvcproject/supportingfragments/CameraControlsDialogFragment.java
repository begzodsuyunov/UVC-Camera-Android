package com.example.javauvcproject.supportingfragments;

import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import com.example.javauvcproject.R;
import com.herohan.uvcapp.ICameraHelper;

import java.lang.ref.WeakReference;

public class CameraControlsDialogFragment extends DialogFragment {


    private WeakReference<ICameraHelper> mCameraHelperWeak;

//    private IndicatorSeekBar isbBrightness;
//    private IndicatorSeekBar isbContrast;
//    private CheckBox cbContrastAuto;
//    private IndicatorSeekBar isbHue;
//    private CheckBox cbHueAuto;
//    private IndicatorSeekBar isbSaturation;
//    private IndicatorSeekBar isbSharpness;
//    private IndicatorSeekBar isbGamma;
//    private IndicatorSeekBar isbWhiteBalance;
//    private CheckBox cbWhiteBalanceAuto;
//    private IndicatorSeekBar isbBacklightComp;
//    private IndicatorSeekBar isbGain;
//    private IndicatorSeekBar isbExposureTime;
//    private CheckBox cbExposureTimeAuto;
//    private IndicatorSeekBar isbIris;
//    private IndicatorSeekBar isbFocus;
//    private CheckBox cbFocusAuto;
//    private IndicatorSeekBar isbZoom;
//    private IndicatorSeekBar isbPan;
//    private IndicatorSeekBar isbTilt;
//    private IndicatorSeekBar isbRoll;
//    private RadioGroup rgPowerLineFrequency;
//    private Button btnCameraControlsCancel;
//    private Button btnCameraControlsReset;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_controls_dialog, container, false);
    }
}