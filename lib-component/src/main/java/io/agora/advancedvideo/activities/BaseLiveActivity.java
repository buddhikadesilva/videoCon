package io.agora.advancedvideo.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import io.agora.advancedvideo.R;
import io.agora.advancedvideo.annotations.DisplayActivity;
import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.ss.ScreenSharingClient;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;

@DisplayActivity(
    SubClasses = {
        "io.agora.advancedvideo.switchvideoinput.SwitchVideoInputActivity",
        "io.agora.advancedvideo.videoencryption.VideoActivity",
        "io.agora.advancedvideo.customrenderer.CustomRemoteRenderActivity" ,
        "io.agora.advancedvideo.videopush.VideoPushActivity",
        "io.agora.advancedvideo.rawdatasample.RawDataActivity"
    }
)


public abstract class BaseLiveActivity extends BaseActivity implements PopupMenu.OnMenuItemClickListener {

    private static final String LOG_TAG = BaseLiveActivity.class.getSimpleName();

    protected RelativeLayout videoContainer;

    protected ImageView mMuteAudioBtn;
    protected ImageView mMuteVideoBtn;

    protected boolean mIsBroadcaster;

    private ScreenSharingClient mSSClient;
    private VideoEncoderConfiguration mVEC;
    private boolean mSS = false;


    private RtcEngine mRtcEngine;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUI();
        initAgoraEngineAndJoinChannel();
    }

    public void showPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.setOnMenuItemClickListener(this);
        //MenuInflater inflater = popup.getMenuInflater();
        popup.inflate(R.menu.actionslist);
        popup.show();
    }

    private void initAgoraEngineAndJoinChannel() {
        initializeAgoraEngine();
        setupVideoProfile();
      //  setupLocalVideo();
        joinChannel();
    }

    private void initializeAgoraEngine() {
        try {
            mRtcEngine = RtcEngine.create(getApplicationContext(), getString(R.string.agora_app_id), mRtcEventHandler);
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));

            throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
        }
    }

    public void onScreenSharingClicked(View view) {
        Button button = (Button) view;
        boolean selected = button.isSelected();
        button.setSelected(!selected);

        if (button.isSelected()) {
            mSSClient.start(getApplicationContext(), getResources().getString(R.string.agora_app_id), null,
                    getResources().getString(R.string.live_room_name), Constant.SCREEN_SHARE_UID, mVEC);
          //  button.setText(getResources().getString(R.string.label_stop_sharing_your_screen));
            mSS = true;
        } else {
            mSSClient.stop(getApplicationContext());
          //  button.setText(getResources().getString(R.string.label_start_sharing_your_screen));
            mSS = false;
        }
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//       //  Handle item selection
//        int itemId = item.getItemId();
//        if (itemId == R.id.toggle_cam) {
//           // onSwitchCameraClicked(View view);
//            System.out.println("toggle");
//            return true;
//        } else if (itemId == R.id.screen_share) {
//            System.out.println("SS");
//            return true;
//        } return super.onOptionsItemSelected(item);
//    }


    private void initUI() {
        setContentView(R.layout.activity_live_room);

//        TextView roomName = findViewById(R.id.live_room_name);
//        roomName.setText(config().getChannelName());
//        roomName.setSelected(true);

        initUserIcon();

        rtcEngine().setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
        int role = getIntent().getIntExtra(
                io.agora.advancedvideo.Constants.KEY_CLIENT_ROLE,
                Constants.CLIENT_ROLE_AUDIENCE);
        rtcEngine().setClientRole(role);
        mIsBroadcaster =  (role == Constants.CLIENT_ROLE_BROADCASTER);

        mMuteVideoBtn = findViewById(R.id.live_btn_mute_video);
        mMuteVideoBtn.setActivated(mIsBroadcaster);

        mMuteAudioBtn = findViewById(R.id.live_btn_mute_audio);
        mMuteAudioBtn.setActivated(mIsBroadcaster);

        ImageView beautyBtn = findViewById(R.id.live_btn_beautification);
        beautyBtn.setActivated(true);
        rtcEngine().setBeautyEffectOptions(beautyBtn.isActivated(),
                io.agora.advancedvideo.Constants.DEFAULT_BEAUTY_OPTIONS);

        videoContainer = findViewById(R.id.live_video_container);

        mSSClient = ScreenSharingClient.getInstance();
        mSSClient.setListener(mListener);
        onInitializeVideo();
    }

    private void initUserIcon() {
        Bitmap origin = BitmapFactory.decodeResource(getResources(), R.drawable.fake_user_icon);
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), origin);
        drawable.setCircular(true);
        ImageView iconView = findViewById(R.id.live_name_board_icon);
        iconView.setImageDrawable(drawable);
    }

    @Override
    protected void onGlobalLayoutCompleted() {
        RelativeLayout topLayout = findViewById(R.id.live_room_top_layout);
        RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams) topLayout.getLayoutParams();
        params.height = mStatusBarHeight + topLayout.getMeasuredHeight();
        topLayout.setLayoutParams(params);
        topLayout.setPadding(0, mStatusBarHeight, 0, 0);
    }

    public void onLeaveClicked(View view) {
        onLeaveButtonClicked(view);
        finish();
    }

    public void onSwitchCameraClicked(View view) {
        onSwitchCameraButtonClicked(view);
    }

    public void onBeautyClicked(View view) {

    }

    public void onMoreClicked(View view) {
        onMoreButtonClicked(view);
    }

    public void onPushStreamClicked(View view) {

    }

    public void onMuteAudioClicked(View view) {
        onMuteAudioButtonClicked(view);
    }

    public void onMuteVideoClicked(View view) {
        onMuteVideoButtonClicked(view);
    }

    protected abstract void onInitializeVideo();

    protected abstract void onLeaveButtonClicked(View view);

    protected abstract void onSwitchCameraButtonClicked(View view);

    protected abstract void onMuteAudioButtonClicked(View view);

    protected abstract void onMuteVideoButtonClicked(View view);

    protected abstract void onMoreButtonClicked(View view);

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.toggle_cam) {
            rtcEngine().switchCamera();
            return true;
        } else if (itemId == R.id.screen_share) {

            return true;
        } return super.onOptionsItemSelected(item);

    }

    private void setupRemoteView(int uid) {
        SurfaceView ssV = RtcEngine.CreateRendererView(getApplicationContext());
        ssV.setZOrderOnTop(true);
        ssV.setZOrderMediaOverlay(true);
        videoContainer.addView(ssV, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRtcEngine.setupRemoteVideo(new VideoCanvas(ssV, VideoCanvas.RENDER_MODE_FIT, uid));
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
    private void joinChannel() {
        mRtcEngine.joinChannel(null, getResources().getString(R.string.live_room_name),"Extra Optional Data", Constant.CAMERA_UID); // if you do not specify the uid, we will generate the uid for you
    }

    private void leaveChannel() {
        mRtcEngine.leaveChannel();
    }

    private void setupVideoProfile() {
        mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING);
        mRtcEngine.enableVideo();
        mVEC = new VideoEncoderConfiguration(VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT);
        mRtcEngine.setVideoEncoderConfiguration(mVEC);
        mRtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
    }

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
}
