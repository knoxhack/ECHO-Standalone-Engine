package dev.echo.engine.api;

import java.util.Map;

public interface ModuleState {
    String get(String key, String fallback);
    double getDouble(String key, double fallback);
    int getInt(String key, int fallback);
    boolean getBoolean(String key, boolean fallback);
    void put(String key, String value);
    void putDouble(String key, double value);
    void putInt(String key, int value);
    void putBoolean(String key, boolean value);
    Map<String, String> snapshot();
}
