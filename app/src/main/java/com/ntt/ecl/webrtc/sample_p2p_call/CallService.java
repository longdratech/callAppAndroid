package com.ntt.ecl.webrtc.sample_p2p_call;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.ntt.ecl.webrtc.sample_p2p_call.enums.CallState;

import java.util.Date;

import at.markushi.ui.CircleButton;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.DataConnection;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

import static com.ntt.ecl.webrtc.sample_p2p_call.Constants.API_KEY;
import static com.ntt.ecl.webrtc.sample_p2p_call.Constants.DOMAIN;

public class CallService extends Service {
    private MediaPlayer mediaPlayer;
    private DataConnection _signalingChannel;
    private WindowManager mWindowManager;
    private MediaStream _localStream;
    private MyGroupView mGroupView;
    private WindowManager.LayoutParams layoutParams;
    private MediaConnection _mediaConnection;
    private CallState _callState;
    private CircleButton answer;
    private CircleButton reject;
    private Chronometer chronometer;
    public Peer peer;
    private RelativeLayout params;
    private MediaStream _remoteStream;
    Vibrator v;

    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(this, "onCreate service", Toast.LENGTH_SHORT).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground();
        } else {
            startForeground(1, new Notification());
        }
        //
        // Initialize Peer
        //
        PeerOption option = new PeerOption();
        option.key = API_KEY;
        option.domain = DOMAIN;
        peer = new Peer(this, option);
    }

    void setSignalingCallbacks() {
        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, object -> {
            PeerError error = (PeerError) object;
            Toast.makeText(this, "[On/DataError]" + error, Toast.LENGTH_SHORT).show();
        });

        _signalingChannel.on(DataConnection.DataEventEnum.CLOSE, object -> {
            Toast.makeText(this, "DataConnection.DataEventEnum.CLOSE", Toast.LENGTH_SHORT).show();
            handleReject();
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, object -> {
            Toast.makeText(this, "DataConnection.DataEventEnum.DATA", Toast.LENGTH_SHORT).show();


        });

    }

    void closeRemoteStream() {
        if (null == _remoteStream) {
            return;
        }

        _remoteStream.close();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "com.example.simpleapp";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.answer_button)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(1, notification);
    }

    void startLocalStream() {
        Navigator.initialize(peer);
        MediaConstraints constraints = new MediaConstraints();
        _localStream = Navigator.getUserMedia(constraints);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);


        //
        // Set Peer event callbacks
        //

        // OPEN
        peer.on(Peer.PeerEventEnum.OPEN, object -> {
            Toast.makeText(this, "Peer.PeerEventEnum.OPEN" + object, Toast.LENGTH_SHORT).show();
            startLocalStream();
            // Show my ID
        });

        // CALL (Incoming call)
        peer.on(Peer.PeerEventEnum.CALL, object -> {
            Toast.makeText(this, "Peer.PeerEventEnum.CALL", Toast.LENGTH_SHORT).show();
            if (!(object instanceof MediaConnection)) {
                return;
            }
            initView();
            _mediaConnection = (MediaConnection) object;
            _callState = CallState.CALLING;

        });

        // CONNECT (Custom Signaling Channel for a call)
        peer.on(Peer.PeerEventEnum.CONNECTION, object -> {
            Toast.makeText(this, "Peer.PeerEventEnum.CONNECTION", Toast.LENGTH_SHORT).show();
            if (!(object instanceof DataConnection)) {
                return;
            }
            _signalingChannel = (DataConnection) object;
            setSignalingCallbacks();

        });

        peer.on(Peer.PeerEventEnum.CLOSE, object -> {
            Toast.makeText(this, "Peer.PeerEventEnum.CLOSE", Toast.LENGTH_SHORT).show();
            handleReject();
        });
        peer.on(Peer.PeerEventEnum.DISCONNECTED, object -> {
            Toast.makeText(this, "Peer.PeerEventEnum.DISCONNECTED", Toast.LENGTH_SHORT).show();
        });
        peer.on(Peer.PeerEventEnum.ERROR, object -> {
            Toast.makeText(this, "Peer.PeerEventEnum.ERROR", Toast.LENGTH_SHORT).show();
            PeerError error = (PeerError) object;
        });

        return START_STICKY;
    }

    private void showIncomingCall() {
        mWindowManager.addView(mGroupView, layoutParams);
        ringing();
    }

    void ringing() {
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_electrical_sweep);
        mediaPlayer.setLooping(true);
        long[] pattern = {0, 500, 1000};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            //deprecated in API 26
            v.vibrate(500);
        }
        mediaPlayer.start();
    }

    void stopRing() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        v.cancel();
    }

    private void initView() {
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createIconView();
        showIncomingCall();
    }

    private void createIconView() {
        mGroupView = new MyGroupView(this);
        View view = View.inflate(this, R.layout.activity_incoming_call, mGroupView);

        params = view.findViewById(R.id.layoutBottom);
        reject = view.findViewById(R.id.reject);
        reject.setOnClickListener(v -> {
            if (null != _signalingChannel && CallState.CALLING == _callState) {
                handleReject();

            }
        });

        chronometer = new Chronometer(this);


        answer = view.findViewById(R.id.answer);
        answer.setOnClickListener(view1 -> handleAnswer());

        layoutParams = new WindowManager.LayoutParams();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    }

    private void handleAnswer() {
        stopRing();
        _mediaConnection.answer(_localStream);
        setMediaCallbacks();
        moveAnimation();
    }

    private void handleReject() {
        stopRing();
        closeRemoteStream();
        _mediaConnection.close(true);
        _signalingChannel.close(true);
        _callState = CallState.TERMINATED;
        Log.d("DDD", "hang up");
        mWindowManager.removeView(mGroupView);

    }

    public void moveAnimation() {
        int width = Resources.getSystem().getDisplayMetrics().widthPixels / 3;
        Animation anim = new TranslateAnimation(Animation.INFINITE, width, Animation.ABSOLUTE, Animation.ABSOLUTE);
        anim.setDuration(800);
        anim.setFillAfter(true);
        reject.startAnimation(anim);

        anim.setAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
                ((ViewGroup) answer.getParent()).removeView(answer);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                ((ViewGroup) reject.getParent()).removeView(reject);
                CircleButton reject = new CircleButton(CallService.this);
                reject.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                params.setGravity(Gravity.CENTER);
                reject.setImageDrawable(getDrawable(R.drawable.ic_call_end));
                reject.setColor(Color.RED);
                reject.getLayoutParams().width = 145;
                reject.getLayoutParams().height = 145;
                params.addView(reject);
                reject.setOnClickListener(view -> {
                    handleReject();

                });
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    void setMediaCallbacks() {
        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, object -> {
            Toast.makeText(this, "MediaConnection.MediaEventEnum.STREAM", Toast.LENGTH_LONG).show();
            _remoteStream = (MediaStream) object;
            _callState = CallState.ESTABLISHED;
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, object -> {
            Toast.makeText(CallService.this, "MediaConnection.MediaEventEnum.CLOSE", Toast.LENGTH_SHORT).show();
            handleReject();

        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, object -> {
            Toast.makeText(this, "MediaConnection.MediaEventEnum.ERROR", Toast.LENGTH_LONG).show();
            PeerError error = (PeerError) object;
        });

    }


    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(this, "onBind service", Toast.LENGTH_SHORT).show();
        return null;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "onDestroy service", Toast.LENGTH_SHORT).show();
    }
}
