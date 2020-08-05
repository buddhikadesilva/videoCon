package io.agora.advancedvideo.videoencryption;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import io.agora.advancedvideo.Constants;
import io.agora.advancedvideo.activities.BaseLiveActivity;
import io.agora.advancedvideo.activities.Constant;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.ss.ScreenSharingClient;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

public class VideoActivity extends BaseLiveActivity {
    private static final String TAG = VideoActivity.class.getSimpleName();

    private PacketProcessor mProcessor = new PacketProcessor();
    private FrameLayout mLocalPreview;
    private FrameLayout mRemotePreview;

    private static final String LOG_TAG = VideoActivity.class.getSimpleName();
    private RtcEngine mRtcEngine;
    private FrameLayout mFlCam;
    private FrameLayout mFlSS;
    private boolean mSS = false;
    private VideoEncoderConfiguration mVEC;
    private ScreenSharingClient mSSClient;

    /////////////
    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.d(LOG_TAG, "onUserOffline: " + uid + " reason: " + reason);
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.d(LOG_TAG, "onJoinChannelSuccess: " + channel + " " + elapsed);
        }

        @Override
        public void onUserJoined(final int uid, int elapsed) {
            Log.d(LOG_TAG, "onUserJoined: " + (uid&0xFFFFFFL));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(uid == Constant.SCREEN_SHARE_UID) {
                        setupRemoteView(uid);
                    }
                }
            });
        }
    };

    private final ScreenSharingClient.IStateListener mListener = new ScreenSharingClient.IStateListener() {
        @Override
        public void onError(int error) {
            Log.e(LOG_TAG, "Screen share service error happened: " + error);
        }

        @Override
        public void onTokenWillExpire() {
            Log.d(LOG_TAG, "Screen share service token will expire");
            mSSClient.renewToken(null); // Replace the token with your valid token
        }
    };
////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProcessor.registerProcessing();

        mSSClient = ScreenSharingClient.getInstance();
        mSSClient.setListener(mListener);

        initAgoraEngineAndJoinChannel();
    }


    /////////////////

    private void initAgoraEngineAndJoinChannel() {
        initializeAgoraEngine();
        setupVideoProfile();
        setupLocalVideo();
        joinChannel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        leaveChannel();
        RtcEngine.destroy();
        mRtcEngine = null;
        if (mSS) {
            mSSClient.stop(getApplicationContext());
        }
    }


    private void setupVideoProfile() {
        mRtcEngine.setChannelProfile(io.agora.rtc.Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
        mRtcEngine.enableVideo();
        mVEC = new VideoEncoderConfiguration(VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT);
        mRtcEngine.setVideoEncoderConfiguration(mVEC);
        mRtcEngine.setClientRole(io.agora.rtc.Constants.CLIENT_ROLE_BROADCASTER);
    }



    public void onScreenSharingClicked2(View view) {
        Button button = (Button) view;
        boolean selected = button.isSelected();
        button.setSelected(!selected);

        if (button.isSelected()) {
            mSSClient.start(getApplicationContext(), getResources().getString(R.string.agora_app_id), null,
                    "A", Constant.SCREEN_SHARE_UID, mVEC);
         //   button.setText(getResources().getString(R.string.label_stop_sharing_your_screen));
            mSS = true;
        } else {
            mSSClient.stop(getApplicationContext());
          //  button.setText(getResources().getString(R.string.label_start_sharing_your_screen));
            mSS = false;
        }
    }

    private void initializeAgoraEngine() {
        try {
            mRtcEngine = RtcEngine.create(getApplicationContext(), getString(R.string.agora_app_id), mRtcEventHandler);
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));

            throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
        }
    }

    private void joinChannel() {
        mRtcEngine.joinChannel(null, "A","Extra Optional Data", Constant.CAMERA_UID); // if you do not specify the uid, we will generate the uid for you

    }

    private void leaveChannel() {
        mRtcEngine.leaveChannel();
    }

    private void setupRemoteView(int uid) {
        SurfaceView ssV = RtcEngine.CreateRendererView(getApplicationContext());
        ssV.setZOrderOnTop(true);
        ssV.setZOrderMediaOverlay(true);
        mLocalPreview.addView(ssV, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRtcEngine.setupRemoteVideo(new VideoCanvas(ssV, VideoCanvas.RENDER_MODE_FIT, uid));
    }


    /////////////

    @Override
    protected void onInitializeVideo() {
        initView();
        setVideoConfig();
        setupLocalVideo();
        rtcEngine().joinChannel(token(), config().getChannelName(), null, 0);
    }

    private void initView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View layout = inflater.inflate(R.layout.activity_video, videoContainer, false);
        videoContainer.addView(layout);

        mLocalPreview = videoContainer.findViewById(R.id.local_preview_layout);
        mRemotePreview = videoContainer.findViewById(R.id.remote_preview_layout);
    }

    private void setVideoConfig() {
        rtcEngine().setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                Constants.VIDEO_DIMENSIONS[config().getVideoDimenIndex()],
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        ));
    }



    private void setupLocalVideo() {
        if (!mIsBroadcaster) {
            return;
        }

        SurfaceView surface = RtcEngine.CreateRendererView(this);
        rtcEngine().setupLocalVideo(new VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, 0));
         mLocalPreview.addView(surface);
    }

    @Override
    public void onFirstRemoteVideoDecoded(final int uid, int width, int height, int elapsed) {
        Log.i(TAG, "onJoinChannelSuccess:" + (uid & 0xFFFFFFFFL));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setupRemoteVideo(uid);
            }
        });
    }

    private void setupRemoteVideo(int uid) {
        if (mRemotePreview.getChildCount() >= 1) {
            return;
        }

        SurfaceView surface = RtcEngine.CreateRendererView(this);
        surface.setZOrderOnTop(false);
        rtcEngine().setupRemoteVideo(new VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, uid));
        mRemotePreview.addView(surface);
    }

    @Override
    public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
        Log.i(TAG, "onJoinChannelSuccess:" + (uid & 0xFFFFFFFFL));
    }

    @Override
    public void onUserOffline(int uid, int reason) {
        Log.i(TAG, "onUserOffline:" + (uid & 0xFFFFFFFFL));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRemotePreview.removeAllViews();
            }
        });
    }

    @Override
    protected void onLeaveButtonClicked(View view) {

    }



    @Override
    public void finish() {
        mProcessor.unregisterProcessing();
        rtcEngine().leaveChannel();
        super.finish();
    }

    @Override
    protected void onSwitchCameraButtonClicked(View view) {
        rtcEngine().switchCamera();
    }

    @Override
    protected void onMuteAudioButtonClicked(View view) {
        rtcEngine().muteLocalAudioStream(view.isActivated());
        view.setActivated(!view.isActivated());
    }

    @Override
    protected void onMuteVideoButtonClicked(View view) {
        rtcEngine().muteLocalVideoStream(view.isActivated());
        view.setActivated(!view.isActivated());
    }

    @Override
    protected void onMoreButtonClicked(View view) {

    }
}
