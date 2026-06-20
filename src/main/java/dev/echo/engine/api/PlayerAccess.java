package dev.echo.engine.api;

public interface PlayerAccess {
    double x();
    double y();
    double z();
    double health();
    double hunger();
    double hydration();
    double exposure();
    void setHealth(double value);
    void setHunger(double value);
    void setHydration(double value);
    void setExposure(double value);
    int itemCount(ResourceId itemId);
    boolean consumeItem(ResourceId itemId, int count);
    int addItem(ResourceId itemId, int count);
}
