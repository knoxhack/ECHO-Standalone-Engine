package dev.echo.engine.game;

import dev.echo.engine.api.GameAudio;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JavaSoundEngine implements GameAudio,AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"echo-audio");t.setDaemon(true);return t;});private final AtomicBoolean enabled=new AtomicBoolean(true);
    @Override public void play(String cueId){if(!enabled.get())return;worker.submit(()->tone(cueId==null?"":cueId));}
    private void tone(String cue){try{float rate=22050;int duration=cue.contains("hazard")?180:cue.contains("break")?70:cue.contains("place")?55:90;double frequency=180+Math.floorMod(cue.hashCode(),520);byte[] data=new byte[(int)(rate*duration/1000.0)];for(int i=0;i<data.length;i++){double envelope=1.0-i/(double)data.length;data[i]=(byte)(Math.sin(2*Math.PI*frequency*i/rate)*48*envelope);}AudioFormat format=new AudioFormat(rate,8,1,true,false);try(SourceDataLine line=AudioSystem.getSourceDataLine(format)){line.open(format,Math.max(1024,data.length));line.start();line.write(data,0,data.length);line.drain();}}catch(Exception failure){enabled.set(false);System.err.println("[audio] disabled: "+failure.getMessage());}}
    public boolean enabled(){return enabled.get();}@Override public void close(){worker.shutdownNow();}
}
