package com.example.ttxnetdemo;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, AutoFocusCallback, OnTouchListener {
    private CameraInterface mCamera;
	private SurfaceHolder mHolder;
	private ImageView mImageFocus;
	private boolean mAutoFocus = false;
	private boolean mHasSurfaceCreated = false;
 
    public CameraPreview(Context context) {
        super(context);
        
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setOnTouchListener(this);
    }
    
    public boolean isSurfaceCreated() {
    	return mHasSurfaceCreated;
    }

    public void SetCameraInterface(CameraInterface camera) {
    	mCamera = camera;
    }
    
    public void setImageViewFocus(ImageView ivFocus) {
    	mImageFocus = ivFocus;
    }
    
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
    	mHasSurfaceCreated = true;
    	mCamera.setSurfaceHolder(holder);
        mCamera.startCameraPreview();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    	mCamera.stopCameraPreview();
    	mHasSurfaceCreated = false;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }
        
        if (mCamera == null) {
        	return;
        }

        // stop preview before making changes
        mCamera.stopCameraPreview();
        
        // set preview size and make any resize, rotate or
        // reformatting changes here
        // start preview with new settings
       	surfaceCreated(mHolder);
    }
    
    @Override
	public void onAutoFocus(boolean success, Camera camera) {
    	if (mImageFocus != null) {
    		mImageFocus.setAlpha(0.0f);
    	}
	}
    
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN: 
			{
				if (!mAutoFocus && mImageFocus != null) {
					mImageFocus.setX(event.getX() - mImageFocus.getWidth()/2);
					mImageFocus.setY(event.getY() - mImageFocus.getHeight()/2);
					mImageFocus.setAlpha(1.0f);	
//					mCamera.autoFocus(this);
				}
			}
			break;
		default:
			break;
		}
		
		return false;
	}
	
}
