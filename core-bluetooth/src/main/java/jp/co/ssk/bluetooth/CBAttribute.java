package jp.co.ssk.bluetooth;

import android.support.annotation.NonNull;

import java.util.UUID;

public class CBAttribute {

    @NonNull
    private final CBUUID mCBUUID;

    @SuppressWarnings("unused")
    CBAttribute(@NonNull String uuid) {
        mCBUUID = new CBUUID(uuid);
    }

    @SuppressWarnings("unused")
    CBAttribute(@NonNull UUID uuid) {
        mCBUUID = new CBUUID(uuid);
    }

    @NonNull
    public CBUUID uuid() {
        return mCBUUID;
    }
}
