package jp.co.ssk.bluetooth;

import android.support.annotation.NonNull;

public interface CBCentralManagerDebugDelegate {
    void onStateChanged(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral, @NonNull CBPeripheralState newState);

    void onDetailedStateChanged(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral, @NonNull CBPeripheralDetailedState newState);

    void onPairingRequest(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral);

    void onBondStateChanged(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral, @NonNull CBPeripheral.BondState bondState);

    void onAclConnectionStateChanged(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral, @NonNull CBPeripheral.AclConnectionState aclConnectionState);

    void onGattConnectionStateChanged(@NonNull CBCentralManager central, @NonNull CBPeripheral peripheral, @NonNull CBPeripheral.GattConnectionState gattConnectionState, int status);
}
