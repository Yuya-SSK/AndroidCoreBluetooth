package jp.co.ssk.utility;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Reflect {
    @NonNull
    public static Object invokeMethod(
            @NonNull Object target,
            @NonNull String methodName,
            @Nullable Class<?>[] parameterClasses,
            @Nullable Object[] parameterValues)
            throws IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Class<?> clazz = target.getClass();
        Method method = clazz.getDeclaredMethod(methodName, parameterClasses);
        return method.invoke(target, parameterValues);
    }
}
