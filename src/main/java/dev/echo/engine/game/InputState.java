package dev.echo.engine.game;

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class InputState implements KeyListener,MouseListener,MouseMotionListener,MouseWheelListener {
    private final boolean[] keys=new boolean[1024];private final boolean[] pressedKeys=new boolean[1024];private final boolean[] mouse=new boolean[8];private final boolean[] pressedMouse=new boolean[8];private double mouseDx,mouseDy;private int wheel;private Canvas canvas;private Robot robot;private boolean captured;private boolean warping;private int lastX=Integer.MIN_VALUE,lastY=Integer.MIN_VALUE;private Cursor normalCursor;
    public void attach(Canvas canvas){this.canvas=canvas;canvas.addKeyListener(this);canvas.addMouseListener(this);canvas.addMouseMotionListener(this);canvas.addMouseWheelListener(this);canvas.setFocusable(true);normalCursor=canvas.getCursor();try{robot=new Robot();robot.setAutoDelay(0);}catch(AWTException|SecurityException ignored){robot=null;}}
    public synchronized boolean keyDown(int code){return code>=0&&code<keys.length&&keys[code];}public synchronized boolean consumeKey(int code){if(code<0||code>=pressedKeys.length)return false;boolean value=pressedKeys[code];pressedKeys[code]=false;return value;}public synchronized boolean mouseDown(int button){return button>=0&&button<mouse.length&&mouse[button];}public synchronized boolean consumeMouse(int button){if(button<0||button>=pressedMouse.length)return false;boolean value=pressedMouse[button];pressedMouse[button]=false;return value;}public synchronized int consumeWheel(){int value=wheel;wheel=0;return value;}public synchronized double[] consumeMouseDelta(){double[] value={mouseDx,mouseDy};mouseDx=mouseDy=0;return value;}
    public synchronized void releaseAll(){Arrays.fill(keys,false);Arrays.fill(pressedKeys,false);Arrays.fill(mouse,false);Arrays.fill(pressedMouse,false);mouseDx=mouseDy=0;wheel=0;}
    public void setCaptured(boolean value){if(canvas==null||captured==value)return;captured=value;lastX=lastY=Integer.MIN_VALUE;if(value){BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);Cursor invisible=Toolkit.getDefaultToolkit().createCustomCursor(image,new Point(0,0),"echo-hidden");canvas.setCursor(invisible);centerMouse();canvas.requestFocusInWindow();}else{canvas.setCursor(normalCursor==null?Cursor.getDefaultCursor():normalCursor);}}
    public boolean captured(){return captured;}
    private void centerMouse(){if(robot==null||canvas==null||!canvas.isShowing())return;try{Point screen=canvas.getLocationOnScreen();int x=screen.x+canvas.getWidth()/2,y=screen.y+canvas.getHeight()/2;warping=true;robot.mouseMove(x,y);}catch(Exception ignored){}}
    @Override public synchronized void keyPressed(KeyEvent e){int code=e.getKeyCode();if(code>=0&&code<keys.length){if(!keys[code])pressedKeys[code]=true;keys[code]=true;}}
    @Override public synchronized void keyReleased(KeyEvent e){int code=e.getKeyCode();if(code>=0&&code<keys.length)keys[code]=false;}@Override public void keyTyped(KeyEvent e){}
    @Override public synchronized void mousePressed(MouseEvent e){int b=e.getButton();if(b>=0&&b<mouse.length){if(!mouse[b])pressedMouse[b]=true;mouse[b]=true;}if(canvas!=null)canvas.requestFocusInWindow();}
    @Override public synchronized void mouseReleased(MouseEvent e){int b=e.getButton();if(b>=0&&b<mouse.length)mouse[b]=false;}@Override public void mouseClicked(MouseEvent e){}@Override public void mouseEntered(MouseEvent e){}@Override public void mouseExited(MouseEvent e){}
    @Override public void mouseDragged(MouseEvent e){motion(e);}@Override public void mouseMoved(MouseEvent e){motion(e);}
    private void motion(MouseEvent e){synchronized(this){if(captured&&robot!=null&&canvas!=null&&canvas.isShowing()){try{Point screen=canvas.getLocationOnScreen();int cx=screen.x+canvas.getWidth()/2,cy=screen.y+canvas.getHeight()/2;int dx=e.getXOnScreen()-cx,dy=e.getYOnScreen()-cy;if(warping&&Math.abs(dx)<=1&&Math.abs(dy)<=1){warping=false;return;}mouseDx+=dx;mouseDy+=dy;}catch(Exception ignored){}centerMouse();return;}if(lastX!=Integer.MIN_VALUE){mouseDx+=e.getX()-lastX;mouseDy+=e.getY()-lastY;}lastX=e.getX();lastY=e.getY();}}
    @Override public synchronized void mouseWheelMoved(MouseWheelEvent e){wheel+=e.getWheelRotation();}
}
