/*
 * 项目名:android
 * 作者:高明阳
 * 邮箱:gaomingyang@migu.cn
 * 类名:CameraLandspaceDisplay.java
 * 包名:com.migu.ai.camera.CameraLandspaceDisplay
 * 当前修改时间:2020年12月09日 09:58:08
 * 上次修改时间:2020年12月09日 09:58:08
 * Copyright(c) 2020 咪咕视讯科技有限公司
 */
package com.gmy.camerapreview.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;


import com.gmy.camerapreview.glutils.OpenGLUtils;
import com.gmy.camerapreview.glutils.TextureRotationUtil;
import com.gmy.camerapreview.utils.Accelerometer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraLandspaceDisplay  implements GLSurfaceView.Renderer , SensorEventListener {

    private String TAG = "CameraDisplay";

    protected int mTextureId = OpenGLUtils.NO_TEXTURE;

    public int mImageWidth, mImageHeight;
    private GLSurfaceView mGlSurfaceView;
    private int mSurfaceWidth, mSurfaceHeight;

    public CameraProxy mCameraProxy;
    private SurfaceTexture mSurfaceTexture;
    private MyGLRender mGLRender;
    private int mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;

    private boolean mCameraChanging = false;
    private int mCurrentPreview = 0;
    private ArrayList<String> mSupportedPreviewSizes;

    private FloatBuffer mTextureBuffer;
    private boolean mIsPaused = false;
    private HandlerThread mHandlerThread;
    private Handler mProcessHandler;
    private static final int MESSAGE_PROCESS_IMAGE = 100;
    private SurfaceView mSurfaceViewOverlap;
    private byte[] mImageData;
    public byte[] mTmpBuffer;

    private boolean mIsChangingPreviewSize = false;


    public CameraLandspaceDisplay(Context context, GLSurfaceView glSurfaceView, SurfaceView surfaceView) {
        mCameraProxy = new CameraProxy(context);
        mGlSurfaceView = glSurfaceView;
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(this);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);


        mSurfaceViewOverlap = surfaceView;
        mSurfaceViewOverlap.setZOrderMediaOverlay(true);
        mSurfaceViewOverlap.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLRender = new MyGLRender();
        mTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
        initAction();
    }

    private void initAction() {
        mHandlerThread = new HandlerThread("ProcessImageThread");
        mHandlerThread.start();
        mProcessHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_PROCESS_IMAGE && !mIsPaused) {
                    mGlSurfaceView.requestRender();
                }
            }
        };
    }


    /**
     * 工作在opengl线程, 当前Renderer关联的view创建的时候调用
     *
     * @param gl
     * @param config
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        if (mIsPaused == true) {
            return;
        }
        GLES20.glEnable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glEnable(GL10.GL_DEPTH_TEST);

        if (mCameraProxy.getCamera() != null) {
            setUpCamera();
        }
    }

    /**
     * 工作在opengl线程, 当前Renderer关联的view尺寸改变的时候调用
     *
     * @param gl
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
        if (mIsPaused == true) {
            return;
        }
        adjustViewPort(width, height);
        mGLRender.init(mImageWidth, mImageHeight);
    }

    /**
     * 根据显示区域大小调整一些参数信息
     *
     * @param width
     * @param height
     */
    private void adjustViewPort(int width, int height) {
        mSurfaceHeight = height;
        mSurfaceWidth = width;
        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        mGLRender.calculateVertexBuffer(mSurfaceWidth, mSurfaceHeight, mImageWidth, mImageHeight);
    }

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        //        方法获取到的数据为YUV420格式数据
        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {

            if (mCameraChanging || mCameraProxy.getCamera() == null) {
                return;
            }

            if (mImageData == null || mImageData.length != mImageHeight * mImageWidth * 3 / 2) {
                mImageData = new byte[mImageWidth * mImageHeight * 3 / 2];
            }

            synchronized (mImageData) {
                System.arraycopy(data, 0, mImageData, 0, data.length);
            }

            mProcessHandler.removeMessages(MESSAGE_PROCESS_IMAGE);
            mProcessHandler.sendEmptyMessage(MESSAGE_PROCESS_IMAGE);

        }
    };

    /**
     * 工作在opengl线程, 具体渲染的工作函数
     *
     * @param gl
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        if (mCameraChanging || mCameraProxy.getCamera() == null) {
            return;
        }

        if (mSurfaceTexture != null) {
            mSurfaceTexture.updateTexImage();
        } else {
            return;
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        int textureId = mGLRender.preProcess(mTextureId, null);
        processImageData();
        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);

        mGLRender.onDrawFrame(textureId);
    }

    private int getCurrentOrientation() {
        int dir = Accelerometer.getDirection();
        int orientation = dir;
        if (orientation < 0) {
            orientation = dir ^ 3;
        }

        return orientation;
    }

    /**
     * camera设备startPreview
     */
    private void setUpCamera() {
        // 初始化Camera设备预览需要的显示区域(mSurfaceTexture)
        if (mTextureId == OpenGLUtils.NO_TEXTURE) {
            mTextureId = OpenGLUtils.getExternalOESTextureID();
            mSurfaceTexture = new SurfaceTexture(mTextureId);
        }

        if (mSupportedPreviewSizes == null || mSupportedPreviewSizes.size() == 0) {
            return;
        }
        String size = mSupportedPreviewSizes.get(mCurrentPreview);
        int index = size.indexOf('x');
        mImageWidth = Integer.parseInt(size.substring(0, index));
        mImageHeight = Integer.parseInt(size.substring(index + 1));

        mCameraProxy.setPreviewSize(mImageWidth,mImageHeight);
        mGLRender.adjustLandscapeTextureBuffer(180, false);
        mCameraProxy.startPreview(mSurfaceTexture, mPreviewCallback);
    }

    public void onResume() {
        Log.i(TAG, "onResume");
        mIsPaused = false;
        if (mCameraProxy.getCamera() == null) {
//            if (mCameraProxy.getNumberOfCameras() == 2) {
//                mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
//            }
            mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
            mCameraProxy.openCamera(mCameraID);
            mSupportedPreviewSizes = mCameraProxy.getSupportedPreviewSize(new String[]{"1280x720", "640x480"});
        }
        mGLRender = new MyGLRender();

        mGlSurfaceView.onResume();
        mGlSurfaceView.forceLayout();
        mGlSurfaceView.requestRender();
    }

    public void onPause() {
        Log.i(TAG, "onPause");
        mIsPaused = true;
        mCameraProxy.releaseCamera();

        mGlSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {

                deleteTextures();
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.release();
                }
                mGLRender.destroyFrameBuffers();
            }
        });

        mGlSurfaceView.onPause();
    }

    public void onDestroy() {

        mImageData = null;
        mTmpBuffer = null;
        if (mProcessHandler != null) {
            mProcessHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * 释放纹理资源
     */
    protected void deleteTextures() {
        if (mTextureId != OpenGLUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, new int[]{mTextureId}, 0);
        }
        mTextureId = OpenGLUtils.NO_TEXTURE;
        mGLRender.deleteSegmentResultTexture();
    }

    public void switchCamera() {
        //清屏
        clearOverLap();

        if (Camera.getNumberOfCameras() == 1
                || mCameraChanging) {
            return;
        }
        mCameraID = 1 - mCameraID;
        mCameraChanging = true;
        mCameraProxy.openCamera(mCameraID);
        mGlSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                deleteTextures();
                if (mCameraProxy.getCamera() != null) {
                    setUpCamera();
                }
                mCameraChanging = false;
            }
        });

        mGlSurfaceView.requestRender();
    }

    public void changePreviewSize(int currentPreview) {
        if (mCameraProxy.getCamera() == null || mCameraChanging
                || mIsPaused) {
            return;
        }

        mCurrentPreview = currentPreview;

        mIsChangingPreviewSize = true;
        mCameraChanging = true;

        mCameraProxy.stopPreview();
        mGlSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {

                deleteTextures();
                if (mCameraProxy.getCamera() != null) {
                    setUpCamera();
                }

                mGLRender.init(mImageWidth, mImageHeight);
                mGLRender.calculateVertexBuffer(mSurfaceWidth, mSurfaceHeight, mImageWidth, mImageHeight);
                mCameraChanging = false;
                mIsChangingPreviewSize = false;
                Log.d(TAG, "exit  change Preview size queue event");
            }
        });
    }

    private void processImageData() {
        if (mImageData == null) {
            return;
        }
        if (mTmpBuffer == null || mTmpBuffer.length != mImageHeight * mImageWidth * 3 / 2) {
            mTmpBuffer = new byte[mImageWidth * mImageHeight * 3 / 2];
        }

        if (mCameraChanging || mTmpBuffer.length != mImageData.length) {
            clearOverLap();
            return;
        }
        synchronized (mImageData) {
            System.arraycopy(mImageData, 0, mTmpBuffer, 0, mImageData.length);
        }

        //如果使用前置摄像头，请注意显示的图像与帧图像左右对称，需处理坐标
        boolean frontCamera = (mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT);

        //获取重力传感器返回的方向
        int dir = Accelerometer.getDirection();

        //在使用后置摄像头，且传感器方向为0或2时，后置摄像头与前置orentation相反
        if (!frontCamera && dir == 0) {
            dir = 2;
        } else if (!frontCamera && dir == 2) {
            dir = 0;
        }

        // 请注意前置摄像头与后置摄像头旋转定义不同
        // 请注意不同手机摄像头旋转定义不同
        if (((mCameraProxy.getOrientation() == 270 && (dir & 1) == 1) ||
                (mCameraProxy.getOrientation() == 90 && (dir & 1) == 0))) {
            dir = (dir ^ 2);
        }
        int mirror = 0;
        if (mCameraProxy.isFrontCamera()) {
            mirror = 1;
        } else {
            mirror = 0;
        }

        if (mCameraChanging || mIsPaused) {
            clearOverLap();
            return;
        }

        if (mCameraChanging || mIsPaused) {
            clearOverLap();
            return;
        }
    }

    private void clearOverLap() {
        if (!mSurfaceViewOverlap.getHolder().getSurface().isValid()) {
            return;
        }

        Canvas canvas = mSurfaceViewOverlap.getHolder().lockCanvas();
        if (canvas == null) {
            return;
        }

        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mSurfaceViewOverlap.getHolder().unlockCanvasAndPost(canvas);
    }


    public boolean isChangingPreviewSize() {
        return mIsChangingPreviewSize;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
