package com.example.flutter_skyway;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicReference;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerOption;

public class MainActivity extends FlutterActivity {
    private String CHANNEL = "samples.flutter.dev/battery";
    private Peer _peer;
    private MediaStream _localStream;
    private MediaStream _remoteStream;
    private MediaConnection _mediaConnection;

    private boolean _bConnected;
    private String _peerId;

    private Handler _handler;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL).setMethodCallHandler((call, result) -> {
            if (call.method.equals("getBatteryLevel")) {
                int batteryLevel = getBatteryLevel();

                if (batteryLevel != -1) {
                    result.success(batteryLevel);
                } else {
                    result.error("UNAVAILABLE", "Battery level not available.", null);
                }
            } else if (call.method.equals("connectSkyway")) {
                String peerId = getPeerId(call.argument("API_KEY"), call.argument("DOMAIN"));
                result.success(peerId);
            } else {
                result.notImplemented();
            }
        });
    }

    private int getBatteryLevel() {
        int batteryLevel = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
        }
        return batteryLevel;
    }

    private String getPeerId(String key, String domain) {
        AtomicReference<String> peerId = null;
        PeerOption option = new PeerOption();
        option.key = key;
        option.domain = domain;
        option.debug = Peer.DebugLevelEnum.ALL_LOGS;
        _peer = new Peer(this, option);
        _peer.on(Peer.PeerEventEnum.OPEN, object -> {

            // Show my ID
            _peerId = ((String) object);
            Log.d("DDD", _peerId);

        });
        return peerId.get();
    }
}
