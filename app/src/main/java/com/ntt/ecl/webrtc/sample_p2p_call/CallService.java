package com.ntt.ecl.webrtc.sample_p2p_call;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import io.skyway.Peer.DataConnection;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

public class CallService extends Service {

    private Peer _peer;
    private DataConnection _signalingChannel;
    private LinearLayout overlay;

    private WindowManager windowManager;
    private ImageView close;
    private RelativeLayout chatheadView;
    private FrameLayout content;

    public enum CallState {
        TERMINATED,
        CALLING,
        ESTABLISHED
    }

    private Looper serviceLooper;
    private ServiceHandler serviceHandler;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // Normally we would do some work here, like download a file.
            // For our sample, we just sleep for 5 seconds.
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // Restore interrupt status.
                Thread.currentThread().interrupt();
            }
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(this, "onCreate service", Toast.LENGTH_SHORT).show();
        HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);
        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);

        //
        // Set Peer event callbacks
        //
        PeerOption option = new PeerOption();
        option.key = Constants.API_KEY;
        option.domain = Constants.DOMAIN;
        _peer = new Peer(this, option);

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


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "onStartCommand service", Toast.LENGTH_SHORT).show();
        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = startId;
        serviceHandler.sendMessage(msg);
        // CONNECT (Custom Signaling Channel for a call)
        _peer.on(Peer.PeerEventEnum.CONNECTION, object -> {
            if (!(object instanceof DataConnection)) {
                return;
            }

            _signalingChannel = (DataConnection) object;
            setSignalingCallbacks(this);

        });

        _peer.on(Peer.PeerEventEnum.CALL, object -> {
            if (!(object instanceof MediaConnection)) {
                return;

            }
            Log.d("LONG", "Incoming call");
            Log.d("CALLER_ID", "Show Caller info of: ");

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);


            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 0;
            params.y = 100;
            chatheadView = (RelativeLayout) inflater.inflate(R.layout.activity_alert_dialog, null);
            close = chatheadView.findViewById(R.id.close);
            content = chatheadView.findViewById(R.id.content);
            content.setOnClickListener(v -> {

                windowManager.removeView(chatheadView);
                Intent intent1 = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://saravananandroid.blogspot.in/"));
                intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent1);
            });
            close.setOnClickListener(v -> {
                windowManager.removeView(chatheadView);
                Intent intent12 = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://saravananandroid.blogspot.in/"));
                intent12.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent12);
                startService(new Intent(getApplicationContext(), CallService.class));
            });

            windowManager.addView(chatheadView, params);

        });
        return super.onStartCommand(intent, flags, startId);
    }

    private void dismissCallerInfo(final Context context) {
        if (overlay != null) {
            WindowManager windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.removeView(overlay);
                overlay = null;
            }
        }
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
