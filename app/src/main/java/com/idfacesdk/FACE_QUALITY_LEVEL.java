package com.idfacesdk;

@SuppressWarnings("unused")

// FACE_QUALITY_LEVEL 结构, 存放人脸质量级别
public class FACE_QUALITY_LEVEL {
	public int facesize; // 人脸大小：0-正常，1-人脸偏小，2-人脸较小，3-人脸太小
	public boolean posture; // 姿态：false-正常，true-偏头
	public boolean half; // 人脸完整性: false-正常，true-人脸不完整
	public boolean mask; // 脸部遮挡: false-正常，true-遮挡
	public boolean glasses; // 眼镜: false-未戴眼镜，true-戴眼镜
	public int lefteye, righteye; // 睁眼程度(0-100，睁闭眼参考阈值为20)
	public int gape; // 张嘴程度(0-100，张闭嘴参考阈值为20)
	public boolean blur; // 模糊度：false-正常，true-模糊
	public int bright; // 脸部曝光度：0-正常，1-太暗，2-过爆
	public int light; // 光源方向: 0-正常，1-侧光，2-顶光, 3-逆光
}
