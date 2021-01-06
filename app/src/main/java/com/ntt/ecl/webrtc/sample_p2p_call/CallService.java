package com.ntt.ecl.webrtc.sample_p2p_call;

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
import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
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


import java.util.Date;

import at.markushi.ui.CircleButton;
import io.skyway.Peer.DataConnection;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

public class CallService extends Service {

    private DataConnection _signalingChannel;
    private WindowManager mWindowManager;
    private MyGroupView mGroupView;
    private WindowManager.LayoutParams layoutParams;
    private MediaConnection _mediaConnection;
    private CallState _callState;
    private CircleButton answer;
    private CircleButton reject;
    private Chronometer chronometer;
    public Peer peer;
    private RelativeLayout params;
    private static final String TAG = "ForegroundService";
    private static final String CHANNEL_ID = "11111";
    private static final String CHANNEL_NAME = "ForegroundServiceChannel";


    public enum CallState {
        TERMINATED,
        CALLING,
        ESTABLISHED
    }


    @Override
    public void onCreate() {
        super.onCreate();
        PeerOption option = new PeerOption();
        option.key = Constants.API_KEY;
        option.domain = Constants.DOMAIN;
        peer = new Peer(this, option);
        Toast.makeText(this, "onCreate service", Toast.LENGTH_SHORT).show();
    }

    void setSignalingCallbacks() {

        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, object -> {
            PeerError error = (PeerError) object;
            Toast.makeText(this, "[On/DataError]" + error, Toast.LENGTH_SHORT).show();
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, object -> {
            String message = (String) object;
            if (message.equals("cancel")) {
                chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.stop();
                _callState = CallState.TERMINATED;
                mWindowManager.removeView(mGroupView);
            }

            Toast.makeText(this, message + "[On/Data]", Toast.LENGTH_SHORT).show();
        });

    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,CHANNEL_NAME,
//                NotificationManager.IMPORTANCE_HIGH);
//
//        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//        manager.createNotificationChannel(channel);
//
//        Notification notification = new Notification.Builder(getApplicationContext(),CHANNEL_ID).build();
//        startForeground(1, notification);

        Toast.makeText(this, "onStartCommand service", Toast.LENGTH_SHORT).show();
        peer.on(Peer.PeerEventEnum.OPEN, object -> {

            // Show my ID
            Toast.makeText(this, "peer id" + object, Toast.LENGTH_LONG).show();
        });

        // Set volume control stream type to WebRTC audio.
        // CONNECT (Custom Signaling Channel for a call)
        peer.on(Peer.PeerEventEnum.CONNECTION, object -> {
            if (!(object instanceof DataConnection)) {
                return;
            }

            _signalingChannel = (DataConnection) object;
            setSignalingCallbacks();

        });
        // CALL (Incoming call)
        peer.on(Peer.PeerEventEnum.CALL, object -> {
            if (!(object instanceof MediaConnection)) {
                return;
            }
            _mediaConnection = (MediaConnection) object;
            _callState = CallState.CALLING;
            initView();
        });

        return START_STICKY;
    }

    private void showIncomingCall() {
        mWindowManager.addView(mGroupView, layoutParams);
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
        layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    }

    private void handleAnswer() {
        setMediaCallbacks();
        _callState = CallState.CALLING;
        chronometer.start();
        chronometer.setEnabled(false);
        moveAnimation();

    }

    private void handleReject() {
        if (CallState.CALLING == _callState) {
            _signalingChannel.send("reject");
            _callState = CallState.TERMINATED;
            mWindowManager.removeView(mGroupView);
        }
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
                    Log.d("DDD", "end callll");
                });
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, object -> {
            _callState = CallState.ESTABLISHED;
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, object -> {
            _signalingChannel.close(true);
            _callState = CallState.TERMINATED;
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, object -> {
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
