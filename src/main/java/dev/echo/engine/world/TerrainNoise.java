package dev.echo.engine.world;

public final class TerrainNoise {
    private TerrainNoise(){}
    public static double value2(long seed,double x,double z){int x0=(int)Math.floor(x),z0=(int)Math.floor(z);double tx=smooth(x-x0),tz=smooth(z-z0);double a=hash(seed,x0,z0),b=hash(seed,x0+1,z0),c=hash(seed,x0,z0+1),d=hash(seed,x0+1,z0+1);return lerp(lerp(a,b,tx),lerp(c,d,tx),tz);}
    public static double fractal(long seed,double x,double z,int octaves){double value=0,amplitude=1,total=0,frequency=1;for(int i=0;i<octaves;i++){value+=value2(seed+i*9973,x*frequency,z*frequency)*amplitude;total+=amplitude;amplitude*=0.5;frequency*=2;}return value/Math.max(1e-9,total);}
    private static double smooth(double t){return t*t*(3-2*t);}private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static double hash(long seed,int x,int z){long n=seed+x*341873128712L+z*132897987541L;n=(n^(n>>>13))*1274126177L;n^=n>>>16;return((n&0xFFFFFF)/(double)0x7FFFFF)-1.0;}
}
