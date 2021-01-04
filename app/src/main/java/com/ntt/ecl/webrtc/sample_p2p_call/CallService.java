package com.ntt.ecl.webrtc.sample_p2p_call;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;


import io.skyway.Peer.DataConnection;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

public class CallService extends Service implements View.OnTouchListener {

    private Peer _peer;
    private DataConnection _signalingChannel;

    private WindowManager mWindowManager;
    private MyGroupView mGroupView;
    private WindowManager.LayoutParams layoutParams;
    private MediaConnection _mediaConnection;
    private CallState _callState;
    private Button answer;


    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    public enum CallState {
        TERMINATED,
        CALLING,
        ESTABLISHED
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(this, "onCreate service", Toast.LENGTH_SHORT).show();
        // Gety

    }

    void setSignalingCallbacks(Context context) {
        _signalingChannel.on(DataConnection.DataEventEnum.OPEN, object -> {

        });

        _signalingChannel.on(DataConnection.DataEventEnum.CLOSE, object -> {

        });

        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, object -> {
            PeerError error = (PeerError) object;
            Toast.makeText(this, "[On/DataError]" + error, Toast.LENGTH_SHORT).show();
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, object -> {
            String message = (String) object;
            Toast.makeText(this, message + "[On/Data]", Toast.LENGTH_SHORT).show();
        });

    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PeerOption option = new PeerOption();
        option.key = Constants.API_KEY;
        option.domain = Constants.DOMAIN;
        _peer = new Peer(this, option);
        Toast.makeText(this, "onStartCommand service", Toast.LENGTH_SHORT).show();
        // CONNECT (Custom Signaling Channel for a call)
        _peer.on(Peer.PeerEventEnum.CONNECTION, object -> {
            if (!(object instanceof DataConnection)) {
                return;
            }

            _signalingChannel = (DataConnection) object;
            setSignalingCallbacks(this);

        });
        // CALL (Incoming call)
        _peer.on(Peer.PeerEventEnum.CALL, object -> {
            if (!(object instanceof MediaConnection)) {
                return;
            }
            _mediaConnection = (MediaConnection) object;
//            _callState = CallState.CALLING;
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

        layoutParams = new WindowManager.LayoutParams();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE ;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
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
