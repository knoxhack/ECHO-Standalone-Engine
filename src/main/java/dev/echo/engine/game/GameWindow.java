package dev.echo.engine.game;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GameWindow implements AutoCloseable {
    @FunctionalInterface public interface FramePainter { void paint(Graphics2D graphics,int width,int height); }
    private final JFrame frame=new JFrame("ECHO Standalone Engine");private final Canvas canvas=new Canvas();private final AtomicBoolean closing=new AtomicBoolean();
    public GameWindow(int width,int height,InputState input){canvas.setPreferredSize(new Dimension(width,height));canvas.setIgnoreRepaint(true);frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){closing.set(true);}});frame.add(canvas);frame.pack();frame.setLocationRelativeTo(null);frame.setVisible(true);input.attach(canvas);canvas.requestFocusInWindow();canvas.createBufferStrategy(2);}
    public boolean shouldClose(){return closing.get();}public void requestClose(){closing.set(true);}public int width(){return Math.max(1,canvas.getWidth());}public int height(){return Math.max(1,canvas.getHeight());}
    public void render(FramePainter painter){BufferStrategy strategy=canvas.getBufferStrategy();if(strategy==null){canvas.createBufferStrategy(2);return;}do{do{Graphics2D g=(Graphics2D)strategy.getDrawGraphics();try{painter.paint(g,width(),height());}finally{g.dispose();}}while(strategy.contentsRestored());strategy.show();Toolkit.getDefaultToolkit().sync();}while(strategy.contentsLost());}
    @Override public void close(){closing.set(true);SwingUtilities.invokeLater(frame::dispose);}
}
