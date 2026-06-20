package dev.echo.engine.game;

import java.nio.file.Path;

public record LaunchOptions(Path packRoot,Path manifest,Path saveRoot,boolean headlessSmoke,boolean strict){
    public static LaunchOptions parse(String[] args){Path packRoot=Path.of("dist"),manifest=Path.of("pack.json"),saveRoot=Path.of("saves");boolean headless=false,strict=true;String[] values=args==null?new String[0]:args;for(int i=0;i<values.length;i++){switch(values[i]){case "--pack-root"->packRoot=Path.of(require(values,++i,"--pack-root"));case "--manifest"->manifest=Path.of(require(values,++i,"--manifest"));case "--save-root"->saveRoot=Path.of(require(values,++i,"--save-root"));case "--headless-smoke"->headless=true;case "--dev"->strict=false;default->throw new IllegalArgumentException("Unknown argument: "+values[i]);}}packRoot=packRoot.toAbsolutePath().normalize();Path manifestPath=manifest.isAbsolute()?manifest:packRoot.resolve(manifest).normalize();return new LaunchOptions(packRoot,manifestPath,saveRoot.toAbsolutePath().normalize(),headless,strict);}
    private static String require(String[] values,int index,String option){if(index>=values.length||values[index].isBlank())throw new IllegalArgumentException("Missing value after "+option);return values[index];}
}
