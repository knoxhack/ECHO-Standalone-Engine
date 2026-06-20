package dev.echo.engine.game;

public final class EngineMain {
    private EngineMain(){}
    public static void main(String[] args){try{LaunchOptions options=LaunchOptions.parse(args);System.out.println("ECHO Standalone Engine "+GameSession.ENGINE_VERSION);System.out.println("packRoot="+options.packRoot());System.out.println("manifest="+options.manifest());if(options.headlessSmoke()){HeadlessSmoke.run(options);return;}try(GameClient client=new GameClient(options)){client.run();}}catch(Throwable failure){failure.printStackTrace(System.err);System.exit(1);}}
}
