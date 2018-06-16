package jp.co.ssk.bluetooth;

import android.bluetooth.BluetoothGattService;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.List;

import jp.co.ssk.sm.State;
import jp.co.ssk.sm.StateMachine;
import jp.co.ssk.utility.SynchronousCallback;

final class CBPeripheralStateMachine extends StateMachine {

    private static final EnumMap<CBPeripheralDetailedState, CBPeripheralState> sStateMap = new EnumMap<>(CBPeripheralDetailedState.class);

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
    private final State mServiceDiscoveringState = new ServicesDiscoveringState();
    private final State mConnectCancelingState = new ConnectCancelingState();
    private final State mCleanupConnectionState = new CleanupConnectionState();
    private final State mConnectionRetryReadyState = new ConnectionRetryReadyState();
    private final State mConnectedState = new ConnectedState();
    private final State mDisconnectingState = new DisconnectingState();
    @NonNull
    private final WeakReference<AndroidPeripheral> mPeripheralRef;
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
    @NonNull
    private ConnectionRetry mConnectionRetry = ConnectionRetry.No;
    @NonNull
    private RemoveBond mCleanupWithRemoveBond = RemoveBond.No;
    CBPeripheralStateMachine(
            @NonNull AndroidPeripheral peripheral,
            @NonNull EventListener eventListener,
            @NonNull Looper looper) {
        super(looper);

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

        setDbg(CBLog.OUTPUT_LOG_ENABLED);
        start();
    }

    boolean isConnected() {
        return CBPeripheralState.Connected == getState();
    }

    @NonNull
    CBPeripheralState getState() {
        final CBPeripheralState ret;
        if (getHandler().isCurrentThread()) {
            ret = mState;
        } else {
            final SynchronousCallback<CBPeripheralState> callback = new SynchronousCallback<>();
            getHandler().post(() -> {
                callback.setResult(mState);
                callback.unlock();
            });
            callback.lock();
            ret = callback.getResult();
            if (null == ret) {
                throw new UnknownError("null == ret");
            }
        }
        return ret;
    }

    private void setState(@NonNull CBPeripheralState state) {
        if (mState == state) {
            return;
        }
        mState = state;
        mEventListener.onStateChanged(state);
    }

    @NonNull
    CBPeripheralDetailedState getDetailedState() {
        final CBPeripheralDetailedState ret;
        if (getHandler().isCurrentThread()) {
            ret = mDetailedState;
        } else {
            final SynchronousCallback<CBPeripheralDetailedState> callback = new SynchronousCallback<>();
            getHandler().post(() -> {
                callback.setResult(mDetailedState);
                callback.unlock();
            });
            callback.lock();
            ret = callback.getResult();
            if (null == ret) {
                throw new UnknownError("null == ret");
            }
        }
        return ret;
    }

    private void setDetailedState(final @NonNull CBPeripheralDetailedState detailedState) {
        if (mDetailedState == detailedState) {
            return;
        }
        setState(sStateMap.get(detailedState));
        mDetailedState = detailedState;
        mEventListener.onDetailedStateChanged(detailedState);
    }

    void connect() {
        sendMessageSyncIf(Event.Connect.ordinal());
    }

    void cancelConnection() {
        sendMessageSyncIf(Event.CancelConnection.ordinal());
    }

    void onPairingRequest(@NonNull final CBConstants.PairingVariant variant) {
        sendMessageSyncIf(Event.PairingRequest.ordinal(), variant);
    }

    void onBondStateChanged(@NonNull AndroidPeripheral.BondState newState) {
        switch (newState) {
            case Bonded:
                sendMessageSyncIf(Event.Bonded.ordinal());
                break;
            case Bonding:
                sendMessageSyncIf(Event.Bonding.ordinal());
                break;
            case None:
                sendMessageSyncIf(Event.BondNone.ordinal());
                break;
            default:
                throw new IllegalArgumentException("Wrong argument.");
        }
    }

    void onAclConnectionStateChanged(@NonNull AndroidPeripheral.AclConnectionState newState) {
        switch (newState) {
            case Connected:
                sendMessageSyncIf(Event.AclConnected.ordinal());
                break;
            case Disconnected:
                sendMessageSyncIf(Event.AclDisConnected.ordinal());
                break;
            default:
                throw new IllegalArgumentException("Wrong argument.");
        }
    }

    void onGattConnectionStateChanged(@NonNull AndroidPeripheral.GattConnectionState newState, int status) {
        switch (newState) {
            case Connected:
                sendMessageSyncIf(Event.GattConnected.ordinal());
                break;
            case Disconnected:
                sendMessageSyncIf(Event.GattDisconnected.ordinal(), status);
                break;
            default:
                throw new IllegalArgumentException("Wrong argument.");
        }
    }

    void onServicesDiscovered(int status) {
        if (CBStatusCode.GATT_SUCCESS == status) {
            sendMessageSyncIf(Event.DiscoverServicesSuccess.ordinal());
        } else {
            sendMessageSyncIf(Event.DiscoverServicesFailure.ordinal(), status);
        }
    }

    @Override
    protected void outputProcessMessageLogTrigger(@NonNull String currentStateName, @NonNull Message msg) {
        CBLog.d("processMessage: " + currentStateName + " " + Event.values()[msg.what]);
    }

    private void autoPairingIfNeeded(@NonNull AndroidPeripheral peripheral, CBConstants.PairingVariant variant) {
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
                break;
            case Consent:
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

    @NonNull
    private AndroidPeripheral getPeripheral() {
        return mPeripheralRef.get();
    }

    private enum ConnectionRetry {
        Yes, No
    }

    private enum RemoveBond {
        Yes, No
    }

    private enum Event {
        Connect,
        CancelConnection,
        PairingRequest,
        Bonded,
        Bonding,
        BondNone,
        AclConnected,
        AclDisConnected,
        GattConnected,
        GattDisconnected,
        DiscoverServicesSuccess,
        DiscoverServicesFailure,

        GattConnectingTimeout,
        GattConnectionStabled,
        RequireCreateBond,
        RequireConnectGatt,

        StartDiscoverServices,
        ExecDiscoverServices,
        ServicesDiscoveringTimeout,

        ConnectCancelingTimeout,

        GattClosed,
        CleanupConnectionTimeout,

        ConnectionRetry,

        DisconnectingTimeout,

        UnknownError,
    }

    interface EventListener {

        void didConnect();

        void didFailToConnect();

        void didDisconnectPeripheral();

        void onStateChanged(@NonNull CBPeripheralState newState);

        void onDetailedStateChanged(@NonNull CBPeripheralDetailedState newState);
    }

    private static class DefaultState extends State<CBPeripheralStateMachine> {
        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            return StateMachine.HANDLED;
        }
    }

    private static class UnconnectedState extends State<CBPeripheralStateMachine> {
        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            if (owner.getPeripheral().hasGatt()) {
                if (owner.mConfig.isUseRefreshWhenDisconnect()) {
                    owner.getPeripheral().refreshGatt();
                }
                owner.getPeripheral().closeGatt();
            }
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case Connect:
                    owner.transitionTo(owner.mGattConnectingState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }
    }

    private static class DisconnectedState extends State<CBPeripheralStateMachine> {
        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.Disconnected);
            owner.mEventListener.didDisconnectPeripheral();
        }
    }

    private static class ConnectionCanceledState extends State<CBPeripheralStateMachine> {
        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.ConnectionCanceled);
            owner.mEventListener.didFailToConnect();
        }
    }

    private static class ConnectionFailedState extends State<CBPeripheralStateMachine> {
        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.ConnectionFailed);
            owner.mEventListener.didFailToConnect();
        }
    }

    private static class ConnectingState extends State<CBPeripheralStateMachine> {
        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.mConnectionRetryCount = 0;
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case CancelConnection:
                    owner.transitionTo(owner.mConnectCancelingState);
                    break;
                case BondNone:
                case Bonding:
                case GattDisconnected:
                    transitionToCleanupState(owner);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private void transitionToCleanupState(@NonNull CBPeripheralStateMachine owner) {
            if (owner.mIsShowPairingDialog) {
                // No retry when connection failed in showing pairing dialog.
                // ex) Select [Cancel] / Invalid PIN input
                CBLog.w("Pairing canceled or timeout or invalid PIN input.");
                owner.mConnectionRetry = ConnectionRetry.No;
                owner.mCleanupWithRemoveBond = RemoveBond.No;
                owner.transitionTo(owner.mCleanupConnectionState);
            } else {
                // Retry when unexpected connection failed.
                CBLog.e("Connection failed.");
                owner.mConnectionRetry = ConnectionRetry.Yes;
                owner.mCleanupWithRemoveBond = RemoveBond.No;
                owner.transitionTo(owner.mCleanupConnectionState);
            }
        }
    }

    private static class GattConnectingState extends State<CBPeripheralStateMachine> {

        private static final long GATT_CONNECTING_TIMEOUT_MS = 15 * 1000;

        private boolean mNeedPairing;

        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.GattConnecting);
            owner.mIsShowPairingDialog = false;
            mNeedPairing = false;
            if (owner.mConfig.isUseCreateBond() && !owner.getPeripheral().isBonded()) {
                mNeedPairing = true;
                owner.sendMessage(Event.RequireCreateBond.ordinal());
            }
            owner.sendMessage(Event.RequireConnectGatt.ordinal());
            owner.sendMessageDelayed(Event.GattConnectingTimeout.ordinal(), GATT_CONNECTING_TIMEOUT_MS);
        }

        @Override
        public void exit(@NonNull CBPeripheralStateMachine owner) {
            owner.removeMessages(Event.GattConnectingTimeout.ordinal());
            owner.removeMessages(Event.GattConnectionStabled.ordinal());
            owner.removeMessages(Event.RequireCreateBond.ordinal());
            owner.removeMessages(Event.RequireConnectGatt.ordinal());
            owner.removeMessages(Event.UnknownError.ordinal());
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case RequireCreateBond:
                    if (!owner.getPeripheral().createBond()) {
                        owner.sendMessage(Event.UnknownError.ordinal());
                    }
                    break;
                case RequireConnectGatt:
                    if (!owner.getPeripheral().connectGatt()) {
                        owner.sendMessage(Event.UnknownError.ordinal());
                    }
                    break;
                case PairingRequest:
                    owner.removeMessages(Event.GattConnectingTimeout.ordinal());
                    owner.mIsShowPairingDialog = true;
                    owner.autoPairingIfNeeded(owner.getPeripheral(), (CBConstants.PairingVariant) msg.obj);
                    break;
                case GattConnected:
                    long stableConnectionWaitTime = 0;
                    if (owner.mConfig.isStableConnectionEnabled()) {
                        stableConnectionWaitTime = owner.mConfig.getStableConnectionWaitTime();
                    }
                    owner.sendMessageDelayed(Event.GattConnectionStabled.ordinal(), stableConnectionWaitTime);
                    break;
                case Bonding:
                    mNeedPairing = true;
                    break;
                case Bonded:
                    owner.mIsShowPairingDialog = false;
                    transitionToNextStateIfConnectionStabled(owner);
                    break;
                case GattConnectionStabled:
                    transitionToNextStateIfConnectionStabled(owner);
                    break;
                case UnknownError: {
                    owner.mConnectionRetry = ConnectionRetry.Yes;
                    owner.mCleanupWithRemoveBond = RemoveBond.No;
                    owner.transitionTo(owner.mCleanupConnectionState);
                    break;
                }
                case GattConnectingTimeout: {
                    if (!owner.hasMessages(Event.GattConnectionStabled.ordinal())) {
                        CBLog.e("Gatt connection timeout.");
                        owner.mConnectionRetry = ConnectionRetry.Yes;
                        owner.mCleanupWithRemoveBond = RemoveBond.Yes;
                        owner.transitionTo(owner.mCleanupConnectionState);
                    }
                    break;
                }
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private void transitionToNextStateIfConnectionStabled(@NonNull CBPeripheralStateMachine owner) {
            if (!owner.getPeripheral().isGattConnected()) {
                CBLog.i("Gatt connecting.");
                return;
            }
            if (mNeedPairing && !owner.getPeripheral().isBonded()) {
                CBLog.i("Wait bonded.");
                return;
            }
            if (owner.hasMessages(Event.GattConnectionStabled.ordinal())) {
                CBLog.i("Wait connection stabled.");
                return;
            }
            CBLog.i("Gatt connection completed.");
            owner.transitionTo(owner.mServiceDiscoveringState);
        }
    }

    private static class ServicesDiscoveringState extends State<CBPeripheralStateMachine> {

        private static final long SERVICES_DISCOVERING_TIMEOUT_MS = 30 * 1000;
        private static final long EXEC_INTERVAL = 5 * 1000;

        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.ServiceDiscovering);
            List<BluetoothGattService> services = owner.getPeripheral().getServices();
            if (0 == services.size()) {
                owner.sendMessage(Event.StartDiscoverServices.ordinal());
            } else {
                owner.sendMessage(Event.DiscoverServicesSuccess.ordinal());
            }
        }

        @Override
        public void exit(@NonNull CBPeripheralStateMachine owner) {
            owner.removeMessages(Event.StartDiscoverServices.ordinal());
            owner.removeMessages(Event.ExecDiscoverServices.ordinal());
            owner.removeMessages(Event.ServicesDiscoveringTimeout.ordinal());
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case StartDiscoverServices:
                    owner.sendMessageDelayed(Event.ServicesDiscoveringTimeout.ordinal(), SERVICES_DISCOVERING_TIMEOUT_MS);
                    owner.sendMessage(Event.ExecDiscoverServices.ordinal());
                    break;
                case ExecDiscoverServices:
                    owner.getPeripheral().discoverServices();
                    owner.sendMessageDelayed(Event.ExecDiscoverServices.ordinal(), EXEC_INTERVAL);
                    break;
                case DiscoverServicesSuccess:
                    owner.removeMessages(Event.ServicesDiscoveringTimeout.ordinal());
                    owner.removeMessages(Event.ExecDiscoverServices.ordinal());
                    if (verifyServices(owner)) {
                        CBLog.i("Discover services success.");
                        owner.transitionTo(owner.mConnectedState);
                    } else {
                        CBLog.e("Verify services failed.");
                        owner.mConnectionRetry = ConnectionRetry.Yes;
                        owner.mCleanupWithRemoveBond = RemoveBond.Yes;
                        owner.transitionTo(owner.mCleanupConnectionState);
                    }
                    break;
                case DiscoverServicesFailure: {
                    CBLog.e("Discover services failure.");
                    owner.mConnectionRetry = ConnectionRetry.Yes;
                    owner.mCleanupWithRemoveBond = RemoveBond.Yes;
                    owner.transitionTo(owner.mCleanupConnectionState);
                    break;
                }
                case ServicesDiscoveringTimeout: {
                    owner.removeMessages(Event.ExecDiscoverServices.ordinal());
                    CBLog.e("Discover service timeout.");
                    owner.mConnectionRetry = ConnectionRetry.Yes;
                    owner.mCleanupWithRemoveBond = RemoveBond.Yes;
                    owner.transitionTo(owner.mCleanupConnectionState);
                    break;
                }
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean verifyServices(@NonNull CBPeripheralStateMachine owner) {
            List<BluetoothGattService> services = owner.getPeripheral().getServices();
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

    private static class ConnectCancelingState extends State<CBPeripheralStateMachine> {

        private static final long CONNECT_CANCELING_TIMEOUT_MS = 15 * 1000;

        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.ConnectCanceling);
            teardownOrTransitionToNextState(owner);
            owner.sendMessageDelayed(Event.ConnectCancelingTimeout.ordinal(), CONNECT_CANCELING_TIMEOUT_MS);
        }

        @Override
        public void exit(@NonNull CBPeripheralStateMachine owner) {
            owner.removeMessages(Event.ConnectCancelingTimeout.ordinal());
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case BondNone:
                case GattDisconnected:
                    teardownOrTransitionToNextState(owner);
                    break;
                case ConnectCancelingTimeout:
                    CBLog.e("Connect cancel timeout.");
                    // There are cases when timeout has occurred without notification of
                    // ACL Disconnected or Bond None and move to next state in theses cases.
                    owner.transitionTo(owner.mConnectionCanceledState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean isTeardownCompleted(@NonNull CBPeripheralStateMachine owner) {
            if (owner.getPeripheral().isGattConnected()) {
                CBLog.i("Gatt disconnecting.");
                return false;
            }
            if (CBPeripheral.BondState.Bonding == owner.getPeripheral().getBondState()) {
                CBLog.i("Bond process canceling.");
                return false;
            }
            CBLog.i("Teardown completed.");
            return true;
        }

        private void teardownOrTransitionToNextState(@NonNull CBPeripheralStateMachine owner) {
            if (isTeardownCompleted(owner)) {
                owner.transitionTo(owner.mConnectionCanceledState);
            } else {
                if (owner.getPeripheral().isGattConnected()) {
                    owner.getPeripheral().disconnectGatt();
                } else if (CBPeripheral.BondState.Bonding == owner.getPeripheral().getBondState()) {
                    owner.getPeripheral().cancelBondProcess();
                }
            }
        }
    }

    private static class CleanupConnectionState extends State<CBPeripheralStateMachine> {

        private static final long CLEANUP_CONNECTION_TIMEOUT_MS = 15 * 1000;

        private boolean mNeededRetry;
        private boolean mNeededRemoveBond;

        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.CleanupConnection);
            mNeededRetry = (ConnectionRetry.Yes == owner.mConnectionRetry);
            mNeededRemoveBond = (RemoveBond.Yes == owner.mCleanupWithRemoveBond);
            cleanupOrTransitionToNextState(owner);
            owner.sendMessageDelayed(Event.CleanupConnectionTimeout.ordinal(), CLEANUP_CONNECTION_TIMEOUT_MS);
        }

        @Override
        public void exit(@NonNull CBPeripheralStateMachine owner) {
            owner.removeMessages(Event.GattClosed.ordinal());
            owner.removeMessages(Event.CleanupConnectionTimeout.ordinal());
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case BondNone:
                case GattDisconnected:
                case GattClosed:
                    cleanupOrTransitionToNextState(owner);
                    break;
                case CleanupConnectionTimeout:
                    owner.transitionTo(owner.mConnectionFailedState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean isCleanupCompleted(@NonNull CBPeripheralStateMachine owner) {
            if (owner.getPeripheral().isGattConnected()) {
                CBLog.i("Gatt disconnecting.");
                return false;
            }
            if (CBPeripheral.BondState.Bonding == owner.getPeripheral().getBondState()) {
                CBLog.i("Bond process canceling.");
                return false;
            }
            if (owner.getPeripheral().hasGatt()) {
                CBLog.i("Gatt closing.");
                return false;
            }
            if (mNeededRemoveBond && CBPeripheral.BondState.None != owner.getPeripheral().getBondState()) {
                CBLog.i("Bond removing.");
                return false;
            }
            CBLog.i("Cleanup completed.");
            return true;
        }

        private void cleanup(@NonNull CBPeripheralStateMachine owner) {
            if (owner.getPeripheral().isGattConnected()) {
                owner.getPeripheral().disconnectGatt();
            } else if (CBPeripheral.BondState.Bonding == owner.getPeripheral().getBondState()) {
                owner.getPeripheral().cancelBondProcess();
            } else if (owner.getPeripheral().hasGatt()) {
                if (owner.mConfig.isUseRefreshWhenDisconnect()) {
                    owner.getPeripheral().refreshGatt();
                }
                owner.getPeripheral().closeGatt();
                owner.sendMessage(Event.GattClosed.ordinal());
            } else if (CBPeripheral.BondState.None != owner.getPeripheral().getBondState() && mNeededRetry) {
                owner.getPeripheral().removeBond();
            }
        }

        private void cleanupOrTransitionToNextState(@NonNull CBPeripheralStateMachine owner) {
            if (!isCleanupCompleted(owner)) {
                cleanup(owner);
                return;
            }
            if (mNeededRetry && owner.mConfig.isConnectionRetryEnabled()) {
                owner.transitionTo(owner.mConnectionRetryReadyState);
            } else {
                CBLog.w("Connection finished because not request a retry.");
                owner.transitionTo(owner.mConnectionFailedState);
            }
        }
    }

    private static class ConnectionRetryReadyState extends State<CBPeripheralStateMachine> {

        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.ConnectionRetryReady);
            if (CBConfig.RETRY_UNLIMITED == owner.mConfig.getConnectionRetryCount() ||
                    owner.mConnectionRetryCount < owner.mConfig.getConnectionRetryCount()) {
                owner.mConnectionRetryCount++;
                owner.sendMessageDelayed(Event.ConnectionRetry.ordinal(), owner.mConfig.getConnectionRetryDelayTime());
            } else {
                CBLog.e("Connection failed because retry count reaches the maximum value.");
                owner.transitionTo(owner.mConnectionFailedState);
            }
        }

        @Override
        public void exit(@NonNull CBPeripheralStateMachine owner) {
            owner.removeMessages(Event.ConnectionRetry.ordinal());
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case ConnectionRetry: {
                    CBLog.w("Connection retry. count:" + owner.mConnectionRetryCount);
                    owner.transitionTo(owner.mGattConnectingState);
                    break;
                }
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }
    }

    private static class ConnectedState extends State<CBPeripheralStateMachine> {

        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.Connected);
            owner.mEventListener.didConnect();
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case Connect:
                    owner.mEventListener.didFailToConnect();
                    break;
                case CancelConnection:
                    owner.transitionTo(owner.mDisconnectingState);
                    break;
                case BondNone:
                case GattDisconnected:
                    owner.transitionTo(owner.mDisconnectingState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }
    }

    private static class DisconnectingState extends State<CBPeripheralStateMachine> {

        private static final long DISCONNECTING_WAIT_TIME = 15 * 1000;

        @Override
        public void enter(@NonNull CBPeripheralStateMachine owner) {
            owner.setDetailedState(CBPeripheralDetailedState.Disconnecting);
            teardownOrTransitionToNextState(owner);
            owner.sendMessageDelayed(Event.DisconnectingTimeout.ordinal(), DISCONNECTING_WAIT_TIME);
        }

        @Override
        public void exit(@NonNull CBPeripheralStateMachine owner) {
            owner.removeMessages(Event.DisconnectingTimeout.ordinal());
        }

        @Override
        public boolean processMessage(@NonNull CBPeripheralStateMachine owner, @NonNull Message msg) {
            switch (Event.values()[msg.what]) {
                case Connect:
                    owner.mEventListener.didFailToConnect();
                    break;
                case BondNone:
                case GattDisconnected:
                    teardownOrTransitionToNextState(owner);
                    break;
                case DisconnectingTimeout:
                    CBLog.e("Disconnection timeout.");
                    // There are cases when timeout has occurred without notification of
                    // ACL Disconnected or Bond None and move to next state in theses cases.
                    owner.transitionTo(owner.mDisconnectedState);
                    break;
                default:
                    return StateMachine.NOT_HANDLED;
            }
            return StateMachine.HANDLED;
        }

        private boolean isTeardownCompleted(@NonNull CBPeripheralStateMachine owner) {
            if (owner.getPeripheral().isGattConnected()) {
                CBLog.i("Gatt disconnecting.");
                return false;
            }
            if (CBPeripheral.BondState.Bonding == owner.getPeripheral().getBondState()) {
                CBLog.i("Bond process canceling.");
                return false;
            }
            CBLog.i("Teardown completed.");
            return true;
        }

        private void teardownOrTransitionToNextState(@NonNull CBPeripheralStateMachine owner) {
            if (isTeardownCompleted(owner)) {
                owner.transitionTo(owner.mDisconnectedState);
            } else {
                if (owner.getPeripheral().isGattConnected()) {
                    owner.getPeripheral().disconnectGatt();
                } else if (CBPeripheral.BondState.Bonding == owner.getPeripheral().getBondState()) {
                    owner.getPeripheral().cancelBondProcess();
                }
            }
        }
    }
}
