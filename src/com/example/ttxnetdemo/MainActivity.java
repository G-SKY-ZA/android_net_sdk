package com.example.ttxnetdemo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.example.ttxnetdemo.CameraInterface.CameraListener;

import net.babelstar.gdispatch.service.TtxNetwork;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

	private static final int AIDL_BYTE_PACK_LENGTH = 1024000;
	
	public static final int TTX_RESOLUTION_QCIF	= 1;	//分辨率
	public static final int TTX_RESOLUTION_CIF	= 2;	//分辨率
	public static final int TTX_RESOLUTION_HD1	= 3;	//分辨率
	public static final int TTX_RESOLUTION_D1	= 4;	//分辨率
	public static final int TTX_RESOLUTION_720P	= 5;	//分辨率
	public static final int TTX_RESOLUTION_1080P	= 6;	//分辨率

	private FrameLayout mPreviewLayout;
	private CameraInterface mCamera;
	private CameraPreview mPreview;
	private TtxNetwork mNetBind;
	private boolean mBindSuc = false;
	private MessageReceiver mMessageReceiver;
	private TtxMessageListener mMessageListener = new TtxMessageListener();
	private Integer mYuvIndex = 0;
	private byte[] mTempYuvbuf = new byte[AIDL_BYTE_PACK_LENGTH];
	private Integer mChannel = 0;
	private Integer mNaluIndex = 0;
	private byte[] mTempNalubuf = new byte[AIDL_BYTE_PACK_LENGTH];
	
	private Handler mHandler = new MyHandler();
	private int DELAY_TIMER_STATUS = 2000;	//15秒获取一次状态的时间间隔
	private Timer mTimer;		//用来获取实时状态
	private static final int MESSAGE_STATUS = 4;
	
	private boolean mIsOnline = false;
	private boolean mIsLive = false;
	private boolean mIsTalkback = false;
	private boolean mIsListen = false;
	private boolean mIsAppPosition = true; //是否用app定位
	//protected static Context mContext;
	private String mtext;
	
	private Button mBtnPttTalk;
	private boolean mIsPttTalking = false;
	
	private String mServer;
	private String mAccount;
	
	private static final int MESSAGE_POSITION_STATUS = 5;
	private int DELAY_TIMER_POSITION_STATUS = 15000;	//15秒获取一次状态的时间间隔
	private Timer mPositionTimer;		//用来获取实时状态
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mPreviewLayout = (FrameLayout) findViewById(R.id.live_surface_camera);

		mPreview = new CameraPreview(this);
		mCamera = new CameraInterface();
		//mCamera.setHopeResolution(1920, 1080);
		mCamera.setHopeResolution(640, 480);	
		mCamera.setCameraListener(mCameraListener);
		mPreview.SetCameraInterface(mCamera);
		mPreviewLayout.addView(mPreview);
		
		mBtnPttTalk = (Button) findViewById(R.id.btnPttTalk);
		ButtonClickListener buttonClickListen = new ButtonClickListener();
		mBtnPttTalk.setOnClickListener(buttonClickListen);
		
		mBindSuc = false;
		
		//看门狗服务，会守护net.babelstar.gdispatch.remoteservice服务，当net.babelstar.gdispatch.remoteservice当机后，会再把net.babelstar.gdispatch.remoteservice重新启动
		Intent intentDeamon = new Intent("net.babelstar.gdispatch.dogservice");		
		intentDeamon.setPackage("net.babelstar.gdispatch"); 
		startService(intentDeamon);
		//开始ttx网络服务，此服务主要与ttx cmsv6 server进行网络通信，进行音视频传输，位置上报等业务
		Intent intent = new Intent("net.babelstar.gdispatch.remoteservice");
		//执法仪版本用的包名
		intent.setPackage("net.babelstar.gdispatch");
		startService(intent);
		//intentDeamon.setPackage("net.babelstar.gdispatch"); 
		
		//当net.babelstar.gdispatch.remoteservice没有启动时，bindService调用可能会失败，请先手动启动CMSCruise APP程序，此APP启动时会创建好net.babelstar.gdispatch.remoteservice这个服务
		//注意，第一次绑定成功后，配置参数，会造成 net.babelstar.gdispatch.remoteservice服务挂掉，请重新运行 CMSCruise APP，再启用ttxnetdemo即可恢复正常		
		bindService(intent, mServerConnection, BIND_AUTO_CREATE);
		registerMessageReceiver();
		
		runTimerStatus();
		
		// 车辆位置状态
		if(!mIsAppPosition){
			runTimerPositionStatus();
		}
		
	}

	@Override
	protected void onPause() {
		super.onPause();
		mCamera.stopCameraPreview(); // 在暂停事件中立即释放摄像头
	}

	@Override
	protected void onStop() {
		super.onStop();
		mCamera.stopCameraPreview();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCamera.startCameraPreview();
	}
	
	protected void onDestroy() {
		super.onDestroy();
		unregisterMessageReceiver();
		unbindService(mServerConnection);
	}
	
	private ServiceConnection mServerConnection = new ServiceConnection() {	
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBindSuc = false;
			mNetBind = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder bind) {
			mNetBind = TtxNetwork.Stub.asInterface(bind);	
			try {
				//注意，第一次绑定成功后，配置参数，会造成 net.babelstar.gdispatch.remoteservice服务挂掉，请重新运行 CMSCruise APP，再启用ttxnetdemo即可恢复正常
				//设置通道数目，1表示1通道
				mNetBind.setChnCount(2);
				//设置app是否要自动操作摄像头
				mNetBind.setUsedCamera(false);
				mNetBind.SetAccStatus(true);
				mNetBind.IsAppPosition(false);
				//是否打开网络定位 默认true打开，false关闭
				mNetBind.IsNetWorkPosition(false);
				//后视镜  设置服务器IP和设备编号 
				//120.26.98.110服务器上，测试可使用的设备编号有112233, 67706,67707,67708 请选择一个未在线的设备做测试，另不建议使用  112233这个编号来做测试
				//客户端信息  账号：szhsj 密码：000000 服务器：http://120.26.98.110
				//登录界面上有windows客户端下载，也可以下载windows客户端进行测试
				
				mNetBind.setServerAndAccount("39.108.194.249", "10006");
				//执法仪 
				//mNetBind.setServerAndAccount("39.104.57.38", "10005");
				//设置GPS上报间隔
				mNetBind.setGpsInterval(10);
				//设置不进行主码流和子码流录像，默认会进行通道1的码流录像
				mNetBind.setRecord(false, false);
				mNetBind.setVideoEncode(false);
				//设置不进行视频编码，如果输入264数据，则要调用 setVideoEncode(false)，
				//如果输入yuv数据，则要调用setVideoEncode(true)，默认为true
				//mNetBind.setVideoEncode(false);
				//设置预览大小，如果是直接送h264数据，则不需要调用initCameraPreview
				Size previewSize = mCamera.getPreviewSize();
//				previewSize.width = 480;
//				previewSize.height = 640;
				//配置通道1的预览大小
				mNetBind.initCameraPreview(mChannel, previewSize.width, previewSize.height);
//				//如果配置成2个通道，则需要mNetBind.initCameraPreview(1，配置通道2的预览大小
				mNetBind.initCameraPreview(1, previewSize.width, previewSize.height);
				//设置音频格式和视频类型 264，265
				mNetBind.setMediaInfoEx(12, 0, 0,0);
//				mNetBind.setMediaInfoEx(12, 0, 0, 0);
				//设置分辨率
				//doChangeResolution  最后一个参数，0是CIF，1是D1，2是720P，3是1080P
				//设置通道1子码流分辨率为D1
				//设置通道1主码流分辨率为720P
				mNetBind.doChangeResolution(0, 1, 1);
				mNetBind.doChangeResolution(0, 0, 2);
				mBindSuc = true;
				mServer = mNetBind.getServerIp();
				mAccount = mNetBind.getServerAccount();
				
				//设置是否使用回音消除
//				mNetBind.SetEchoParam(true, 109);
				// 设置设备录像路径 bIsSetRecPath:true 有效
				//mNetBind.SetRecPathEx(recPath, jpgPath, sdLogPath, bIsSetRecPath);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	};
	
	protected CameraListener mCameraListener = new CameraListener()   {
		@Override
		public void initCameraPreview(int width, int height) throws RemoteException {
//			if(mBindSuc && mNetBind != null){
//				mNetBind.initCameraPreview(0, width, height);
//				mNetBind.doChangeResolution(0, 1, 1);
//				mNetBind.doChangeResolution(0, 0, 2);
//			}

//			如果配置成2个通道，则需要mNetBind.initCameraPreview(1，配置通道2的预览大小
//			mNetBind.initCameraPreview(1, width, height);
		}

		public void inputYv12Data(byte[] yv12, boolean isYv12, Size size) {
    		++ mYuvIndex;
    		if (mBindSuc && mNetBind != null) {
    			try {    				
    				 
    				int totalLen = size.width * size.height * 3 / 2;
    				int offset = 0;
    				int packMaxLength = AIDL_BYTE_PACK_LENGTH;
    				int packIndex = 0;
    				int packCount = totalLen / packMaxLength;
    				if ( (totalLen % packMaxLength) > 0 ) {
    					packCount += 1;
    				}
    				if (packCount == 1) {
    					//输入通道1
    					mNetBind.inputYv12Data(0, mYuvIndex, packIndex, packCount, yv12, isYv12, offset, totalLen, size.width, size.height);
    					//输入通道2数据
    					//mNetBind.inputYv12Data(1, mYuvIndex, packIndex, packCount, yv12, isYv12, offset, totalLen, size.width, size.height);
    				} else {
 	    				int packLength = 0;
	    				while (offset < totalLen) {
	    					if (offset + packMaxLength >= totalLen) {
	    						packLength = totalLen - offset;
	    					} else {
	    						packLength = packMaxLength;
	    					}
	    					System.arraycopy(yv12, offset, mTempYuvbuf, 0, packLength);
	    					//输入通道1
	    					mNetBind.inputYv12Data(0, mYuvIndex, packIndex, packCount, mTempYuvbuf, isYv12, offset, packLength, size.width, size.height);
	    					//输入通道2
	    					//mNetBind.inputYv12Data(1, mYuvIndex, packIndex, packCount, mTempYuvbuf, isYv12, offset, packLength, size.width, size.height);
	    					offset += packLength;
	    					++ packIndex;
	    				}
    				}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    		}
    	}


    };

	public ContextWrapper mContext;
    
    protected void inputH264Nalu(int channel, int stream, byte[] nalu, int naluLength) {
    	//channel为通道号，0表示通道1，1表示通道2,
    	//stream为码流类型，0为主码流，1为子码流
    	//输入264数据时，要把  SPS, PPS, SEI, IDR 合并成一帧，或者把 SPS, PPS, IDR合并成一帧
    	//264数据是以0x00, 0x00, 0x00, 0x01开头的数据
    	++ mNaluIndex;    	
		if (mBindSuc && mNetBind != null) {
			try {
				int totalLen = naluLength;
				int offset = 0;
				int packMaxLength = AIDL_BYTE_PACK_LENGTH;
				int packIndex = 0;
				int packCount = totalLen / packMaxLength;
				if ( (totalLen % packMaxLength) > 0 ) {
					packCount += 1;
				}
				if (packCount == 1) {
					mNetBind.inputH264Data(channel, stream, mNaluIndex, packIndex, packCount, nalu, offset, naluLength, naluLength);
				} else {
	    			int packLength = 0;
    				while (offset < totalLen) {
    					if (offset + packMaxLength >= totalLen) {
    						packLength = totalLen - offset;
    					} else {
    						packLength = packMaxLength;
    					}
    					System.arraycopy(nalu, offset, mTempNalubuf, 0, packLength);
    					mNetBind.inputH264Data(channel, stream, mNaluIndex, packIndex, packCount, mTempNalubuf, offset, packLength, naluLength);
    					offset += packLength;
    					++ packIndex;
    				}
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
    }
    
    protected void inputAacData(int channel, byte aac[], int length) {
    	if (mBindSuc && mNetBind != null) {
			try {
				mNetBind.inputAacData(channel, aac, length);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
    	}
    }
    
    //输入对讲输入pcm 8k采样率数据
    //启用回音消除调用(初始化调用)SetEchoParam(boolean bIsOpenEcho, int nDelay);
    //bIsOpenEcho:true 
    //nDelay 毫秒 大于0 建议用109
    protected void InputPcmData(byte[] pWavBuf, int nWavLen) {
    	if (mBindSuc && mNetBind != null) {
			try {				
							
				mNetBind.InputPcmData(pWavBuf, nWavLen);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public static final int TTX_ALARM_TYPE_URGENCY_BUTTON = 2;		//紧急按钮报警AlarmInfo为按钮编号
  	public static final int TTX_ALARM_TYPE_CUSTOM = 113;	//自定义报警
  	public static final int CUSTOM_ALARM_TYPE_REPORT_VIDEO		= 44;	//上报实时视频（客户端做主动视频弹出）
  	public static final int CUSTOM_ALARM_TYPE_STOP_REPORT_VIDEO	= 45;	//停止上报实时视频（客户端把主动弹出的视频关闭掉）
  	public static final int TTX_ALARM_TYPE_UPLOAD_FILE = 109;		//紧急按钮报警AlarmInfo为按钮编号
  	public static final int TTX_ALARM_TYPE_SHAKE = 3;		//振动报警AlarmInfo（加速度报警，bit0：X方向,bit1：Y方向,bit2：Z方向，bit3：碰撞,bit4：侧翻）
  	public static final int TTX_ALARM_TYPE_END_SHAKE = 53;		//振动报警结束
  	public static final int TTX_ALARM_TYPE_TPMS = 166;		//胎压报警
  	
  	
  	//adas
  //疲劳一级
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_FATIGUE_ONE_LEVEL_START = 101;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_FATIGUE_ONE_LEVEL_END = 102;
  	
  	//疲劳二级
	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_FATIGUE_TWO_LEVEL_START = 103;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_FATIGUE_TWO_LEVEL_END = 104;

  	//离开驾驶视线（左顾右盼）
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_LEAVE_DRIVING_SIGHT_START = 105;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_LEAVE_DRIVING_SIGHT_END = 106;


  	//打哈欠
 	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_YAWN_START = 107;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_YAWN_END = 108;


  	//打电话
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_CALL_START = 109;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_CALL_END = 110;

  	
  	//不系安全带
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_NOT_WEAR_SEAT_BELT_START = 111;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_NOT_WEAR_SEAT_BELT_END = 112;


  	//吸烟
	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_SMOKIng_START = 113;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_SMOKIng_END = 114;

  	//遮挡摄像头
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_BLOCK_CAMERA_START = 115;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_BLOCK_CAMERA_END = 116;

  	//设备故障
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_DEV_FAILURE_START = 117;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_DEV_FAILURE_END = 118;


  //车道偏移
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_LANE_OFFSET_START = 119;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_LANE_OFFSET_END = 120;
  	
  //前车近距
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_CLOSE_CAR_START = 121;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_CLOSE_CAR_END = 122;

  
  	//前车碰撞危险
	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_DANGER_COLLISION_CAR_START = 123;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_DANGER_COLLISION_CAR_END = 124;


  	//车辆侧倾
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_VEHICLE_ROLL_START = 125;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_VEHICLE_ROLL_END = 126;

  	//急刹车
	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_BRAKING_START = 127;
  	public static final int TTX_ALARM_CUSTOM_ADAS_ALARM_BRAKING_END = 128;

  	//人脸识别
  	public static final int TTX_ALARM_CUSTOM_TYPE_PRESON = 2;
   
  

    //用于上报警情信息，如紧急按钮报警等
    protected void addAlarmInfo() {
    	if (mBindSuc && mNetBind != null) {
			try {
				//不同的nAlarmType后面对应 的nAlarmInfo,param1,等参数都不相同
				//示例1   主动上报实时视频 param[0] = 通道(0开始) param[1] = 码流类型(0:主码流，1:子码流)
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_CUSTOM, CUSTOM_ALARM_TYPE_REPORT_VIDEO, 1, 0, 0, 0, 0, 0, 0, 0, 0, "");
				//示例2  主动上报紧急按钮报警
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_URGENCY_BUTTON, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
				//示例3  上报报警录像文件也是通过此接口来实现
				//		录像文件需要上传AlarmInfo类型1表示JPG图片，2表示录像文件,  szDesc为文件绝对路径
				//		param[0] = 文件长度，param[1] = 文件类型(常规或者报警)，param[2] = 文件时长, param[3] = 通道
				//		param[4] = 对应的报警类型alarmtype，param[5]对应的alarmInfo
				//		szReserve = 文件开始时间，如: 2013-06-13 100001
				//		当为JPG图片时，文件时长无效  /mnt/sdcard/1234.mp4
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_UPLOAD_FILE, 1, 102400, 1, 25, 0, TTX_ALARM_TYPE_CUSTOM, 
						TTX_ALARM_CUSTOM_ADAS_ALARM_LANE_OFFSET_START, 0, 0, 0, "/snapshot/upload/20171123-165020.jpg");
				
//				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_UPLOAD_FILE, 1, 102400, 1, 25, 0, TTX_ALARM_TYPE_CUSTOM, 
//						TTX_ALARM_CUSTOM_TYPE_PRESON, 0, 0, 0, "/snapshot/upload/20171123-165020.jpg");
				
				//振动报警AlarmInfo（加速度报警，bit0：X方向,bit1：Y方向,bit2：Z方向，bit3：碰撞,bit4：侧翻）
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_SHAKE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
				//振动报警结束
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_END_SHAKE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
				//adas报警 /疲劳一级 开始  其他报警同下
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_CUSTOM, TTX_ALARM_CUSTOM_ADAS_ALARM_FATIGUE_ONE_LEVEL_START, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
				//adas报警 /疲劳一级 结束 其他报警同下
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_CUSTOM, TTX_ALARM_CUSTOM_ADAS_ALARM_FATIGUE_ONE_LEVEL_END, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
				
				//胎压报警
				//alarmInfo  TPMS报警类型（1表示电池电压警告，2-胎压过高，3-胎压过低 4-漏气 5-温度异常（只有过高））
			  	//	param[0] 当前温度 如: 200 = 20度
				//			param[1] 当前胎压 如: 25 = 2.5P
				//			param[2] 当前电压 如: 102=10.2V
				//			param[3] 传感器编号（01表示TPMS左1，02表示TPMS左2，03表示TPMS左3，04表示TPMS左4，11表示TPMS右1，12表示TPMS右2，13表示TPMS右3，14表示TPMS右4）
				
				mNetBind.AddAlarmInfo(TTX_ALARM_TYPE_TPMS, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
			} catch (RemoteException e) {
				e.printStackTrace();
			}
    	}
    }
	//视频预览
	public static final String MESSAGE_RECEIVED_LIVE_ACTION = "net.babelstar.MESSAGE_RECEIVED_LIVE";	//视频预览请求
	public static final String START_MESSAGE = "start";
	public static final String TYPE_MESSAGE = "type";
	public static final String CHANNEL_MESSAGE = "channel";
	public static final String STREAM_MESSAGE = "stream";
	//下发文字信息
	public static final String MESSAGE_RECEIVED_TTS_ACTION = "net.babelstar.MESSAGE_RECEIVED_TTS";
	public static final String TTS_MESSAGE = "tts";
	//升级
	public static final String MESSAGE_RECEIVED_UPDATE_ACTION = "net.babelstar.MESSAGE_RECEIVED_UPDATE";
	public static final String UPDATE_MESSAGE = "update";
	
	//透传 下发控制指令
	public static final String MESSAGE_RECEIVED_ORT_ACTION = "net.babelstar.MESSAGE_RECEIVED_ORT";
	public static final String ORT_MESSAGE = "ort";	
	
	//参数配置
	public static final String MESSAGE_RECEIVED_DOCONFIG_ACTION = "net.babelstar.MESSAGE_RECEIVED_DOCONFIG";
	public static final String DOCONFIG_MESSAGE = "doconfig";
	//前端抓怕
	public static final String MESSAGE_RECEIVED_DOSNAPSHOT_ACTION = "net.babelstar.MESSAGE_RECEIVED_DOSNAPSHOT";
	public static final String DOSNAPSHOT_MESSAGE = "dosnapshot";
	
	//前端抓拍回复广播	
	public static final String MESSAGE_RECEIVED_DOSNAPSHOTREPLY_ACTION = "net.babelstar.MESSAGE_RECEIVED_DOSNAPSHOTREPLY";
	public static final String MESSAGE_RECEIVED_DOCONFIGREPLY_ACTION = "net.babelstar.MESSAGE_RECEIVED_DOCONFIGREPLY";
		
	//集群对讲相关消息
	//
	public static final String MESSAGE_RECEIVED_PTT_TALK_ACTION = "net.babelstar.MESSAGE_RECEIVED_PTT_TALK_ACTION";
	public static final String MESSAGE_PTT_PARAM_GROUP = "groupId";	//群组编号(int)
	public static final String MESSAGE_PTT_PARAM_TERMINAL = "terminalId";	//终端ID(int)
	public static final String MESSAGE_PTT_PARAM_START = "start";	//开始(boolean)
	
	public static final String MESSAGE_RECEIVED_DOCONTROL_ACTION = "net.babelstar.MESSAGE_RECEIVED_DOCONTROL";//控制指令

	/**
	 * 注册广播
	 */
	public void registerMessageReceiver() {
		if (mMessageReceiver == null) {
			mMessageReceiver = new MessageReceiver();
			mMessageReceiver.setListener(mMessageListener);
			IntentFilter filter = new IntentFilter();
			filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
			filter.addAction(MESSAGE_RECEIVED_UPDATE_ACTION);
			filter.addAction(MESSAGE_RECEIVED_LIVE_ACTION);
			filter.addAction(MESSAGE_RECEIVED_TTS_ACTION);
			filter.addAction(MESSAGE_RECEIVED_DOCONFIG_ACTION);
			filter.addAction(MESSAGE_RECEIVED_PTT_TALK_ACTION);
			getApplicationContext().registerReceiver(mMessageReceiver, filter);
		}
	}
	
	/*
	 * 
	 */
	protected void unregisterMessageReceiver() {
		if (mMessageReceiver != null) {
			getApplicationContext().unregisterReceiver(mMessageReceiver);
			mMessageReceiver = null;
		}
	}
	
	public static class MessageReceiver extends BroadcastReceiver {
		 public interface MessageListener   {  
	    	public void doMediaLive(int type, int channel, int stream, int start); 
	    	public void DoConfigMessage(int type,String text,Context context);
	    	public void doRecvPttTalk(int nGroupId, int nTerminalID, boolean start);
	    } 
		 
		@Override
		public void onReceive(Context context, Intent intent) {
			if (MESSAGE_RECEIVED_LIVE_ACTION.equals(intent.getAction())) {
				int type = intent.getExtras().getInt(TYPE_MESSAGE);
				int start = intent.getExtras().getInt(START_MESSAGE);
				int channel = intent.getExtras().getInt(CHANNEL_MESSAGE);
				int stream = intent.getExtras().getInt(STREAM_MESSAGE);
				if (mListener != null) {
					mListener.doMediaLive(type, channel, stream, start);
				}
			} else if (MESSAGE_RECEIVED_TTS_ACTION.equals(intent.getAction())) {
				String text = intent.getStringExtra(MainActivity.TTS_MESSAGE);
			}
			else if (MESSAGE_RECEIVED_UPDATE_ACTION.equals(intent.getAction())) {
				String text = intent.getStringExtra(MainActivity.UPDATE_MESSAGE);
				Log.d("aaa","update:"+text);
			}			
			else if (MESSAGE_RECEIVED_ORT_ACTION.equals(intent.getAction())) {
				int type = intent.getExtras().getInt(TYPE_MESSAGE);
				String text = intent.getStringExtra(MainActivity.ORT_MESSAGE);
			}
			else if (MESSAGE_RECEIVED_DOCONFIG_ACTION.equals(intent.getAction())) {
				
				int type = intent.getExtras().getInt(TYPE_MESSAGE);
				String text = intent.getStringExtra(MainActivity.DOCONFIG_MESSAGE);
				//Log.d("aaa","domessage:"+text);
				mListener.DoConfigMessage(type, text,context);
			}
			else if (MESSAGE_RECEIVED_DOSNAPSHOT_ACTION.equals(intent.getAction())) {			
				int type = intent.getExtras().getInt(CHANNEL_MESSAGE);
				//回复路径+文件名
				String FilePath = "";
				Intent domsgIntent = new Intent(MainActivity.MESSAGE_RECEIVED_DOSNAPSHOTREPLY_ACTION);
				domsgIntent.putExtra(MainActivity.DOSNAPSHOT_MESSAGE, FilePath);
				context.sendBroadcast(domsgIntent);//demo中
				//Log.d("aaa","doconfig:"+FilePath);
			} else if (MESSAGE_RECEIVED_PTT_TALK_ACTION.equals(intent.getAction())) {
				//表示收到集群对讲开始讲话的消息
				int groupId = intent.getExtras().getInt(MESSAGE_PTT_PARAM_GROUP);
				int terminalId = intent.getExtras().getInt(MESSAGE_PTT_PARAM_TERMINAL);
				boolean start = intent.getExtras().getBoolean(MESSAGE_PTT_PARAM_START);
				mListener.doRecvPttTalk(groupId, terminalId, start);
			}
		}
		
		public void setListener(MessageListener listener) {
			mListener = listener;
		}
		
		private MessageListener mListener = null;
	}
	
	public static final int TTX_MEDIA_AV		= 1;		//实时视频
	public static final int TTX_MEDIA_TALKBACK	= 2;		//对讲
	public static final int TTX_MEDIA_LISTEN	= 3;		//监听
	public class TtxMessageListener implements MessageReceiver.MessageListener {
		public void doMediaLive(int type, int channel, int stream, int start) {
			if (TTX_MEDIA_AV == type) {
				//实时视频
				//channel表示通道号，从0开始，0表示通道1
				//stream表示码流类型，0主码流，1子码流
				//start >0表示开启, =0表示停止
				if (start > 0) {
//					mSendVideo = true;
					Toast.makeText(MainActivity.this, String.format("开始 预览通道%d 码流%d 的实时视频", channel + 1, stream), Toast.LENGTH_SHORT).show();
 				} else {
// 					mSendVideo = false;
 					Toast.makeText(MainActivity.this, String.format("停止 预览通道%d 码流%d 的实时视频", channel + 1, stream), Toast.LENGTH_SHORT).show();
 				}
				
	    		
	    		
			} else if (TTX_MEDIA_TALKBACK == type) {
				//对讲
			} else {
				//监听
			}
		}

		@Override
		public void DoConfigMessage(int type, String text,Context context) {
			if(type == 700003){
				Toast.makeText(MainActivity.this, String.format("获取wifi参数:%d", type), Toast.LENGTH_SHORT).show();
				//发广播
				mtext = "{\"net\":\"1\",\"ssid\":\"ttx\"}";
				Intent domsgIntent = new Intent(MESSAGE_RECEIVED_DOCONFIGREPLY_ACTION);
				domsgIntent.putExtra(TYPE_MESSAGE, type);
				domsgIntent.putExtra(MainActivity.DOCONFIG_MESSAGE, mtext);
				context.sendBroadcast(domsgIntent);//demo中
			}else if(type == 700001){
				//Toast.makeText(MainActivity.this, String.format("获取视频参数:%d", type), Toast.LENGTH_SHORT).show();
				//发广播	
				//mtext = "{\"net\":\"1\",\"ssid\":\"ttx\"}";
				Intent domsgIntent = new Intent(MainActivity.MESSAGE_RECEIVED_DOCONFIG_ACTION);
				domsgIntent.putExtra(TYPE_MESSAGE, type);
				domsgIntent.putExtra(MainActivity.DOCONFIG_MESSAGE, mtext);
				context.sendBroadcast(domsgIntent);//demo中
			}
			// TODO Auto-generated method stub
			
		}

		public void doRecvPttTalk(int nGroupId, int nTerminalID, boolean start) {
			recvPttTalk(nGroupId, nTerminalID, start);
		}
	}

	
	//获取车辆状态消息
	final class StatusTask extends TimerTask {
		@Override
		public void run() {
			Message message = new Message();
			message.what = MESSAGE_STATUS;
			mHandler.sendMessage(message);
		}
	};
		
	//启动定时器获取车辆状态
	private void runTimerStatus() {
		cancelTimerStatus();
		mTimer = new Timer();
		mTimer.schedule(new StatusTask(), DELAY_TIMER_STATUS, DELAY_TIMER_STATUS);
	}
	
	//取消获取车辆状态的定时器
	private void cancelTimerStatus() {
		if (mTimer != null)  {
			mTimer.cancel();
		}
		mTimer = null;
	}
	
	
	//获取车辆状态消息
	final class PositionStatusTask extends TimerTask {
		@Override
		public void run() {
			Message message = new Message();
			message.what = MESSAGE_POSITION_STATUS;
			mHandler.sendMessage(message);
		}
	};
	
	//启动定时器获取车辆位置状态
	private void runTimerPositionStatus() {
		cancelTimerPositionStatus();
		mPositionTimer = new Timer();
		mPositionTimer.schedule(new PositionStatusTask(), DELAY_TIMER_POSITION_STATUS, DELAY_TIMER_POSITION_STATUS);
	}
	
	//取消获取车辆状态的定时器
	private void cancelTimerPositionStatus() {
		if (mPositionTimer != null)  {
			mPositionTimer.cancel();
		}
		mPositionTimer = null;
	}
	
	private void updateStatus() {
		if (mBindSuc && mNetBind != null) {
			boolean online = false;
			try {
				online = mNetBind.isOnline();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			if (online != mIsOnline) {
				mIsOnline = online;
				if (mIsOnline) {
					Toast.makeText(MainActivity.this, "终端在线Ip:" + mServer + "Account:" + mAccount, Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(MainActivity.this, "终端离线", Toast.LENGTH_SHORT).show();
				}
			}
			boolean live = false;
			try {
				live = mNetBind.IsVideoLiving(0, 1);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			if (live != mIsLive) {
				mIsLive = live;
				if (mIsLive) {
					Toast.makeText(MainActivity.this, "正在预览子码流的视频", Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(MainActivity.this, "没有预览子码流的视频", Toast.LENGTH_SHORT).show();
				}
			}
			boolean talkback = false;
			try {
				talkback = mNetBind.IsTalkback();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			if (talkback != mIsTalkback) {
				mIsTalkback = talkback;
				if (mIsTalkback) {
					Toast.makeText(MainActivity.this, "正在双向对讲", Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(MainActivity.this, "没有双向对讲", Toast.LENGTH_SHORT).show();
				}
			}
			boolean audioListen = false;
			try {
				audioListen = mNetBind.IsAudioListen(0);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			if (audioListen != mIsListen) {
				mIsListen = audioListen;
				if (mIsListen) {
					Toast.makeText(MainActivity.this, "正在监听通道1声音", Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(MainActivity.this, "没有监听通道1声音", Toast.LENGTH_SHORT).show();
				}
			}
		}
	}
	//GPS坐标类型定义
	private static final int TTX_COOR_TYPE_WSG84	= 0;		//WSG84类型坐标
	private static final int TTX_COOR_TYPE_GCJ02	= 1;		//火星坐标，高德地图使用
	private static final int TTX_COOR_TYPE_BD09	= 2;		//百度坐标

	//GPS定位类型定义
	private static final int TTX_LOC_TYPE_GPS	= 0;		//GPS定位
	private static final int TTX_LOC_TYPE_WIFI	= 1;		//WIFI定位
	private void updatePositionStatus() {
		if (mBindSuc && mNetBind != null){
			if(!mIsAppPosition){
				//例如 nValid:1,longitude=113.6981935253515,latitude=22.99217691745281,
				//nCoorType=0,nLocType=1nSpeed=0,baiduspeed0.0direction-1.0ndirection-1
				int nValid = 1;
				double longtitude = 113.6981935253515;
				double latitude = 22.99217691745281;
				int nCoorType = TTX_COOR_TYPE_WSG84; // TTX_COOR_TYPE_WSG84 : 0 
				int nLocType = TTX_LOC_TYPE_GPS;
				int nSpeed = 6600;
				int nDirection = 180;
				try {
					mNetBind.UpdateLocation(nValid, longtitude, latitude, nCoorType, nLocType, nSpeed, nDirection);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		
	}

	/**
	 * 接受消息,处理消息 ,此Handler会与当前主线程一块运行
	 */
	class MyHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (msg.what == MESSAGE_STATUS) {
				updateStatus();
			}else if(msg.what == MESSAGE_POSITION_STATUS){
				updatePositionStatus();
			}
		}
	}
	
	public static final int TTX_PTT_SUC								= 0;	//成功
	public static final int TTX_PTT_ERR_PLAY_RECORDING          	= 198; //正在播放录音
	public static final int TTX_PTT_ERR_DUE_TALKING                 = 199; //正在双向对讲
	public static final int TTX_PTT_ERR_PASSWORD					= 200;	//密码错误
	public static final int TTX_PTT_ERR_ALREADY_LOGIN				= 201;	//已经登录
	public static final int TTX_PTT_ERR_NETWORK						= 202;	//网络错误
	public static final int TTX_PTT_ERR_NO_TALK_RIGHT				= 203;	//未取得讲话权
	public static final int TTX_PTT_ERR_OTHER_TALKING				= 204;	//其它用户正在讲话
	public static final int TTX_PTT_ERR_GROUP_IDLE					= 205;	//没有处于某个群组(空闲状态)
	public static final int TTX_PTT_ERR_GROUP_NO_EXIST				= 206;	//群组不存在
	public static final int TTX_PTT_ERR_NO_GROUP_MEMBER				= 207;	//非群组成员
	public static final int TTX_PTT_ERR_TEMP_GROUP_EXIST			= 208;	//已经存在临时小组（用户同时只能建立一个临时群组）
	public static final int TTX_PTT_ERR_NO_TEMP_GROUP				= 209;	//非临时群组（目前只有临时群组才能添加成员）
	public static final int TTX_PTT_ERR_NO_RIGHT					= 210;	//无权限
	public static final int TTX_PTT_ERR_DATABASE					= 211;	//操作数据库
	public static final int TTX_PTT_ERR_UNKOWN						= 212;	//未知错误
	public static final int TTX_PTT_ERR_OFFLINE						= 213;	//离线状态
	public static final int TTX_PTT_ERR_TERMINAL_NO_EXIST			= 214;	//终端不存在
	public static final int TTX_PTT_ERR_TERMINAL_OFFLINE			= 215;	//终端不在线
	public static final int TTX_PTT_ERR_TERMINAL_TALKING			= 216;	//终端正在讲话，强拉或者强拆会失败
	
	protected void recvPttTalk(int nGroupId, int nTerminalID, boolean start) {
		if (start) {
			Toast.makeText(MainActivity.this, "群组用户进行讲话", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(MainActivity.this, "群组用户停止讲话", Toast.LENGTH_SHORT).show();
		}
	}
	
	//PTT集成说明
	//1、处理其它用户讲话，监听MESSAGE_RECEIVED_PTT_TALK_ACTION，消息，这个是群组用户讲话的消息
	//		讲话时，cmscruise.apk会自行播放ptt的声音
	//2、发送讲话请求，先PttRequireTalk讲话请求，然后不停调用 PttInputAacFrame送声音，最后停止调用PttRequireTalk
	//备注
	//	faac音频编码测试：开启音频编码后，首次需要送500毫秒(8192个pcm)的音频后，编码接口才会生成音频数据，相当于音频编码器里面有500毫秒的音频数据缓存
	//		android系统aac硬编码未作测试，判断编码器音频缓存的方法：增加打印，判断送多少次的pcm，编码才会开始有aac音频数据产生，如果是8000的采样率，每秒会有 16000个pcm数据
	//		单只考虑集群对讲，讲话结束前，人为制造送静音的8192个pcm数据进去，也可以把缓存的音频给编码出来了
	//		考虑实时音视频和集群对讲并存的情况，延时500毫秒停止
	protected void DoPttTalk() {
		if (mIsPttTalking) {
			int ret = -1;
			try {
				//最好延时500毫秒再停止，解决 声音缺少的问题
				ret = mNetBind.PttRequireTalk(0);				
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			mIsPttTalking = false;
			Toast.makeText(MainActivity.this, "停止讲话成功:" + ret, Toast.LENGTH_SHORT).show();
		} else {
			int ret = TTX_PTT_ERR_UNKOWN;
			try {
				 ret = mNetBind.PttRequireTalk(1);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			if (ret != TTX_PTT_SUC) {
				//显示出错原因
				Toast.makeText(MainActivity.this, "请求讲话失败，错误原因：" + ret, Toast.LENGTH_SHORT).show();
			} else {
				mIsPttTalking = true;
				Toast.makeText(MainActivity.this, "请求讲话成功，请调用PttInputAacFrame发送音频数据", Toast.LENGTH_SHORT).show();
				//循环发送AAC声音数据
//				while (true)
//				{
//					byte[] pAac = new byte[7];
//					try {
//						mNetBind.PttInputAacFrame(pAac, 775);
//					} catch (RemoteException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//				}
				
			}
		}
	}
	
	final class ButtonClickListener implements OnClickListener {
		public void onClick(View v) {
			if (v.equals(mBtnPttTalk)) {
				DoPttTalk();
			} 
		}
	}
	
}
