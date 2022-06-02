package com.reactnativevolumemanager;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

@ReactModule(name = VolumeManagerModule.NAME)
public class VolumeManagerModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {
    public static final String NAME = "VolumeManager";
    private final String TAG = VolumeManagerModule.class.getSimpleName();

    private static final String VOL_VOICE_CALL = "call";
    private static final String VOL_SYSTEM = "system";
    private static final String VOL_RING = "ring";
    private static final String VOL_MUSIC = "music";
    private static final String VOL_ALARM = "alarm";
    private static final String VOL_NOTIFICATION = "notification";

    private final ReactApplicationContext mContext;
    private final AudioManager am;
    private final VolumeBroadcastReceiver volumeBR;

    public VolumeManagerModule(ReactApplicationContext reactContext) {

      super(reactContext);
      mContext = reactContext;
      reactContext.addLifecycleEventListener(this);
      am = (AudioManager) mContext.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
      volumeBR = new VolumeBroadcastReceiver();
    }

  private void registerVolumeReceiver() {
    if (!volumeBR.isRegistered()) {
      IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
      mContext.registerReceiver(volumeBR, filter);
      volumeBR.setRegistered(true);
    }
  }

  private void unregisterVolumeReceiver() {
    if (volumeBR.isRegistered()) {
      mContext.unregisterReceiver(volumeBR);
      volumeBR.setRegistered(false);
    }
  }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

  @ReactMethod
  public void setVolume(float val, ReadableMap config) {
    unregisterVolumeReceiver();
    String type = config.getString("type");
    boolean playSound = config.getBoolean("playSound");
    boolean showUI = config.getBoolean("showUI");
    assert type != null;
    int volType = getVolType(type);
    int flags = 0;
    if (playSound) {
      flags |= AudioManager.FLAG_PLAY_SOUND;
    }
    if (showUI) {
      flags |= AudioManager.FLAG_SHOW_UI;
    }
    try {
      am.setStreamVolume(volType, (int) (val * am.getStreamMaxVolume(volType)), flags);
    } catch (SecurityException e) {
      if (val == 0) {
        Log.w(TAG, "setVolume(0) failed. See https://github.com/c19354837/react-native-system-setting/issues/48");
        NotificationManager notificationManager =
          (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
          && !notificationManager.isNotificationPolicyAccessGranted()) {
          Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
          mContext.startActivity(intent);
        }
      }
      Log.e(TAG, "err", e);
    }
    registerVolumeReceiver();
  }

  @ReactMethod
  public void getVolume(String type, Promise promise) {
    promise.resolve(getNormalizationVolume(type));
  }

  private float getNormalizationVolume(String type) {
    int volType = getVolType(type);
    return am.getStreamVolume(volType) * 1.0f / am.getStreamMaxVolume(volType);
  }

  private int getVolType(String type) {
    switch (type) {
      case VOL_VOICE_CALL:
        return AudioManager.STREAM_VOICE_CALL;
      case VOL_SYSTEM:
        return AudioManager.STREAM_SYSTEM;
      case VOL_RING:
        return AudioManager.STREAM_RING;
      case VOL_ALARM:
        return AudioManager.STREAM_ALARM;
      case VOL_NOTIFICATION:
        return AudioManager.STREAM_NOTIFICATION;
      default:
        return AudioManager.STREAM_MUSIC;
    }
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {

  }

  @Override
  public void onNewIntent(Intent intent) {

  }

  @Override
  public void onHostResume() {
    registerVolumeReceiver();
  }

  @Override
  public void onHostPause() {
    unregisterVolumeReceiver();
  }

  @Override
  public void onHostDestroy() {

  }


  private class VolumeBroadcastReceiver extends BroadcastReceiver {

    private boolean isRegistered = false;

    public void setRegistered(boolean registered) {
      isRegistered = registered;
    }

    public boolean isRegistered() {
      return isRegistered;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {
        WritableMap para = Arguments.createMap();
        para.putDouble("value", getNormalizationVolume(VOL_MUSIC));
        para.putDouble(VOL_VOICE_CALL, getNormalizationVolume(VOL_VOICE_CALL));
        para.putDouble(VOL_SYSTEM, getNormalizationVolume(VOL_SYSTEM));
        para.putDouble(VOL_RING, getNormalizationVolume(VOL_RING));
        para.putDouble(VOL_MUSIC, getNormalizationVolume(VOL_MUSIC));
        para.putDouble(VOL_ALARM, getNormalizationVolume(VOL_ALARM));
        para.putDouble(VOL_NOTIFICATION, getNormalizationVolume(VOL_NOTIFICATION));
        try {
          mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("EventVolume", para);
        } catch (RuntimeException e) {
          // Possible to interact with volume before JS bundle execution is finished.
          // This is here to avoid app crashing.
        }
      }
    }
  }
}