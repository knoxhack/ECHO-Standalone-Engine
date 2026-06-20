package dev.echo.engine.runtime.module;

import dev.echo.engine.api.ModuleState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultModuleState implements ModuleState {
    private final ConcurrentHashMap<String,String> values=new ConcurrentHashMap<>();
    public DefaultModuleState(){}
    public DefaultModuleState(Map<String,String> initial){if(initial!=null)values.putAll(initial);}
    @Override public String get(String key,String fallback){return values.getOrDefault(key,fallback);}
    @Override public double getDouble(String key,double fallback){try{return Double.parseDouble(get(key,""));}catch(Exception ignored){return fallback;}}
    @Override public int getInt(String key,int fallback){try{return Integer.parseInt(get(key,""));}catch(Exception ignored){return fallback;}}
    @Override public boolean getBoolean(String key,boolean fallback){String value=get(key,"");return value.isBlank()?fallback:Boolean.parseBoolean(value);}
    @Override public void put(String key,String value){if(key==null||key.isBlank())throw new IllegalArgumentException("state key required");values.put(key,value==null?"":value);}
    @Override public void putDouble(String key,double value){put(key,Double.toString(value));}
    @Override public void putInt(String key,int value){put(key,Integer.toString(value));}
    @Override public void putBoolean(String key,boolean value){put(key,Boolean.toString(value));}
    @Override public Map<String,String> snapshot(){return Map.copyOf(new LinkedHashMap<>(values));}
    public void replace(Map<String,String> next){values.clear();if(next!=null)values.putAll(next);}
}
