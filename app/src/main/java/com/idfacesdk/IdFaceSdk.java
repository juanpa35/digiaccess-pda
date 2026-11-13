package com.idfacesdk;

// 请注意 ! 本接口文件必须放在目录 com\idfacesdk\ 下面，不得更改目录，否则APP运行时会因找不到本接口定义的方法而无法运行
// 请注意 ! 本接口文件必须放在目录 com\idfacesdk\ 下面，不得更改目录，否则APP运行时会因找不到本接口定义的方法而无法运行
// 请注意 ! 本接口文件必须放在目录 com\idfacesdk\ 下面，不得更改目录，否则APP运行时会因找不到本接口定义的方法而无法运行

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

@SuppressWarnings("unused")

// 人脸识别 SDK 接口类
public class IdFaceSdk {

	// 获取SDK版本（可不调用）
	// 输入参数：无
	// 输出参数：无
	// 返回：SDK版本号
	// 备注：可不调用，任何时候均可调用
	public static native int Ver();

	// 获取设备运行码【适用于软授权】
	// 输入参数：无
	// 输出参数：无
	// 返回：返回设备运行码
	// 备注：本接口可不必调用，只在无有效授权时，通过调用此接口生成设备运行码提供给供应商换取对应的授权码
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native String GetRunCode(Context context);

	// 设置设备授权码【适用于软授权】
	// 输入参数：
	//    strRegCode ---- 设备授权码
	// 输出参数：无
	// 返回：成功返回0, 授权码无效返回-1
	// 备注：必须在SDK初始化前调用才有效
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native int SetRegCode(Context context, String strRegCode);

	// 设置包含有授权码的授权文件或授权文件所在目录（支持CSV批量授权文件）【适用于软授权】
	// 输入参数：
	//   strLicenseFilesPath ---- 包含设备授权码的授权文件名或授权文件所在目录
	// 输出参数：无
	// 返回：成功返回0, 无有效的授权码返回-1
	// 备注：
	//       如果设置的是授权文件所在的目录，则自动搜索该目录下的 license*.dat 及 license*.csv
	//       必须在SDK初始化前调用才有效
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native int SetLicenseFilesPath(Context context, String strLicenseFilesPath);

	// 云授权请求供应商批准授权接口【适用于云授权】
	// 输入参数：
	//	strAccount ---- 供应商提供的账号
	//  strCaller ---- 申请者（调用方）名称，方便供应商批准时辨别申请人，可以是设备名称、型号、本公司名称等，也可以传空
	//	strLicensePath ---- 授权文件路径或文件名，必须为一个有效的本地目录或文件名并具有可读写权限，用于存放授权数据
	// 输出参数：
	//	无
	// 返回值：
	//	1 ---- 成功，指定位置已存在授权且有效
	//	0 ---- 成功，已批准新的授权并写入指定位置
	//	-1 ---- 失败，账号错误
	//	-2 ---- 失败，等待供应商批准，请联系供应商或稍后再试
	//	-3 ---- 失败，连接云服务器错误，请联系供应商或稍后再试
	//	-5 ---- 失败，供应商拒绝授权，请联系供应商
	//	-4 ---- 失败，其它错误，请联系供应商或稍后再试
	// 备注：
	//   1. 必须在初始化前调用，成功后才能调用SDK初始化接口成功初始化SDK
	//   2. 成功批准了授权时自动存入指定文件或目录，如指定位置的授权存在，则不会重复请求
	//   3. 每次传入的授权文件路径或文件名应固定不变，并且应存放在系统目录或者SD卡等与APP无关的路径，
	//      否则因找不到原来的授权文件而必须联网请求到授权才能初始化成功（同一设备多次请求不会重复扣除授权数）
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native int ApplyLicense(Context context, String strAccount, String strCaller, String strLicensePath);

	// 根据用户账号的云授权校验接口【适用于云授权】
	// 输入参数：
	//	strAccount ---- 客户申请授权的账号
	//	strPwd ---- 客户申请授权的密码
	//	strLicensePath ---- 授权文件路径或文件名，必须为一个有效的带绝对目录的路径或文件名（应指定在系统目录或者SD卡等与APP无关的路径上）并具有可读写权限，用于存放授权数据
	// 输出参数：
	//	无
	// 返回值：
	//	1 ---- 成功，指定位置已存在授权且有效
	//	0 ---- 成功，申请到新的授权并写入指定位置
	//	-1 ---- 失败，账号或密码错误
	//	-2 ---- 失败，无有效授权，请购买授权
	//	-3 ---- 失败，连接云服务器错误，请稍后重试
	//	-4 ---- 失败，其它错误
	// 备注：
	//   1. 必须在初始化前调用
	//   2. 成功申请了授权时自动存入指定文件或目录，如指定位置的授权存在，则不会重复请求
	//   3. 每次传入的授权文件路径或文件名应固定不变，并且应存放在系统目录或者SD卡等与APP无关的路径，
	//      否则因找不到原来的授权文件而必须联网请求到授权才能初始化成功（同一设备多次请求不会重复扣除授权数）
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native int CheckLicense(Context context, String strAccount, String strPwd, String strLicensePath);

	// 根据激活码的云授权校验接口【适用于云授权】
	// 输入参数：
	//	strActiveCode ---- 授权激活码
	//	strLicensePath ---- 授权文件路径或文件名，必须为一个有效的带绝对目录的路径或文件名（应指定在系统目录或者SD卡等与APP无关的路径上）并具有可读写权限，用于存放授权数据
	// 输出参数：
	//	无
	// 返回值：
	//	1 ---- 成功，指定位置已存在授权且有效
	//	0 ---- 成功，申请到新的授权并写入指定位置
	//	-1 ---- 失败，激活码无效，请使用有效的激活码。如无有效的激活码请购买授权
	//	-2 ---- 失败，激活码已失效，请使用新的激活码。如激活码用尽请购买授权
	//	-3 ---- 失败，连接云服务器错误，请稍后重试
	//	-4 ---- 失败，其它错误
	// 备注：
	//   1. 必须在初始化前调用
	//   2. 成功申请了授权时自动存入指定文件或目录，如指定位置的授权存在，则不会重复请求
	//   3. 每次传入的授权文件路径或文件名应固定不变，并且应存放在系统目录或者SD卡等与APP无关的路径，
	//      否则因找不到原来的授权文件而必须联网请求到授权才能初始化成功（同一设备多次请求不会重复扣除授权数）
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native int CheckLicenseAC(Context context, String strActiveCode, String strLicensePath);

	// 根据用户账号的云授权获取授权码接口【适用于云授权】
	// 输入参数：
	//	strAccount ---- 客户申请授权的账号
	//	strPwd ---- 客户申请授权的密码
	//	strRunCode ---- 设备运行码
	// 输出参数：
	//	bLicenseCode ---- 成功时返回运行码对应的长度为35字符的授权码，调用前必须至少分配35字节空间
	// 返回值：
	//	0 ---- 成功，申请到授权
	//	-1 ---- 失败，账号或密码错误
	//	-2 ---- 失败，无有效授权，请购买授权
	//	-3 ---- 失败，连接云服务器错误，请稍后重试
	//	-4 ---- 失败，其它错误
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native int GetLicense(Context context, String strAccount, String strPwd, String strRunCode, byte[] bLicenseCode);

	// 根据激活码的云授权获取授权码接口【适用于云授权】
	// 输入参数：
	//	strActiveCode ---- 授权激活码
	//	strRunCode ---- 设备运行码
	// 输出参数：
	//	bLicenseCode ---- 成功时返回设备对应的长度为35字符的授权码，调用前必须至少分配35字节空间
	// 返回值：
	//	0 ---- 成功，申请到授权
	//	-1 ---- 失败，激活码无效，请使用有效的激活码。如无有效的激活码请购买授权
	//	-2 ---- 失败，激活码已失效，请使用新的激活码。如激活码用尽请购买授权
	//	-3 ---- 失败，连接云服务器错误，请稍后重试
	//	-4 ---- 失败，其它错误
	// 备注：相同的激活码不要重复调用
	//   !!! 特别注意，以下情况必须得到保证，否则会导致设备运行码改变，从而导致原来的软授权及云授权失效（需要申请新的授权才能继续使用）：
	//          1. 不重装或重置ANDROID系统。卸载重装APP不受影响；
	//          2. APP申请的权限必须相同（APP可以额外申请更多的权限，但DEMO包含的几种权限必须相同）。申请权限包括 Mainifest.xml 中静态申请和APP动态申请；
	//          3. 如果在 ANDROID 8.0 或以上系统运行，APP必须使用固定的的密钥签名。无论是同一个APP还是不同APP，如果签名密钥不同，得到的运行码就不同。
	public static native int GetLicenseAC(Context context, String strActiveCode, String strRunCode, byte[] bLicenseCode);

	// 通过USB加密狗授权时设置加密狗【适用于USB加密狗/加密芯片授权】
	// 输入参数：
	//     connection ---- USB加密狗认证时指定加密狗设备连接，需据此才能操作USB加密狗（可参考DEMO例程请求权限并获取设备连接）
	// 输出参数：无
	// 返回：无
	// 备注：必须在SDK初始化前调用才有效
	//       原SetUsbDogHandle接口已过时，YT88（加密狗/加密芯片）授权时由本接口替代
	//       软授权或云授权不要调用此接口
	public static native void SetUsbdog(UsbDeviceConnection connection);

	// 设置硬授权认证方式【适用于所有加密狗/加密芯片授权】
	// 输入参数：
	//     nAuthType ---- 认证方式：0-红色或银色或蓝色USB加密狗或USB加密芯片，1-白色USB加密狗，2-板载I2C加密芯片，3-读卡器I2C加密芯片，其它-保留
	//     nUsbDogHandle ---- USB加密狗认证时指定加密狗设备句柄，需据此才能操作USB加密狗设备（可参考DEMO例程请求权限并获取设备句柄）
	//                        I2C加密芯片认证时本参数无意义，可指定任何值
	// 输出参数：无
	// 返回：无
	// 备注：必须在SDK初始化前调用才有效
	//       原SetUsbDogHandle接口已过时，非YT88（加密狗/加密芯片）授权时由本接口替代
	//       软授权或云授权或服务器授权不要调用此接口
	public static native void SetAuth(int nAuthType, int nUsbDogHandle);

	// SDK初始化
	// 输入参数：
	//     strCachePath ---- 本APP的cache目录，需要此目录有可读写权限，且能根据上级目录找到lib目录加载模型文件（可参考DEMO例程获取cache目录）
	// 输出参数：无
	// 返回：成功返回0，许可无效返回-1，算法初始化失败返回-2
	// 备注：检测人脸、质量检测、活体检测、获取特征大小、提取特征、一对一及一对多等功能接口都必须在SDK初始化成功后才能调用
	//      本接口要求能根据cache目录的上级目录找到LIB目录且模型文件必须放置在LIB目录
	public static native int Init(String strCacheDir);

	// SDK初始化(允许指定独立的库文件及临时文件目录)
	// 输入参数：
	//     strLibPath ---- SDK依赖的模型文件所在目录
	//     strCachePath ---- 临时文件目录，需要此目录有可读写权限（可参考DEMO例程获取cache目录）
	// 输出参数：无
	// 返回：成功返回0，许可无效返回-1，算法初始化失败返回-2
	// 备注：检测人脸、质量检测、活体检测、获取特征大小、提取特征、一对一及一对多等功能接口都必须在SDK初始化成功后才能调用
	//       本接口支持模型文件所在目录与临时文件目录任意指定
	public static native int InitEx(String strLibDir, String strCacheDir);

	// SDK反初始化
	// 输入参数：无
	// 输出参数：无
	// 返回：无
	// 备注：必须在初始化成功后调用，反初始化后不能再调用除获取SDK版本及SDK初始化外的任何功能接口
	public static native void Uninit();

	// 设置检测人脸的图象压缩大小
	// 输入参数：nDetectSize ---- 检测图象的最大宽度或高度（象素单位），超过此大小则压缩至此大小以内，此参数为0表示采用原图检测不压缩
	// 输出参数：无
	// 返回：无
	// 备注：原始图象的宽或高超过输入参数值时，将被压缩至此参数值以内再检测以保证检测速度；
	//       输入参数越大，则压缩比越小，支持的人脸大小则越大，检测识别精度越大，但检测效率低；
	//       输入参数越小，则压缩比越大，支持的人脸大小则越小，检测识别精度越低，但检测效率高；
	//       输入参数为0时，对原始图象直接检测，精度最高，可支持极小的人脸，但分辨率越高，检测效率越低
	//       不调用本接口时，SDK默认值为320，建议在较高性能的平台设置为640（单人脸检测识别场景建议图象分辨率为640x480，更高分辨率可能导致精度降低）
	public static native void SetDetectSize(int nDetectSize);

    // 设置身份证小照片的质量检测
    // 输入参数：bCheckQuality
    //              0 ---- 对于身份证小照片（102x126分辨率）不检测质量，较模糊的照片也能检测出人脸并能参与比对
    //              1 ---- 对于身份证小照片（102x126分辨率）也检测质量，较模糊的照片不返回人脸
    // 输出参数：无
    // 返回：无
    // 备注：本接口只影响身份证小照片，特指从身份证阅读器中读出来的分辨率为102x126的照片
    //       不调用本接口时，SDK默认不检测身份证小照片的质量，适合一对一人证核验场景。如果用于1:N场景，请设置为检测质量，避免特别模糊的身份证小照片引起误识
	public static native void SetIdPhotoQuality(int bCheckQuality);

	// 检测单个（最大的）人脸
	// 输入参数：
	//     bRgb24 ---- RGB24格式的图象数据
	//     nWidth ---- 图象数据宽度（象素单位）
	//     nHeight ---- 图象数据高度（象素单位）
	// 输出参数：sFaceResult ---- 结构体存放检测到的人脸参数（人脸及眼睛等坐标位置及角度等，调用前必须分配有效的空间）
	// 返回：返回1表示检测到人脸，0表示无人脸，-1表示未授权，-2表示参数错误，其它表示内部错误
	public static native int DetectFace(byte[] bRgb24, int nWidth, int nHeight, FACE_DETECT_RESULT sFaceResult);

	// 检测单个（最大的）人脸, 支持NV21格式，支持旋转与镜象，支持中间区域检测以加快检测速度
	// 输入参数：
	//     nFmt ---- 输入源图象数据格式（0：YUV420P, 1: NV12，2: NV21）
	//     bSrcImg ---- 输入源图象数据
	//     nWidth ---- 输入源图象的宽度（象素单位）
	//     nHeight ---- 输入源图象的高度（象素单位）
	//     nLeft ---- 检测区域左上角X坐标(相对于输入源图象，全图检测时填0)
	//     nTop ---- 检测区域左上角Y坐标(相对于输入源图象，全图检测时填0)
	//     nRight ---- 检测区域右下角X坐标(相对于输入源图象，全图检测时也可填0)
	//     nBottom ---- 检测区域右下角Y坐标(相对于输入源图象，全图检测时也可填0)
	//     nRotate ---- 旋转方式（对输入源图象旋转，0：不旋转，1：逆时针旋转90度，2：顺时针旋转90度, 3: 旋转180）
	//     bMirror ---- 左右镜象（相对于旋转后的图象，0：左右不镜象，1：左右镜象）
	// 输出参数:
	//     bRgb24 ---- 输出的RGB24格式图象数据(裁减、旋转和镜象后的图象数据)
	//     nNewWidth ---- 输出图象的宽度(裁减、旋转和镜象后的图象宽度)
	//     nNewHeight ---- 输出图象的高度(裁减、旋转和镜象后的图象宽度)
	//     sFaceResult ---- 检测到的人脸参数（人脸及眼睛等坐标位置及角度等，相对于裁减、旋转和镜象后的图象，调用前必须分配有效的空间）
	// 返回：返回1表示检测到人脸，0表示无人脸，-1表示未授权，-2表示参数错误，其它表示内部错误
	// 备注：1. 使用此接口的检测结果来提取特征或检测活体时，必须使用这里的输出图象、输出图象的宽高和检测到的人脸参数做为参数
	//       2. 界面上需要画人脸人眼等坐标时，需要对检测出的人脸坐标参数根据裁减、旋转和镜象情况进行校正
	public static native int DetectFaceEx(int nFmt, byte[] bSrcImg, int nWidth, int nHeight, int nLeft, int nTop, int nRight, int nBottom, int nRotate, int bMirror, byte[] bRgb24, int[] nNewWidth, int[] nNewHeight, FACE_DETECT_RESULT sFaceResult);

	// 检测多人脸
	// 输入参数：
	//     bRgb24 ---- RGB24格式的图象数据
	//     nWidth ---- 图象数据宽度（象素单位）
	//     nHeight ---- 图象数据高度（象素单位）
	//     nMaxFace ---- 最多支持的人脸个数
	// 输出参数：
	//     sFaceResult ---- 结构体存放检测到的人脸参数（人脸及眼睛等坐标位置及角度等，调用前必须分配 nMaxFace 个对象空间）
	// 返回：返回检测到的人脸个数，0表示无人脸，-1表示未授权，-2表示参数错误，其它表示内部错误
	// 备注: sFaceResult 需分配不少于 nMaxFace 个对象空间，实际填充的人脸参数个数以返回的人脸个数为准
	public static native int DetectAllFaces(byte[] bRgb24, int nWidth, int nHeight, int nMaxFace, FACE_DETECT_RESULT[] sFaceResult);

	// 检测多人脸，支持NV21格式，支持旋转与镜象
	// 输入参数：
	//     nFmt ---- 输入源图象数据格式（0：YUV420P, 1: NV12，2: NV21）
	//     bSrcImg ---- 输入源图象数据
	//     nWidth ---- 输入源图象的宽度（象素单位）
	//     nHeight ---- 输入源图象的高度（象素单位）
	//     nRotate ---- 旋转方式（对输入源图象旋转，0：不旋转，1：逆时针旋转90度，2：顺时针旋转90度, 3: 旋转180）
	//     bMirror ---- 左右镜象（相对于旋转后的图象，0：左右不镜象，1：左右镜象）
	//     nMaxFace ---- 最多支持的人脸个数
	// 输出参数:
	//     bRgb24 ---- 输出的RGB24格式图象数据(裁减、旋转和镜象后的图象数据)
	//     nNewWidth ---- 输出图象的宽度(裁减、旋转和镜象后的图象宽度)
	//     nNewHeight ---- 输出图象的高度(裁减、旋转和镜象后的图象宽度)
	//     sFaceResult ---- 检测到的人脸参数（人脸及眼睛等坐标位置及角度等，相对于裁减、旋转和镜象后的图象，调用前必须分配nMaxFace 个对象空间）
	// 返回：返回检测到的人脸个数，0表示无人脸，-1表示未授权，-2表示参数错误，其它表示内部错误
	// 备注：1. sFaceResult 需分配不少于 nMaxFace 个对象空间，实际填充的人脸参数个数以返回的人脸个数为准
	//       2. 使用此接口的检测结果来提取特征时，必须使用这里的输出图象、输出图象的宽高和检测到的人脸参数做为参数
	//       3. 界面上需要画人脸人眼等坐标时，需要对检测出的人脸坐标参数根据裁减、旋转和镜象情况进行校正
	public static native int DetectAllFacesEx(int nFmt, byte[] bSrcImg, int nWidth, int nHeight, int nRotate, int bMirror, int nMaxFace, byte[] bRgb24, int[] nNewWidth, int[] nNewHeight, FACE_DETECT_RESULT[] sFaceResult);

	// 检测人脸质量级别
	// 输入参数：
	//     bRgb24 ---- RGB24格式的图象数据
	//     nWidth ---- 图象数据宽度
	//     nHeight ---- 图象数据高度
	//     sFaceResult ---- 检测到的人脸参数（必须将检测人脸返回的结果原样传入）
	// 输出参数：sFaceQualityLevel ---- 返回人脸质量
	// 返回：成功返回0，未授权返回-1，参数错误返回-2，失败返回其它
	public static native int FaceQualityLevel(byte[] bRgb24, int nWidth, int nHeight, FACE_DETECT_RESULT sFaceResult, FACE_QUALITY_LEVEL sFaceQualityLevel);

	// 检测口罩
	// 输入参数：
	//     bRgb24 ---- RGB24格式的图象数据
	//     nWidth ---- 图象数据宽度
	//     nHeight ---- 图象数据高度
	//     sFaceResult ---- 检测到的人脸参数（必须将检测人脸返回的结果原样传入）
	// 输出参数：无
	// 返回：未戴口罩返回0，口罩未遮住鼻子返回1，口罩遮住鼻子返回2，失败返回负值
	public static native int FaceMask(byte[] bRgb24, int nWidth, int nHeight, FACE_DETECT_RESULT sFaceResult);

	// 检测年龄性别
	// 输入参数：
	//     bRgb24 ---- RGB24格式的图象数据
	//     nWidth ---- 图象数据宽度
	//     nHeight ---- 图象数据高度
	//     sFaceResult ---- 检测到的人脸参数（必须将检测人脸返回的结果原样传入）
	// 输出参数：
	//     pnAge ---- 返回年龄（0-100）
	//     pnGender ---- 返回性别（1-male, 0-female）
	// 返回：检测成功返回0，失败返回负值
	public static native int DetectAge(byte[] bRgb24, int nWidth, int nHeight, FACE_DETECT_RESULT sFaceResult, int[] pnAge, int[] pnGender);

	// 获取特征码大小
	// 输入参数：无
	// 输出参数：无
	// 返回：特征码大小
	public static native int FeatureSize();

	// 提取特征码
	// 输入参数：
	//     bRgb24 ---- RGB24格式的图象数据
	//     nWidth ---- 图象数据宽度
	//     nHeight ---- 图象数据高度
	//     sFaceResult ---- 检测到的人脸参数（必须将检测人脸返回的结果原样传入）
	// 输出参数：bFeature ---- 特征码（调用前必须分配有效的空间）
	// 返回：成功返回0，未授权返回-1，参数错误返回-2，失败返回其它
	public static native int FeatureGet(byte[] bRgb24, int nWidth, int nHeight, FACE_DETECT_RESULT sFaceResult, byte[] bFeature);

	// 一对一特征比对
	// 输入参数：
	//     bFeature1 ---- 第1个人脸特征
	//     bFeature2 ---- 第2个人脸特征
	// 输出参数：无
	// 返回：返回两个人脸特征对应的人脸的相似度（0-100）
	public static native int FeatureCompare(byte[] bFeature1, byte[] bFeature2);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//                                                                                                               //
	//  以下为一对多比对接口，对于目标人员较多且相对固定的场景，效率远比循环调用一对一接口时要高得多                 //
	//                                                                                                               //
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// 创建一对多特征比对列表
	// 输入参数：nMaxFeatureNum ---- 列表能容纳的最大特征数
	// 输出参数：无
	// 返回：返回特征比对列表句柄，为空表示创建列表失败
	// 备注：能容纳的最大特征数越大，则消耗的内存越多，另外实际支持的最大特征数可能受加密狗限制
	public static native int ListCreate(int nMaxFeatureNum);

	// 向一对多特征比对列表加入目标特征
	// 输入参数：
	//        hList ---- 目标特征要加入的特征比对列表句柄
	//        nPos ---- 指针非空时存放特征插入位置，位置值为0表示插到列表最前面，为-1或大于当前列表中的特征数则表示插到列表最后面
	//        bFeatures ---- 要插入的目标特征码，多个特征按特征码大小（FeatureSize函数接口返回的值）对齐
	//        nFeatureNum ---- 要插入的特征数量
	// 输出参数：pnPos ---- 指针非空时返回第一个特征的插入位置
	// 返回：返回当前列表的总特征数
	public static native int ListInsert(int hList, int[] nPos, byte[] bFeatures, int nFeatureNum);

	// 从一对多特征比对列表中删除部分特征
	// 输入参数：
	//        hList ---- 要删除特征的特征比对列表句柄
	//        nPos ---- 要删除的特征的起始位置
	//        nFeatureNum ---- 要删除的特征数量
	// 输出参数：无
	// 返回：返回当前列表的总特征数
	public static native int ListRemove(int hList, int nPos, int nFeatureNum);

	// 清空一对多特征比对列表中的所有特征
	// 输入参数：
	//        hList ---- 要清空的一对多特征比对列表句柄
	// 输出参数：无
	// 返回：无
	// 备注：调用后列表中的实际特征数量为0（列表能容纳的最大特征数保持不变）
	public static native void ListClearAll(int hList);

	// 一对多特征比对
	// 输入参数：
	//     hList ---- 存放要参与比较的目标特征库的特征比对列表句柄
	//     bFeature ---- 要参与特征比对的源特征码
	//     nPosBegin ---- 要参与比对的目标特征的起始位置（如果比对全部目标特征，则起始位置填0）
	//     nCompareNum ---- 要参与比对的目标特征的数量（如果比对全部目标特征，则比对数量填0或-1或实际特征数或最大特征数）
	// 输出参数：nScores ---- 顺序存放与各目标特征进行比对的相似度（每个特征比对的结果均为一字节，值范围为0-100）
	// 返回：返回实际参与比对的特征数量，也是返回的相似度个数
	public static native int ListCompare(int hList, byte[] bFeature, int nPosBegin, int nCompareNum, byte[] nScores);

	// 销毁一对多特征比对列表
	// 输入参数：
	//        hList ---- 要销毁的一对多特征比对列表句柄
	// 输出参数：无
	// 返回：无
	// 备注：特征比对列表不再使用时需调用此接口释放资源
	public static native void ListDestroy(int hList);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//                                                                                                               //
	//  以下为活体检测接口                                                                                            //
	//                                                                                                               //
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// 双目活体检测（需要双模彩色和红外图象）
	// 输入参数：
	//     nWidth ---- 图象数据宽度（象素单位）
	//     nHeight ---- 图象数据高度（象素单位）
	//     bColorRgb24 ---- RGB24格式的彩色图象数据，为空时表示进行红外单目活检
	//     sColorFaceResult ---- 彩色图象检测出的人脸参数（调用 DetectFace 对彩色图象检测出的结果，传入此参数检测效率最高）
	//     bBwRgb24 ---- RGB24格式的红外图象数据，为空时表示进行彩色单目活检
	//     sBwFaceResult ---- 红外图象检测出的人脸参数（调用 DetectFace 对黑白图象检测出的结果，传入此参数检测效率最高）
	// 输出参数：
	//     nScores ---- 输出活检分数（双目活检时彩色活检分数存入nScores[0]，红外活检分数存入nScores[1]；单目活检时分数存入nScores[0]），调用前至少分配2个整数空间，传空值表示不需要返回分数
	// 返回值：
	//    1 ---- 已确认是活体,本轮结果无需继续检测
	//    0 ---- 本帧不确认是活体,需继续检测
	//   -1 ---- 活体检测功能未授权
	//   -2 ---- 参数错误
	//   -X ---- 内部错误
	// 备注：
	//    1. LiveDetect 调用前需要同时对彩色图象和红外图象进行人脸检测，并且都能检测到人脸，然后才能调用本接口并将检测结果传入；如果彩色与红外图象中只有一个能检测到活体，则直接判为非活体；如果彩色与红外图象均检测不到人脸，则直接认定相机前没有人
	//    2. LiveDetectImg 调用前不需要对图象进行人脸检测，直接调用并传入彩色和红外图象（内部会先对两个图象检测人脸然后判别是否活体，检测效率最低）
	//    3. 过程中只要一次确认是活体，则本轮结果确认为活体，但人脸丢失时仍需重新检活；如超时仍无一次确认是活体，则本轮结果确认为非活体
	//    4. 自定义阈值时，需彩色和红外分数均达到阈值方能判定为活体
	public static native int LiveDetect(int nWidth, int nHeight, byte[] bColorRgb24, FACE_DETECT_RESULT sColorFaceResult, byte[] bBwRgb24, FACE_DETECT_RESULT sBwFaceResult);
	public static native int LiveDetectEx(int nWidth, int nHeight, byte[] bColorRgb24, FACE_DETECT_RESULT sColorFaceResult, byte[] bBwRgb24, FACE_DETECT_RESULT sBwFaceResult, int[] nScores);
	public static native int LiveDetectImg(int nWidth, int nHeight, byte[] bColorRgb24, byte[] bBwRgb24);
	public static native int LiveDetectImgEx(int nWidth, int nHeight, byte[] bColorRgb24, byte[] bBwRgb24, int[] nScores);

	// 单目活体检测（只需要单路图象，彩色或红外均支持）
	// 输入参数：
	//     nCameraType ---- 采集设备类型（0-彩色相机，1-红外相机）
	//     nWidth ---- 图象数据宽度（象素单位）
	//     nHeight ---- 图象数据高度（象素单位）
	//     bRgb24 ---- RGB24格式的图象数据
	//     sFaceResult ---- 图象检测出的人脸参数（调用 DetectFace 对图象检测出的结果，传入此参数检测效率最高）
	// 输出参数：
	//     nScore ---- 活检分数（存入nScores[0]中），调用前需分配空间，传空值表示不需要返回分数
	// 返回值：
	//    1 ---- 已确认是活体,本轮结果无需继续检测
	//    0 ---- 本帧不确认是活体,需继续检测
	//   -1 ---- 活体检测功能未授权
	//   -2 ---- 参数错误
	//   -X ---- 内部错误
	// 备注：
	//    1. LiveDetectOneCamera 调用前需要先对图象进行人脸检测，并能检测到人脸，然后再调用本接口并将检测结果传入；如果图象不能检测到活体，则可认定相机前没有人
	//    2. LiveDetectImgOneCamera 调用前不需要先对图象进行人脸检测，直接调用本接口并传入图象数据（内部会先对图象检测人脸然后判别是否活体，检测效率最低）
	//    3. 过程中只要一次确认是活体，则本轮结果确认为活体，但人脸丢失时仍需重新检活；如超时仍无一次确认是活体，则本轮结果确认为非活体
	public static native int LiveDetectOneCamera(int nCameraType, int nWidth, int nHeight, byte[] bRgb24, FACE_DETECT_RESULT sFaceResult);
	public static native int LiveDetectOneCameraEx(int nCameraType, int nWidth, int nHeight, byte[] bRgb24, FACE_DETECT_RESULT sFaceResult, int[] nScore);
	public static native int LiveDetectImgOneCamera(int nCameraType, int nWidth, int nHeight, byte[] bRgb24);
	public static native int LiveDetectImgOneCameraEx(int nCameraType, int nWidth, int nHeight, byte[] bRgb24, int[] nScore);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//                                                                                                               //
	//  以下为扩展接口                                                                                                //
	//                                                                                                               //
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// 设置活体检测的判活阈值
	// 输入参数：threshold ---- 想要设置的阈值,支持的阈值范围为 30~70，默认值为50.
	// 输出参数：无
	// 返回值：无
	// 备注：设置过高的阈值则真人也难判别为活体，设置过低的阈值会导致照片也可能误判为活体。建议保持默认值或在默认值左右微调
	//       任何时候调用均有效
	public static native void SetLiveFaceThreshold(int threshold);

	// 获取判活阈值
	// 输入参数：无
	// 输出参数：无
	// 返回值：返回当前活体检测的判活阈值（默认值为50）
	// 备注：任何时候调用均有效
	public static native int GetLiveFaceThreshold();

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//                                                                                                               //
	//  以下为支持应用做二次加密开放接口                                                                               //
	//                                                                                                               //
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// 获取加密狗的唯一ID
	// 输入参数：无
	// 输出参数：bHwID ---- 返回加密狗的唯一ID（8字节二进制数据，调用前必须预分配8字节缓冲区）
	// 返回值：无
	public static native void DogGetID(byte[] bHwID);

	// 写用户数据到加密狗
	// 输入参数：bData ---- 需要写入加密狗中的数据
	//         nDataLen ---- 需要写入的数据长度（不同类型的加密狗支持的最大数据长度不同，通常不小于64字节不大于256字节）
	// 输出参数：无
	// 返回：成功返回写入的数据长度，失败返回-1
	// 备注：1. 写入加密狗中的数据已加密，同样的数据写入不同加密狗后也是不同的。只有插入同一加密狗调用读函数读出来才是正确的。
	//       2. 不支持频繁写入，最多写入1000次，写超过1000次以后就只读了（再写入直接返回失败）
	//       3. 不同类型加密狗支持的最大数据长度不同，通常不小于64字节不大于256字节，超过加密狗支持的长度直接返回失败
	public static native int DogWriteData(byte[] bData, int nDataLen);

	// 从加密狗读用户数据
	// 输入参数：nReadLen ---- 需要读取的数据长度（返回的数据长度不会超过写入的长度，有写入数据后才能读取）
	// 输出参数：bData ---- 存放读到的数据，调用前必须分配足够的空间
	// 返回：成功返回读到的数据长度，失败返回-1
	// 备注：1. 写入加密狗中的数据已加密，同样的数据写入不同加密狗后也是不同的，只有插入同一加密狗调用本函数读出来才是正确的。
	//      2. 读数据无次数限制
	public static native int DogReadData(byte[] bData, int nReadLen);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//                                                                                                               //
	//  以下为辅助接口                                                                                                //
	//                                                                                                               //
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// 读图象文件，支持BMP、JPEG、PNG文件
	// 输入参数：
	//     strFileName ---- 要解码的图象文件名
	//     nBufSize ---- 图象数据缓冲区长度，长度值小于解码数据所需空间时，解码失败只返回图象分辨率不返回数据
	//     nDepth ---- 请求解码后的图象数据的颜色深度，要求灰度图象数据时真 8，要求RGB24格式的图象数据时填 24
	// 输出参数：
	//     bBuffer ---- 解码出的图象数据，必须预先分配足够的缓冲区，或者传入空值表示只检测图象分辨率不返回数据
	//     nWidth ---- 接收图象宽度
	//     nHeight ---- 接收图象高度
	// 返回：0-成功 ，< 0 失败
	public static native int ReadImageFile(String strFileName, byte[] bBuffer, int nBufSize, int[] nWidth, int[] nHeight, int nDepth);

	// 读图象文件的角度
	// 输入参数：
	//     strFileName ---- 要解码的图象文件名
	// 输出参数：
	//     nWidth ---- 接收图象宽度
	//     nHeight ---- 接收图象高度
	// 返回：需旋转的角度（正数表示顺时针, 负数表示逆时针)
	public static native int ReadDegreeImageFile(String strFileName, int[] nWidth, int[] nHeight);

	// 读图象文件数据，支持BMP、JPEG、PNG文件数据
	// 输入参数：
	//     bFileData ---- 要读取的图象文件数据（支持原始文件数据及BASE64编码的文件数据）
	//     nFileDataSize ---- 要读取的图象文件数据大小（如果输入的文件数据是BASE64编码，则此参数为BASE64编码后的数据长度）
	//     nBufSize ---- 接收图象数据的缓冲区bRgbBufferr 大小，当接收缓冲区大小为0时，只输出图象的宽度和高度
	//     nDepth ---- 要接收的图象数据的位深度（需要灰度图象时填8，需要RGB24图象时填24）
	// 输出参数：
	//     bRgbBuffer ---- 接收图象数据，必须分配足够大的缓冲区并且将分配的缓冲区大小通过nBufSize参数传入
	//     nWidth ---- 接收图象宽度
	//     nHeight ---- 接收图象高度
	// 返回：0-成功 ，< 0 失败
	public static native int ReadImageFileData(byte[] bFileData, int nFileDataSize, byte[] bRgbBuffer, int nBufSize, int[] nWidth, int[] nHeight, int nDepth);

	// 读图象文件的角度
	// 输入参数：
	//     strFileName ---- 要解码的图象文件名
	// 输出参数：
	//     nWidth ---- 接收图象宽度
	//     nHeight ---- 接收图象高度
	// 返回：需旋转的角度（正数表示顺时针, 负数表示逆时针)
	public static native int ReadDegreeImageFileData(byte[] bFileData, int nFileDataSize, int[] nWidth, int[] nHeight);

	// RGB24或灰度图象数据保存为JPEG文件
	// 输入参数：
	//     strFileName ---- 要保存的JPEG文件名
	//     bData ---- RGB24或灰度图象数据
	//     nWidth ---- 图象宽度
	//     nHeight ---- 图象高度
	//     nDepth ---- 图象数据的颜色深度，灰度图象数据必须为 8，RGB24图象数据必须为 24
	//     nQuality ---- 图象质量（范围：0-100, 值越大图象质量超好文件也越大，建议一般场景为 75，要求高图象质量时为 95）
	// 输出参数：无
	// 返回：0-成功 ，< 0 失败
	public static native int SaveJpegFile(String strFileName, byte[] bData, int nWidth, int nHeight, int nDepth, int nQuality);

	// RGB24或灰度图象数据编码成JPEG文件数据
	// 输入参数：
	//     bRgbData ---- RGB24格式的图象数据
	//     nWidth ---- 图象宽度
	//     nHeight ---- 图象高度
	//     nDepth ---- 要保存的图象类型（24--保存彩色图象，其它-保存灰度图象）
	//     nQuality ---- 要保存的图象文件质量（0-100，参考值90）
	//     nBufSize ---- bFileDataBuffer分配的大小
	// 输出参数：
	//     bFileDataBuffer ---- 用于保存JPEG编码后的文件数据的缓冲区（调用前需分配足够的内存空间并通过nBufSize参数传入分配的空间大小）
	//     nFileDataSize ---- 接收JPEG编码后的文件数据长度（如文件数据缓冲区分配不足，也会返回所需的缓冲区长度）
	// 返回值：
	//     0 ---- 成功
	//     非0 ---- 失败
	public static native int SaveJpegFileData(byte[] bRgbData, int nWidth, int nHeight, int nDepth, int nQuality, byte[] bFileDataBuffer, int nBufSize, int[] nFileDataSize);

	// 旋转镜象RGB24图象数据
	// 输入参数：
	//     bRgbDataSrc ---- 旋转前的原始图象数据
	//     nWidth ---- 旋转前的图象宽度
	//     nHeight ---- 旋转前的图象高度
	//     nDegree ---- 旋转的角度（支持0、±90、±180、±270，正数表示右转，负数表示左转，0表示不旋转）
	//     nMirror ---- 左右镜象（0-不镜象，1-左右镜象）
	//     bRgbDataDst ---- 旋转后的目标图象数据，必须分配足够的缓冲区（不小于 nWidth * nHeight * 3）
	// 输出参数：无
	// 返回值：
	//     0 ---- 成功
	//     -1 ---- 参数错误
	//     -2 ---- 其它错误
	public static native int RotateRgb24Data(byte[] bRgbDataSrc, int nWidth, int nHeight, int nDegree, int nMirror, byte[] bRgbDataDst);

	// 裁剪小图
	// 输入参数：
	//     bRgbDataSrc ---- 原图数据（RGB24格式）
	//     nSrcWidth ---- 原图宽度
	//     nSrcHeight ---- 原图高度
	//     nLeft ---- 裁剪区域左上角X坐标
	//     nTop ---- 裁剪区域左上角Y坐标
	//     nWidth ---- 裁剪区域宽度
	//     nHeight ---- 裁剪区域高度
	//     nRate ---- 裁剪图象缩小倍数（0-不缩小，1-宽高缩小至原来的1/2，2-宽高缩小至原来的1/3，...）
	// 输出参数：
	//     bRgbDataCrop ---- 小图数据(RGB24格式，调用前需分析足够的缓冲区)
	// 返回值：
	//     0 ---- 成功
	//     -1 ---- 参数错误
	// 备注：裁剪后的图象分辨率为：宽度 = nWidth / (nRate + 1), 高度 = nHeight / (nRate + 1)
	public static native int CropImage(byte[] bRgbDataSrc, int nSrcWidth, int nSrcHeight, byte[] bRgbDataCrop, int nLeft, int nTop, int nWidth, int nHeight, int nRate);

	// YUV420P图象数据转换为RGB24图象数据
	// 输入参数：
	//     bYuv420P ---- YUV420P格式的图象数据（nFmt参数为0时，图象数据序列：Y Y Y Y ... U ... V ..., nFmt为1时，图象数据序列为：Y Y Y Y ... U V ...,nFmt为2时，图象数据序列为：Y Y Y Y ... V U ...）
	//     nWidth ---- 图象宽度
	//     nHeight ---- 图象高度
	//     nFmt ---- 图象数据格式（0：YUV420P, 1: NV12，2: NV21）
	// 输出参数：
	//     bRgb24 ---- RGB24格式的图象数据，必须预先分配足够的缓冲区
	// 返回：0-成功 ，< 0 失败
	public static native int YUV420P_TO_RGB24(byte[] bYuv420P, int nWidth, int nHeight, int nFmt, byte[] bRgb24);

	// YUV420P图象数据转换为RGB24+RGB32图象数据
	// 输入参数：
	//     bYuv420P ---- YUV420P格式的图象数据（nFmt参数为0时，图象数据序列：Y Y Y Y ... U ... V ..., nFmt为1时，图象数据序列为：Y Y Y Y ... U V ...,nFmt为2时，图象数据序列为：Y Y Y Y ... V U ...）
	//     nWidth ---- 图象宽度
	//     nHeight ---- 图象高度
	//     nFmt ---- 数据格式（0：YUV420P, 1: NV12，2: NV21）
	//     nZoom -- 缩放（0：不缩放，1：宽高缩小1倍，2：宽高缩小2倍）
	//     nRotate ---- 旋转（0：不旋转，1：逆时针旋转90度，2：顺时针旋转90度，3：旋转180度）
	//     bMirror ---- 镜象（0：不镜象，1：左右镜象）
	// 输出参数：
	//     bRgb24 ---- RGB24格式的图象数据，必须预先分配足够的缓冲区
	//     iRgb32 ---- RGB32格式的图象数据，必须预先分配足够的缓冲区
	// 返回：0-成功 ，< 0 失败
	// 备注：RGB32格式的ALPHA值应是0xFF，直接调用Bitmap.setPixels后就能显示出来
	public static native int YUV420P_TO_RGB24_RGB32(byte[] bYuv420P, int nWidth, int nHeight, int nFmt, int nZoom, int nRotate, int bMirror, byte[] bRgb24, int[] iRgb32);

	// YUV422图象数据转换为RGB24图象数据
	// 输入参数：
	//     bYuv422 ---- YUV422格式的图象数据（bYExchange参数为0时，图象数据序列：U Y V Y U Y V Y ...，bYExchange参数为1时，图象数据序列为：Y U Y V Y U Y V ...）
	//     nWidth ---- 图象宽度
	//     nHeight ---- 图象高度
	//     bYExchange ---- 源数据Y与UV排列位置交换（为0表示UYVY，为1表示YUYV）
	// 输出参数：
	//     bRgb24 ---- RGB24格式的图象数据，调用前必须预先分配足够的缓冲区
	// 返回：0-成功 ，< 0 失败
	public static native int YUV422_TO_RGB24(byte[] bYuv422, int nWidth, int nHeight, int bYExchange, byte[] bRgb24);

	// RGB24图象数据转换为YUV422图象数据
	// 输入参数：
	//     bRgb24 ---- RGB24格式的图象数据
	//     nWidth ---- 图象宽度
	//     nHeight ---- 图象高度
	//     bYExchange ---- 目标数据Y与UV排列位置交换（为0表示UYVY，为1表示YUYV）
	// 输出参数：
	//     bYuv422 ---- YUV422格式的图象数据（bYExchange参数为0时，图象数据序列：U Y V Y U Y V Y ...，bYExchange参数为1时，图象数据序列为：Y U Y V Y U Y V ...）
	//                  调用前必须预先分配足够的缓冲区
	// 返回：0-成功 ，< 0 失败
	public static native int RGB24_TO_YUV422(byte[] bRgb24, int nWidth, int nHeight, int bYExchange, byte[] bYuv422);

	// YUV422图象数据转换为RGB24图象数据，支持缩放、旋转和镜像
	// 输入参数：
	//     bYuv422 ---- YUV422格式的图象数据（bYExchange参数为0时，图象数据序列：U Y V Y U Y V Y ...，bYExchange参数为1时，图象数据序列为：Y U Y V Y U Y V ...）
	//     nWidth ---- 图象宽度
	//     nHeight ---- 图象高度
	//     bYExchange ---- Y与UV排列位置交换（为0表示UYVY，为1表示YUYV）
	//     nZoom -- 缩放（0：不缩放，1：宽高缩小1倍，2：宽高缩小2倍）
	//     nRotate ---- 旋转（0：不旋转，1：逆时针旋转90度，2：顺时针旋转90度，3：旋转180度）
	//     bMirror ---- 镜象（0：不镜象，1：左右镜象）
	// 输出参数：
	//     bRgb24 ---- RGB24格式的图象数据，必须预先分配足够的缓冲区
	// 返回：0-成功 ，< 0 失败
	public static native int YUV422_TO_RGB24_EX(byte[] bYuv422, int nWidth, int nHeight, int bYExchange, int nZoom, int nRotate, int bMirror, byte[] bRgb24);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//                                                                                                               //
	//  加载动态库，Android 5.0以上只需加需 IdFaceSdk 这个主动态库（其它会自动加载）                                    //
	//                                                                                                               //
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	static {
	    try {
			if(android.os.Build.VERSION.SDK_INT < 21) { // API-Level 21 : ANDROID 5.0
				System.loadLibrary("eface_detect");
				System.loadLibrary("eface_feature");
				System.loadLibrary("eface_check");
				System.loadLibrary("eface_landmark");
				System.loadLibrary("eface_liveness");
				System.loadLibrary("THFaceQuality");
				System.loadLibrary("THFaceMask");
				System.loadLibrary("THFaceProperty");
			}
			System.loadLibrary("IdFaceSdk");
		} catch (Throwable e) {
			Log.e("LoadLibrary", "Load libIdFaceSdk.so fail：" + e.getMessage());
		}
	}
}

