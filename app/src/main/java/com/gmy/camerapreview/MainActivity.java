package com.gmy.camerapreview;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import com.gmy.camerapreview.camera.CameraDisplay;
import com.gmy.camerapreview.utils.Accelerometer;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    private Accelerometer mAccelerometer = null;
    private CameraDisplay mCameraDisplay;
    private SurfaceView mSurfaceViewOverlap;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        this.initView();
    }
    private void initView() {
        mAccelerometer = new Accelerometer(getApplicationContext());
        GLSurfaceView glSurfaceView = (GLSurfaceView) findViewById(R.id.gsv_camera_display);
        mSurfaceViewOverlap = (SurfaceView) findViewById(R.id.sv_canvas_overlay);
        mCameraDisplay = new CameraDisplay(getApplicationContext(), glSurfaceView, mSurfaceViewOverlap);
        mSurfaceViewOverlap.getHolder().setFormat(PixelFormat.TRANSLUCENT);

    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
        mAccelerometer.start();

        mCameraDisplay.onResume();

    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();

        mAccelerometer.stop();
        mCameraDisplay.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraDisplay.onDestroy();
    }
}