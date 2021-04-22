package de.patwoz.rn.bluetoothstatemanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import androidx.annotation.Nullable;

public class RNBluetoothStateManagerModule extends ReactContextBaseJavaModule {

    private final static int REQUEST_ENABLE_BT = 795;
    private final static String EVENT_BLUETOOTH_STATE_CHANGE = "EVENT_BLUETOOTH_STATE_CHANGE";
    private final ReactApplicationContext reactContext;
    private final Intent INTENT_OPEN_BLUETOOTH_SETTINGS = new Intent(
            android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
    );
    private final Intent INTENT_REQUEST_ENABLE_BLUETOOTH = new Intent(
            BluetoothAdapter.ACTION_REQUEST_ENABLE
    );
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                );
                sendEvent(EVENT_BLUETOOTH_STATE_CHANGE, "{\"Device Status\": \"" + BridgeUtils.fromBluetoothState(state) + "\"");
            }//@Sudhir
            else if (action != null && action.equals(BluetoothDevice.ACTION_ACL_CONNECTED) || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String data = "{\"id\":\"" + bluetoothDevice.getAddress() + "\", \"name\": \"" + bluetoothDevice.getName() + "\",";
                Toast.makeText(context, "Connected to " + bluetoothDevice.getName(),
                        Toast.LENGTH_SHORT).show();
                switch (action) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        data += "\"isConnected\":true}";
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        data += "\"isConnected\":false}";
                        break;
                }
                sendEvent(EVENT_BLUETOOTH_STATE_CHANGE, data);
            }
        }
    };
    private Promise requestToEnablePromise;
    private final ActivityEventListener requestToEnableListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            if (requestCode != REQUEST_ENABLE_BT) {
                return;
            }

            if (requestToEnablePromise == null) {
                Log.w(
                        "RNBluetoothStateManager",
                        "onActivityResult() :: Result code:" + resultCode + " ::'requestToEnablePromise' should be defined!"
                );
            } else {
                if (resultCode == Activity.RESULT_CANCELED) {
                    requestToEnablePromise.reject("CANCELED", "The user canceled the action.");
                } else if (resultCode == Activity.RESULT_OK) {
                    requestToEnablePromise.resolve(null);
                } else {
                    Log.w(
                            "RNBluetoothStateManager",
                            "onActivityResult() :: Result code:" + resultCode + " :: Unhandled result code"
                    );
                }
            }

            removeRequestToEnableListener();
        }
    };

    public RNBluetoothStateManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        if (BluetoothUtils.isBluetoothSupported()) {
            this.startListenForBluetoothStateChange();
        }
    }

    private void destroy() {
        if (BluetoothUtils.isBluetoothSupported()) {
            this.stopListenForBluetoothStateChange();
        }
        this.removeRequestToEnableListener();
    }

    @Override
    public String getName() {
        return "RNBluetoothStateManager";
    }

    // --------------------------------------------------------------------------------------------- -
    // BLUETOOTH STATE

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("EVENT_BLUETOOTH_STATE_CHANGE", EVENT_BLUETOOTH_STATE_CHANGE);
        return constants;
    }

    // --------------------------------------------------------------------------------------------- -
    // PROGRAMMATICALLY CHANGE BLUETOOTH STATE (not recommended)

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        this.destroy();
    }

    @ReactMethod
    public void getState(Promise promise) {
        if (this.handleIfBluetoothNotSupported(promise, false)) {
            promise.resolve(Constants.BluetoothState.UNSUPPORTED);
            return;
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        promise.resolve(BridgeUtils.fromBluetoothState(bluetoothAdapter.getState()));
    }

    @SuppressLint("MissingPermission")
    private void setBluetooth(boolean enable) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (enable) {
            bluetoothAdapter.enable();
        } else {
            bluetoothAdapter.disable();
        }
    }

    // --------------------------------------------------------------------------------------------- -
    // OPEN SETTINGS

    @ReactMethod
    public void enable(Promise promise) {
        if (this.handleIfBluetoothNotSupported(promise)) {
            return;
        }
        Activity currentActivity = this.handleCurrentActivity(promise);
        if (currentActivity == null) {
            return;
        }

        if (BluetoothUtils.hasBluetoothAdminPermission(currentActivity)) {
            this.setBluetooth(true);
            promise.resolve(null);
        } else {
            promise.reject("UNAUTHORIZED", "You are not authorized to do this.");
        }
    }

    @ReactMethod
    public void disable(Promise promise) {
        if (this.handleIfBluetoothNotSupported(promise)) {
            return;
        }
        Activity currentActivity = this.handleCurrentActivity(promise);
        if (currentActivity == null) {
            return;
        }

        if (BluetoothUtils.hasBluetoothAdminPermission(currentActivity)) {
            this.setBluetooth(false);
            promise.resolve(null);
        } else {
            promise.reject("UNAUTHORIZED", "You are not authorized to do this.");
        }
    }

    // --------------------------------------------------------------------------------------------- -
    // BLUETOOTH STATE CHANGE

    @ReactMethod
    public void openSettings(Promise promise) {
        if (this.handleIfBluetoothNotSupported(promise)) {
            return;
        }
        Activity currentActivity = this.handleCurrentActivity(promise);
        if (currentActivity == null) {
            return;
        }

        currentActivity.startActivity(INTENT_OPEN_BLUETOOTH_SETTINGS);
        promise.resolve(null);
    }

    // Get previously connected devices.
    @ReactMethod
    public void getPairedDevices(Promise promise) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        String list = "[";
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String macAddress = device.getAddress();
                list += (list == "[" ? "" : ",") + "{\"id\":\"" + macAddress + "\",\"name\":\"" + deviceName + "\"}";
            }
            list += "]";
            promise.resolve(list);
        }
    }

    private void startListenForBluetoothStateChange() {
        //@Sudhir
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.reactContext.registerReceiver(mReceiver, filter);
    }

    // --------------------------------------------------------------------------------------------- -
    // REQUEST TO ENABLE BLUETOOTH

    private void stopListenForBluetoothStateChange() {
        this.reactContext.unregisterReceiver(mReceiver);
    }

    private void addRequestToEnableListener(Promise promise) {
        this.requestToEnablePromise = promise;
        this.reactContext.addActivityEventListener(this.requestToEnableListener);
    }

    private void removeRequestToEnableListener() {
        this.reactContext.removeActivityEventListener(this.requestToEnableListener);
        this.requestToEnablePromise = null;
    }

    @ReactMethod
    public void requestToEnable(Promise promise) {
        Activity currentActivity = this.handleCurrentActivity(promise);
        if (currentActivity == null) {
            return;
        }

        this.addRequestToEnableListener(promise);
        currentActivity.startActivityForResult(INTENT_REQUEST_ENABLE_BLUETOOTH, REQUEST_ENABLE_BT);
    }

    // --------------------------------------------------------------------------------------------- -
    // HELPERS

    private void sendEvent(String eventName, @Nullable Object params) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private boolean handleIfBluetoothNotSupported(Promise promise, boolean reject) {
        if (!BluetoothUtils.isBluetoothSupported()) {
            if (reject) {
                promise.reject("BLUETOOTH_NOT_SUPPORTED", "This device doesn't support Bluetooth");
            }
            return true;
        }
        return false;
    }

    private boolean handleIfBluetoothNotSupported(Promise promise) {
        return this.handleIfBluetoothNotSupported(promise, true);
    }

    private Activity handleCurrentActivity(Promise promise) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("INTERNAL_ERROR", "There is no activity");
        }
        return currentActivity;
    }
}
