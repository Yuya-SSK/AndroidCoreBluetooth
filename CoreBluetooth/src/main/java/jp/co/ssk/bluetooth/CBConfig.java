package jp.co.ssk.bluetooth;

import android.support.annotation.NonNull;

public class CBConfig {

    static final int RETRY_UNLIMITED = 0;
    private static final boolean DEF_USE_CREATE_BOND = false;
    private static final RemoveBondOption DEF_REMOVE_BOND_OPTION = RemoveBondOption.NotUse;
    private static final boolean DEF_ASSIST_PAIRING_DIALOG_ENABLED = false;
    private static final boolean DEF_AUTO_PAIRING_ENABLED = false;
    private static final boolean DEF_AUTO_ENTER_THE_PIN_CODE_ENABLED = false;
    private static final String DEF_PIN_CODE = "000000";
    private static final boolean DEF_STABLE_CONNECTION_ENABLED = true;
    private static final long DEF_STABLE_CONNECTION_WAIT_TIME = 1500;
    private static final boolean DEF_CONNECTION_RETRY_ENABLED = true;
    private static final long DEF_CONNECTION_RETRY_DELAY_TIME = 1000;
    private static final int DEF_CONNECTION_RETRY_COUNT = RETRY_UNLIMITED;
    private static final boolean DEF_USE_REFRESH_WHEN_DISCONNECT = true;
    private boolean mUseCreateBond = DEF_USE_CREATE_BOND;
    private RemoveBondOption mRemoveBondOption = DEF_REMOVE_BOND_OPTION;
    private boolean mAssistPairingDialogEnabled = DEF_ASSIST_PAIRING_DIALOG_ENABLED;
    private boolean mAutoPairingEnabled = DEF_AUTO_PAIRING_ENABLED;
    private boolean mAutoEnterThePinCodeEnabled = DEF_AUTO_ENTER_THE_PIN_CODE_ENABLED;
    @NonNull
    private String mPinCode = DEF_PIN_CODE;
    private boolean mStableConnectionEnabled = DEF_STABLE_CONNECTION_ENABLED;
    private long mStableConnectionWaitTime = DEF_STABLE_CONNECTION_WAIT_TIME;
    private boolean mConnectionRetryEnabled = DEF_CONNECTION_RETRY_ENABLED;
    private long mConnectionRetryDelayTime = DEF_CONNECTION_RETRY_DELAY_TIME;
    private int mConnectionRetryCount = DEF_CONNECTION_RETRY_COUNT;
    private boolean mUseRefreshWhenDisconnect = DEF_USE_REFRESH_WHEN_DISCONNECT;

    boolean isUseCreateBond() {
        return mUseCreateBond;
    }

    RemoveBondOption getRemoveBondOption() {
        return mRemoveBondOption;
    }

    boolean isAssistPairingDialogEnabled() {
        return mAssistPairingDialogEnabled;
    }

    boolean isAutoPairingEnabled() {
        return mAutoPairingEnabled;
    }

    boolean isAutoEnterThePinCodeEnabled() {
        return mAutoEnterThePinCodeEnabled;
    }

    @NonNull
    String getPinCode() {
        return mPinCode;
    }

    boolean isStableConnectionEnabled() {
        return mStableConnectionEnabled;
    }

    long getStableConnectionWaitTime() {
        return mStableConnectionWaitTime;
    }

    boolean isConnectionRetryEnabled() {
        return mConnectionRetryEnabled;
    }

    long getConnectionRetryDelayTime() {
        return mConnectionRetryDelayTime;
    }

    int getConnectionRetryCount() {
        return mConnectionRetryCount;
    }

    boolean isUseRefreshWhenDisconnect() {
        return mUseRefreshWhenDisconnect;
    }

    public enum RemoveBondOption {
        NotUse,
        UsedBeforeConnectionProcessEveryTime
    }
}
