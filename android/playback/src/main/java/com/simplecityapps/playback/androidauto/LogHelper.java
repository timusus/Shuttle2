package com.simplecityapps.playback.androidauto;

import timber.log.Timber;

public class LogHelper {

    public static String makeLogTag(Class<?> cls) {
        return cls.getSimpleName();
    }

    public static void v(String tag, Object... messages) {
        Timber.tag(tag).v(concatMessages(messages));
    }

    public static void w(String tag, Object... messages) {
        Timber.tag(tag).w(concatMessages(messages));
    }

    public static void i(String tag, Object... messages) {
        Timber.tag(tag).i(concatMessages(messages));
    }

    public static void e(String tag, Throwable throwable, Object... messages) {
        Timber.tag(tag).e(throwable, concatMessages(messages));
    }

    private static String concatMessages(Object... messages) {
        StringBuilder builder = new StringBuilder();
        for (Object msg : messages) {
            builder.append(msg);
        }
        return builder.toString();
    }
}
