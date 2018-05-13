package jp.co.ssk.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AndroidRuntimeException;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.co.ssk.utility.Bytes;
import jp.co.ssk.utility.Handler;
import jp.co.ssk.utility.Reflect;
import jp.co.ssk.utility.SynchronizeCallback;
import jp.co.ssk.utility.Types;

abstract class AndroidPeripheral {

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final Context mContext;
    @NonNull
    private final BluetoothDevice mBluetoothDevice;
    @NonNull
    private final String mAddress;
    @Nullable
    private final String mLocalName;
    @Nullable
    private BluetoothGatt mBluetoothGatt;
    @NonNull
    private BondState mBondState;
    @NonNull
    private AclConnectionState mAclConnectionState;
    @NonNull
    private GattConnectionState mGattConnectionState;
    @NonNull
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onConnectionStateChange(gatt, status, newState);
                }
            });
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onServicesDiscovered(gatt, status);
                }
            });
        }

        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onCharacteristicRead(gatt, characteristic, status);
                }
            });
        }

        @Override
        public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onCharacteristicWrite(gatt, characteristic, status);
                }
            });
        }

        @Override
        public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onCharacteristicChanged(gatt, characteristic);
                }
            });
        }

        @Override
        public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onDescriptorRead(gatt, descriptor, status);
                }
            });
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onDescriptorWrite(gatt, descriptor, status);
                }
            });
        }

        @Override
        public void onReliableWriteCompleted(final BluetoothGatt gatt, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onReliableWriteCompleted(gatt, status);
                }
            });
        }

        @Override
        public void onReadRemoteRssi(final BluetoothGatt gatt, final int rssi, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onReadRemoteRssi(gatt, rssi, status);
                }
            });
        }

        @Override
        public void onMtuChanged(final BluetoothGatt gatt, final int mtu, final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    _onMtuChanged(gatt, mtu, status);
                }
            });
        }
    };

    AndroidPeripheral(
            @NonNull Context context,
            @NonNull BluetoothDevice bluetoothDevice,
            @Nullable Looper looper) {
        if (null == looper) {
            HandlerThread thread = new HandlerThread("Peripheral-" + bluetoothDevice.getAddress());
            thread.start();
            looper = thread.getLooper();
        }
        mHandler = new Handler(looper);

        mContext = context;
        mBluetoothDevice = bluetoothDevice;
        mAddress = bluetoothDevice.getAddress();
        mLocalName = bluetoothDevice.getName();
        mBondState = BondState.valueOf(bluetoothDevice.getBondState());
        mAclConnectionState = AclConnectionState.Unknown;
        mGattConnectionState = GattConnectionState.Disconnected;
        mBluetoothGatt = null;

        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (null == bluetoothManager) {
            throw new AndroidRuntimeException("null == bluetoothManager");
        }
        int gattConnectionState = bluetoothManager.getConnectionState(bluetoothDevice, BluetoothProfile.GATT);
        if (BluetoothProfile.STATE_DISCONNECTED != gattConnectionState) {
            CBLog.w("Illegal onGattConnectionStateChanged state is BluetoothProfile.STATE_DISCONNECTED != gattConnectionState");
        }
    }

    @NonNull
    public final String getAddress() {
        return mAddress;
    }

    @Nullable
    public final String getLocalName() {
        return mLocalName;
    }

    final boolean isGattConnected() {
        return GattConnectionState.Connected == getGattConnectionState();
    }

    @NonNull
    final GattConnectionState getGattConnectionState() {
        final GattConnectionState ret;
        if (mHandler.isCurrentThread()) {
            ret = mGattConnectionState;
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mGattConnectionState);
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean isBonded() {
        return BondState.Bonded == getBondState();
    }

    @NonNull
    final BondState getBondState() {
        final BondState ret;
        if (mHandler.isCurrentThread()) {
            ret = mBondState;
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mBondState);
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final void setBondState(@NonNull final BondState state) {
        if (mHandler.isCurrentThread()) {
            mBondState = state;
            onBondStateChanged(mBondState);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBondState = state;
                    onBondStateChanged(mBondState);
                }
            });
        }
    }

    @NonNull
    final AclConnectionState getAclConnectionState() {
        final AclConnectionState ret;
        if (mHandler.isCurrentThread()) {
            ret = mAclConnectionState;
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mAclConnectionState);
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final void setAclConnectionState(@NonNull final AclConnectionState state) {
        if (mHandler.isCurrentThread()) {
            mAclConnectionState = state;
            onAclConnectionStateChanged(mAclConnectionState);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAclConnectionState = state;
                    onAclConnectionStateChanged(mAclConnectionState);
                }
            });
        }
    }

    final boolean isAclConnected() {
        return AclConnectionState.Connected == getAclConnectionState();
    }

    @NonNull
    final Context getContext() {
        return mContext;
    }

    @NonNull
    final Handler getHandler() {
        return mHandler;
    }

    final void notifyPairingRequest(@NonNull final CBConstants.PairingVariant pairingVariant) {
        if (mHandler.isCurrentThread()) {
            onPairingRequest(pairingVariant);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onPairingRequest(pairingVariant);
                }
            });
        }
    }

    final boolean createBond() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _createBond();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_createBond());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean cancelBondProcess() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _cancelBondProcess();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_cancelBondProcess());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean removeBond() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _removeBond();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_removeBond());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean setPairingConfirmation(final boolean enable) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _setPairingConfirmation(enable);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_setPairingConfirmation(enable));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean setPin(final String pinCode) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _setPin(pinCode);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_setPin(pinCode));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean setPasskey(final String pinCode) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _setPasskey(pinCode);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_setPasskey(pinCode));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean hasGatt() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = null != mBluetoothGatt;
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(null != mBluetoothGatt);
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean connectGatt() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _connectGatt();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_connectGatt());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean disconnectGatt() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _disconnectGatt();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_disconnectGatt());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean discoverServices() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _discoverServices();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_discoverServices());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean refreshGatt() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _refreshGatt();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_refreshGatt());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean closeGatt() {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _closeGatt();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_closeGatt());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    @NonNull
    final List<BluetoothGattService> getServices() {
        final List<BluetoothGattService> ret;
        if (mHandler.isCurrentThread()) {
            ret = _getServices();
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_getServices());
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean setCharacteristicNotification(@NonNull final BluetoothGattCharacteristic characteristic, final boolean enable) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _setCharacteristicNotification(characteristic, enable);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_setCharacteristicNotification(characteristic, enable));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean readCharacteristic(@NonNull final BluetoothGattCharacteristic characteristic) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _readCharacteristic(characteristic);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_readCharacteristic(characteristic));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean writeCharacteristic(@NonNull final BluetoothGattCharacteristic characteristic) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _writeCharacteristic(characteristic);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_writeCharacteristic(characteristic));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean readDescriptor(@NonNull final BluetoothGattDescriptor descriptor) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _readDescriptor(descriptor);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_readDescriptor(descriptor));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean writeDescriptor(@NonNull final BluetoothGattDescriptor descriptor) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _writeDescriptor(descriptor);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_writeDescriptor(descriptor));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    final boolean requestMtu(final int mtu) {
        final boolean ret;
        if (mHandler.isCurrentThread()) {
            ret = _requestMtu(mtu);
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(_requestMtu(mtu));
                    callback.unlock();
                }
            });
            callback.lock();
            ret = Types.autoCast(callback.getResult());
        }
        return ret;
    }

    private boolean _createBond() {
        boolean ret = false;
        CBLog.iOsApi("createBond() exec.");
        if (Build.VERSION_CODES.KITKAT > Build.VERSION.SDK_INT) {
            try {
                ret = (Boolean) Reflect.invokeMethod(mBluetoothDevice, "createBond", null, null);
            } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        } else {
            ret = mBluetoothDevice.createBond();
        }
        if (ret) {
            CBLog.d("createBond() called. ret=true");
        } else {
            CBLog.e("createBond() called. ret=false");
        }
        return ret;
    }

    private boolean _cancelBondProcess() {
        boolean ret = false;
        CBLog.iOsApi("cancelBondProcess() exec.");
        try {
            ret = (Boolean) Reflect.invokeMethod(mBluetoothDevice, "cancelBondProcess", null, null);
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        if (ret) {
            CBLog.d("cancelBondProcess() called. ret=true");
        } else {
            CBLog.e("cancelBondProcess() called. ret=false");
        }
        return ret;
    }

    private boolean _removeBond() {
        boolean ret = false;
        CBLog.iOsApi("removeBond() exec.");
        try {
            ret = (Boolean) Reflect.invokeMethod(mBluetoothDevice, "removeBond", null, null);
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        if (ret) {
            CBLog.d("removeBond() called. ret=true");
        } else {
            CBLog.e("removeBond() called. ret=false");
        }
        return ret;
    }

    private boolean _setPairingConfirmation(boolean enable) {
        boolean ret = false;
        CBLog.iOsApi("setPairingConfirmation(" + enable + ") exec.");
        if (Build.VERSION_CODES.KITKAT > Build.VERSION.SDK_INT) {
            try {
                ret = (Boolean) Reflect.invokeMethod(
                        mBluetoothDevice,
                        "setPairingConfirmation",
                        new Class<?>[]{boolean.class},
                        new Object[]{enable}
                );
            } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else if (Build.VERSION_CODES.N <= Build.VERSION.SDK_INT) {
            try {
                ret = mBluetoothDevice.setPairingConfirmation(enable);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
            ret = mBluetoothDevice.setPairingConfirmation(enable);
        }
        if (ret) {
            CBLog.d("setPairingConfirmation() called. ret=true");
        } else {
            CBLog.e("setPairingConfirmation() called. ret=false");
        }
        return ret;
    }

    private boolean _setPin(String pinCode) {
        boolean ret = false;
        byte[] pin = _convertPinToBytes(pinCode);
        if (null == pin) {
            CBLog.e("null == pin");
            return false;
        }

        CBLog.iOsApi("setPin(" + pinCode + ") exec.");
        if (Build.VERSION_CODES.KITKAT > Build.VERSION.SDK_INT) {
            try {
                ret = (Boolean) Reflect.invokeMethod(
                        mBluetoothDevice,
                        "setPin",
                        new Class<?>[]{byte[].class},
                        new Object[]{pin}
                );
            } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else {
            ret = mBluetoothDevice.setPin(pin);
        }
        if (ret) {
            CBLog.d("setPin() called. ret=true");
        } else {
            CBLog.e("setPin() called. ret=false");
        }
        return ret;
    }

    private boolean _setPasskey(String pinCode) {
        boolean ret = false;
        CBLog.iOsApi("setPasskey(" + pinCode + ") exec.");
        try {
            ByteBuffer converter = ByteBuffer.allocate(4);
            converter.order(ByteOrder.nativeOrder());
            converter.putInt(Integer.parseInt(pinCode));
            byte[] pin = converter.array();
            ret = (Boolean) Reflect.invokeMethod(
                    Reflect.invokeMethod(BluetoothDevice.class, "setPasskey", null, null),
                    "setPasskey",
                    new Class<?>[]{BluetoothDevice.class, boolean.class, int.class, byte[].class},
                    new Object[]{mBluetoothDevice, true, pin.length, pin}
            );
        } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
        if (ret) {
            CBLog.d("setPasskey() called. ret=true");
        } else {
            CBLog.e("setPasskey() called. ret=false");
        }
        return ret;
    }

    private boolean _connectGatt() {
        if (null != mBluetoothGatt) {
            CBLog.e("null != mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("connectGatt() exec.");
        mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false, mGattCallback);
        if (null != mBluetoothGatt) {
            CBLog.d("connectGatt() called. ret=Not Null");
        } else {
            CBLog.e("connectGatt() called. ret=Null");
        }
        return null != mBluetoothGatt;
    }

    private boolean _disconnectGatt() {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("disconnect() exec.");
        mBluetoothGatt.disconnect();
        CBLog.d("disconnect() called.");
        return true;
    }

    private boolean _discoverServices() {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("discoverServices() exec.");
        boolean ret = mBluetoothGatt.discoverServices();
        if (ret) {
            CBLog.d("discoverServices() called. ret=true");
        } else {
            CBLog.e("discoverServices() called. ret=false");
        }
        return ret;
    }

    private boolean _refreshGatt() {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        boolean ret = false;
        CBLog.iOsApi("refresh() exec.");
        try {
            ret = (Boolean) Reflect.invokeMethod(mBluetoothGatt, "refresh", null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (ret) {
            CBLog.d("refresh() called. ret=true");
        } else {
            CBLog.e("refresh() called. ret=false");
        }
        return ret;
    }

    private boolean _closeGatt() {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("close() exec.");
        mBluetoothGatt.close();
        CBLog.d("close() called.");
        mBluetoothGatt = null;
        return true;
    }

    @NonNull
    private List<BluetoothGattService> _getServices() {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return new ArrayList<>();
        }
        CBLog.iOsApi("getServices() exec.");
        List<BluetoothGattService> ret = mBluetoothGatt.getServices();
        if (null == ret) {
            CBLog.e("getServices() called. ret=Null");
            return new ArrayList<>();
        }
        if (0 == ret.size()) {
            CBLog.d("getServices() called. ret.size=0");
        } else {
            CBLog.d("getServices() called. ret=Not Null");
        }
        return ret;
    }

    private boolean _setCharacteristicNotification(@NonNull final BluetoothGattCharacteristic characteristic, boolean enable) {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("setCharacteristicNotification(" + characteristic.getUuid().toString() + ", " + enable + ") exec.");
        boolean ret = mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
        if (ret) {
            CBLog.d("setCharacteristicNotification() called. ret=true");
        } else {
            CBLog.e("setCharacteristicNotification() called. ret=false");
        }
        return ret;
    }

    private boolean _readCharacteristic(@NonNull final BluetoothGattCharacteristic characteristic) {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("readCharacteristic(" + characteristic.getUuid().toString() + ") exec.");
        boolean ret = mBluetoothGatt.readCharacteristic(characteristic);
        if (ret) {
            CBLog.d("readCharacteristic() called. ret=true");
        } else {
            CBLog.e("readCharacteristic() called. ret=false");
        }
        return ret;
    }

    private boolean _writeCharacteristic(@NonNull final BluetoothGattCharacteristic characteristic) {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("writeCharacteristic(" + characteristic.getUuid().toString() + ") exec.");
        CBLog.iOsApi("raw data : " + Bytes.toHexString(characteristic.getValue()));
        boolean ret = mBluetoothGatt.writeCharacteristic(characteristic);
        if (ret) {
            CBLog.d("writeCharacteristic() called. ret=true");
        } else {
            CBLog.e("writeCharacteristic() called. ret=false");
        }
        return ret;
    }

    private boolean _readDescriptor(@NonNull final BluetoothGattDescriptor descriptor) {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("readDescriptor(" + descriptor.getCharacteristic().getUuid().toString() + ", " +
                descriptor.getUuid().toString() + ") exec.");
        boolean ret = mBluetoothGatt.readDescriptor(descriptor);
        if (ret) {
            CBLog.d("readDescriptor() called. ret=true");
        } else {
            CBLog.e("readDescriptor() called. ret=false");
        }
        return ret;
    }

    private boolean _writeDescriptor(@NonNull final BluetoothGattDescriptor descriptor) {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        CBLog.iOsApi("writeDescriptor(" + descriptor.getCharacteristic().getUuid().toString() + ", " +
                descriptor.getUuid().toString() + ") exec.");
        boolean ret = mBluetoothGatt.writeDescriptor(descriptor);
        if (ret) {
            CBLog.d("writeDescriptor() called. ret=true");
        } else {
            CBLog.e("writeDescriptor() called. ret=false");
        }
        return ret;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean _requestMtu(int mtu) {
        if (null == mBluetoothGatt) {
            CBLog.e("null == mBluetoothGatt");
            return false;
        }
        if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT) {
            CBLog.e("VERSION_CODES.LOLLIPOP > VERSION.SDK_INT");
            return false;
        }
        CBLog.iOsApi("requestMtu(" + mtu + ") exec.");
        boolean ret = mBluetoothGatt.requestMtu(mtu);
        if (ret) {
            CBLog.d("requestMtu() called. ret=true");
        } else {
            CBLog.e("requestMtu() called. ret=false");
        }
        return ret;
    }

    private byte[] _convertPinToBytes(String pin) {
        byte[] ret = null;
        try {
            Class<?>[] types = {
                    String.class
            };
            Object[] args = {
                    pin
            };
            ret = (byte[]) Reflect.invokeMethod(mBluetoothDevice, "convertPinToBytes", types, args);
        } catch (IllegalAccessException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private void _onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        final GattConnectionState gattConnectionState = GattConnectionState.valueOf(newState);
        CBLog.iOsApi("Received " + gattConnectionState.name() + " of " + mAddress + ". status:" +
                String.format(Locale.US, "status=%d(0x%02x)", status, status));
        if (CBStatusCode.GATT_SUCCESS != status) {
            CBLog.e(String.format(Locale.US, "newState=%d status=%d(0x%02x)", newState, status, status));
        }
        mGattConnectionState = gattConnectionState;
        onGattConnectionStateChanged(mGattConnectionState, status);
    }

    private void _onServicesDiscovered(BluetoothGatt gatt, int status) {
        CBLog.iOsApi(String.format(Locale.US, "status=%d(0x%02x)", status, status));
        if (CBStatusCode.GATT_SUCCESS != status) {
            CBLog.e(String.format(Locale.US, "status=%d(0x%02x)", status, status));
        }
        onServicesDiscovered(status);
    }

    private void _onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        CBLog.iOsApi(characteristic.getUuid().toString() + " " +
                String.format(Locale.US, "status=%d(0x%02x)", status, status));
        if (CBStatusCode.GATT_SUCCESS != status) {
            CBLog.e(String.format(Locale.US, "status=%d(0x%02x)", status, status));
        } else {
            if (null != characteristic.getValue()) {
                CBLog.iOsApi("raw data : " + Bytes.toHexString(characteristic.getValue()));
            }
        }
        onCharacteristicRead(characteristic, status);
    }

    private void _onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        CBLog.iOsApi(characteristic.getUuid().toString() + " " +
                String.format(Locale.US, "status=%d(0x%02x)", status, status));
        if (CBStatusCode.GATT_SUCCESS != status) {
            CBLog.e(String.format(Locale.US, "status=%d(0x%02x)", status, status));
        }
        onCharacteristicWrite(characteristic, status);
    }

    private void _onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        CBLog.iOsApi(characteristic.getUuid().toString() + " raw data : " + Bytes.toHexString(characteristic.getValue()));
        onCharacteristicChanged(characteristic);
    }

    private void _onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        CBLog.iOsApi(descriptor.getCharacteristic().getUuid().toString() + " " +
                descriptor.getUuid().toString() + " " +
                String.format(Locale.US, "status=%d(0x%02x)", status, status));
        if (CBStatusCode.GATT_SUCCESS != status) {
            CBLog.e(String.format(Locale.US, "status=%d(0x%02x)", status, status));
        }
        onDescriptorRead(descriptor, status);
    }

    private void _onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
        CBLog.iOsApi(descriptor.getCharacteristic().getUuid().toString() + " " +
                descriptor.getUuid().toString() + " " +
                String.format(Locale.US, "status=%d(0x%02x)", status, status));
        if (CBStatusCode.GATT_SUCCESS != status) {
            CBLog.e(String.format(Locale.US, "status=%d(0x%02x)", status, status));
        }
        onDescriptorWrite(descriptor, status);
    }

    private void _onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        CBLog.iOsApi(String.format(Locale.US, "status=%d(0x%02x)", status, status));
        onReliableWriteCompleted(status);
    }

    private void _onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        CBLog.iOsApi("rssi=" + rssi + " " + String.format(Locale.US, "status=%d(0x%02x) ", status, status));
        onReadRemoteRssi(rssi, status);
    }

    private void _onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        CBLog.iOsApi("mtu=" + mtu + " " + String.format(Locale.US, "status=%d(0x%02x) ", status, status));
        onMtuChanged(mtu, status);
    }

    protected abstract void onPairingRequest(@NonNull CBConstants.PairingVariant variant);

    protected abstract void onBondStateChanged(@NonNull BondState bondState);

    protected abstract void onAclConnectionStateChanged(@NonNull AclConnectionState aclConnectionState);

    protected abstract void onGattConnectionStateChanged(@NonNull GattConnectionState gattConnectionState, int status);

    protected abstract void onServicesDiscovered(int status);

    protected abstract void onCharacteristicRead(@NonNull BluetoothGattCharacteristic characteristic, int status);

    protected abstract void onCharacteristicWrite(@NonNull BluetoothGattCharacteristic characteristic, int status);

    protected abstract void onCharacteristicChanged(@NonNull BluetoothGattCharacteristic characteristic);

    protected abstract void onDescriptorRead(@NonNull BluetoothGattDescriptor descriptor, int status);

    protected abstract void onDescriptorWrite(@NonNull BluetoothGattDescriptor descriptor, int status);

    protected abstract void onReliableWriteCompleted(int status);

    protected abstract void onReadRemoteRssi(int rssi, int status);

    protected abstract void onMtuChanged(int mtu, int status);

    public enum BondState {
        None(BluetoothDevice.BOND_NONE),
        Bonding(BluetoothDevice.BOND_BONDING),
        Bonded(BluetoothDevice.BOND_BONDED);
        private int value;

        BondState(int value) {
            this.value = value;
        }

        static BondState valueOf(int value) {
            for (BondState type : values()) {
                if (type.value() == value) {
                    return type;
                }
            }
            return None;
        }

        int value() {
            return value;
        }
    }

    public enum AclConnectionState {
        Disconnected,
        Connected,
        Unknown
    }

    public enum GattConnectionState {
        Disconnected(BluetoothProfile.STATE_DISCONNECTED),
        Connected(BluetoothProfile.STATE_CONNECTED);
        private int value;

        GattConnectionState(int value) {
            this.value = value;
        }

        static GattConnectionState valueOf(int value) {
            for (GattConnectionState type : values()) {
                if (type.value() == value) {
                    return type;
                }
            }
            return Disconnected;
        }

        int value() {
            return value;
        }
    }
}
