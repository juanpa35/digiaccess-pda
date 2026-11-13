package com.idfacesdk;

@SuppressWarnings("unused")

// FACE_DETECT_RESULT类, 存放检测到的人脸参数
public class FACE_DETECT_RESULT {
	public int nFaceLeft, nFaceTop, nFaceRight, nFaceBottom; // 人脸坐标
	public int nLeftEyeX, nLeftEyeY, nRightEyeX, nRightEyeY; // 左右眼坐标
	public int nMouthX, nMouthY, nNoseX, nNoseY; // 嘴巴鼻子坐标
	public int nAngleYaw, nAnglePitch, nAngleRoll; // 人脸偏转角度（左右，上下，水平方向）
	public int nConfidence; // 人脸置信度
	public int nQuality; // 人脸质量
	public byte[] FaceData; // 脸部详细参数，特征提取等接口需要此数据确认脸部轮廓，所以提特征时需要将人脸检测的结果原样输入

	public FACE_DETECT_RESULT()
	{
		 FaceData = new byte[512];
	}
	public FACE_DETECT_RESULT(FACE_DETECT_RESULT ori)
	{
		this.nFaceLeft = ori.nFaceLeft;
		this.nFaceTop = ori.nFaceTop;
		this.nFaceRight = ori.nFaceRight;
		this.nFaceBottom = ori.nFaceBottom;
		this.nLeftEyeX = ori.nLeftEyeX;
		this.nLeftEyeY = ori.nLeftEyeY;
		this.nRightEyeX = ori.nRightEyeX;
		this.nRightEyeY = ori.nRightEyeY;
		this.nMouthX = ori.nMouthX;
		this.nMouthY = ori.nMouthY;
		this.nNoseX = ori.nNoseX;
		this.nNoseY = ori.nNoseY;
		this.nAngleYaw = ori.nAngleYaw;
		this.nAnglePitch = ori.nAnglePitch;
		this.nAngleRoll = ori.nAngleRoll;
		this.nConfidence = ori.nConfidence;
		this.nQuality = ori.nQuality;

		this.FaceData = new byte[512];
		System.arraycopy(ori.FaceData, 0, this.FaceData, 0, ori.FaceData.length);
	}
}
