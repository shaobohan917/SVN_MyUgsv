package com.tencent.qcloud.xiaoshipin.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;


import com.tencent.qcloud.xiaoshipin.common.utils.TCConstants;
import com.tencent.qcloud.xiaoshipin.common.utils.TCUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 用户管理模块
 */
public class TCUserMgr {

    public static final String TAG = TCUserMgr.class.getSimpleName();
    public static final int SUCCESS_CODE = 200;
    private String mAppId = "";
    private String mSubAppId = "";
    private String mSecretId = "";
    private boolean userid;

    private TCUserMgr() {
        mHttpClient = new OkHttpClient().newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    private static class TCUserMgrHolder {
        private static TCUserMgr instance = new TCUserMgr();
    }

    public static TCUserMgr getInstance() {
        return TCUserMgrHolder.instance;
    }

    private OkHttpClient mHttpClient;
    private Context mContext;
    private String mUserId = "";
    private String mUserPwd = "";
    private String mToken = "";
    private String mRefreshToken = "";
    private int mTokenExpires = 0;
    private long mSdkAppID = 0;
    private String mUserSig = "";
    private String mAccountType;
    private String mNickName = "";
    private String mUserAvatar = "";
    private int mSex = -1;//0:male,1:female,-1:unknown
    private String mCoverPic;
    private String mLocation;


    //cos 配置
    public static class CosInfo {
        public String bucket = "";
        public String appID = "";
        public String secretID = "";
        public String region = "";
    }

    private CosInfo mCosInfo = new CosInfo();

    public CosInfo getCosInfo() {
        return mCosInfo;
    }

    public interface Callback {

        /**
         * 登录成功
         */
        void onSuccess(JSONObject data);

        /**
         * 登录失败
         *
         * @param code 错误码
         * @param msg  错误信息
         */
        void onFailure(int code, final String msg);

    }

    public static class HttpCallback implements okhttp3.Callback {
        private Callback callback;
        private String module;

        public HttpCallback(String module, Callback callback) {
            this.callback = callback;
            this.module = module;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            if (callback != null) {
                callback.onFailure(-1, module + " request failure");
            }
            Log.w(TAG, "xzb_process: " + module + " failure");
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            String body = response.body().string();
            JSONObject jsonObject = null;
            int code = -1;
            String message = "";
            JSONObject data = null;
            try {
                jsonObject = new JSONObject(body);
                code = jsonObject.getInt("code");
                message = jsonObject.getString("message");
                data = jsonObject.optJSONObject("data");
            } catch (JSONException e) {
                code = -1;
                e.printStackTrace();
            }

            if (code == SUCCESS_CODE) {
                if (callback != null) callback.onSuccess(data);
                Log.w(TAG, "xzb_process: " + module + " success");
            } else {
                if (callback != null) callback.onFailure(code, message);
                Log.w(TAG, "xzb_process: " + module + " error " + code + " message " + message);
            }

            Log.d(TAG, "xzb_process: " + response.toString() + ", Body" + body);
        }
    }

    public void initContext(Context context) {
        mContext = context;
        loadUserInfo();
    }

    public boolean hasUser() {
        return !TextUtils.isEmpty(mUserId) && !TextUtils.isEmpty(mUserPwd);
    }

    public String getUserToken() {
        return mToken;
    }

    public void setUserId(String userId) {
        this.mUserId = userId;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getNickname() {
        return mNickName;
    }

    public void setNickName(String nickName, final Callback callback) {
        mNickName = nickName;
        uploadUserInfo(callback);
    }

    public String getHeadPic() {
        return mUserAvatar;
    }

    public void setHeadPic(String pic, final Callback callback) {
        mUserAvatar = pic;
        uploadUserInfo(callback);
    }

    public String getCoverPic() {
        return mCoverPic;
    }

    public void setCoverPic(String pic, final Callback callback) {
        mCoverPic = pic;
        uploadUserInfo(callback);
    }

    public String getLocation() {
        return mLocation;
    }

    public void setLocation(String location, final Callback callback) {
        mLocation = location;
//        uploadUserInfo(callback);
    }

    public int getUserSex() {
        return mSex;
    }

    public void setUserSex(int sex, final Callback callback) {
        mSex = sex;
        uploadUserInfo(callback);
    }

    public long getSDKAppID() {
        return mSdkAppID;
    }

    public String getAccountType() {
        return mAccountType;
    }

    public void autoLogin(final Callback callback) {
        loginByToken(mUserId, mUserPwd, callback);
    }

    public void login(final String userid, final String password, final Callback callback) {
        final String pwd = TCUtils.md5(TCUtils.md5(password) + userid);
        loginByToken(userid, pwd, callback);
    }

    public void logout() {
        mUserId = "";
        mUserPwd = "";
        mCoverPic = "";
        mUserAvatar = "";
        mLocation = "";
        clearUserInfo();
    }

    public void refresh(final Callback callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("userid", mUserId)
                    .put("refresh_token", mRefreshToken);

            request("/refresh", body, new HttpCallback("login", new Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    mToken = data.optString("token");
                    mRefreshToken = data.optString("refresh_token");
                    mTokenExpires = data.optInt("expires");
                    if (callback != null) {
                        callback.onSuccess(data);
                    }
                }

                @Override
                public void onFailure(int code, final String msg) {
                    if (callback != null) {
                        callback.onFailure(code, msg);
                    }
                }
            }));

        } catch (Exception e) {
            if (callback != null) {
                callback.onFailure(-1, "");
            }
        }
    }

    public void uploadLogs(String action, String userName, long code, String errorMsg, okhttp3.Callback callback) {
        Log.w(TAG, "uploadLogs: errorMsg " + errorMsg);
        String reqUrl = TCConstants.DEFAULT_ELK_HOST;
        String body = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("action", action);
            jsonObject.put("action_result_code", code);
            jsonObject.put("action_result_msg", errorMsg);
            jsonObject.put("type", "xiaoshipin");
            jsonObject.put("userName", userName);
            jsonObject.put("platform", "android");
            body = jsonObject.toString();
            Log.d(TAG, body);
        } catch (JSONException e) {
            e.printStackTrace();
        }
//        Request request = new Request.Builder()
//                .url(reqUrl)
//                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
//                .build();
//        mHttpClient.newCall(request).enqueue(callback);
    }

    public void register(final String userid, final String password, final Callback callback) {
        try {
            String pwd = TCUtils.md5(TCUtils.md5(password) + userid);
            String body = new JSONObject()
                    .put("userid", userid)
                    .put("password", pwd)
                    .toString();
            Log.w(TAG, "xzb_process: start register " + userid);
            request("/register", body, new HttpCallback("register", callback));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getCosSig(final Callback callback) {
        try {
            JSONObject body = new JSONObject();
            request("/get_cos_sign", body, new HttpCallback("get_cos_sign", callback));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getVodSig(final Callback callback) {
        try {
            JSONObject body = new JSONObject();
            request("/get_vod_sign", body, new HttpCallback("get_vod_sign", callback));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void fetchUserInfo(final Callback callback) {
        try {
            JSONObject body = new JSONObject();
            request("/get_user_info", body, new HttpCallback("get_user_info", new Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    if (data != null) {
                        mUserAvatar = data.optString("avatar");
                        mNickName = data.optString("nickname");
                        mCoverPic = data.optString("frontcover");
                        mSex = data.optInt("sex");
                    }
                    if (callback != null) {
                        callback.onSuccess(data);
                    }
                    saveUserInfo();
                }

                @Override
                public void onFailure(int code, final String msg) {
                    if (callback != null) {
                        callback.onFailure(code, msg);
                    }
                }
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void uploadUserInfo(final Callback callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("nickname", mNickName != null ? mNickName : "")
                    .put("avatar", mUserAvatar != null ? mUserAvatar : "")
                    .put("sex", mSex)
                    .put("frontcover", mCoverPic != null ? mCoverPic : "");
            request("/upload_user_info", body, new HttpCallback("upload_user_info", callback));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void request(String cmd, String body, okhttp3.Callback callback) {
        String reqUrl = TCConstants.APP_SVR_URL + cmd;
        Request request = new Request.Builder()
                .url(reqUrl)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
                .build();
        mHttpClient.newCall(request).enqueue(callback);
    }

    public void request(String cmd, JSONObject body, okhttp3.Callback callback) {
        long now = System.currentTimeMillis() / 1000;
        try {
            String strBody = body.put("userid", mUserId)
                    .put("timestamp", now)
                    .put("expires", 3000)
                    .toString();

            String sig = getRequestSig(body);

            Request request = new Request.Builder()
                    .url(TCConstants.APP_SVR_URL + cmd)
                    .addHeader("Liteav-Sig", sig)
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), strBody))
                    .build();
            mHttpClient.newCall(request).enqueue(callback);
            Log.d(TAG, "xzb_process: " + request.toString() + ", Body" + strBody);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public String getRequestSig(JSONObject body) {
        long now = System.currentTimeMillis() / 1000;
        String strBody = null;
        try {
            strBody = body.put("userid", mUserId)
                    .put("timestamp", now)
                    .put("expires", 3000)
                    .toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String sig = TCUtils.md5(mToken + TCUtils.md5(strBody));
        return sig;
    }

    private void loginByToken(final String userid, final String pwd, final Callback callback) {
        try {
            String body = new JSONObject()
                    .put("userid", userid)
                    .put("password", pwd)
                    .toString();

            request("/login", body, new HttpCallback("login", new Callback() {
                @Override
                public void onSuccess(JSONObject data) {
                    mUserId = userid;
                    mUserPwd = pwd;
                    mToken = data.optString("token");
                    mRefreshToken = data.optString("refresh_token");
                    mTokenExpires = data.optInt("expires");
                    if (data.has("roomservice_sign")) {
                        JSONObject serviceSig = data.optJSONObject("roomservice_sign");
                        mUserSig = serviceSig.optString("userSig");
//                        mUserId = serviceSig.optString("userID");
                        mAccountType = serviceSig.optString("accountType");
                        mSdkAppID = serviceSig.optInt("sdkAppID");
                    }
                    JSONObject serviceSig = data.optJSONObject("vod_info");
                    mAppId = serviceSig.optString("Appid");
                    mSubAppId = serviceSig.optString("SubAppId");
                    mSecretId = serviceSig.optString("SecretId");

                    JSONObject cosInfo = data.optJSONObject("cos_info");
                    mCosInfo.bucket = cosInfo.optString("Bucket");
                    mCosInfo.appID = cosInfo.optString("Appid");
                    mCosInfo.region = cosInfo.optString("Region");
                    mCosInfo.secretID = cosInfo.optString("SecretId");

                    fetchUserInfo(null);

                    saveUserInfo();

                    // 登录成功上报
                    uploadLogs(TCConstants.ELK_ACTION_LOGIN, userid, SUCCESS_CODE, "登录成功", new okhttp3.Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.d(TAG,"login uploadLogs onFailure");
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            Log.d(TAG,"login uploadLogs onResponse");
                        }
                    });
                    if (callback != null) {
                        callback.onSuccess(data);
                    }

                }

                @Override
                public void onFailure(int code, final String msg) {
                    // 登录失败上报
                    uploadLogs(TCConstants.ELK_ACTION_LOGIN, userid, code, msg, new okhttp3.Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.d(TAG,"login uploadLogs onFailure");
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            Log.d(TAG,"login uploadLogs onResponse");
                        }
                    });
                    if (callback != null) {
                        String errorMsg = msg;
                        if (code == 620) {
                            errorMsg = "用户不存在";
                        } else if (code == 621) {
                            errorMsg = "密码错误";
                        }
                        callback.onFailure(code, errorMsg);
                    }
                    clearUserInfo();
                }
            }));

        } catch (Exception e) {
            if (callback != null) {
                callback.onFailure(-1, "");
            }
        }
    }

    private void loadUserInfo() {
        //TODO: decrypt
        if (mContext == null) return;
        Log.d(TAG, "xzb_process: load local user info");
        SharedPreferences settings = mContext.getSharedPreferences("TCUserInfo", Context.MODE_PRIVATE);
        mUserId = settings.getString("userid", "");
        mUserPwd = settings.getString("userpwd", "");
    }

    private void saveUserInfo() {
        //TODO: encrypt
        if (mContext == null) return;
        Log.d(TAG, "xzb_process: save local user info");
        SharedPreferences settings = mContext.getSharedPreferences("TCUserInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("userid", mUserId);
        editor.putString("userpwd", mUserPwd);
        editor.commit();
    }

    private void clearUserInfo() {
        if (mContext == null) return;
        SharedPreferences settings = mContext.getSharedPreferences("TCUserInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("userid", "");
        editor.putString("userpwd", "");
        editor.commit();
    }
}
