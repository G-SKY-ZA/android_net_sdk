package net.babelstar.gdispatch.service;

interface TtxNetwork {
	//设置服务ip和账号 server默认为120.26.98.110，account默认为112233
	void setServerAndAccount(String server, String account);
	//设置通道数目，默认为1
	void setChnCount(int chnCount);
	//设置媒体类型
	//audioType音频类型   12=AAC_8KBPS	13=AAC_16KBPS  14=AAC_24KBPS
	void setMediaInfo(int audioType, int param1, int param2);
	//设置是否进行视频编码，默认为ture
	void setVideoEncode(boolean encode);
	//设置GPS上报间隔，默认为30
	void setGpsInterval(int second);
	//设置是否使用摄像头，如果为true，则app会打开摄像头获取yuv数据，进行进行编码，如果为false，则app不会打开摄像头
	//默认为true
	//如果是后视镜对接时，setUsedCamera(false)，即TTX的APP程序不执行打开摄像头等操作
	void setUsedCamera(boolean used);
	//设置录像参数
	//mainStreamRecord主码流录像，默认为false，
	//subStreamRecord子码流录像，默认为true
	//如果是后视镜对接时，后视镜自己编码，则mainStreamRecord设置为false，subStreamRecord也设置为false，即TTX的APP程序不执行录像操作
	//两个不能同时为true
	void setRecord(boolean mainStreamRecord, boolean subStreamRecord);
	//是否在线
	boolean isOnline();
	//是否在预览视频
	//channel表示通道号，从0开始，0表示通道1
	//stream为码流类型，0为主码流，1为子码流
	boolean IsVideoLiving(int channel, int stream);
	//是否正在对讲
	boolean IsTalkback();
	//是否正在监听 
	boolean IsAudioListen(int channel);
	//获取最后的经度和纬度
	double getLastLongitude();
	double getLastLatitude();
	//上层抓图时，调用摄像头时，调用此接口通知service
	void AppCreateCamera(int channel);	
	//抓图结束时，释放摄像头时，调用些接口通知service
	void AppReleaseCamera(int channel);
	//上层输入yuv数据前，开始要调用此接口通知视频的尺寸
	void initCameraPreview(int channel, int width, int height);
	//上层不再使用摄像头，此服务会自动打开摄像头
	void freeCameraPreview(int channel);
	//上报jpeg图片
	int uploadJpegFile(int channel, String fileName);
	//保存录像，服务会把正在录像的文件进行打包
	int storageRecord(int channel, boolean save);
	//发送对讲请求
	int SendAudioIntercom(boolean start);
	//发送报警信息
	int AddAlarmInfo(int nAlarmType, int nAlarmInfo, int nParam1, int nParam2, int nParam3, int nParam4, int nParam5, int nParam6, int nParam7, int nParam8, int nParam9, String desc);
	//上报最好存储好的录像
	int UploadLastRecord();
	
	int inputYv12Buf(int channel, inout byte[] yv12, boolean isYv12, int width, int heigth, int length);
	//aidl对输入的byte[]长度有限制，限制不得超过1M，如果数据超过1M，则需要分段写入
	//输入yuv数据
	//channel表示通道号，从0开始，0为通道1
	//index表示几个完整的yuv图像，递增传递，如果yuv长度大于 512000字节的长度，则需要进行分包传输
	//(packIndex + 1) = packCount 表示包结束了，每包最多传  512000字节的长度
	//offset表示在yuv图像中的偏移
	//length表示本次yuv数据包的长度
	//上层使用时   System.arraycopy(yv12, 0, dest, offset, packLength);
	//dest目标地址，offset这段数据位于目标位置的偏移
    boolean inputYv12Data(int channel, int index, int packIndex, int packCount, inout byte[] yv12, boolean isYv12, int offset, int packLength, int width, int height);
    
    //输入nalu数据
    //h264数据介绍	http://www.jianshu.com/p/d28e25ae339e
    //channel表示通道号，从0开始，0为通道1
    //stream为0主码流，1子码流
    //index表示几个完整的nalu图像，递增传递，如果nalu长度大于  1024000 字节的长度，则需要进行分包传输
    //(packIndex + 1) = packCount 表示包结束了，每包最多传  1024000 字节的长度
	//offset表示在nalu图像中的偏移
	//length表示nalu本次传递的长度
	//上层使用时   System.arraycopy(yv12, 0, dest, offset, packLength);
	//dest目标地址，offset这段数据位于目标位置的偏移
	//System.arraycopy(nalu, 0, mNaluBuffer, offset, packLength);
    boolean inputH264Data(int channel, int stream, int index, int packIndex, int packCount, inout byte[] nalu, int offset, int packLength, int naluLength);
    //输入的aac数据，要带有adts的头部
    //备注：把带adts头部数据的aac直接存储成文件，用vlc可以正常播放声音
    boolean inputAacData(int channel, inout byte[] aac, int length);
    
    //改变帧率，码率，分辨率配置时  stream为0主码流，1子码流
    void doChangeFrameRate(int channel, int stream, int frameRate);
    void doChangeBitrate(int channel, int stream, int bitrate);
    void doChangeResolution(int channel, int stream, int resolution);
  	//取得录像的时间信息，输入录像文件名
	String GetRecFileTimeInfo(String file);
	//上报record
	int uploadRecordFile(int channel, String fileName);
	//设置acc状态
	void SetAccStatus(boolean accStatus);
	//设置录像状态和视频丢失状态 
	//nVideoRecord: 第0位表示通道1,  值为1表示正在录像
	//nVideoStatus: 第0位表示通道1,  值为1表示视频丢失
	void SetRecordVideoStatus(int nRecordStatus, int nVideoStatus);
	//获取音量大小
	int GetAudioRecorderAmplitude();
	//开始捕获音频数据
	void StartRecordSound();
	//停止捕获音频数据
	void StopRecordSound();
	//存储aac录音
	int storageRecordSound(int channel, boolean save);
	//上传录音文件
	int UploadLastRecordSound(long second);
	
	//ptt相关接口
	//ptt申请讲话的接口	
	int PttRequireTalk(int bTalk);
	//ptt开始录音的接口,ptt结束录音的接口
	int PttRecordAudio(int bRecord, boolean bCancel);
	//ptt传送音频的接口
	int PttInputAacFrame(inout byte[] pAac, int nLength);
	void StopWork();
	//是否开启网络定位
	void IsNetWorkPosition(boolean isNetWorkPosition);
	//获取服务器ip
	String getServerIp();
	//获取设备Account
	String getServerAccount();
	// 更新位置信息
	int UpdateLocation(int nValid, double longtitude, double latitude, int nCoorType, int nLocType, int nSpeed,int nDirection);
	//是否开启app定位
	void IsAppPosition(boolean isAppPosition);
	//对接设备录像路径设置
	//recPath:录像路径
	//jpgPath:图片路径 
	//sdLogPath:日志文件路径
	//bIsSetRecPath:true 有效
	int SetRecPathEx(String recPath, String jpgPath, String sdLogPath, boolean bIsSetRecPath);
	//对接输入pcm数据 8k采样率
	//delay 延时值毫秒 建议20ms
	int SetEchoParam(boolean bIsOpenEcho, int nDelay);	
	int InputPcmData(inout byte[] pWavBuf, int nWavLen);

	//输入nalu数据 265
    //h264数据介绍	http://www.jianshu.com/p/d28e25ae339e
    //channel表示通道号，从0开始，0为通道1
    //stream为0主码流，1子码流
    //index表示几个完整的nalu图像，递增传递，如果nalu长度大于  1024000 字节的长度，则需要进行分包传输
    //(packIndex + 1) = packCount 表示包结束了，每包最多传  1024000 字节的长度
    //offset表示在nalu图像中的偏移
    //length表示nalu本次传递的长度
    //上层使用时   System.arraycopy(yv12, 0, dest, offset, packLength);
    //dest目标地址，offset这段数据位于目标位置的偏移
    //System.arraycopy(nalu, 0, mNaluBuffer, offset, packLength);
    boolean inputH265Data(int channel, int stream, int index, int packIndex, int packCount, inout byte[] nalu, int offset, int packLength, int naluLength);
    //audioType音频类型   12=AAC_8KBPS	13=AAC_16KBPS  14=AAC_24KBPS
    //videoType视频类型   0 = TTX_VIDEO_CODEC_H264  4 = TTX_VIDEO_CODEC_H265
    void setMediaInfoEx(int audioType, int param1, int param2, int videoType);
}
