package com.ntt.ecl.webrtc.sample_p2p_call;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import io.skyway.Peer.OnCallback;
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
    private static final String API_KEY = "7087900e-91b9-44eb-b477-601292937668";
    private static final String DOMAIN = "localhost";

    private Peer _peer;
    private MediaStream _localStream;
    private MediaStream _remoteStream;
    private MediaConnection _mediaConnection;
    private DataConnection _signalingChannel;
    private MediaPlayer mediaPlayer;


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

        mediaPlayer = MediaPlayer.create(this, R.raw.alert_electrical_sweep);
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
            Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.OPEN", Toast.LENGTH_SHORT).show();
            // Show my ID
            _strOwnId = (String) object;
            TextView tvOwnId = (TextView) findViewById(R.id.txtPeerId);
            tvOwnId.setText(_strOwnId);

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
            Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.CALL", Toast.LENGTH_SHORT).show();
            if (!(object instanceof MediaConnection)) {
                return;
            }

            _mediaConnection = (MediaConnection) object;
            _callState = CallState.CALLING;
            showIncomingCallDialog();

        });

        // CONNECT (Custom Signaling Channel for a call)
        _peer.on(Peer.PeerEventEnum.CONNECTION, object -> {
            Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.CONNECTION", Toast.LENGTH_SHORT).show();
            if (!(object instanceof DataConnection)) {
                return;
            }

            _signalingChannel = (DataConnection) object;
            setSignalingCallbacks();

        });

        _peer.on(Peer.PeerEventEnum.CLOSE, object -> {
            Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.CLOSE", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "[On/Close]");
        });
        _peer.on(Peer.PeerEventEnum.DISCONNECTED, object -> {
            Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.DISCONNECTED", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "[On/Disconnected]");
        });
        _peer.on(Peer.PeerEventEnum.ERROR, object -> {
            Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.ERROR", Toast.LENGTH_SHORT).show();
            PeerError error = (PeerError) object;
            Log.d(TAG, "[On/Error]" + error.getMessage());
        });


        //
        // Set GUI event listeners
        //

        Button btnAction = findViewById(R.id.btnAction);
        btnAction.setEnabled(true);
        btnAction.setOnClickListener(v -> {
            v.setEnabled(false);

            if (CallState.TERMINATED == _callState) {
                // Select remote peer & make a call
                showPeerIDs();
            } else {
                // Hang up a call
                stopRing();
                closeRemoteStream();
                _mediaConnection.close(true);
                _signalingChannel.close(true);
                _callState = CallState.TERMINATED;
                updateActionButtonTitle();
            }

            v.setEnabled(true);
        });

    }

    private void ringing() {
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_electrical_sweep);
        mediaPlayer.start();
        mediaPlayer.setLooping(true);
    }

    private void stopRing() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        checkDrawOverlayPermission();
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocalStream();
            }
        }
    }

    public final static int REQUEST_CODE = -1010101;

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void checkDrawOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // if not construct intent to request permission
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getApplicationContext().getPackageName()));
            startActivityForResult(intent, REQUEST_CODE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            Settings.canDrawOverlays(this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Hide the status bar.
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

        // Disable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set volume control stream type to WebRTC audio.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        // Set default volume control stream type.
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Enable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        Intent intent = new Intent(this, CallService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Toast.makeText(this, "startForegroundService", Toast.LENGTH_SHORT).show();
            startForegroundService(intent);
        } else {
            Toast.makeText(this, "startService", Toast.LENGTH_SHORT).show();
            startService(intent);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyPeer();
        super.onDestroy();
    }

    //
    // Get a local MediaStream & show it
    //
    void startLocalStream() {
        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        _localStream = Navigator.getUserMedia(constraints);

    }

    //
    // Set callbacks for MediaConnection.MediaEvents
    //
    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, object -> {
            Toast.makeText(MainActivity.this, "MediaConnection.MediaEventEnum.STREAM", Toast.LENGTH_SHORT).show();
            _remoteStream = (MediaStream) object;
            _callState = CallState.ESTABLISHED;
            updateActionButtonTitle();
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, object -> {
            Toast.makeText(MainActivity.this, "MediaConnection.MediaEventEnum", Toast.LENGTH_SHORT).show();
            closeRemoteStream();
            _signalingChannel.close(true);
            _callState = CallState.TERMINATED;
            updateActionButtonTitle();
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, object -> {
            Toast.makeText(MainActivity.this, "MediaConnection.MediaEventEnum.ERROR", Toast.LENGTH_SHORT).show();
            PeerError error = (PeerError) object;
            Log.d(TAG, "[On/MediaError]" + error);
        });

    }

    //
    // Set callbacks for DataConnection.DataEvents
    //
    void setSignalingCallbacks() {
        _signalingChannel.on(DataConnection.DataEventEnum.OPEN, object -> Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.OPEN", Toast.LENGTH_SHORT).show());

        _signalingChannel.on(DataConnection.DataEventEnum.CLOSE, object -> {
            Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.CLOSE", Toast.LENGTH_SHORT).show();
            stopRing();
            closeRemoteStream();
            _mediaConnection.close(true);
            _signalingChannel.close(true);
            _callState = CallState.TERMINATED;
            updateActionButtonTitle();
            dismissIncomingCallDialog();

        });

        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, object -> {
            Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.ERROR", Toast.LENGTH_SHORT).show();
            PeerError error = (PeerError) object;
            Log.d(TAG, "[On/DataError]" + error);
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, object -> {
            Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.DATA", Toast.LENGTH_SHORT).show();
            String message = (String) object;
            Log.d(TAG, "[On/Data]" + message);

            if ("reject".equals(message)) {
                closeMediaConnection();
                _signalingChannel.close(true);
                _callState = CallState.TERMINATED;
                updateActionButtonTitle();
            }
        });

    }

    //
    // Clean up objects
    //
    private void destroyPeer() {
        closeRemoteStream();

        if (null != _localStream) {
            _localStream.close();
        }

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

    //
    // Unset callbacks for PeerEvents
    //
    void unsetPeerCallback(Peer peer) {
        if (null == _peer) {
            return;
        }

        peer.on(Peer.PeerEventEnum.OPEN, o -> {
            Toast.makeText(this, "Peer.PeerEventEnum.OPEN" + o, Toast.LENGTH_LONG).show();
        });
        peer.on(Peer.PeerEventEnum.CONNECTION, o -> {
            Toast.makeText(this, "Peer.PeerEventEnum.CONNECTION" + o, Toast.LENGTH_LONG).show();
        });
        peer.on(Peer.PeerEventEnum.CALL, o -> {
            Toast.makeText(this, "Peer.PeerEventEnum.CALL" + o, Toast.LENGTH_LONG).show();
        });
        peer.on(Peer.PeerEventEnum.CLOSE, o -> {
            Toast.makeText(this, "Peer.PeerEventEnum.CLOSE" + o, Toast.LENGTH_LONG).show();
        });
        peer.on(Peer.PeerEventEnum.DISCONNECTED, o -> {
            Toast.makeText(this, "Peer.PeerEventEnum.DISCONNECTED" + o, Toast.LENGTH_LONG).show();
        });
        peer.on(Peer.PeerEventEnum.ERROR, o -> {
            Toast.makeText(this, "Peer.PeerEventEnum.ERROR" + o, Toast.LENGTH_LONG).show();
        });
    }

    //
    // Unset callbacks for MediaConnection.MediaEvents
    //
    void unsetMediaCallbacks() {
        if (null == _mediaConnection) {
            return;
        }

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, o -> {
            Toast.makeText(this, "MediaConnection.MediaEventEnum.STREAM" + o, Toast.LENGTH_LONG).show();
        });
        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, o -> {
            Toast.makeText(this, "MMediaConnection.MediaEventEnum.CLOSE" + o, Toast.LENGTH_LONG).show();
        });
        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, o -> {
            Toast.makeText(this, "PMediaConnection.MediaEventEnum.ERROR" + o, Toast.LENGTH_LONG).show();
        });
    }

    //
    // Close a MediaConnection
    //
    void closeMediaConnection() {
        if (null != _mediaConnection) {
            if (_mediaConnection.isOpen()) {
                _mediaConnection.close(true);
            }
            unsetMediaCallbacks();
        }
    }

    //
    // Close a remote MediaStream
    //
    void closeRemoteStream() {
        if (null == _remoteStream) {
            return;
        }
        _remoteStream.close();
    }

    //
    // Create a MediaConnection
    //
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

        updateActionButtonTitle();
    }

    //
    // Listing all peers
    //
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

    //
    // Update actionButton title
    //
    void updateActionButtonTitle() {
        _handler.post(() -> {
            Button btnAction = findViewById(R.id.btnAction);
            if (null != btnAction) {
                if (CallState.TERMINATED == _callState) {
                    btnAction.setText("Make Call");
                } else {
                    btnAction.setText("Hang up");
                }
            }
        });
    }

    //
    // Show alert dialog on an incoming call
    //
    AlertDialog incomingCallDialog;

    void showIncomingCallDialog() {
        ringing();
        incomingCallDialog = new AlertDialog.Builder(this)
                .setTitle("Incoming call")
                .setMessage("from : " + _mediaConnection.peer())
                .setPositiveButton("Answer", (dialogInterface, i) -> {
                    _mediaConnection.answer(_localStream);
                    stopRing();
                    setMediaCallbacks();
                    _callState = CallState.ESTABLISHED;
                    updateActionButtonTitle();
                })
                .setNegativeButton("Reject", (dialogInterface, i) -> {
                    if (null != _signalingChannel) {
                        stopRing();
                        _signalingChannel.close(true);
                        _callState = CallState.TERMINATED;
                        updateActionButtonTitle();
                    }
                })
                .show();
    }

    //
    // Dismiss alert dialog for an incoming call
    //
    void dismissIncomingCallDialog() {
        if (null != incomingCallDialog) {
            incomingCallDialog.cancel();
            incomingCallDialog = null;
        }
    }
}
