package com.example.ttxnetdemo;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.RemoteException;
import android.view.SurfaceHolder;

public class CameraInterface {
    public interface CameraListener   {
		public void initCameraPreview(int width, int height) throws RemoteException;
    	public void inputYv12Data(byte[] yv12, boolean isYv12, Size size);
//    	public void inputH264Data();
    } 
    
 	private Camera mCamera = null;
	private SurfaceHolder mSurfaceHolder = null;
	private boolean mAppOpenCamera = false;
	private long mRefCount = 0;
    private boolean mFrontCamera = false;
    private boolean mFlashOff = true;
    private int	mFrontCameraId = 1;
    private int mBackCameraId = 0;
    private boolean mAutoFocus = true;	//自动对焦
    private Size mPreviewSize;
    private CameraListener mCameraListener;	//点击事件
    private int mHopePreviewWidth = 0;		//期望预览的宽度
    private int mHopePreviewHeight = 0;		//期望预览的高度
//	private byte[] mVerBuf = null;
    public CameraInterface() {
    }
    
    public void setCameraListener(CameraListener listener) {
    	mCameraListener = listener;
    }
    
    public void setHopeResolution(int width, int height) {
    	mHopePreviewWidth = width;
    	mHopePreviewHeight = height;
    }
    
    public void setSurfaceHolder(SurfaceHolder holder) {
    	mSurfaceHolder = holder;
    }

	public synchronized void setAppOpenCamera(Boolean bOpen) {
		mAppOpenCamera = bOpen;
	}
	
	public synchronized Boolean getAppOpenCamera() {
		return mAppOpenCamera;
	}
	
    public Size getPreviewSize() {
    	return mPreviewSize;
    }
    
    public synchronized void tryCameraPermission() {
    	findAvailableCameras();
    	mCamera = getCameraInstance(mFrontCameraId);
    	stopCameraPreview();
    }
    
	public synchronized boolean createCameraView() {
		++ mRefCount;
		if (!mAppOpenCamera) {
			return startCameraPreview();
		} else {
			return false;
		}
	}
	
    public synchronized void releaseCameraView() {
    	-- mRefCount;
    	if (mRefCount == 0) {
    		stopCameraPreview();
    	}
    }
    
    //上层抓图时，调用摄像头时，调用此接口通知service
  	public void AppCreateCamera() {
  		mAppOpenCamera = true;
  		stopCameraPreview();
  	}
  	
  	//抓图结束时，释放摄像头时，调用些接口通知service
  	public void AppReleaseCamera() {
  		mAppOpenCamera = false;
  		if (mRefCount > 0) {
  			startCameraPreview();
  		}
  	}
  	
  	public boolean IsVideoLiving() {
  		return mRefCount > 0 ? true : false;
  	}
  	
    public synchronized boolean startCameraPreview() {
    	if (mCamera != null) {
			return true;
		}
		
		findAvailableCameras();
		// 创建Camera实例 
		if (mFrontCamera) {
			mCamera = getCameraInstance(mFrontCameraId);
		} else {
			mCamera = getCameraInstance(mBackCameraId);
		}
        if (mCamera != null) {
        	initPreview();
        	return true;
        } else {
        	return false;
        }
    }
    
    public synchronized boolean stopCameraPreview() {
    	boolean ret = false;
    	if (mCamera != null) {
    		try {
    			mCamera.setPreviewCallback(null);
	    		mCamera.setOneShotPreviewCallback(null);
    			mCamera.stopPreview();
	    	} catch (Exception e){
	            e.printStackTrace();
	        }
	    	mCamera.release();        // 为其它应用释放摄像头
    		mCamera = null;
    		ret = true;
		}
    	return ret;
    }
    
    public synchronized void initPreview() {
        // The Surface has been created, now tell the camera where to draw the preview.
     	if (mCamera != null) {
    		try {
				mCamera.setPreviewDisplay(mSurfaceHolder);
			} catch (IOException e) {
				e.printStackTrace();
			}
    		
            mCamera.setPreviewCallback(mPreviewCallback);
            Camera.Parameters parameters= mCamera.getParameters();  
            
            //设置YV12的格式   YYYY UV UV
            //planar YUV 4:2:0, 12bpp, 1 plane for Y and 1 plane for the UV components, which are interleaved (first byte U and the following byte V)
            //设置NV21的格式   YYYY VU VU
            //as above, but U and V bytes are swapped
            //parameters.setPreviewFormat(ImageFormat.YV12);
            parameters.setPreviewFormat(ImageFormat.NV21);
            //频率
            parameters.setAntibanding(Parameters.ANTIBANDING_50HZ);
            //设置大小
            List<Size> mSupportedPreviewSizes;
        	mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        	mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, mHopePreviewWidth, mHopePreviewHeight);
        	parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        	mCamera.setParameters(parameters);
//			try {
//				mCameraListener.initCameraPreview(mPreviewSize.height, mPreviewSize.width);
//			} catch (RemoteException e) {
//				e.printStackTrace();
//			}
//			mVerBuf = new byte[mPreviewSize.width * mPreviewSize.height * 2];
        	//用于重新初始化编码器，分辨率变化时，需要重新获取视频及重启编码器
            mCamera.startPreview();
            mCamera.setDisplayOrientation(90);
    	}
    }

    PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
	    /*获取预览帧视频*/
	    @Override
	    public void onPreviewFrame(byte[] data, Camera camera) {
        	Camera.Parameters parameters=camera.getParameters();  
            int imageFormat = parameters.getPreviewFormat();  
            Size size = parameters.getPreviewSize();
            //PIX_FMT_NV12 = YUV420 Semi-Planar   ///< planar YUV 4:2:0, 12bpp, 1 plane for Y and 1 plane for the UV components, which are interleaved (first byte U and the following byte V)
            //PIX_FMT_NV21 = COLOR_FormatYUV420Planar 		///< planar YUV 4:2:0, 12bpp, 1 plane for Y and 1 plane for the UV components, which are interleaved (first byte V and the following byte U)
            if (imageFormat == ImageFormat.NV21) {
	        	//byte[] yv12 = changeYUV420SP2P(data, data.length, size.width, size.height);
            	inputYv12Data(data, false, size);
	        } else if (imageFormat == ImageFormat.YV12) {
	        	inputYv12Data(data, true, size);
	        }
	    }												
    };

    private void inputYv12Data(byte[] yv12, boolean isYV12, Size size) {
    	//int yStride   = (int) (size.width / 16.0) * 16;  
	 	//int uvStride  = (int) ((yStride / 2) / 16.0) * 16;  
	 	//int uOffset = yStride * size.height;
	 	//int vOffset = uOffset + uvStride * size.height / 2;
//    	byte[] aa = new byte[1024];
    	if (mCameraListener != null) {
    		mCameraListener.inputYv12Data(yv12,  isYV12, size);

//    		mCameraListener.inputH264Data();
        	//System.arraycopy(yv12, 0, mYv12Buffer, 0, mYv12Buffer.length);
    		//YV12toYUV420PackedSemiPlanar(isYV12, yv12, mYuv420Buffer, size.width, size.height);
    		//mCameraListener.inputYuv420Data(mYuv420Buffer, size);
    		//mCameraListener.inputYuv420Data(yv12, size);
    	}
    }
    
    /*
	 * 取得合适的大小 
	 */
	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;
 
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
 
        int targetHeight = h;
 
        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
 
        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
	
    /**
	 * 获得可用的相机，并设置前后摄像机的ID
	 */
	public synchronized void findAvailableCameras() {
		Camera.CameraInfo info = new CameraInfo();
		int numCamera = Camera.getNumberOfCameras();
		for (int i = 0; i < numCamera; i++) {
			Camera.getCameraInfo(i, info);
			// 找到了前置摄像头
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				mFrontCameraId = info.facing;
			}
			// 招到了后置摄像头
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				mBackCameraId = info.facing;
			}
		}
	}
    
    /** 安全获取Camera对象实例的方法*/ 
	public static Camera getCameraInstance(int index){ 
	    Camera c = null; 
	    try { 
	    	if (index != -1) {
	    		c = Camera.open(index); // 试图获取Camera实例
	    	} else {
	    		c = Camera.open(); 
	    	}
	    } catch (Exception e){ 
	        // 摄像头不可用（正被占用或不存在）
	    	e.printStackTrace();
	    } 
	    return c; // 不可用则返回null
	}
	
	/** 检查设备是否提供摄像头 */ 
	private boolean checkCameraHardware(Context context) { 
	    if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){ 
	        // 摄像头存在 
	        return true; 
	    } else { 
	        // 摄像头不存在 
	        return false; 
	    } 
	}
    
    public synchronized boolean isSupportedZoom() {
        if (mCamera != null) {
            Parameters parameters = mCamera.getParameters();
            return parameters.isZoomSupported();
        }
        return false;
    }

    public synchronized int getMaxZoom() {
    	if (mCamera == null) {
            return 0;
        }
        
    	Parameters p = mCamera.getParameters();
        return p.getMaxZoom();
    }

    // 设置Zoom
    public synchronized void setZoom(int value) {
    	if (mCamera == null) {
            return ;
        }
    	
    	Parameters p = mCamera.getParameters();
        p.setZoom(value);
        mCamera.setParameters(p);
    }

    public synchronized boolean isSupportedFlashMode() {
        if (mCamera == null) {
            return false;
        }
        
        Parameters parameters = mCamera.getParameters();
        List<String> modes = parameters.getSupportedFlashModes();
        if (modes != null && modes.size() != 0) {
            boolean autoSupported = modes.contains(Parameters.FLASH_MODE_AUTO);
            boolean onSupported = modes.contains(Parameters.FLASH_MODE_ON);
            boolean offSupported = modes.contains(Parameters.FLASH_MODE_OFF);
            return autoSupported && onSupported && offSupported;
        }
        
        return false;
    }

    // 设置闪光灯模式
    public synchronized void SetFlashMode(String flashMode) {
    	if (mCamera != null) {
    		Parameters p = mCamera.getParameters();
    		p.setFlashMode(flashMode);
	        mCamera.setParameters(p);
    	}
    }
    
    // 多镜头切换
    public synchronized void switchCamera() {
    	mFrontCamera = !mFrontCamera;
    	stopCameraPreview();
        mFlashOff = true;
        startCameraPreview();
        //setDisplayOrientation(mCamera);
    }
    
    //判断是否为前置摄像头
    public synchronized boolean isFrontCamera() {
    	return mFrontCamera;
    }
    
    // 闪光切换
    public synchronized boolean switchFlash() {
    	if (!mFrontCamera) {
	    	mFlashOff = !mFlashOff;
	    	if (!mFlashOff) {
	    		SetFlashMode(Parameters.FLASH_MODE_TORCH);
	    	} else {
	    		SetFlashMode(Parameters.FLASH_MODE_OFF);
	    	}
	    	return true;
    	} else {
    		return false;
    	}
    }
    
    //获取闪光灯状态
    public synchronized boolean isFlashOff() {
    	return mFlashOff;
    }
    
    /*
     * 切换距焦模式
     */
    public synchronized void switchFocus() {
    	mAutoFocus = !mAutoFocus;
    	
    	String mode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
    	if (!mAutoFocus) {
    		mode = Camera.Parameters.FOCUS_MODE_AUTO;
    	}
    	
    	if (mCamera != null) {
	    	// 获取Camera parameters  
		    Camera.Parameters params = mCamera.getParameters();  
		    // 设置聚焦模式  
		    params.setFocusMode(mode);  
		    // 设置Camera parameters  
		    mCamera.setParameters(params);  
    	}
    }
    
    /*
     * 获取聚焦模式
     */
    public synchronized boolean isAutoFocus() {
    	return mAutoFocus;
    } 
}
