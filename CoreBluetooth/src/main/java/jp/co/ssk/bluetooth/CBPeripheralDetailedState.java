package jp.co.ssk.bluetooth;

public enum CBPeripheralDetailedState {
    Unconnected,
    Disconnected,
    ConnectionCanceled,
    ConnectionFailed,
    GattConnecting,
    ServiceDiscovering,
    ConnectCanceling,
    CleanupConnection,
    ConnectionRetryReady,
    Connected,
    Disconnecting,
}
