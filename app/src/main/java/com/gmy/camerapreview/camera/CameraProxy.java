package com.gmy.camerapreview.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraProxy {

    private static final String TAG = "CameraProxy";
    private boolean isDebug = true;

    private Context mContext;
    private int mCameraId;
    private Camera mCamera;

    private SurfaceTexture mSurfaceTexture;

    private int mCameraDirection;

    private Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    public CameraProxy(Context context) {
        mContext = context;
    }

    public Camera getCamera() {
        return mCamera;
    }

    public boolean openCamera(int cameraId) {
        try {
            releaseCamera();
            mCamera = Camera.open(cameraId);
            mCamera.getParameters();
            mCameraId = cameraId;
            mCamera.getCameraInfo(cameraId, mCameraInfo);

            setDefaultParameters();
        } catch (Exception e) {
            mCamera = null;
            Log.i(TAG, "openCamera fail msg=" + e.getMessage());
            return false;
        }
        return true;
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public void startPreview(SurfaceTexture surfaceTexture, Camera.PreviewCallback previewcallback){
        try {
            mCamera.setPreviewTexture(surfaceTexture);
            if (previewcallback != null) {
                mCamera.setPreviewCallback(previewcallback);
            }
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startPreview(){
        if(mCamera != null)
            mCamera.startPreview();
    }

    public void stopPreview(){
        if(mCamera != null)
            mCamera.stopPreview();
    }

    public Camera.Size getPreviewSize(){
        if(mCamera != null)
        {
            return mCamera.getParameters().getPreviewSize();
        }
        return null;
    }

    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        mCamera.setOneShotPreviewCallback(callback);
    }

    public void addPreviewCallbackBuffer(byte[] callbackBuffer) {
        mCamera.addCallbackBuffer(callbackBuffer);
    }


    public int getOrientation(){
        if(mCameraInfo == null){
            return 0;
        }

        return mCameraInfo.orientation;
    }

    public boolean isFlipHorizontal(){
        if(mCameraInfo == null){
            return false;
        }
        return mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? true : false;
    }

    public int getCameraId() {
        return mCameraId;
    }

    public boolean isFrontCamera(){
        return mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    public void setRotation(int rotation){
        if(mCamera != null)
        {
            Camera.Parameters params = mCamera.getParameters();
            params.setRotation(rotation);
            mCamera.setParameters(params);
        }
    }

    public void takePicture(Camera.ShutterCallback shutterCallback, Camera.PictureCallback rawCallback,
                            Camera.PictureCallback jpegCallback){
        if(mCamera != null)
        {
            mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }

    public int getDisplayOrientation(int dir) {
        /**
         * 请注意前置摄像头与后置摄像头旋转定义不同
         * 请注意不同手机摄像头旋转定义不同
         */
        int newdir = dir;
        if (isFrontCamera() &&
                ((mCameraInfo.orientation == 270 && (dir & 1) == 1) ||
                        (mCameraInfo.orientation == 90 && (dir & 1) == 0)))
        {
            newdir = (dir ^ 2);
        }
        return newdir;
    }

    public boolean needMirror(){
        if(isFrontCamera()){
            return true;
        }else{
            return false;
        }
    }

    private void setDefaultParameters(){
        Camera.Parameters parameters = mCamera.getParameters();
        Log.e(TAG, "parameters: " + parameters.flatten());
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        List<String> flashModes = parameters.getSupportedFlashModes();
        if(flashModes !=null && flashModes.contains(Camera.Parameters.FLASH_MODE_OFF))
        {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }

        Point previewSize = getSuitablePreviewSize();
//		parameters.setPreviewSize(1280, 720);
        parameters.setPreviewSize(640, 480);
        Point pictureSize = getSuitablePictureSize();
        parameters.setPictureSize(pictureSize.x, pictureSize.y);

        mCamera.setParameters(parameters);
    }

    public Camera.Parameters getParameters() {
        return mCamera.getParameters();
    }
    public void setPreviewSize(int width, int height){
        if(mCamera == null)
            return;
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(width, height);
        mCamera.setParameters(parameters);
    }

    private Point getSuitablePreviewSize(){
        Point defaultsize = new Point(1920, 1080);
        if(mCamera != null){
            List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
            for (Camera.Size s : sizes)
            {
                if((s.width == defaultsize.x)&&(s.height == defaultsize.y))
                {
                    return defaultsize;
                }
            }
            return new Point(640,480);
        }
        return null;
    }

    public ArrayList<String> getSupportedPreviewSize(String[] previewSizes)
    {
        ArrayList<String> result = new ArrayList<String>();
        if(mCamera != null)
        {
            List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
            for(String candidate : previewSizes)
            {
                int index = candidate.indexOf('x');
                if (index == -1) continue;
                int width = Integer.parseInt(candidate.substring(0, index));
                int height = Integer.parseInt(candidate.substring(index + 1));
                for(Camera.Size s : sizes){
                    if((s.width == width) && (s.height == height)){
                        result.add(candidate);
                    }
                }
            }
        }
        return result;
    }

    private Point getSuitablePictureSize(){
        Point defaultsize = new Point(4608, 3456);
        //	Point defaultsize = new Point(3264, 2448);
        if(mCamera != null){
            Point maxSize = new Point(0, 0);
            List<Camera.Size> sizes = mCamera.getParameters().getSupportedPictureSizes();
            for (Camera.Size s : sizes)
            {
                if((s.width == defaultsize.x)&&(s.height == defaultsize.y))
                {
                    return defaultsize;
                }
                if(maxSize.x < s.width)
                {
                    maxSize.x = s.width;
                    maxSize.y = s.height;
                }
            }
            return maxSize;
        }
        return null;
    }


    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    public float getAngleOfView(){
        return mCamera.getParameters().getVerticalViewAngle();
    }
}
