package io.github.jerryt92.jrag.utils;

public final class UUIDUtil {
    public static String randomUUID() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
