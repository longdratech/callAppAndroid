package com.ntt.ecl.webrtc.sample_p2p_call;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.DataConnection;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

/**
 * MainActivity.java
 * ECL WebRTC p2p call sample
 * <p>
 * In this sample, a callee will be prompted by an alert-dialog to select
 * either "answer" or "reject" an incoming call (unlike p2p-videochat sample,
 * in which a callee will answer the call automatically).
 */

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    //
    // Set your APIkey and Domain
    //
    private static final String API_KEY = Constants.API_KEY;
    private static final String DOMAIN = Constants.DOMAIN;

    private Peer _peer;
    private MediaStream _localStream;
    private MediaStream _remoteStream;
    private MediaConnection _mediaConnection;
    private DataConnection _signalingChannel;

    private String _strOwnId;

    public enum CallState {
        TERMINATED,
        CALLING,
        ESTABLISHED
    }

    private CallState _callState;

    private Handler _handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        _handler = new Handler(Looper.getMainLooper());
        final Activity activity = this;
        _callState = CallState.TERMINATED;

        //
        // Initialize Peer
        //
        PeerOption option = new PeerOption();
        option.key = API_KEY;
        option.domain = DOMAIN;
        _peer = new Peer(this, option);

        //
        // Set Peer event callbacks
        //

        // OPEN
        _peer.on(Peer.PeerEventEnum.OPEN, object -> {

            // Show my ID
            _strOwnId = (String) object;
            TextView txtPeerId = findViewById(R.id.txtPeerId);
            txtPeerId.setText(_strOwnId);

            // Request permissions
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
            } else {

                // Get a local MediaStream & show it
                startLocalStream();
            }

        });

        // CALL (Incoming call)
        _peer.on(Peer.PeerEventEnum.CALL, object -> {
            if (!(object instanceof MediaConnection)) {
                return;
            }

            _mediaConnection = (MediaConnection) object;
            _callState = CallState.CALLING;
            showIncomingCallDialog();

        });

        // CONNECT (Custom Signaling Channel for a call)
        _peer.on(Peer.PeerEventEnum.CONNECTION, object -> {
            if (!(object instanceof DataConnection)) {
                return;
            }

            _signalingChannel = (DataConnection) object;
            setSignalingCallbacks();

        });

        _peer.on(Peer.PeerEventEnum.CLOSE, object -> Log.d(TAG, "[On/Close]"));
        _peer.on(Peer.PeerEventEnum.DISCONNECTED, object -> Log.d(TAG, "[On/Disconnected]"));
        _peer.on(Peer.PeerEventEnum.ERROR, object -> {
            PeerError error = (PeerError) object;
            Log.d(TAG, "[On/Error]" + error.getMessage());
        });


        //
        // Set GUI event listeners
        //

        Button btnCall = findViewById(R.id.btnCall);
        Button btnEnd = findViewById(R.id.btnEnd);
        btnEnd.setOnClickListener(v -> {
            // Hang up a call
            _mediaConnection.close(true);
            _signalingChannel.close(true);
            _callState = CallState.TERMINATED;
        });

        btnCall.setOnClickListener(v -> {
            if (CallState.TERMINATED == _callState) {
                showPeerIDs();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        checkDrawOverlayPermission();
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocalStream();
            } else {
                Toast.makeText(this, "Failed to access the camera and microphone.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public final static int REQUEST_CODE = -1010101;

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void checkDrawOverlayPermission() {
        Log.v("App", "Package Name: " + getApplicationContext().getPackageName());

        // check if we already  have permission to draw over other apps
        if (!Settings.canDrawOverlays(this)) {
            Log.v("App", "Requesting Permission" + Settings.canDrawOverlays(this));
            // if not construct intent to request permission
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getApplicationContext().getPackageName()));
            startActivityForResult(intent, REQUEST_CODE);
        } else {
            Log.v("App", "We already have permission for it.");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v("App", "OnActivity Result.");
        //check if received result code
        //  is equal our requested code for draw permission
        if (requestCode == REQUEST_CODE) {
            Settings.canDrawOverlays(this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        Intent intent = new Intent(this,CallService.class);
        if(Build.VERSION.SDK_INT >=  Build.VERSION_CODES.O) {
            Toast.makeText(this, "startForegroundService", Toast.LENGTH_SHORT).show();
            startForegroundService(intent);
        }else {
            Toast.makeText(this, "startService", Toast.LENGTH_SHORT).show();
            startService(intent);
        }
        super.onStop();
    }



    @Override
    protected void onDestroy() {
        Log.d("DDD", "Distroy activity");
        destroyPeer();
        super.onDestroy();
    }

    void startLocalStream() {
        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        _localStream = Navigator.getUserMedia(constraints);
    }

    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, object -> {
            _remoteStream = (MediaStream) object;
            _callState = CallState.ESTABLISHED;
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, object -> {
            _signalingChannel.close(true);
            _callState = CallState.TERMINATED;
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, object -> {
            PeerError error = (PeerError) object;
            Log.d(TAG, "[On/MediaError]" + error);
        });

    }

    void setSignalingCallbacks() {
        _signalingChannel.on(DataConnection.DataEventEnum.OPEN, object -> {

        });

        _signalingChannel.on(DataConnection.DataEventEnum.CLOSE, object -> {

        });

        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, object -> {
            PeerError error = (PeerError) object;
            Log.d(TAG, "[On/DataError]" + error);
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, object -> {
            String message = (String) object;
            Log.d(TAG, "[On/Data]" + message);

            switch (message) {
                case "reject":
                    closeMediaConnection();
                    _signalingChannel.close(true);
                    _callState = CallState.TERMINATED;
                    break;
                case "cancel":
                    closeMediaConnection();
                    _signalingChannel.close(true);
                    _callState = CallState.TERMINATED;
                    dismissIncomingCallDialog();
                    break;
            }
        });

    }

    private void destroyPeer() {
        closeMediaConnection();
        Navigator.terminate();
        if (null != _peer) {
            unsetPeerCallback(_peer);
            if (!_peer.isDisconnected()) {
                _peer.disconnect();
            }

            if (!_peer.isDestroyed()) {
                _peer.destroy();
            }

            _peer = null;
        }
    }

    void unsetPeerCallback(Peer peer) {
        if (null == _peer) {
            return;
        }

        peer.on(Peer.PeerEventEnum.OPEN, null);
        peer.on(Peer.PeerEventEnum.CONNECTION, null);
        peer.on(Peer.PeerEventEnum.CALL, null);
        peer.on(Peer.PeerEventEnum.CLOSE, null);
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null);
        peer.on(Peer.PeerEventEnum.ERROR, null);
    }

    void unsetMediaCallbacks() {

        if (null == _mediaConnection) {
            return;
        }

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, null);
    }

    void closeMediaConnection() {
        if (null != _mediaConnection) {
            if (_mediaConnection.isOpen()) {
                _mediaConnection.close(true);
            }
            unsetMediaCallbacks();
        }
    }

    void onPeerSelected(String strPeerId) {
        if (null == _peer) {
            return;
        }

        if (null != _mediaConnection) {
            _mediaConnection.close(true);
        }

        CallOption option = new CallOption();
        _mediaConnection = _peer.call(strPeerId, _localStream, option);
        if (null != _mediaConnection) {
            setMediaCallbacks();
            _callState = CallState.CALLING;
        }

        // custom P2P signaling channel to reject call attempt
        _signalingChannel = _peer.connect(strPeerId);
        if (null != _signalingChannel) {
            setSignalingCallbacks();
        }
    }

    void showPeerIDs() {
        if ((null == _peer) || (null == _strOwnId) || (0 == _strOwnId.length())) {
            Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get all IDs connected to the server
        final Context fContext = this;
        _peer.listAllPeers(object -> {
            if (!(object instanceof JSONArray)) {
                return;
            }

            JSONArray peers = (JSONArray) object;
            ArrayList<String> _listPeerIds = new ArrayList<>();
            String peerId;

            // Exclude my own ID
            for (int i = 0; peers.length() > i; i++) {
                try {
                    peerId = peers.getString(i);
                    if (!_strOwnId.equals(peerId)) {
                        _listPeerIds.add(peerId);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Show IDs using DialogFragment
            if (0 < _listPeerIds.size()) {
                FragmentManager mgr = getFragmentManager();
                PeerListDialogFragment dialog = new PeerListDialogFragment();
                dialog.setListener(
                        item -> _handler.post(() -> onPeerSelected(item)));
                dialog.setItems(_listPeerIds);
                dialog.show(mgr, "peerlist");
            } else {
                Toast.makeText(fContext, "PeerID list (other than your ID) is empty.", Toast.LENGTH_SHORT).show();
            }
        });

    }

    AlertDialog incomingCallDialog;

    void showIncomingCallDialog() {
        incomingCallDialog = new AlertDialog.Builder(this)
                .setTitle("Incoming call")
                .setMessage("from : " + _mediaConnection.peer())
                .setPositiveButton("Answer", (dialogInterface, i) -> {
                    _mediaConnection.answer(_localStream);
                    setMediaCallbacks();
                    _callState = CallState.ESTABLISHED;
                })
                .setNegativeButton("Reject", (dialogInterface, i) -> {
                    if (null != _signalingChannel) {
                        _signalingChannel.send("reject");
                        _callState = CallState.TERMINATED;
                    }
                })
                .show();
    }

    void dismissIncomingCallDialog() {
        if (null != incomingCallDialog) {
            incomingCallDialog.cancel();
            incomingCallDialog = null;
        }
    }
}