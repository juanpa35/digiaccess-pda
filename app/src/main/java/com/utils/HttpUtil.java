package com.utils;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class HttpUtil {
    private static HttpUtil instance;
    private final OkHttpClient client;
    private final Handler mainHandler; // 用于切换到主线程
    private final Gson gson; // 用于JSON转换

    // 单例模式
    public static HttpUtil getInstance() {
        if (instance == null) {
            synchronized (HttpUtil.class) {
                if (instance == null) {
                    instance = new HttpUtil();
                }
            }
        }
        return instance;
    }

    private HttpUtil() {
        // 初始化OkHttpClient
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        // 初始化主线程Handler
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();
    }

    /**
     * GET请求
     * @param url 请求地址
     * @param callback 回调接口
     */
    public void get(String url, HttpCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 切换到主线程回调失败
                mainHandler.post(() -> callback.onFailure(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        // 切换到主线程回调成功
                        mainHandler.post(() -> callback.onSuccess(responseData));
                    } else {
                        // 响应不成功
                        mainHandler.post(() -> callback.onFailure(
                                new IOException("Response code: " + response.code())));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onFailure(e));
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    /**
     * POST请求（JSON格式参数）
     * @param url 请求地址
     * @param params JSON参数（Map形式）
     * @param callback 回调接口
     */
    public void postJson(String url, Map<String, Object> params, HttpCallback callback) {
        // 将Map转换为JSON字符串
        String jsonParams = gson.toJson(params);

        // 创建JSON请求体
        RequestBody requestBody = RequestBody.create(
                jsonParams,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        mainHandler.post(() -> callback.onSuccess(responseData));
                    } else {
                        mainHandler.post(() -> callback.onFailure(
                                new IOException("Response code: " + response.code())));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onFailure(e));
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    /**
     * POST请求（直接传入JSON字符串）
     * @param url 请求地址
     * @param jsonString JSON字符串
     * @param callback 回调接口
     */
    public void postJson(String url, String jsonString, HttpCallback callback) {
        RequestBody requestBody = RequestBody.create(
                jsonString,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        mainHandler.post(() -> callback.onSuccess(responseData));
                    } else {
                        mainHandler.post(() -> callback.onFailure(
                                new IOException("Response code: " + response.code())));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onFailure(e));
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    /**
     * 回调接口
     */
    public interface HttpCallback {
        // 请求成功回调
        void onSuccess(String responseData);

        // 请求失败回调
        void onFailure(Exception e);
    }
}
