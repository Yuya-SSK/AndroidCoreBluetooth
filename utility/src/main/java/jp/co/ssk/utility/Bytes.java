package jp.co.ssk.utility;

import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Bytes {
    public static String toHexString(@NonNull byte[] data)
            throws IllegalArgumentException {
        if (null == data) {
            throw new IllegalArgumentException("null == data");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("0x");
        for (byte b : data) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}
