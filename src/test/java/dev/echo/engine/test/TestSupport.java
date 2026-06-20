package dev.echo.engine.test;

public final class TestSupport {
    private TestSupport(){}
    public static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
