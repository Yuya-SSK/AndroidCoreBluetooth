package jp.co.ssk.utility;

import android.support.annotation.Nullable;

public final class Types {
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T autoCast(@Nullable Object obj) {
        return (T) obj;
    }
}
