package com.tencent.qcloud.xiaoshipin.videorecord;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.liteav.basic.log.TXCLog;
import com.tencent.qcloud.xiaoshipin.R;
import com.tencent.qcloud.xiaoshipin.common.activity.TCBaseActivity;
import com.tencent.qcloud.xiaoshipin.common.utils.TCConstants;
import com.tencent.qcloud.xiaoshipin.common.widget.BeautySettingPannel;
import com.tencent.qcloud.xiaoshipin.login.TCUserMgr;
import com.tencent.qcloud.xiaoshipin.videoeditor.TCVideoPreprocessActivity;
import com.tencent.qcloud.xiaoshipin.videoeditor.bgm.BGMSelectActivity;
import com.tencent.qcloud.xiaoshipin.videoeditor.bgm.view.TCBGMPannel;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLog;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.ugc.TXRecordCommon;
import com.tencent.ugc.TXUGCRecord;
import com.tencent.ugc.TXVideoEditConstants;
import com.umeng.socialize.UMShareAPI;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static android.view.View.GONE;

/**
 * UGC短视频录制界面
 */
public class TCVideoRecordActivity extends TCBaseActivity implements View.OnClickListener, BeautySettingPannel.IOnBeautyParamsChangeListener
        , TXRecordCommon.ITXVideoRecordListener, View.OnTouchListener, GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "TCVideoRecordActivity";
    private static final String OUTPUT_DIR_NAME = "TXUGC";
    private boolean mRecording = false;
    private boolean mStartPreview = false;
    private boolean mFront = true;
    private TXUGCRecord mTXCameraRecord;
    private TXRecordCommon.TXRecordResult mTXRecordResult;

    private BeautySettingPannel.BeautyParams mBeautyParams = new BeautySettingPannel.BeautyParams();
    private TXCloudVideoView mVideoView;
    private LinearLayout backLL;
    private TextView mTvNextStep;
    private TextView mProgressTime;
    private ProgressDialog mCompleteProgressDialog;
    private ImageView mIvTorch;
    private RelativeLayout mRlBeautyAndBGMContainer;
    private ImageView mIvBeauty;
    private ImageView mIvScale;
    private ComposeRecordBtn mComposeRecordBtn;
    private RelativeLayout mRlAspect;
    private RelativeLayout mRlAspectSelect;
    private ImageView mIvAspectSelectFirst;
    private ImageView mIvAspectSelectSecond;
    private ImageView mIvScaleMask;
    private ImageView mIvMusicMask;
    private boolean mAspectSelectShow = false;

    private BeautySettingPannel mBeautyPannelView;
    private AudioManager mAudioManager;
    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusListener;
    private boolean mPause = false;

    private TCBGMPannel mTCBGMPannel;
    private long mBGMStartTime, mBgmEndTime;
    private int mBgmPosition = -1;
    private int mCurrentAspectRatio;
    private int mFirstSelectScale;
    private int mSecondSelectScale;
    private RelativeLayout mRecordRelativeLayout = null;
    private FrameLayout mMaskLayout;
    private RecordProgressView mRecordProgressView;
    private ImageView mIvDeleteLastPart;
    private boolean isSelected = false; // 回删状态
    private boolean mIsTorchOpen = false; // 闪光灯的状态

    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private float mScaleFactor;
    private float mLastScaleFactor;

    private int mRecommendQuality = TXRecordCommon.VIDEO_QUALITY_MEDIUM;
    private int mMinDuration = 2 * 1000;
    private int mMaxDuration = 16 * 1000;
    private int mAspectRatio = TXRecordCommon.VIDEO_ASPECT_RATIO_9_16; // 视频比例
    private int mHomeOrientation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN; // 录制方向
    private int mRenderRotation = TXLiveConstants.RENDER_ROTATION_PORTRAIT; // 渲染方向
    private String mBGMPath;
    private String mBGMPlayingPath;
    private int mBGMDuration;
    private RadioGroup mRadioGroup;
    private int mRecordSpeed = TXRecordCommon.RECORD_SPEED_NORMAL;
    private RadioButton mRbSloweset;
    private RadioButton mRbFast;
    private RadioButton mRbFastest;
    private RadioButton mRbNormal;
    private RadioButton mRbSlow;
    private RelativeLayout mLayoutMusic;
    private RelativeLayout mLayoutAspect;
    private LinearLayout mLayoutBeauty;
    private LinearLayout mLayoutLeftPanel;
    private RelativeLayout mLayoutRightPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_video_record);

        initViews();

        getData();
    }

    private void getData() {
        Intent intent = getIntent();
        if (intent == null) {
            TXCLog.e(TAG, "intent is null");
            return;
        }

        mCurrentAspectRatio = mAspectRatio;

        mRecordProgressView.setMaxDuration(mMaxDuration);
        mRecordProgressView.setMinDuration(mMinDuration);
    }

    private void startCameraPreview() {
        if (mStartPreview) return;
        mStartPreview = true;

        mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
        mTXCameraRecord.setVideoRecordListener(this);
        // 推荐配置
        TXRecordCommon.TXUGCSimpleConfig simpleConfig = new TXRecordCommon.TXUGCSimpleConfig();
        simpleConfig.videoQuality = mRecommendQuality;
        simpleConfig.minDuration = mMinDuration;
        simpleConfig.maxDuration = mMaxDuration;
        simpleConfig.isFront = mFront;
        simpleConfig.needEdit = true;
        mTXCameraRecord.setHomeOrientation(mHomeOrientation);
        mTXCameraRecord.setRenderRotation(mRenderRotation);
//        mTXCameraRecord.setRecordSpeed(mRecordSpeed);
        mTXCameraRecord.startCameraSimplePreview(simpleConfig, mVideoView);
        mTXCameraRecord.setAspectRatio(mCurrentAspectRatio);

        mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
        mTXCameraRecord.setFaceScaleLevel(mBeautyParams.mFaceSlimLevel);
        mTXCameraRecord.setEyeScaleLevel(mBeautyParams.mBigEyeLevel);
        mTXCameraRecord.setFilter(mBeautyParams.mFilterBmp);
        mTXCameraRecord.setGreenScreenFile(mBeautyParams.mGreenFile, true);
        mTXCameraRecord.setMotionTmpl(mBeautyParams.mMotionTmplPath);
        mTXCameraRecord.setFaceShortLevel(mBeautyParams.mFaceShortLevel);
        mTXCameraRecord.setFaceVLevel(mBeautyParams.mFaceVLevel);
        mTXCameraRecord.setChinLevel(mBeautyParams.mChinSlimLevel);
        mTXCameraRecord.setNoseSlimLevel(mBeautyParams.mNoseScaleLevel);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initViews() {
        backLL = (LinearLayout) findViewById(R.id.back_ll);
        backLL.setOnClickListener(this);

        mMaskLayout = (FrameLayout) findViewById(R.id.mask);
        mMaskLayout.setOnTouchListener(this);

        mTvNextStep = (TextView) findViewById(R.id.tv_next_step);
        mTvNextStep.setOnClickListener(this);
        mTvNextStep.setVisibility(View.GONE);

        mBeautyPannelView = (BeautySettingPannel) findViewById(R.id.beauty_pannel);
        mBeautyPannelView.setBeautyParamsChangeListener(this);
        mBeautyPannelView.disableExposure();

        mTCBGMPannel = (TCBGMPannel) findViewById(R.id.tc_record_bgm_pannel);
        mTCBGMPannel.setMicVolumeINVisible();
        mTCBGMPannel.setOnBGMChangeListener(new TCBGMPannel.BGMChangeListener() {
            @Override
            public void onMicVolumeChanged(float volume) {
            }

            @Override
            public void onBGMVolumChanged(float volume) {
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setBGMVolume(volume);
                }
            }

            @Override
            public void onBGMTimeChanged(long startTime, long endTime) {
                if (mTXCameraRecord != null) {
                    mBGMStartTime = startTime;
                    mBgmEndTime = endTime;
                    mTXCameraRecord.playBGMFromTime((int) startTime, (int) endTime);
                }
            }

            @Override
            public void onClickReplace() {
                chooseBGM();
            }

            @Override
            public void onClickDelete() {
                prepareToDeleteBGM();
            }

            @Override
            public void onClickConfirm() {
                hideBgmPannel();
                stopPreviewBGM();
            }

            @Override
            public void onClickVoiceChanger(int type) {
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setVoiceChangerType(type);
                }
            }

            @Override
            public void onClickReverb(int type) {
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setReverb(type);
                }
            }
        });

        mVideoView = (TXCloudVideoView) findViewById(R.id.video_view);
        mVideoView.enableHardwareDecode(true);

        mProgressTime = (TextView) findViewById(R.id.progress_time);
        mIvDeleteLastPart = (ImageView) findViewById(R.id.btn_delete_last_part);
        mIvDeleteLastPart.setOnClickListener(this);

        mLayoutMusic = (RelativeLayout) findViewById(R.id.layout_music);
        mLayoutAspect = (RelativeLayout) findViewById(R.id.layout_aspect);
        mLayoutBeauty = (LinearLayout) findViewById(R.id.layout_beauty);

        mLayoutLeftPanel = (LinearLayout) findViewById(R.id.record_left_panel);
        mLayoutRightPanel = (RelativeLayout) findViewById(R.id.record_right_panel);

        mIvScale = (ImageView) findViewById(R.id.iv_scale);
        mIvScaleMask = (ImageView) findViewById(R.id.iv_scale_mask);
        mIvAspectSelectFirst = (ImageView) findViewById(R.id.iv_scale_first);
        mIvAspectSelectSecond = (ImageView) findViewById(R.id.iv_scale_second);
        mRlAspect = (RelativeLayout) findViewById(R.id.layout_aspect);
        mRlAspectSelect = (RelativeLayout) findViewById(R.id.layout_aspect_select);

        mRlBeautyAndBGMContainer = (RelativeLayout) findViewById(R.id.beauty_container);

        mIvMusicMask = (ImageView) findViewById(R.id.iv_music_mask);

        mIvBeauty = (ImageView) findViewById(R.id.btn_beauty);

        mRecordRelativeLayout = (RelativeLayout) findViewById(R.id.record_layout);
        mRecordProgressView = (RecordProgressView) findViewById(R.id.record_progress_view);

        mGestureDetector = new GestureDetector(this, this);
        mScaleGestureDetector = new ScaleGestureDetector(this, this);

        mCompleteProgressDialog = new ProgressDialog(this);
        mCompleteProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // 设置进度条的形式为圆形转动的进度条
        mCompleteProgressDialog.setCancelable(false);                           // 设置是否可以通过点击Back键取消
        mCompleteProgressDialog.setCanceledOnTouchOutside(false);               // 设置在点击Dialog外是否取消Dialog进度条

        mIvTorch = (ImageView) findViewById(R.id.btn_torch);
        mIvTorch.setOnClickListener(this);

        if (mFront) {
            mIvTorch.setVisibility(View.GONE);
            mIvTorch.setImageResource(R.drawable.ugc_torch_disable);
        } else {
            mIvTorch.setImageResource(R.drawable.selector_torch_close);
            mIvTorch.setVisibility(View.VISIBLE);
        }
        mComposeRecordBtn = (ComposeRecordBtn) findViewById(R.id.compose_record_btn);
        mComposeRecordBtn.setOnRecordButtonListener(new ComposeRecordBtn.IRecordButtonListener() {
            @Override
            public void onButtonStart() {
                if (mAspectSelectShow) {
                    hideAspectSelectAnim();
                    mAspectSelectShow = !mAspectSelectShow;
                }
                if (!mRecording || mTXCameraRecord.getPartsManager().getPartsPathList().size() == 0) {
                    TXCLog.i(TAG, "startRecord");
                    mTXCameraRecord.setRecordSpeed(mRecordSpeed);
                    startRecord();
                } else if (mPause) {
                    TXCLog.i(TAG, "resumeRecord");
                    resumeRecord();
                }
            }

            @Override
            public void onButtonPause() {
                if (mRecording && !mPause) {
                    TXCLog.i(TAG, "pauseRecord");
                    pauseRecord();
                }
            }
        });
        mRadioGroup = (RadioGroup) findViewById(R.id.rg_record_speed);
        mRbFast = (RadioButton) findViewById(R.id.rb_fast);
        mRbFastest = (RadioButton) findViewById(R.id.rb_fastest);
        mRbNormal = (RadioButton) findViewById(R.id.rb_normal);
        mRbSlow = (RadioButton) findViewById(R.id.rb_slow);
        mRbSloweset = (RadioButton) findViewById(R.id.rb_slowest);
        ((RadioButton) findViewById(R.id.rb_normal)).setChecked(true);
        mRbNormal.setBackground(getDrawable(R.drawable.record_mid_bg));
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
                if (checkedId == R.id.rb_fast) {
                    mRbFast.setBackground(getDrawable(R.drawable.record_mid_bg));
                    mRbFastest.setBackground(null);
                    mRbNormal.setBackground(null);
                    mRbSlow.setBackground(null);
                    mRbSloweset.setBackground(null);
                    mRecordSpeed = TXRecordCommon.RECORD_SPEED_FAST;

                } else if (checkedId == R.id.rb_fastest) {
                    mRbFastest.setBackground(getDrawable(R.drawable.record_right_bg));
                    mRbFast.setBackground(null);
                    mRbNormal.setBackground(null);
                    mRbSlow.setBackground(null);
                    mRbSloweset.setBackground(null);
                    mRecordSpeed = TXRecordCommon.RECORD_SPEED_FASTEST;

                } else if (checkedId == R.id.rb_normal) {
                    mRbNormal.setBackground(getDrawable(R.drawable.record_mid_bg));
                    mRbFastest.setBackground(null);
                    mRbFast.setBackground(null);
                    mRbSlow.setBackground(null);
                    mRbSloweset.setBackground(null);
                    mRecordSpeed = TXRecordCommon.RECORD_SPEED_NORMAL;

                } else if (checkedId == R.id.rb_slow) {
                    mRbSlow.setBackground(getDrawable(R.drawable.record_mid_bg));
                    mRbFastest.setBackground(null);
                    mRbFast.setBackground(null);
                    mRbNormal.setBackground(null);
                    mRbSloweset.setBackground(null);
                    mRecordSpeed = TXRecordCommon.RECORD_SPEED_SLOW;

                } else if (checkedId == R.id.rb_slowest) {
                    mRbSloweset.setBackground(getDrawable(R.drawable.record_left_bg));
                    mRbFastest.setBackground(null);
                    mRbFast.setBackground(null);
                    mRbNormal.setBackground(null);
                    mRbSlow.setBackground(null);
                    mRecordSpeed = TXRecordCommon.RECORD_SPEED_SLOWEST;

                }
            }
        });

        hideBgmPannel();
    }

    private void showBgmPannel() {
        backLL.setVisibility(View.GONE);
        mLayoutMusic.setVisibility(View.GONE);
        mTCBGMPannel.setVisibility(View.VISIBLE);
        mRlAspect.setVisibility(View.GONE);
        mProgressTime.setVisibility(View.GONE);
        mRlBeautyAndBGMContainer.setVisibility(View.GONE);
        mRecordRelativeLayout.setVisibility(View.INVISIBLE);

    }

    private void hideBgmPannel() {
        backLL.setVisibility(View.VISIBLE);
        mLayoutMusic.setVisibility(View.VISIBLE);
        mTCBGMPannel.setVisibility(View.GONE);
        mRlAspect.setVisibility(View.VISIBLE);
        mProgressTime.setVisibility(View.VISIBLE);
        mRlBeautyAndBGMContainer.setVisibility(View.VISIBLE);
        mRecordRelativeLayout.setVisibility(View.VISIBLE);
    }

    private void previewBGM(long startTime, long endTime) {
        if (!TextUtils.isEmpty(mBGMPath)) {
            // 保证在试听的时候音乐是正常播放的
            mTXCameraRecord.setRecordSpeed(TXRecordCommon.RECORD_SPEED_NORMAL);
            mTXCameraRecord.playBGMFromTime((int) startTime, (int) endTime);
        }
    }

    private void stopPreviewBGM() {
        // 选择完音乐返回时试听结束
        if (!TextUtils.isEmpty(mBGMPath)) {
            mTXCameraRecord.stopBGM();
            // 在试听结束时，再设置回原来的速度
            mTXCameraRecord.setRecordSpeed(mRecordSpeed);
        }
    }

    private void chooseBGM() {
        Intent bgmIntent = new Intent(this, BGMSelectActivity.class);
        bgmIntent.putExtra(TCConstants.BGM_POSITION, mBgmPosition);
        startActivityForResult(bgmIntent, TCConstants.ACTIVITY_BGM_REQUEST_CODE);
    }

    private void prepareToDeleteBGM() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog alertDialog = builder.setTitle(getString(R.string.tips)).setCancelable(false).setMessage(R.string.delete_bgm_or_not)
                .setPositiveButton(R.string.confirm_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mBGMPath = null;
                        mBgmPosition = -1;
                        mTXCameraRecord.stopBGM();
                        mTXCameraRecord.setBGM(null);

                        hideBgmPannel();
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();
        alertDialog.show();
    }

    public interface OnItemClickListener {
        void onBGMSelect(String path);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setSelectAspect();

        if (hasPermission()) {
            startCameraPreview();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mComposeRecordBtn.pauseRecord();
        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopCameraPreview();
            mStartPreview = false;
        }
        if (mRecording && !mPause) {
            pauseRecord();
        }
        if (mTXCameraRecord != null) {
            mTXCameraRecord.pauseBGM();
        }

        // 设置闪光灯的状态为关闭
        if (mIsTorchOpen) {
            mIsTorchOpen = false;
            if (mFront) {
                mIvTorch.setVisibility(View.GONE);
                mIvTorch.setImageResource(R.drawable.ugc_torch_disable);
            } else {
                mIvTorch.setImageResource(R.drawable.selector_torch_close);
                mIvTorch.setVisibility(View.VISIBLE);
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mRecordProgressView != null) {
            mRecordProgressView.release();
        }

        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopBGM();
            mTXCameraRecord.stopCameraPreview();
            mTXCameraRecord.setVideoRecordListener(null);
            mTXCameraRecord.release();
            mTXCameraRecord = null;
            mStartPreview = false;
        }
        abandonAudioFocus();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mTXCameraRecord.stopCameraPreview();
        if (mRecording && !mPause) {
            pauseRecord();
        }

        if (mTXCameraRecord != null) {
            mTXCameraRecord.pauseBGM();
        }

        mStartPreview = false;
        startCameraPreview();
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.back_ll) {
            back();

        } else if (i == R.id.btn_beauty) {
            mBeautyPannelView.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? GONE : View.VISIBLE);
            mIvBeauty.setImageResource(mBeautyPannelView.getVisibility() == View.VISIBLE ? R.drawable.ugc_record_beautiful_girl_hover : R.drawable.ugc_record_beautiful_girl);
            mRecordRelativeLayout.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? GONE : View.VISIBLE);
            mProgressTime.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? GONE : View.VISIBLE);

        } else if (i == R.id.btn_switch_camera) {
            mFront = !mFront;
            mIsTorchOpen = false;
            if (mFront) {
                mIvTorch.setVisibility(View.GONE);
                mIvTorch.setImageResource(R.drawable.ugc_torch_disable);
            } else {
                mIvTorch.setImageResource(R.drawable.selector_torch_close);
                mIvTorch.setVisibility(View.VISIBLE);
            }
            if (mTXCameraRecord != null) {
                TXCLog.i(TAG, "switchCamera = " + mFront);
                mTXCameraRecord.switchCamera(mFront);
            }

        } else if (i == R.id.btn_music_pannel) {
            if (TextUtils.isEmpty(mBGMPath)) {
                chooseBGM();
            } else {
                showBgmPannel();
                mTXCameraRecord.setBGM(mBGMPath);
                previewBGM(mBGMStartTime, mBgmEndTime);
            }

            if (mBeautyPannelView.getVisibility() == View.VISIBLE) {
                mBeautyPannelView.setVisibility(GONE);
                mIvBeauty.setImageResource(R.drawable.ugc_record_beautiful_girl);
            }

        } else if (i == R.id.tv_next_step) {
            nextStep();

        } else if (i == R.id.iv_scale) {
            scaleDisplay();

        } else if (i == R.id.iv_scale_first) {
            selectAnotherAspect(mFirstSelectScale);

        } else if (i == R.id.iv_scale_second) {
            selectAnotherAspect(mSecondSelectScale);

        } else if (i == R.id.btn_delete_last_part) {
            deleteLastPart();

        } else if (i == R.id.btn_torch) {
            toggleTorch();

        } else {
            if (mTCBGMPannel != null && mTCBGMPannel.getVisibility() == View.VISIBLE) {
                mTCBGMPannel.onClick(view);
            }

        }
    }

    private void toggleTorch() {
        if (mIsTorchOpen) {
            mTXCameraRecord.toggleTorch(false);
            mIvTorch.setImageResource(R.drawable.selector_torch_close);
        } else {
            mTXCameraRecord.toggleTorch(true);
            mIvTorch.setImageResource(R.drawable.selector_torch_open);
        }
        mIsTorchOpen = !mIsTorchOpen;
    }

    private void deleteLastPart() {
        if (mRecording && !mPause) {
            return;
        }
        if (!isSelected) {
            isSelected = true;
            mRecordProgressView.selectLast();
        } else {
            isSelected = false;
            mRecordProgressView.deleteLast();
            mTXCameraRecord.getPartsManager().deleteLastPart();
            float timeSecondFloat = mTXCameraRecord.getPartsManager().getDuration() / 1000;
            mProgressTime.setText(String.format(Locale.CHINA, "%.1f", timeSecondFloat) + "秒");
            if (timeSecondFloat < mMinDuration / 1000) {
                mTvNextStep.setVisibility(View.GONE);
            } else {
                mTvNextStep.setVisibility(View.VISIBLE);
            }

            if (mTXCameraRecord.getPartsManager().getPartsPathList().size() == 0) {
                // 重新开始录
                mRecording = false;
                mPause = false;
                mIvMusicMask.setVisibility(View.GONE);
                mIvScaleMask.setVisibility(GONE);
            }
        }
    }

    private void setSelectAspect() {
        if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_9_16) {
            mIvScale.setImageResource(R.drawable.selector_aspect169);
            mFirstSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_1_1;
            mIvAspectSelectFirst.setImageResource(R.drawable.selector_aspect11);

            mSecondSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_3_4;
            mIvAspectSelectSecond.setImageResource(R.drawable.selector_aspect43);
        } else if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_1_1) {
            mIvScale.setImageResource(R.drawable.selector_aspect11);
            mFirstSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_3_4;
            mIvAspectSelectFirst.setImageResource(R.drawable.selector_aspect43);

            mSecondSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_9_16;
            mIvAspectSelectSecond.setImageResource(R.drawable.selector_aspect169);
        } else {
            mIvScale.setImageResource(R.drawable.selector_aspect43);
            mFirstSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_1_1;
            mIvAspectSelectFirst.setImageResource(R.drawable.selector_aspect11);

            mSecondSelectScale = TXRecordCommon.VIDEO_ASPECT_RATIO_9_16;
            mIvAspectSelectSecond.setImageResource(R.drawable.selector_aspect169);
        }
    }

    private void scaleDisplay() {
        if (!mAspectSelectShow) {
            showAspectSelectAnim();
        } else {
            hideAspectSelectAnim();
        }

        mAspectSelectShow = !mAspectSelectShow;
    }

    private void selectAnotherAspect(int targetScale) {
        if (mTXCameraRecord != null) {
            scaleDisplay();

            mCurrentAspectRatio = targetScale;

            if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_9_16) {
                mTXCameraRecord.setAspectRatio(TXRecordCommon.VIDEO_ASPECT_RATIO_9_16);

            } else if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_3_4) {
                mTXCameraRecord.setAspectRatio(TXRecordCommon.VIDEO_ASPECT_RATIO_3_4);

            } else if (mCurrentAspectRatio == TXRecordCommon.VIDEO_ASPECT_RATIO_1_1) {
                mTXCameraRecord.setAspectRatio(TXRecordCommon.VIDEO_ASPECT_RATIO_1_1);
            }

            setSelectAspect();
        }
    }

    private void hideAspectSelectAnim() {
        ObjectAnimator showAnimator = ObjectAnimator.ofFloat(mRlAspectSelect, "translationX", 0f,
                2 * (getResources().getDimension(R.dimen.ugc_aspect_divider) + getResources().getDimension(R.dimen.ugc_aspect_width)));
        showAnimator.setDuration(80);
        showAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mRlAspectSelect.setVisibility(GONE);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        showAnimator.start();
    }

    private void showAspectSelectAnim() {
        ObjectAnimator showAnimator = ObjectAnimator.ofFloat(mRlAspectSelect, "translationX",
                2 * (getResources().getDimension(R.dimen.ugc_aspect_divider) + getResources().getDimension(R.dimen.ugc_aspect_width)), 0f);
        showAnimator.setDuration(80);
        showAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mRlAspectSelect.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {

            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        showAnimator.start();
    }

    private void resumeRecord() {
        mTXCameraRecord.setRecordSpeed(mRecordSpeed);
        mIvDeleteLastPart.setImageResource(R.drawable.ugc_delete_last_part_disable);
        mIvDeleteLastPart.setEnabled(false);
        mIvScaleMask.setVisibility(View.VISIBLE);

        mPause = false;
        isSelected = false;
        if (mTXCameraRecord != null) {
            mTXCameraRecord.resumeRecord();
            if (!TextUtils.isEmpty(mBGMPath)) {
                if (mBGMPlayingPath == null || !mBGMPath.equals(mBGMPlayingPath)) {
                    mTXCameraRecord.playBGMFromTime(0, mBGMDuration);
                    mBGMPlayingPath = mBGMPath;
                } else {
                    mTXCameraRecord.resumeBGM();
                }
            }
        }
        requestAudioFocus();

        mRadioGroup.setVisibility(View.GONE);
        mLayoutAspect.setVisibility(View.GONE);
        mLayoutMusic.setVisibility(View.GONE);
        mLayoutBeauty.setVisibility(View.GONE);
        mLayoutLeftPanel.setVisibility(View.GONE);
        mLayoutRightPanel.setVisibility(View.GONE);
    }

    private void pauseRecord() {
        mPause = true;
        mIvDeleteLastPart.setImageResource(R.drawable.selector_delete_last_part);
        mIvDeleteLastPart.setEnabled(true);

        if (mTXCameraRecord != null) {
            if (!TextUtils.isEmpty(mBGMPlayingPath)) {
                mTXCameraRecord.pauseBGM();
            }
            int stopResult = mTXCameraRecord.pauseRecord();
            TXLog.i(TAG, "pauseRecord, result = " + stopResult);
        }
        abandonAudioFocus();

        mRadioGroup.setVisibility(View.VISIBLE);
        mLayoutAspect.setVisibility(View.VISIBLE);
        mLayoutMusic.setVisibility(View.VISIBLE);
        mLayoutBeauty.setVisibility(View.VISIBLE);
        mLayoutLeftPanel.setVisibility(View.VISIBLE);
        mLayoutRightPanel.setVisibility(View.VISIBLE);

        if (mTXCameraRecord.getPartsManager().getPartsPathList().size() == 0) {
            mIvMusicMask.setVisibility(View.GONE);
            mIvScaleMask.setVisibility(View.GONE);
        }
    }

    private void nextStep() {
        if (!mRecording) {
            return;
        }
        mCompleteProgressDialog.show();
        stopRecord();
    }

    private void stopRecord() {
        if (!mRecording) {
            return;
        }
        if (mTXCameraRecord != null) {
            mTXCameraRecord.stopBGM();
            mTXCameraRecord.stopRecord();
        }

        mPause = false;
        abandonAudioFocus();

        mRadioGroup.setVisibility(View.VISIBLE);
        mLayoutAspect.setVisibility(View.VISIBLE);
        mLayoutMusic.setVisibility(View.VISIBLE);
        mLayoutBeauty.setVisibility(View.VISIBLE);
        mLayoutLeftPanel.setVisibility(View.VISIBLE);
        mLayoutRightPanel.setVisibility(View.VISIBLE);
    }

    private void startRecord() {
        mIvMusicMask.setVisibility(View.VISIBLE);
        mIvScaleMask.setVisibility(View.VISIBLE);
        mIvDeleteLastPart.setImageResource(R.drawable.ugc_delete_last_part_disable);
        mIvDeleteLastPart.setEnabled(false);
        if (mTXCameraRecord == null) {
            mTXCameraRecord = TXUGCRecord.getInstance(this.getApplicationContext());
        }

        String customVideoPath = getCustomVideoOutputPath();
        String customCoverPath = customVideoPath.replace(".mp4", ".jpg");

        int result = mTXCameraRecord.startRecord(customVideoPath, customCoverPath);

        String desc = null;
        switch (result) {
            case TXRecordCommon.START_RECORD_OK:
                desc = "开始录制成功";
                break;
            case TXRecordCommon.START_RECORD_ERR_IS_IN_RECORDING:
                desc = "开始录制时存在未完成的任务";
                break;
            case TXRecordCommon.START_RECORD_ERR_VIDEO_PATH_IS_EMPTY:
                desc = "开始录制时视频文件路径为空";
                break;
            case TXRecordCommon.START_RECORD_ERR_API_IS_LOWER_THAN_18:
                desc = "版本小于18";
                break;
            case TXRecordCommon.START_RECORD_ERR_NOT_INIT:
                desc = "开始录制时还未初始化结束";
                break;
            case TXRecordCommon.START_RECORD_ERR_LICENCE_VERIFICATION_FAILED:
                desc = "licence校验失败";
                break;
        }
        TCUserMgr.getInstance().uploadLogs("startrecord", TCUserMgr.getInstance().getUserId(), result, desc, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });
        if (result != 0) {
            Toast.makeText(TCVideoRecordActivity.this.getApplicationContext(), "录制失败，错误码：" + result, Toast.LENGTH_SHORT).show();
            mTXCameraRecord.setVideoRecordListener(null);
            mTXCameraRecord.stopRecord();
            return;
        }
        if (!TextUtils.isEmpty(mBGMPath)) {
            mBGMDuration = mTXCameraRecord.setBGM(mBGMPath);
            mTXCameraRecord.playBGMFromTime((int) mBGMStartTime, (int) mBgmEndTime);
            mBGMPlayingPath = mBGMPath;
            TXCLog.i(TAG, "music duration = " + mTXCameraRecord.getMusicDuration(mBGMPath));
        }

        mRecording = true;
        mPause = false;

        requestAudioFocus();

        mRadioGroup.setVisibility(View.GONE);
        mLayoutAspect.setVisibility(View.GONE);
        mLayoutMusic.setVisibility(View.GONE);
        mLayoutBeauty.setVisibility(View.GONE);
        mLayoutLeftPanel.setVisibility(View.GONE);
        mLayoutRightPanel.setVisibility(View.GONE);
    }

    private String getCustomVideoOutputPath() {
        long currentTime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
        String time = sdf.format(new Date(currentTime));
        String outputDir = Environment.getExternalStorageDirectory() + File.separator + OUTPUT_DIR_NAME;
        File outputFolder = new File(outputDir);
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }
        String tempOutputPath = outputDir + File.separator + "TXUGC_" + time + ".mp4";
        return tempOutputPath;
    }

    private void startEditerPreview() {
        Intent intent = new Intent(this, TCVideoPreprocessActivity.class);
        intent.putExtra(TCConstants.VIDEO_EDITER_PATH, mTXRecordResult.videoPath);
        startActivity(intent);
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray(new String[0]),
                        100);
                return false;
            }
        }

        return true;
    }

    @Override
    public void onBeautyParamsChange(BeautySettingPannel.BeautyParams params, int key) {
        switch (key) {
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY:
                mBeautyParams.mBeautyLevel = params.mBeautyLevel;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_WHITE:
                mBeautyParams.mWhiteLevel = params.mWhiteLevel;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACE_LIFT:
                mBeautyParams.mFaceSlimLevel = params.mFaceSlimLevel;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setFaceScaleLevel(params.mFaceSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BIG_EYE:
                mBeautyParams.mBigEyeLevel = params.mBigEyeLevel;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setEyeScaleLevel(params.mBigEyeLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER:
                mBeautyParams.mFilterBmp = params.mFilterBmp;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setFilter(params.mFilterBmp);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_MOTION_TMPL:
                mBeautyParams.mMotionTmplPath = params.mMotionTmplPath;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setMotionTmpl(params.mMotionTmplPath);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_GREEN:
                mBeautyParams.mGreenFile = params.mGreenFile;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setGreenScreenFile(params.mGreenFile, true);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_RUDDY:
                mBeautyParams.mRuddyLevel = params.mRuddyLevel;
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setBeautyDepth(mBeautyParams.mBeautyStyle, mBeautyParams.mBeautyLevel, mBeautyParams.mWhiteLevel, mBeautyParams.mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setBeautyStyle(params.mBeautyStyle);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACEV:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setFaceVLevel(params.mFaceVLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACESHORT:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setFaceShortLevel(params.mFaceShortLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_CHINSLIME:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setChinLevel(params.mChinSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_NOSESCALE:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setNoseSlimLevel(params.mNoseScaleLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER_MIX_LEVEL:
                if (mTXCameraRecord != null) {
                    mTXCameraRecord.setSpecialRatio(params.mFilterMixLevel / 10.f);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onRecordEvent(int event, Bundle param) {
        TXCLog.d(TAG, "onRecordEvent event id = " + event);
        if (event == TXRecordCommon.EVT_ID_PAUSE) {
            mRecordProgressView.clipComplete();
        } else if (event == TXRecordCommon.EVT_CAMERA_CANNOT_USE) {
            Toast.makeText(this, "摄像头打开失败，请检查权限", Toast.LENGTH_SHORT).show();
        } else if (event == TXRecordCommon.EVT_MIC_CANNOT_USE) {
            Toast.makeText(this, "麦克风打开失败，请检查权限", Toast.LENGTH_SHORT).show();
        } else if (event == TXRecordCommon.EVT_ID_RESUME) {

        }
    }

    @Override
    public void onRecordProgress(long milliSecond) {
        if (mRecordProgressView == null) {
            return;
        }
        mRecordProgressView.setProgress((int) milliSecond);
        float timeSecondFloat = milliSecond / 1000f;
        mProgressTime.setText(String.format(Locale.CHINA, "%.1f", timeSecondFloat) + "秒");
        if (timeSecondFloat < mMinDuration / 1000) {
            mTvNextStep.setVisibility(View.GONE);
        } else {
            mTvNextStep.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /** attention to this below ,must add this**/
        UMShareAPI.get(this).onActivityResult(requestCode, resultCode, data);
        if (requestCode != TCConstants.ACTIVITY_BGM_REQUEST_CODE) {
            return;
        }
        if (data == null) {
            return;
        }
        mBGMPath = data.getStringExtra(TCConstants.BGM_PATH);
        mBgmPosition = data.getIntExtra(TCConstants.BGM_POSITION, -1);

        mBGMDuration = mTXCameraRecord.setBGM(mBGMPath);
        mBGMStartTime = 0;
        mBgmEndTime = mBGMDuration;
        mTCBGMPannel.setBgmDuration(mBGMDuration);
        mTCBGMPannel.resetRangePos();
        showBgmPannel();
        previewBGM(mBGMStartTime, mBgmEndTime);
    }

    @Override
    public void onRecordComplete(TXRecordCommon.TXRecordResult result) {
        mCompleteProgressDialog.dismiss();

        mTXRecordResult = result;
        String desc = null;
        if (mTXRecordResult.retCode < 0) {
            desc = "onRecordComplete录制失败:" + mTXRecordResult.descMsg;
        } else {
            desc = "视频录制成功";
        }
        TCUserMgr.getInstance().uploadLogs("videorecord", TCUserMgr.getInstance().getUserId(), mTXRecordResult.retCode, desc, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });
        TXCLog.i(TAG, "onRecordComplete, result retCode = " + result.retCode + ", descMsg = " + result.descMsg + ", videoPath + " + result.videoPath + ", coverPath = " + result.coverPath);
        if (mTXRecordResult.retCode < 0) {
            mRecording = false;
            Toast.makeText(TCVideoRecordActivity.this.getApplicationContext(), "录制失败，原因：" + mTXRecordResult.descMsg, Toast.LENGTH_SHORT).show();
        } else {
            mRecording = false;
            mPause = false;
            mComposeRecordBtn.pauseRecord();
            startEditerPreview();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 100:
                for (int ret : grantResults) {
                    if (ret != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(getApplicationContext(), "获取权限失败" , Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                startCameraPreview();
                break;
            default:
                break;
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 100);
                return false;
            }
        }
        return true;
    }

    private void requestAudioFocus() {
        if (null == mAudioManager) {
            mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        }

        if (null == mOnAudioFocusListener) {
            mOnAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int focusChange) {
                    try {
                        TXCLog.i(TAG, "requestAudioFocus, onAudioFocusChange focusChange = " + focusChange);

                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            pauseRecord();
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pauseRecord();
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {

                        } else {
                            pauseRecord();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
        }
        try {
            mAudioManager.requestAudioFocus(mOnAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void abandonAudioFocus() {
        try {
            if (null != mAudioManager && null != mOnAudioFocusListener) {
                mAudioManager.abandonAudioFocus(mOnAudioFocusListener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (view == mMaskLayout) {
            if (motionEvent.getPointerCount() >= 2) {
                mScaleGestureDetector.onTouchEvent(motionEvent);
            } else if (motionEvent.getPointerCount() == 1) {
                mGestureDetector.onTouchEvent(motionEvent);
            }
        }
        return true;
    }

    // OnGestureListener回调start
    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        if (mBeautyPannelView.isShown()) {
            mBeautyPannelView.setVisibility(GONE);
            mIvBeauty.setImageResource(R.drawable.ugc_record_beautiful_girl);
            mRecordRelativeLayout.setVisibility(View.VISIBLE);
            mProgressTime.setVisibility(View.VISIBLE);
        }
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }
    // OnGestureListener回调end

    // OnScaleGestureListener回调start
    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        int maxZoom = mTXCameraRecord.getMaxZoom();
        if (maxZoom == 0) {
            TXCLog.i(TAG, "camera not support zoom");
            return false;
        }

        float factorOffset = scaleGestureDetector.getScaleFactor() - mLastScaleFactor;

        mScaleFactor += factorOffset;
        mLastScaleFactor = scaleGestureDetector.getScaleFactor();
        if (mScaleFactor < 0) {
            mScaleFactor = 0;
        }
        if (mScaleFactor > 1) {
            mScaleFactor = 1;
        }

        int zoomValue = Math.round(mScaleFactor * maxZoom);
        mTXCameraRecord.setZoom(zoomValue);
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
        mLastScaleFactor = scaleGestureDetector.getScaleFactor();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

    }
    // OnScaleGestureListener回调end

    private void back() {
        if (!mRecording) {
            if (mTXCameraRecord != null) {
                mTXCameraRecord.getPartsManager().deleteAllParts();
            }
            finish();
            return;
        }

        if (!mPause) {
            pauseRecord();
        }

        if (mTXCameraRecord.getPartsManager().getPartsPathList().size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            AlertDialog alertDialog = builder.setTitle(getString(R.string.cancel_record)).setCancelable(false).setMessage(R.string.confirm_cancel_record_content)
                    .setPositiveButton(R.string.give_up, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            if (mTXCameraRecord != null) {
                                mTXCameraRecord.getPartsManager().deleteAllParts();
                            }
                            finish();
                        }
                    })
                    .setNegativeButton(getString(R.string.wrong_click), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create();
            alertDialog.show();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        back();
    }
}