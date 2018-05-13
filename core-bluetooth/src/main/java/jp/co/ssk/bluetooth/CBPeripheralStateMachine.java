package jp.co.ssk.bluetooth;

import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.List;

import jp.co.ssk.utility.SynchronizeCallback;
import jp.co.ssk.utility.Types;
import jp.co.ssk.utility.sm.State;
import jp.co.ssk.utility.sm.StateMachine;

final class CBPeripheralStateMachine extends StateMachine {

    private static final EnumMap<CBPeripheralDetailedState, CBPeripheralState> sStateMap = new EnumMap<>(CBPeripheralDetailedState.class);
    private static final int EVT_BASE = 0x10000000;
    private static final int EVT_CONNECT = EVT_BASE + 0x0002;
    private static final int EVT_CANCEL_CONNECTION = EVT_BASE + 0x0003;
    private static final int EVT_PAIRING_REQUEST = EVT_BASE + 0x1001;
    private static final int EVT_BONDED = EVT_BASE + 0x1002;
    private static final int EVT_BONDING = EVT_BASE + 0x1003;
    private static final int EVT_BOND_NONE = EVT_BASE + 0x1004;
    private static final int EVT_ACL_CONNECTED = EVT_BASE + 0x1005;
    private static final int EVT_ACL_DISCONNECTED = EVT_BASE + 0x1006;
    private static final int EVT_GATT_CONNECTED = EVT_BASE + 0x1007;
    private static final int EVT_GATT_DISCONNECTED = EVT_BASE + 0x1008;
    private static final int EVT_DISCOVER_SERVICE_SUCCESS = EVT_BASE + 0x1009;
    private static final int EVT_DISCOVER_SERVICE_FAILURE = EVT_BASE + 0x100a;
    private static final int LOCAL_EVT_BASE = 0xf0000000;

    static {
        sStateMap.put(CBPeripheralDetailedState.Unconnected, CBPeripheralState.Disconnected);
        sStateMap.put(CBPeripheralDetailedState.Disconnected, CBPeripheralState.Disconnected);
        sStateMap.put(CBPeripheralDetailedState.ConnectionCanceled, CBPeripheralState.Disconnected);
        sStateMap.put(CBPeripheralDetailedState.ConnectionFailed, CBPeripheralState.Disconnected);
        sStateMap.put(CBPeripheralDetailedState.GattConnecting, CBPeripheralState.Connecting);
        sStateMap.put(CBPeripheralDetailedState.ServiceDiscovering, CBPeripheralState.Connecting);
        sStateMap.put(CBPeripheralDetailedState.ConnectCanceling, CBPeripheralState.Connecting);
        sStateMap.put(CBPeripheralDetailedState.CleanupConnection, CBPeripheralState.Connecting);
        sStateMap.put(CBPeripheralDetailedState.ConnectionRetryReady, CBPeripheralState.Connecting);
        sStateMap.put(CBPeripheralDetailedState.Connected, CBPeripheralState.Connected);
        sStateMap.put(CBPeripheralDetailedState.Disconnecting, CBPeripheralState.Disconnecting);
    }

    private final State mDisconnectedState = new DisconnectedState();
    private final State mConnectionCanceledState = new ConnectionCanceledState();
    private final State mConnectionFailedState = new ConnectionFailedState();
    private final State mGattConnectingState = new GattConnectingState();
    private final State mServiceDiscoveringState = new ServiceDiscoveringState();
    private final State mConnectCancelingState = new ConnectCancelingState();
    private final State mCleanupConnectionState = new CleanupConnectionState();
    private final State mConnectionRetryReadyState = new ConnectionRetryReadyState();
    private final State mConnectedState = new ConnectedState();
    private final State mDisconnectingState = new DisconnectingState();
    @NonNull
    private final WeakReference<CBPeripheral> mPeripheralRef;
    @NonNull
    private final EventListener mEventListener;
    @NonNull
    private final CBConfig mConfig;
    private int mConnectionRetryCount;
    private boolean mIsShowPairingDialog;
    @NonNull
    private CBPeripheralState mState;
    @NonNull
    private CBPeripheralDetailedState mDetailedState;

    CBPeripheralStateMachine(
            @NonNull CBPeripheral peripheral,
            @NonNull EventListener eventListener,
            @NonNull Looper looper) {
        super(CBPeripheralStateMachine.class.getSimpleName(), looper);

        mPeripheralRef = new WeakReference<>(peripheral);
        mEventListener = eventListener;
        mConfig = new CBConfig();

        final State defaultState = new DefaultState();
        final State unconnectedState = new UnconnectedState();
        final State connectingState = new ConnectingState();

        addState(defaultState);

        addState(unconnectedState, defaultState);
        addState(mDisconnectedState, unconnectedState);
        addState(mConnectionCanceledState, unconnectedState);
        addState(mConnectionFailedState, unconnectedState);

        addState(connectingState, defaultState);
        addState(mGattConnectingState, connectingState);
        addState(mServiceDiscoveringState, connectingState);
        addState(mConnectCancelingState, connectingState);
        addState(mCleanupConnectionState, connectingState);
        addState(mConnectionRetryReadyState, connectingState);

        addState(mConnectedState, defaultState);
        addState(mDisconnectingState, defaultState);

        mState = CBPeripheralState.Disconnected;
        mDetailedState = CBPeripheralDetailedState.Unconnected;
        setInitialState(unconnectedState);

        setTag(CBLog.TAG);
        setDbg(CBLog.OUTPUT_LOG_ENABLED);
        start();
    }

    boolean isConnected() {
        return CBPeripheralState.Connected == getState();
    }

    CBPeripheralState getState() {
        final CBPeripheralState state;
        if (getHandler().isCurrentThread()) {
            state = mState;
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mState);
                    callback.unlock();
                }
            });
            callback.lock();
            state = (CBPeripheralState) callback.getResult();
        }
        return state;
    }

    CBPeripheralDetailedState getDetailedState() {
        final CBPeripheralDetailedState state;
        if (getHandler().isCurrentThread()) {
            state = mDetailedState;
        } else {
            final SynchronizeCallback callback = new SynchronizeCallback();
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.setResult(mDetailedState);
                    callback.unlock();
                }
            });
            callback.lock();
            state = (CBPeripheralDetailedState) callback.getResult();
        }
        return state;
    }

    @NonNull
    Bundle getConfig(@Nullable final List<CBConfig.Key> keys) {
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
        }
        return config;
    }

    void setConfig(@NonNull final Bundle config) {
        if (getHandler().isCurrentThread()) {
            mConfig.set(config);
        } else {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mConfig.set(config);
                }
            });
        }
    }

    void connect() {
        sendMessage(EVT_CONNECT);
    }

    void cancelConnection() {
        sendMessage(EVT_CANCEL_CONNECTION);
    }

    void onPairingRequest(@NonNull final CBConstants.PairingVariant variant) {
        sendMessage(EVT_PAIRING_REQUEST, variant);
    }

    void onBondStateChanged(@NonNull AndroidPeripheral.BondState newState) {
        switch (newState) {
            case Bonded:
                sendMessage(EVT_BONDED);
                break;
            case Bonding:
                sendMessage(EVT_BONDING);
                break;
            case None:
                sendMessage(EVT_BOND_NONE);
                break;
        }
    }

    void onAclConnectionStateChanged(@NonNull AndroidPeripheral.AclConnectionState newState) {
        switch (newState) {
            case Connected:
                sendMessage(EVT_ACL_CONNECTED);
                break;
            case Disconnected:
                sendMessage(EVT_ACL_DISCONNECTED);
                break;
        }
    }

    void onGattConnectionStateChanged(@NonNull AndroidPeripheral.GattConnectionState newState, int status) {
        switch (newState) {
            case Connected:
                sendMessage(EVT_GATT_CONNECTED);
                break;
            case Disconnected:
                sendMessage(EVT_GATT_DISCONNECTED, status);
                break;
        }
    }

    void onServicesDiscovered(int status) {
        if (CBStatusCode.GATT_SUCCESS == status) {
            sendMessage(EVT_DISCOVER_SERVICE_SUCCESS);
        } else {
            sendMessage(EVT_DISCOVER_SERVICE_FAILURE, status);
        }
    }

    private void _assistPairingDialogIfNeeded(@NonNull CBPeripheral peripheral) {
        if (!mConfig.isAssistPairingDialogEnabled()) {
            return;
        }
        // Show pairing dialog mandatory.
        // The app calls start discovery and cancel interface so that app will show pairing dialog each time
        // based on specification that Android O/S shows the dialog when the app pairs with a device
        // within 60 seconds after cancel discovery.
        BluetoothManager bluetoothManager = (BluetoothManager) peripheral.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (null != bluetoothManager) {
            bluetoothManager.getAdapter().startDiscovery();
            bluetoothManager.getAdapter().cancelDiscovery();
        }
    }

    private void _autoPairingIfNeeded(@NonNull CBPeripheral peripheral, CBConstants.PairingVariant variant) {
        switch (variant) {
            case Pin:
            case Pin16Digits:
                if (mConfig.isAutoEnterThePinCodeEnabled() && !mConfig.getPinCode().isEmpty()) {
                    peripheral.setPin(mConfig.getPinCode());
                }
                break;
            case Passkey:
                if (mConfig.isAutoEnterThePinCodeEnabled() && !mConfig.getPinCode().isEmpty()) {
                    peripheral.setPasskey(mConfig.getPinCode());
                }
                break;
            case PasskeyConfirmation:
                if (mConfig.isAutoPairingEnabled()) {
                    peripheral.setPairingConfirmation(true);
                }
                break;
            case Consent:
                if (mConfig.isAutoPairingEnabled()) {
                    peripheral.setPairingConfirmation(true);
                }
                break;
            case DisplayPasskey:
                break;
            case DisplayPin:
                break;
            case OobConsent:
                break;
            default:
                break;
        }
    }

    private void _setState(@NonNull CBPeripheralState state) {
        if (mState == state) {
            return;
        }
        mState = state;
        mEventListener.onStateChanged(state);
    }

    private void _setDetailedState(final @NonNull CBPeripheralDetailedState detailedState) {
        if (mDetailedState == detailedState) {
            return;
        }
        _setState(sStateMap.get(detailedState));
        mDetailedState = detailedState;
        mEventListener.onDetailedStateChanged(detailedState);
    }

    interface EventListener {

        void didConnect();

        void didFailToConnect();

        void didDisconnectPeripheral();

        void onStateChanged(@NonNull CBPeripheralState newState);

        void onDetailedStateChanged(@NonNull CBPeripheralDetailedState newState);
    }

    private class DefaultState extends State {
        @Override
        public boolean processMessage(@NonNull Message msg) {
            return StateMachine.HANDLED;
        }
    }

    private class UnconnectedState extends State {
        public void enter(Object[] transferObjects) {
            if (mPeripheralRef.get().hasGatt()) {
                if (mConfig.isUseRefreshWhenDisconnect()) {
                    mPeripheralRef.get().refreshGatt();
                }
                mPeripheralRef.get().closeGatt();
            }
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_CONNECT:
                    transitionTo(mGattConnectingState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }
    }

    private class DisconnectedState extends State {
        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.Disconnected);
            mEventListener.didDisconnectPeripheral();
        }
    }

    private class ConnectionCanceledState extends State {
        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.ConnectionCanceled);
            mEventListener.didFailToConnect();
        }
    }

    private class ConnectionFailedState extends State {
        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.ConnectionFailed);
            mEventListener.didFailToConnect();
        }
    }

    private class ConnectingState extends State {
        @Override
        public void enter(Object[] transferObjects) {
            mConnectionRetryCount = 0;
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_CANCEL_CONNECTION:
                    transitionTo(mConnectCancelingState);
                    break;
                case EVT_BOND_NONE:
                case EVT_BONDING:
                case EVT_GATT_DISCONNECTED:
                    transitionToCleanupState();
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private void transitionToCleanupState() {
            if (mIsShowPairingDialog) {
                // No retry when connection failed in showing pairing dialog.
                // ex) Select [Cancel] / Invalid PIN input
                CBLog.w("Pairing canceled or timeout or invalid PIN input.");
                Object[] transferObjects = {CleanupConnectionState.NOT_RETRY, CleanupConnectionState.NOT_REMOVE_BOND};
                transitionTo(mCleanupConnectionState, transferObjects);
            } else {
                // Retry when unexpected connection failed.
                CBLog.e("Connection failed.");
                Object[] transferObjects = {CleanupConnectionState.RETRY, CleanupConnectionState.NOT_REMOVE_BOND};
                transitionTo(mCleanupConnectionState, transferObjects);
            }
        }
    }

    private class GattConnectingState extends State {

        private static final int EVT_TIMEOUT = LOCAL_EVT_BASE + 0x0001;
        private static final int EVT_CONNECTION_STABLED = LOCAL_EVT_BASE + 0x0002;
        private static final int EVT_REQUIRE_CREATE_BOND = LOCAL_EVT_BASE + 0x0003;
        private static final int EVT_REQUIRE_CONNECT_GATT = LOCAL_EVT_BASE + 0x0004;
        private static final int EVT_ERROR = LOCAL_EVT_BASE + 0x0005;

        private static final long TIMEOUT_MS = 15 * 1000;

        private boolean mNeedPairing;

        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.GattConnecting);
            mIsShowPairingDialog = false;
            mNeedPairing = false;
            _assistPairingDialogIfNeeded(mPeripheralRef.get());
            if (CBConfig.CreateBondOption.UsedBeforeGattConnection == mConfig.getCreateBondOption() && !mPeripheralRef.get().isBonded()) {
                mNeedPairing = true;
                sendMessage(EVT_REQUIRE_CREATE_BOND);
            }
            sendMessage(EVT_REQUIRE_CONNECT_GATT);
            sendMessageDelayed(EVT_TIMEOUT, TIMEOUT_MS);
        }

        @Override
        public void exit() {
            removeMessages(EVT_TIMEOUT);
            removeMessages(EVT_CONNECTION_STABLED);
            removeMessages(EVT_REQUIRE_CREATE_BOND);
            removeMessages(EVT_REQUIRE_CONNECT_GATT);
            removeMessages(EVT_ERROR);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_REQUIRE_CREATE_BOND:
                    if (!mPeripheralRef.get().createBond()) {
                        sendMessage(EVT_ERROR);
                    }
                    break;
                case EVT_REQUIRE_CONNECT_GATT:
                    if (!mPeripheralRef.get().connectGatt()) {
                        sendMessage(EVT_ERROR);
                    }
                    break;
                case EVT_PAIRING_REQUEST:
                    removeMessages(EVT_TIMEOUT);
                    mIsShowPairingDialog = true;
                    _autoPairingIfNeeded(mPeripheralRef.get(), (CBConstants.PairingVariant) msg.obj);
                    break;
                case EVT_GATT_CONNECTED:
                    long stableConnectionWaitTime = 0;
                    if (mConfig.isStableConnectionEnabled()) {
                        stableConnectionWaitTime = mConfig.getStableConnectionWaitTime();
                    }
                    sendMessageDelayed(EVT_CONNECTION_STABLED, stableConnectionWaitTime);
                    break;
                case EVT_BONDING:
                    mNeedPairing = true;
                    break;
                case EVT_BONDED:
                    mIsShowPairingDialog = false;
                    transitionToNextStateIfConnectionStabled();
                    break;
                case EVT_CONNECTION_STABLED:
                    transitionToNextStateIfConnectionStabled();
                    break;
                case EVT_ERROR: {
                    Object[] objects = {CleanupConnectionState.RETRY, CleanupConnectionState.NOT_REMOVE_BOND};
                    transitionTo(mCleanupConnectionState, objects);
                    break;
                }
                case EVT_TIMEOUT: {
                    if (!hasMessage(EVT_CONNECTION_STABLED)) {
                        CBLog.e("Gatt connection timeout.");
                        Object[] objects = {CleanupConnectionState.RETRY, CleanupConnectionState.REMOVE_BOND};
                        transitionTo(mCleanupConnectionState, objects);
                    }
                    break;
                }
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private void transitionToNextStateIfConnectionStabled() {
            if (!mPeripheralRef.get().isGattConnected()) {
                CBLog.i("Gatt connecting.");
                return;
            }
            if (mNeedPairing && !mPeripheralRef.get().isBonded()) {
                CBLog.i("Wait bonded.");
                return;
            }
            if (hasMessage(EVT_CONNECTION_STABLED)) {
                CBLog.i("Wait connection stabled.");
                return;
            }
            CBLog.i("Gatt connection completed.");
            transitionTo(mServiceDiscoveringState);
        }
    }

    private class ServiceDiscoveringState extends State {

        private static final int EVT_START = LOCAL_EVT_BASE + 0x0001;
        private static final int EVT_EXEC = LOCAL_EVT_BASE + 0x0002;
        private static final int EVT_TIMEOUT = LOCAL_EVT_BASE + 0x0003;

        private static final long TIMEOUT_MS = 30 * 1000;
        private static final long EXEC_INTERVAL = 5 * 1000;

        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.ServiceDiscovering);
            List<BluetoothGattService> services = mPeripheralRef.get().getServices();
            if (0 == services.size()) {
                sendMessage(EVT_START);
            } else {
                sendMessage(EVT_DISCOVER_SERVICE_SUCCESS);
            }
        }

        @Override
        public void exit() {
            removeMessages(EVT_START);
            removeMessages(EVT_EXEC);
            removeMessages(EVT_TIMEOUT);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_START:
                    sendMessageDelayed(EVT_TIMEOUT, TIMEOUT_MS);
                    sendMessage(EVT_EXEC);
                    break;
                case EVT_EXEC:
                    mPeripheralRef.get().discoverServices();
                    sendMessageDelayed(EVT_EXEC, EXEC_INTERVAL);
                    break;
                case EVT_DISCOVER_SERVICE_SUCCESS:
                    removeMessages(EVT_TIMEOUT);
                    removeMessages(EVT_EXEC);
                    if (verifyServices()) {
                        CBLog.i("Discover service success.");
                        transitionTo(mConnectedState);
                    } else {
                        CBLog.e("Verify services failed.");
                        Object[] objects = {CleanupConnectionState.RETRY, CleanupConnectionState.REMOVE_BOND};
                        transitionTo(mCleanupConnectionState, objects);
                    }
                    break;
                case EVT_DISCOVER_SERVICE_FAILURE: {
                    CBLog.e("Discover service failure.");
                    Object[] objects = {CleanupConnectionState.RETRY, CleanupConnectionState.REMOVE_BOND};
                    transitionTo(mCleanupConnectionState, objects);
                    break;
                }
                case EVT_TIMEOUT: {
                    removeMessages(EVT_EXEC);
                    CBLog.e("Discover service timeout.");
                    Object[] objects = {CleanupConnectionState.RETRY, CleanupConnectionState.REMOVE_BOND};
                    transitionTo(mCleanupConnectionState, objects);
                    break;
                }
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean verifyServices() {
            List<BluetoothGattService> services = mPeripheralRef.get().getServices();
            if (0 >= services.size()) {
                CBLog.e("0 >= services.size()");
                return false;
            }
            for (BluetoothGattService service : services) {
                if (null == service.getCharacteristics()) {
                    CBLog.e("null == service.getCharacteristics()");
                    return false;
                } else if (0 >= service.getCharacteristics().size()) {
                    CBLog.e("0 >= service.getCharacteristics().size()");
                    return false;
                }
            }
            return true;
        }
    }

    private class ConnectCancelingState extends State {

        private static final int EVT_CONNECT_CANCEL_TIMEOUT = LOCAL_EVT_BASE + 0x0001;
        private static final long CONNECT_CANCEL_WAIT_TIME = 15 * 1000;

        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.ConnectCanceling);
            teardownOrTransitionToNextState();
            sendMessageDelayed(EVT_CONNECT_CANCEL_TIMEOUT, CONNECT_CANCEL_WAIT_TIME);
        }

        @Override
        public void exit() {
            removeMessages(EVT_CONNECT_CANCEL_TIMEOUT);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_BOND_NONE:
                case EVT_GATT_DISCONNECTED:
                    teardownOrTransitionToNextState();
                    break;
                case EVT_CONNECT_CANCEL_TIMEOUT:
                    CBLog.e("Connect cancel timeout.");
                    // There are cases when timeout has occurred without notification of
                    // ACL Disconnected or Bond None and move to next state in theses cases.
                    transitionTo(mConnectionCanceledState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean isTeardownCompleted() {
            if (mPeripheralRef.get().isGattConnected()) {
                CBLog.i("Gatt disconnecting.");
                return false;
            }
            if (CBPeripheral.BondState.Bonding == mPeripheralRef.get().getBondState()) {
                CBLog.i("Bond process canceling.");
                return false;
            }
            CBLog.i("Teardown completed.");
            return true;
        }

        private void teardownOrTransitionToNextState() {
            if (isTeardownCompleted()) {
                transitionTo(mConnectionCanceledState);
            } else {
                if (mPeripheralRef.get().isGattConnected()) {
                    mPeripheralRef.get().disconnectGatt();
                } else if (CBPeripheral.BondState.Bonding == mPeripheralRef.get().getBondState()) {
                    mPeripheralRef.get().cancelBondProcess();
                }
            }
        }
    }

    private class CleanupConnectionState extends State {

        static final int NOT_RETRY = 0;
        static final int RETRY = 1;
        static final int NOT_REMOVE_BOND = 0;
        static final int REMOVE_BOND = 1;

        private static final int EVT_GATT_CLOSED = LOCAL_EVT_BASE + 0x0001;
        private static final int EVT_CLEANUP_TIMEOUT = LOCAL_EVT_BASE + 0x0002;

        private static final long CLEANUP_WAIT_TIME = 15 * 1000;

        private boolean mNeededRetry;
        private boolean mNeededRemoveBond;

        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.CleanupConnection);
            int retry = Types.autoCast(transferObjects[0]);
            int removeBond = Types.autoCast(transferObjects[0]);
            mNeededRetry = (RETRY == retry);
            mNeededRemoveBond = (REMOVE_BOND == removeBond);
            cleanupOrTransitionToNextState();
            sendMessageDelayed(EVT_CLEANUP_TIMEOUT, CLEANUP_WAIT_TIME);
        }

        @Override
        public void exit() {
            removeMessages(EVT_GATT_CLOSED);
            removeMessages(EVT_CLEANUP_TIMEOUT);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_BOND_NONE:
                case EVT_GATT_DISCONNECTED:
                case EVT_GATT_CLOSED:
                    cleanupOrTransitionToNextState();
                    break;
                case EVT_CLEANUP_TIMEOUT:
                    transitionTo(mConnectionFailedState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean isCleanupCompleted() {
            if (mPeripheralRef.get().isGattConnected()) {
                CBLog.i("Gatt disconnecting.");
                return false;
            }
            if (CBPeripheral.BondState.Bonding == mPeripheralRef.get().getBondState()) {
                CBLog.i("Bond process canceling.");
                return false;
            }
            if (mPeripheralRef.get().hasGatt()) {
                CBLog.i("Gatt closing.");
                return false;
            }
            if (mNeededRemoveBond && CBPeripheral.BondState.None != mPeripheralRef.get().getBondState()) {
                CBLog.i("Bond removing.");
                return false;
            }
            CBLog.i("Cleanup completed.");
            return true;
        }

        private void cleanup() {
            if (mPeripheralRef.get().isGattConnected()) {
                mPeripheralRef.get().disconnectGatt();
            } else if (CBPeripheral.BondState.Bonding == mPeripheralRef.get().getBondState()) {
                mPeripheralRef.get().cancelBondProcess();
            } else if (mPeripheralRef.get().hasGatt()) {
                if (mConfig.isUseRefreshWhenDisconnect()) {
                    mPeripheralRef.get().refreshGatt();
                }
                mPeripheralRef.get().closeGatt();
                sendMessage(EVT_GATT_CLOSED);
            } else if (CBPeripheral.BondState.None != mPeripheralRef.get().getBondState() && mNeededRetry) {
                mPeripheralRef.get().removeBond();
            }
        }

        private void cleanupOrTransitionToNextState() {
            if (!isCleanupCompleted()) {
                cleanup();
                return;
            }
            if (mNeededRetry && mConfig.isConnectionRetryEnabled()) {
                transitionTo(mConnectionRetryReadyState);
            } else {
                CBLog.w("Connection finished because not request a retry.");
                transitionTo(mConnectionFailedState);
            }
        }
    }

    private class ConnectionRetryReadyState extends State {

        private static final int EVT_RETRY = LOCAL_EVT_BASE + 0x0001;

        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.ConnectionRetryReady);
            if (CBConfig.RETRY_UNLIMITED == mConfig.getConnectionRetryCount() ||
                    mConnectionRetryCount < mConfig.getConnectionRetryCount()) {
                mConnectionRetryCount++;
                sendMessageDelayed(EVT_RETRY, mConfig.getConnectionRetryDelayTime());
            } else {
                CBLog.e("Connection failed because retry count reaches the maximum value.");
                transitionTo(mConnectionFailedState);
            }
        }

        @Override
        public void exit() {
            removeMessages(EVT_RETRY);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_RETRY: {
                    CBLog.w("Connection retry. count:" + mConnectionRetryCount);
                    transitionTo(mGattConnectingState);
                    break;
                }
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }
    }

    private class ConnectedState extends State {

        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.Connected);
            mEventListener.didConnect();
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_CONNECT:
                    mEventListener.didFailToConnect();
                    break;
                case EVT_CANCEL_CONNECTION:
                    transitionTo(mDisconnectingState);
                    break;
                case EVT_BOND_NONE:
                case EVT_GATT_DISCONNECTED:
                    transitionTo(mDisconnectingState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }
    }

    private class DisconnectingState extends State {

        private static final int EVT_DISCONNECTION_TIMEOUT = LOCAL_EVT_BASE + 0x0001;
        private static final long DISCONNECTION_WAIT_TIME = 15 * 1000;

        @Override
        public void enter(Object[] transferObjects) {
            _setDetailedState(CBPeripheralDetailedState.Disconnecting);
            teardownOrTransitionToNextState();
            sendMessageDelayed(EVT_DISCONNECTION_TIMEOUT, DISCONNECTION_WAIT_TIME);
        }

        @Override
        public void exit() {
            removeMessages(EVT_DISCONNECTION_TIMEOUT);
        }

        @Override
        public boolean processMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVT_CONNECT:
                    mEventListener.didFailToConnect();
                    break;
                case EVT_BOND_NONE:
                case EVT_GATT_DISCONNECTED:
                    teardownOrTransitionToNextState();
                    break;
                case EVT_DISCONNECTION_TIMEOUT:
                    CBLog.e("Disconnection timeout.");
                    // There are cases when timeout has occurred without notification of
                    // ACL Disconnected or Bond None and move to next state in theses cases.
                    transitionTo(mDisconnectedState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean isTeardownCompleted() {
            if (mPeripheralRef.get().isGattConnected()) {
                CBLog.i("Gatt disconnecting.");
                return false;
            }
            if (CBPeripheral.BondState.Bonding == mPeripheralRef.get().getBondState()) {
                CBLog.i("Bond process canceling.");
                return false;
            }
            CBLog.i("Teardown completed.");
            return true;
        }

        private void teardownOrTransitionToNextState() {
            if (isTeardownCompleted()) {
                transitionTo(mDisconnectedState);
            } else {
                if (mPeripheralRef.get().isGattConnected()) {
                    mPeripheralRef.get().disconnectGatt();
                } else if (CBPeripheral.BondState.Bonding == mPeripheralRef.get().getBondState()) {
                    mPeripheralRef.get().cancelBondProcess();
                }
            }
        }
    }
}
