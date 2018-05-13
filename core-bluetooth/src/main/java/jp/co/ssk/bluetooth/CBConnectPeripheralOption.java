package jp.co.ssk.bluetooth;

public enum CBConnectPeripheralOption {
    /**
     * Unsupported iOS options.
     */
    //NotifyOnConnectionKey,
    //NotifyOnDisconnectionKey,
    //NotifyOnNotificationKey,

    /**
     * Android original options.
     */

    // boolean
    AssistPairingDialogKey,

    // boolean
    AutoPairingConfirmationKey,

    // String
    AutoEnterThePinCodeKey,


    StableConnectionKey
}
