import 'package:flutter/services.dart';

class FlutterSkyWayAndroid {
  MethodChannel _channel = const MethodChannel('flu.gonosen.dev/skyway');

  FlutterSkyWayAndroid() {
    this._channel.setMethodCallHandler(_handleMethod);
  }

  Future<void> initSkyWay(String apiKey, String domain) async {
    try {
      final peerId = await _channel
          .invokeMethod('connectSkyWay', {"API_KEY": apiKey, "DOMAIN": domain});
      print(peerId);
    } on PlatformException catch (e) {
      throw "Failed init connect skyWay: '${e.message}'.";
    }
  }

  Future<String> callingSkyWay(String peerId) async {
    String callState = "TERMINATE";
    try {
      callState = await _channel.invokeMethod('callingSkyWay', {
        "PEERID": peerId,
      });
      print(callState);
    } on PlatformException catch (e) {
      throw "Failed calling SkyWay: '${e.message}'.";
    }
    return callState;
  }

  // Private function that gets called by ObjC/Java
  Future<void> _handleMethod(MethodCall call) async {
    if (call.method == "FlutterSkyWayAndroid#handleInComingCall") {
      print("incoming call");
    }
  }
}
