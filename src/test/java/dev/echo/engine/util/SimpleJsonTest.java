package dev.echo.engine.util;

import dev.echo.engine.test.TestSupport;
import java.util.List;
import java.util.Map;

public final class SimpleJsonTest {
    private SimpleJsonTest(){}
    public static void run(){Map<String,Object> original=Map.of("name","ECHO\nEngine","number",42,"flag",true,"rows",List.of(Map.of("id","echo:test"),"value"));String encoded=SimpleJson.stringify(original);Map<String,Object> restored=SimpleJson.parseObject(encoded);TestSupport.require(restored.get("name").equals("ECHO\nEngine"),"JSON string round trip");TestSupport.require(((Number)restored.get("number")).intValue()==42,"JSON number round trip");TestSupport.require(Boolean.TRUE.equals(restored.get("flag")),"JSON boolean round trip");}
}
