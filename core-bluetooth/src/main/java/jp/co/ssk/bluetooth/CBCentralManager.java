package jp.co.ssk.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.co.ssk.utility.SynchronizeCallback;

public class CBCentralManager extends CBManager {

    @NonNull
    private final CBConfig mConfig = new CBConfig();
    @NonNull
    private final LinkedHashMap<String, CBPeripheral> mPeripherals = new LinkedHashMap<>();
    @NonNull
    private final CBCentralManagerDelegate mDelegate;
    @Nullable
    private final CBCentralManagerDebugDelegate mDebugDelegate;
    @NonNull
    private final CBScanner mScanner;
    private final CBPeripheral.PeripheralEventListenerForManager mPeripheralEventListener = new CBPeripheral.PeripheralEventListenerForManager() {
        @Override
        public void didConnect(@NonNull final CBPeripheral peripheral) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mDelegate.didConnect(CBCentralManager.this, peripheral);
                }
            });
        }

        @Override
        public void didFailToConnect(@NonNull final CBPeripheral peripheral) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mDelegate.didFailToConnect(CBCentralManager.this, peripheral);
                }
            });
        }

        @Override
        public void didDisconnectPeripheral(@NonNull final CBPeripheral peripheral) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mDelegate.didDisconnectPeripheral(CBCentralManager.this, peripheral);
                }
            });
        }

        @Override
        public void onStateChanged(@NonNull final CBPeripheral peripheral, @NonNull final CBPeripheralState newState) {
            if (null == mDebugDelegate) {
                return;
            }
            mDebugDelegate.onStateChanged(CBCentralManager.this, peripheral, newState);
        }

        @Override
        public void onDetailedStateChanged(@NonNull final CBPeripheral peripheral, @NonNull final CBPeripheralDetailedState newState) {
            if (null == mDebugDelegate) {
                return;
            }
            mDebugDelegate.onDetailedStateChanged(CBCentralManager.this, peripheral, newState);
        }

        @Override
        public void onPairingRequest(@NonNull final CBPeripheral peripheral, @NonNull CBConstants.PairingVariant variant) {
            if (null == mDebugDelegate) {
                return;
            }
            mDebugDelegate.onPairingRequest(CBCentralManager.this, peripheral);
        }

        @Override
        public void onBondStateChanged(@NonNull final CBPeripheral peripheral, @NonNull final CBPeripheral.BondState newState) {
            if (null == mDebugDelegate) {
                return;
            }
            mDebugDelegate.onBondStateChanged(CBCentralManager.this, peripheral, newState);
        }

        @Override
        public void onAclConnectionStateChanged(@NonNull final CBPeripheral peripheral, @NonNull final CBPeripheral.AclConnectionState newState) {
            if (null == mDebugDelegate) {
                return;
            }
            mDebugDelegate.onAclConnectionStateChanged(CBCentralManager.this, peripheral, newState);
        }

        @Override
        public void onGattConnectionStateChanged(@NonNull final CBPeripheral peripheral, @NonNull final CBPeripheral.GattConnectionState newState, final int status) {
            if (null == mDebugDelegate) {
                return;
            }
            mDebugDelegate.onGattConnectionStateChanged(CBCentralManager.this, peripheral, newState, status);
        }
    };

    @SuppressWarnings("unused")
    public CBCentralManager(
            @NonNull Context context,
            @NonNull CBCentralManagerDelegate delegate,
            @Nullable Looper looper) {
        this(context, delegate, looper, null);
    }

    public CBCentralManager(
            @NonNull Context context,
            @NonNull CBCentralManagerDelegate delegate,
            @Nullable Looper looper,
            @Nullable CBCentralManagerDebugDelegate debugDelegate) {
        super(context, looper);
        mDelegate = delegate;
        mDebugDelegate = debugDelegate;

        CBScanner.ScanListener scanListener = new CBScanner.ScanListener() {
            @Override
            void onScan(@NonNull final BluetoothDevice bluetoothDevice, final int rssi, final @NonNull byte[] scanRecord) {
                _onScan(bluetoothDevice, rssi, scanRecord);
            }
        };
        mScanner = new CBScanner(context, scanListener, getHandler().getLooper());

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CBConstants.ACTION_PAIRING_REQUEST);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @NonNull final Intent intent) {
                CBLog.vMethodIn();
                if (getHandler().isCurrentThread()) {
                    _onBroadcastReceived(intent);
                } else {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            _onBroadcastReceived(intent);
                        }
                    });
                }
            }
        };
        getContext().registerReceiver(broadcastReceiver, intentFilter);

        _initPeripherals();
    }

    public boolean isScanning() {
        boolean ret;
        if (getHandler().isCurrentThread()) {
            ret = mScanner.isScanning();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mScanner.isScanning());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = (boolean) callback.getResult();
        }
        return ret;
    }

    public void connect(@NonNull final CBPeripheral peripheral) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                _connect(peripheral);
            }
        });
    }

    public void cancelPeripheralConnection(@NonNull final CBPeripheral peripheral) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                _cancelPeripheralConnection(peripheral);
            }
        });
    }

    @SuppressWarnings("unused")
    @NonNull
    public List<CBPeripheral> retrieveConnectedPeripherals(@NonNull final List<CBUUID> serviceUUIDs) {
        return new ArrayList<>();
    }

    @Nullable
    public CBPeripheral retrievePeripherals(@NonNull final String address) {
        final CBPeripheral peripheral;
        if (getHandler().isCurrentThread()) {
            peripheral = _retrievePeripherals(address);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_retrievePeripherals(address));
                    callback.unlock();
                }
            });
            callback.lock();
            peripheral = (CBPeripheral) callback.getResult();
        }
        return peripheral;
    }

    public void scanForPeripherals(@NonNull final List<CBUUID> serviceUUIDs) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                _scanForPeripherals(serviceUUIDs);
            }
        });
    }

    public void stopScan() {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                _stopScan();
            }
        });
    }

    @NonNull
    public Bundle getDefaultConfig(@Nullable final List<CBConfig.Key> keys) {
        final Bundle config;
        if (getHandler().isCurrentThread()) {
            config = mConfig.getDefault(keys);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mConfig.getDefault(keys));
                    callback.unlock();
                }
            });
            callback.lock();
            config = (Bundle) callback.getResult();
            if (null == config) {
                throw new UnknownError("null == config");
            }
        }
        CBLog.d(config.toString());
        return config;
    }

    @NonNull
    public Bundle getConfig(@Nullable final List<CBConfig.Key> keys) {
        final Bundle config;
        if (getHandler().isCurrentThread()) {
            config = mConfig.get(keys);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mConfig.get(keys));
                    callback.unlock();
                }
            });
            callback.lock();
            config = (Bundle) callback.getResult();
            if (null == config) {
                throw new UnknownError("null == config");
            }
        }
        CBLog.d(config.toString());
        return config;
    }

    public void setConfig(@NonNull final Bundle config) {
        CBLog.d(config.toString());
        if (getHandler().isCurrentThread()) {
            _setConfig(config);
        } else {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    _setConfig(config);
                }
            });
        }
    }

    @Override
    protected void onStateChanged(@NonNull final CBManagerState newState) {
        if (CBManagerState.PoweredOff == newState) {
            _deinitPeripherals();
        } else if (CBManagerState.PoweredOn == newState) {
            _initPeripherals();
        }
        mDelegate.centralManagerDidUpdateState(this, newState);
    }

    private void _initPeripherals() {
        mPeripherals.clear();
        Set<BluetoothDevice> bondedDevices = getAdapter().getBondedDevices();
        if (null != bondedDevices) {
            for (BluetoothDevice bluetoothDevice : bondedDevices) {
                mPeripherals.put(bluetoothDevice.getAddress(),
                        new CBPeripheral(getContext(), bluetoothDevice, mPeripheralEventListener, getHandler().getLooper()));
            }
        }
    }

    private void _deinitPeripherals() {
        for (Map.Entry<String, CBPeripheral> entry : mPeripherals.entrySet()) {
            CBPeripheral peripheral = entry.getValue();
            peripheral.cancelConnection();
        }
    }

    private void _setConfig(@NonNull Bundle config) {
        mConfig.set(config);
        for (Map.Entry<String, CBPeripheral> entry : mPeripherals.entrySet()) {
            CBPeripheral peripheral = entry.getValue();
            peripheral.setConfig(config);
        }
    }

    private void _scanForPeripherals(@NonNull final List<CBUUID> serviceUUIDs) {
        if (CBManagerState.PoweredOn != state()) {
            CBLog.e("Bluetooth not work.");
            return;
        }
        mScanner.scanForPeripherals(serviceUUIDs, 0);
    }

    private void _stopScan() {
        if (CBManagerState.PoweredOn != state()) {
            CBLog.w("Bluetooth not work.");
            return;
        }
        mScanner.stopScan();
    }

    @Nullable
    private CBPeripheral _retrievePeripherals(@NonNull String address) {
        CBPeripheral peripheral;
        if (mPeripherals.containsKey(address)) {
            peripheral = mPeripherals.get(address);
            CBLog.d("From the cache.");
        } else {
            try {
                BluetoothDevice bluetoothDevice = getAdapter().getRemoteDevice(address);
                peripheral = new CBPeripheral(getContext(), bluetoothDevice, mPeripheralEventListener, getHandler().getLooper());
                mPeripherals.put(address, peripheral);
                CBLog.d("From the OS.");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                peripheral = null;
            }
        }
        return peripheral;
    }

    private void _connect(@NonNull final CBPeripheral peripheral) {
        if (CBManagerState.PoweredOn != state()) {
            CBLog.e("Bluetooth not work.");
            return;
        }
        peripheral.connect();
    }

    private void _cancelPeripheralConnection(@NonNull final CBPeripheral peripheral) {
        if (CBManagerState.PoweredOn != state()) {
            CBLog.e("Bluetooth not work.");
            return;
        }
        peripheral.cancelConnection();
    }

    private void _onScan(@NonNull final BluetoothDevice bluetoothDevice, final int rssi, final @NonNull byte[] scanRecord) {
        final CBPeripheral peripheral;
        if (mPeripherals.containsKey(bluetoothDevice.getAddress())) {
            peripheral = mPeripherals.get(bluetoothDevice.getAddress());
        } else {
            CBLog.i("New peripheral detected. addr:" + bluetoothDevice.getAddress());
            peripheral = new CBPeripheral(getContext(), bluetoothDevice, mPeripheralEventListener, getHandler().getLooper());
            mPeripherals.put(bluetoothDevice.getAddress(), peripheral);
        }
        mDelegate.didDiscover(this, peripheral, scanRecord, rssi);
    }

    private void _onBroadcastReceived(@NonNull Intent intent) {
        BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String address = bluetoothDevice.getAddress();
        String action = intent.getAction();
        if (!mPeripherals.containsKey(address)) {
            CBLog.w("Ignore the " + action + " broadcast. target:" + address);
            return;
        }
        CBPeripheral peripheral = mPeripherals.get(address);
        if (CBConstants.ACTION_PAIRING_REQUEST.equals(action)) {
            CBConstants.PairingVariant pairingVariant = CBConstants.PairingVariant.valueOf(
                    intent.getIntExtra(CBConstants.EXTRA_PAIRING_VARIANT, CBConstants.PAIRING_VARIANT_UNKNOWN));
            CBLog.iOsApi("Received ACTION_PAIRING_REQUEST of " + address + ". variant:" + pairingVariant.name());
            peripheral.notifyPairingRequest(pairingVariant);
        } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
            AndroidPeripheral.BondState prevState = AndroidPeripheral.BondState.valueOf(
                    intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE));
            AndroidPeripheral.BondState newState = AndroidPeripheral.BondState.valueOf(
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE));
            CBLog.iOsApi("Received ACTION_BOND_STATE_CHANGED[" + prevState.name() + " -> " + newState.name() + "] of " + address + ".");
            peripheral.setBondState(newState);
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            CBLog.iOsApi("Received ACTION_ACL_CONNECTED of " + address + ".");
            peripheral.setAclConnectionState(AndroidPeripheral.AclConnectionState.Connected);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            CBLog.iOsApi("Received ACTION_ACL_DISCONNECTED of " + address + ".");
            peripheral.setAclConnectionState(AndroidPeripheral.AclConnectionState.Disconnected);
        }
    }
}
