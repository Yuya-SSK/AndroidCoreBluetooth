package jp.co.ssk.bluetooth;

import android.support.annotation.NonNull;

public interface CBCentralManagerDelegate {
    void didConnect(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral);

    void didDisconnectPeripheral(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral/*, Error error*/);

    void didFailToConnect(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral/*, Error error*/);

    void didDiscover(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral, @NonNull byte[] advertisementData, int rssi);

    void centralManagerDidUpdateState(@NonNull CBCentralManager central, @NonNull CBManagerState newState);
}
