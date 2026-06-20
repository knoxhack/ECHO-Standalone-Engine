package dev.echo.engine.game;

import dev.echo.engine.render.RenderStats;
import dev.echo.engine.render.SoftwareRenderer;
import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.util.SimpleJson;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public final class GameClient implements AutoCloseable {
    private final LaunchOptions options;private final PackManifest pack;private final ModuleHost modules;private final JavaSoundEngine audio=new JavaSoundEngine();private final InputState input=new InputState();private final GameWindow window;private final SoftwareRenderer worldRenderer=new SoftwareRenderer();private final HudRenderer hud=new HudRenderer();private GameState state=GameState.TITLE;private GameSession session;private boolean debug;private String error="";private RenderStats stats=RenderStats.EMPTY;
    public GameClient(LaunchOptions options)throws Exception{this.options=options;this.pack=PackManifest.load(options.manifest());this.modules=new ModuleHost(options.packRoot(),pack);modules.load();this.window=new GameWindow(1280,720,input);}
    public void run(){double previous=now(),accumulator=0;final double step=1.0/60.0;while(!window.shouldClose()&&state!=GameState.SHUTDOWN){double frameStart=now(),elapsed=Math.min(0.25,frameStart-previous);previous=frameStart;accumulator+=elapsed;int updates=0;while(accumulator>=step&&updates<5){update(step);accumulator-=step;updates++;}if(updates==5&&accumulator>=step)accumulator%=step;window.render(this::render);double remaining=step-(now()-frameStart);if(remaining>0.001)try{Thread.sleep((long)(remaining*1000));}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}}
    private void update(double dt){if(input.consumeKey(KeyEvent.VK_F3))debug=!debug;switch(state){case TITLE->updateTitle();case LOADING->{session.updateLoading(dt);if(session.ready()){state=GameState.PLAYING;input.setCaptured(true);}}case PLAYING->{if(input.consumeKey(KeyEvent.VK_ESCAPE)){state=GameState.PAUSED;input.setCaptured(false);input.releaseAll();}else{session.update(input,dt);if(!session.playerEntity().alive()){session.message("You died — returning to spawn");session.playerEntity().setHealth(20);session.playerEntity().setPosition(0.5,session.voxelWorld().surfaceY(0,0)+2,0.5);}}}case PAUSED->updatePause();case ERROR->{if(input.consumeKey(KeyEvent.VK_ESCAPE))state=GameState.SHUTDOWN;}default->{}}}
    private void updateTitle(){input.setCaptured(false);if(input.consumeKey(KeyEvent.VK_N)||input.consumeKey(KeyEvent.VK_ENTER))startNew();else if(input.consumeKey(KeyEvent.VK_L))continueLast();else if(input.consumeKey(KeyEvent.VK_Q)||input.consumeKey(KeyEvent.VK_ESCAPE))state=GameState.SHUTDOWN;}
    private void updatePause(){if(input.consumeKey(KeyEvent.VK_ESCAPE)){state=GameState.PLAYING;input.setCaptured(true);}else if(input.consumeKey(KeyEvent.VK_S)){try{session.save(true);session.message("Saved world");}catch(IOException e){session.message("Save failed: "+e.getMessage());}}else if(input.consumeKey(KeyEvent.VK_Q)){closeSession();state=GameState.TITLE;}}
    private void startNew(){try{closeSession();String worldId="world-"+System.currentTimeMillis();session=GameSession.createNew(worldId,options.saveRoot(),System.nanoTime(),modules,audio);Files.createDirectories(options.saveRoot());Files.writeString(options.saveRoot().resolve("last-world.txt"),worldId);state=GameState.LOADING;error="";}catch(Exception e){fail(e);}}
    private void continueLast(){try{Path marker=options.saveRoot().resolve("last-world.txt");if(!Files.isRegularFile(marker)){error="No saved world found";return;}String worldId=Files.readString(marker).trim();closeSession();session=GameSession.load(worldId,options.saveRoot(),modules,audio);state=GameState.LOADING;error="";}catch(Exception e){fail(e);}}
    private void fail(Exception failure){failure.printStackTrace(System.err);error=failure.getMessage();state=GameState.ERROR;input.setCaptured(false);}
    private void render(Graphics2D g,int width,int height){if(session!=null&&state!=GameState.TITLE&&state!=GameState.ERROR){double distance=3*16*1.9;stats=worldRenderer.render(g,width,height,session.camera(),session.renderMeshes(),distance);hud.worldHud(g,width,height,session,stats,debug);}else{g.setColor(Color.BLACK);g.fillRect(0,0,width,height);}switch(state){case TITLE->hud.title(g,width,height,modules,lastWorldExists(),error);case LOADING->hud.loading(g,width,height,session);case PAUSED->hud.paused(g,width,height);case ERROR->hud.error(g,width,height,error);default->{}}}
    private boolean lastWorldExists(){try{Path marker=options.saveRoot().resolve("last-world.txt");if(!Files.isRegularFile(marker))return false;String id=Files.readString(marker).trim();return Files.isRegularFile(options.saveRoot().resolve(id).resolve("world.json"));}catch(IOException ignored){return false;}}
    private void closeSession(){if(session!=null){session.close();session=null;}}
    private static double now(){return System.nanoTime()/1_000_000_000.0;}
    @Override public void close(){closeSession();try{modules.close();}catch(Exception e){e.printStackTrace(System.err);}audio.close();window.close();}
}
