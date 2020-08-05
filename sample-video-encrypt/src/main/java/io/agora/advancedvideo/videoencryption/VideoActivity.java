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
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.ss.Constant;
import io.agora.rtc.ss.ScreenSharingClient;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

import static io.agora.advancedvideo.videoencryption.Constant.CAMERA_UID;
import static io.agora.advancedvideo.videoencryption.Constant.SCREEN_SHARE_UID;

public class VideoActivity extends BaseLiveActivity {
    private static final String TAG = VideoActivity.class.getSimpleName();
/////////////////////////////////////////////////////////////////////////////////
private RtcEngine mRtcEngine;
    private FrameLayout mFlCam;
    private FrameLayout mFlSS;
    private boolean mSS = false;
    private VideoEncoderConfiguration mVEC;
    private ScreenSharingClient mSSClient;

    private final ScreenSharingClient.IStateListener mListener = new ScreenSharingClient.IStateListener() {
        @Override
        public void onError(int error) {
            Log.e(TAG, "Screen share service error happened: " + error);
        }

        @Override
        public void onTokenWillExpire() {
            Log.d(TAG, "Screen share service token will expire");
            mSSClient.renewToken(null); // Replace the token with your valid token
        }
    };

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.d(TAG, "onUserOffline: " + uid + " reason: " + reason);
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.d(TAG, "onJoinChannelSuccess: " + channel + " " + elapsed);
        }

        @Override
        public void onUserJoined(final int uid, int elapsed) {
            Log.d(TAG, "onUserJoined: " + (uid&0xFFFFFFL));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(uid == SCREEN_SHARE_UID) {
                        setupRemoteView(uid);
                    }
                }
            });
        }
    };

    /////////////////////////////////////////////////////////////////////////////////
    private PacketProcessor mProcessor = new PacketProcessor();
    private FrameLayout mLocalPreview;
    private FrameLayout mRemotePreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProcessor.registerProcessing();

        mSSClient = ScreenSharingClient.getInstance();
        mSSClient.setListener(mListener);

        initAgoraEngineAndJoinChannel();
    }

    @Override
    protected void onInitializeVideo() {
        initView();
        setVideoConfig();
        setupLocalVideo();
        rtcEngine().joinChannel(token(), config().getChannelName(), null, 0);
    }

    private void initAgoraEngineAndJoinChannel() {
        initializeAgoraEngine();
        setupVideoProfile();
        setupLocalVideo();
        joinChannel();
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

    private void initializeAgoraEngine() {
        try {
            mRtcEngine = RtcEngine.create(getApplicationContext(), getString(R.string.agora_app_id), mRtcEventHandler);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));

            throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
        }
    }

    private void setupRemoteView(int uid) {
        SurfaceView ssV = RtcEngine.CreateRendererView(getApplicationContext());
        ssV.setZOrderOnTop(true);
        ssV.setZOrderMediaOverlay(true);

        if(mSS) {

            mLocalPreview.addView(ssV, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, VideoCanvas.RENDER_MODE_FIT));
        }else{

            mLocalPreview.addView(ssV, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

     //   mLocalPreview.addView(ssV, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRtcEngine.setupRemoteVideo(new VideoCanvas(ssV, VideoCanvas.RENDER_MODE_FIT, uid));
    }

    private void joinChannel() {
        mRtcEngine.joinChannel(null, config().getChannelName(),"Extra Optional Data", CAMERA_UID); // if you do not specify the uid, we will generate the uid for you
    }

    private void leaveChannel() {
        mRtcEngine.leaveChannel();
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
        surface.setZOrderOnTop(true);
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

    protected void onMuteVideoButtonClicked(View view) {

//        rtcEngine().muteLocalVideoStream(view.isActivated());
//        view.setActivated(!view.isActivated());

        if(view.isActivated()){
            rtcEngine().muteLocalVideoStream(true);
            view.setActivated(false);
      //      mSS = true;
        }else{
            mSS = false;
            rtcEngine().muteLocalVideoStream(false);
            view.setActivated(true);
        }

    }


    public void onScreenSharingClicked(View view) {
        Button button = (Button) view;
        boolean selected = button.isSelected();
        button.setSelected(!selected);

        if (button.isSelected()) {
            mSSClient.start(getApplicationContext(), getResources().getString(R.string.agora_app_id), null,
                    config().getChannelName(), SCREEN_SHARE_UID, mVEC);
            button.setText("stop ss");

            mSS = true;
        } else {
            mSSClient.stop(getApplicationContext());
            button.setText("start ss");
            mSS = false;
        }
    }

//    @Override
//    protected void onMuteVideoButtonClicked(View view) {
//        rtcEngine().muteLocalVideoStream(view.isActivated());
//        view.setActivated(!view.isActivated());
//
//        if (view.isActivated()) { //changed
//            mSSClient.start(getApplicationContext(), getResources().getString(R.string.agora_app_id), null,
//                    config().getChannelName(), SCREEN_SHARE_UID, mVEC);
//           // button.setText(getResources().getString(R.string.label_stop_sharing_your_screen));
//            rtcEngine().muteLocalVideoStream(view.isActivated());
//            mSS = true;
//        } else {
//            mSSClient.stop(getApplicationContext());
//         //   button.setText(getResources().getString(R.string.label_start_sharing_your_screen));
//            view.setActivated(!view.isActivated());
//            mSS = false;
//        }
//    }

    @Override
    protected void onMoreButtonClicked(View view) {

    }
}
