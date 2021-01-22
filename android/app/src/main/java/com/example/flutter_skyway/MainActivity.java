package com.example.flutter_skyway;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.DataConnection;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

public class MainActivity extends FlutterActivity {
    private String CHANNEL = "flu.gonosen.dev/skyway";
    private Peer _peer;
    private MediaStream _localStream;
    private MediaStream _remoteStream;
    private MediaConnection _mediaConnection;

    private boolean _bConnected;
    private String _peerId;
    private DataConnection _signalingChannel;
    private WindowManager.LayoutParams layoutParams;
    private RelativeLayout params;
    private Handler _handler;
    private WindowManager mWindowManager;
    private MyGroupView mGroupView;
    private CallState _callState;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);


        MethodChannel channel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL);
        channel.setMethodCallHandler((call, result) -> {
            if (call.method.equals("callingSkyWay")) {
                requestPermission();
                calling(call.argument("PEERID"));

                // CONNECT (Custom Signaling Channel for a call)
                _peer.on(Peer.PeerEventEnum.CONNECTION, object -> {
                    Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.CONNECTION", Toast.LENGTH_SHORT).show();
                    if (!(object instanceof DataConnection)) {
                        return;
                    }

                    _signalingChannel = (DataConnection) object;
                    setSignalingCallbacks();

                });


                // CALL (Incoming call)
                _peer.on(Peer.PeerEventEnum.CALL, object -> {
                    Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.CALL", Toast.LENGTH_SHORT).show();
                    if (!(object instanceof MediaConnection)) {
                        return;
                    }
                    initView();
                    channel.invokeMethod("inComingCall", "asd");
                    _mediaConnection = (MediaConnection) object;
                    _callState = CallState.CALLING;

                });

                _peer.on(Peer.PeerEventEnum.CLOSE, object -> {
                    Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.CLOSE", Toast.LENGTH_SHORT).show();
                    Log.d("DDD", "[On/Close]");
                });
                _peer.on(Peer.PeerEventEnum.DISCONNECTED, object -> {
                    Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.DISCONNECTED", Toast.LENGTH_SHORT).show();
                    Log.d("DDD", "[On/Disconnected]");
                });
                _peer.on(Peer.PeerEventEnum.ERROR, object -> {
                    Toast.makeText(MainActivity.this, "Peer.PeerEventEnum.ERROR", Toast.LENGTH_SHORT).show();
                    PeerError error = (PeerError) object;
                    Log.d("DDD", "[On/Error]" + error.getMessage());
                });


            } else if (call.method.equals("connectSkyWay")) {
                getPeerId(call.argument("API_KEY"), call.argument("DOMAIN"), result);
            } else if (call.method.equals("onReject")) {
                handleReject();

            } else if (call.method.equals("onAnswer")) {
                handleAnswer();
            } else {
                result.notImplemented();
            }
        });
    }

    void startLocalStream() {
        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        _localStream = Navigator.getUserMedia(constraints);

    }

    private void initView() {
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createIconView();
        showIncomingCall();
    }

    private void showIncomingCall() {
        mWindowManager.addView(mGroupView, layoutParams);
    }

    private void createIconView() {
        mGroupView = new MyGroupView(this);
        View view = View.inflate(this, R.layout.activity_incoming_call, mGroupView);

        params = view.findViewById(R.id.layoutBottom);


        layoutParams = new WindowManager.LayoutParams();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    }


    private void handleAnswer() {
        _mediaConnection.answer(_localStream);
        setMediaCallbacks();
    }

    private void handleReject() {
        closeRemoteStream();
        _mediaConnection.close(true);
        _signalingChannel.close(true);
        _callState = CallState.TERMINATED;
        Log.d("DDD", "hang up");
        mWindowManager.removeView(mGroupView);

    }

    void setSignalingCallbacks() {
        _signalingChannel.on(DataConnection.DataEventEnum.OPEN, object -> Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.OPEN", Toast.LENGTH_SHORT).show());

        _signalingChannel.on(DataConnection.DataEventEnum.CLOSE, object -> {
            Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.CLOSE", Toast.LENGTH_SHORT).show();
            closeRemoteStream();
            _mediaConnection.close(true);
            _signalingChannel.close(true);

        });

        _signalingChannel.on(DataConnection.DataEventEnum.ERROR, object -> {
            Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.ERROR", Toast.LENGTH_SHORT).show();
            PeerError error = (PeerError) object;
            Log.d("DDD", "[On/DataError]" + error);
        });

        _signalingChannel.on(DataConnection.DataEventEnum.DATA, object -> {
            Toast.makeText(MainActivity.this, "DataConnection.DataEventEnum.DATA", Toast.LENGTH_SHORT).show();
            String message = (String) object;
            Log.d("DDD", "[On/Data]" + message);

            if ("reject".equals(message)) {
                closeMediaConnection();
                _signalingChannel.close(true);
            }
        });

    }

    void closeMediaConnection() {
        if (null != _mediaConnection) {
            if (_mediaConnection.isOpen()) {
                _mediaConnection.close(true);
            }
            unsetMediaCallbacks();
        }
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
        } else {
            startLocalStream();
        }
    }

    private void calling(String peerId) {
        if (null == _peer) {
            return;
        }

        if (null != _mediaConnection) {
            _mediaConnection.close(true);
        }

        CallOption option = new CallOption();
        _mediaConnection = _peer.call(peerId, _localStream, option);
        if (null != _mediaConnection) {
            setMediaCallbacks();
            _callState = CallState.CALLING;
        }

        // custom P2P signaling channel to reject call attempt
        _signalingChannel = _peer.connect(peerId);

        if (null != _signalingChannel) {
            setSignalingCallbacks();
        }

    }

    private void setMediaCallbacks() {
        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, object -> {
            Toast.makeText(MainActivity.this, "MediaConnection.MediaEventEnum.STREAM", Toast.LENGTH_SHORT).show();
            _remoteStream = (MediaStream) object;
            Log.d("DDD", _remoteStream.getLabel());

        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, object -> {
            closeRemoteStream();
            _bConnected = false;
        });
    }

    void closeRemoteStream() {
        if (null == _remoteStream) {
            return;
        }
        _remoteStream.close();
    }

    private void getPeerId(String key, String domain, MethodChannel.Result result) {
        PeerOption option = new PeerOption();
        option.key = key;
        option.domain = domain;
        option.debug = Peer.DebugLevelEnum.ALL_LOGS;
        _peer = new Peer(this, option);
        // method call or code to be asynch.
        _peer.on(Peer.PeerEventEnum.OPEN, object -> {
            _peerId = (String) object;
            result.success(_peerId);

        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
}

class MyGroupView extends ConstraintLayout {
    public MyGroupView(Context context) {
        super(context);
    }
}
