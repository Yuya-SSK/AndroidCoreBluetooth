package jp.co.ssk.utility.sm;

import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

interface IState {

    void enter(@Nullable Object[] transferObjects);

    boolean processMessage(@NonNull Message msg);

    void exit();

    @NonNull
    String getName();
}
