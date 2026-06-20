package dev.echo.engine.game;

import dev.echo.engine.api.GameAudio;
import dev.echo.engine.render.SoftwareRenderer;
import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.test.TestSupport;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

public final class RenderSmokeTest {
    private RenderSmokeTest(){}
    public static void run(Path packRoot,Path manifest)throws Exception{PackManifest pack=PackManifest.load(manifest);try(ModuleHost modules=new ModuleHost(packRoot,pack)){modules.load();GameAudio audio=cue->{};Path saves=Files.createTempDirectory("echo-render-smoke");GameSession session=GameSession.createNew("render",saves,919191L,modules,audio);long deadline=System.nanoTime()+10_000_000_000L;while(!session.ready()&&System.nanoTime()<deadline){session.updateHeadless(1.0/60.0);Thread.sleep(5);}for(int i=0;i<120;i++){session.updateHeadless(1.0/60.0);Thread.sleep(2);}long meshDeadline=System.nanoTime()+5_000_000_000L;while(session.meshScheduler().meshes().isEmpty()&&System.nanoTime()<meshDeadline){session.updateHeadless(1.0/60.0);Thread.sleep(5);}BufferedImage image=new BufferedImage(640,360,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{new SoftwareRenderer().render(g,640,360,session.camera(),session.renderMeshes(),96);}finally{g.dispose();}HashSet<Integer> colors=new HashSet<>();for(int y=0;y<image.getHeight();y+=4)for(int x=0;x<image.getWidth();x+=4)colors.add(image.getRGB(x,y));Path output=Path.of("build","test-render.png");Files.createDirectories(output.getParent());ImageIO.write(image,"png",output.toFile());TestSupport.require(colors.size()>12,"software render should produce a nonblank voxel frame, colors="+colors.size());session.close();}}
}
