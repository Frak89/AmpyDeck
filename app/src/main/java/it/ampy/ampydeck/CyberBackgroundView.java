package it.ampy.ampydeck;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Animated procedural backdrops (ported 1:1 from AmpyRocket) drawn behind the keys. */
public final class CyberBackgroundView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd=new Random(89);
    private Theme th;private String mode;private boolean scan;private int dim;private boolean eco;
    private boolean running=true;private float parallax=0;private long last;
    private float[] px,py,vx,vy;            // particles
    private float[] dropY,dropSpeed;private char[][] dropChars;   // rain
    private float[] sx,sy,sz;               // stars
    private Bitmap scanBmp;
    private float pulse=0;                 // energy pulse (e.g. on unlock / page change)

    public CyberBackgroundView(Context c){super(c);reload();}

    public void reload(){
        Prefs pr=Prefs.get(getContext());th=Theme.load(getContext());mode=pr.background();
        scan=pr.scanlines();dim=pr.bgDim();eco=pr.ecoFps();px=null;dropY=null;sx=null;invalidate();
    }
    public void setRunning(boolean r){running=r;if(r)postInvalidateOnAnimation();}
    public void setParallax(float f){parallax=f;}
    public void pulse(){pulse=1f;postInvalidateOnAnimation();}

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){px=null;dropY=null;sx=null;scanBmp=null;}

    @Override protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();if(w==0)return;
        long now=SystemClock.uptimeMillis();float t=now/1000f;
        // keyboards have no wallpaper below: solid theme base, then the AmpyRocket gradient
        c.drawColor(th.bg);
        p.setShader(new LinearGradient(0,0,0,h,
            new int[]{Theme.a(th.top,dim*255/100),Theme.a(th.bg,dim*230/100),Theme.a(th.bottom,Math.min(255,dim*300/100))},
            null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        switch(mode){
            case "grid":drawSynth(c,w,h,t);break;
            case "rain":drawRain(c,w,h);break;
            case "particles":drawParticles(c,w,h);break;
            case "hex":drawHex(c,w,h,t);break;
            case "starfield":drawStars(c,w,h);break;
            default:break;
        }
        if(scan)drawScan(c,w,h,t);
        if(pulse>0){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(3));
            p.setColor(Theme.a(th.accent,(int)(160*pulse)));
            float r=(1-pulse)*Math.max(w,h);c.drawCircle(w/2f,h*.55f,r,p);
            p.setColor(Theme.a(th.secondary,(int)(110*pulse)));c.drawCircle(w/2f,h*.55f,r*.8f,p);
            p.setStyle(Paint.Style.FILL);pulse-=.035f;
        }
        if(running&&!"plain".equals(mode)||pulse>0){
            if(eco){long d=now-last;last=now;postInvalidateDelayed(Math.max(0,33-d));}
            else postInvalidateOnAnimation();
        }else if(scan&&running){postInvalidateDelayed(120);}
    }

    private void drawSynth(Canvas c,int w,int h,float t){
        float horizon=h*.62f;
        // neon sun
        float sunR=w*.22f,sx0=w/2f-parallax*w*.08f;
        p.setShader(new LinearGradient(0,horizon-sunR*1.6f,0,horizon,Theme.a(th.warn,150),Theme.a(th.secondary,140),Shader.TileMode.CLAMP));
        c.save();c.clipRect(0,0,w,horizon);
        c.drawCircle(sx0,horizon-sunR*.35f,sunR,p);p.setShader(null);
        p.setColor(Theme.a(th.bg,200));
        for(int i=0;i<7;i++){float y=horizon-sunR*.35f+i*sunR*.16f-sunR*.1f;c.drawRect(0,y,w,y+Ui.dpf(1+i*.8f),p);}
        c.restore();
        // perspective floor
        p.setStrokeWidth(Ui.dpf(1.2f));
        float speed=(t*.35f)%1f;
        for(int i=0;i<18;i++){
            float z=(i+speed)/18f;float y=horizon+(h-horizon)*z*z;
            p.setColor(Theme.a(th.accent,(int)(30+150*z)));c.drawLine(0,y,w,y,p);
        }
        int lines=22;float cx=w/2f-parallax*w*.25f;
        for(int i=-lines;i<=lines;i++){
            float xb=cx+i*(w/7f);
            p.setColor(Theme.a(th.secondary,90));c.drawLine(cx+i*(w/90f),horizon,xb,h,p);
        }
        p.setColor(Theme.a(th.accent,180));p.setStrokeWidth(Ui.dpf(2));c.drawLine(0,horizon,w,horizon,p);
        // distant skyline
        p.setColor(Theme.a(th.bottom,230));rnd.setSeed(7);
        for(float x=-parallax*w*.15f;x<w;){
            float bw=Ui.dpf(14+rnd.nextInt(30)),bh=Ui.dpf(20+rnd.nextInt(90));
            c.drawRect(x,horizon-bh,x+bw,horizon,p);
            if(rnd.nextInt(3)==0){p.setColor(Theme.a(th.accent,120));
                c.drawRect(x+bw*.3f,horizon-bh+Ui.dpf(6),x+bw*.3f+Ui.dpf(3),horizon-bh+Ui.dpf(9),p);
                p.setColor(Theme.a(th.bottom,230));}
            x+=bw+Ui.dpf(2);
        }
    }

    private void drawRain(Canvas c,int w,int h){
        float cell=Ui.dpf(16);int cols=(int)(w/cell)+1;int rows=(int)(h/cell)+2;
        if(dropY==null||dropY.length!=cols){
            dropY=new float[cols];dropSpeed=new float[cols];dropChars=new char[cols][rows];
            for(int i=0;i<cols;i++){dropY[i]=-rnd.nextInt(rows)*cell;dropSpeed[i]=Ui.dpf(2+rnd.nextFloat()*5);
                for(int j=0;j<rows;j++)dropChars[i][j]=GlitchTextView.GLYPHS.charAt(rnd.nextInt(GlitchTextView.GLYPHS.length()));}
        }
        p.setTypeface(Ui.MONO_BOLD);p.setTextSize(cell*.85f);
        for(int i=0;i<cols;i++){
            dropY[i]+=dropSpeed[i];if(dropY[i]-rows*cell*.5f>h){dropY[i]=-rnd.nextInt(20)*cell;}
            int trail=14;float x=i*cell-(parallax*cell*3)%cell;
            for(int k=0;k<trail;k++){
                float y=dropY[i]-k*cell;if(y<0||y>h+cell)continue;
                int row=Math.abs((int)(y/cell))%rows;
                if(rnd.nextInt(30)==0)dropChars[i][row]=GlitchTextView.GLYPHS.charAt(rnd.nextInt(GlitchTextView.GLYPHS.length()));
                int a=(int)(220*(1-k/(float)trail));
                p.setColor(k==0?Theme.a(th.fg,240):Theme.a(i%7==0?th.secondary:th.accent,a));
                c.drawText(String.valueOf(dropChars[i][row]),x,y,p);
            }
        }
    }

    private void drawParticles(Canvas c,int w,int h){
        int n=46;
        if(px==null){px=new float[n];py=new float[n];vx=new float[n];vy=new float[n];
            for(int i=0;i<n;i++){px[i]=rnd.nextFloat()*w;py[i]=rnd.nextFloat()*h;
                vx[i]=(rnd.nextFloat()-.5f)*Ui.dpf(.9f);vy[i]=(rnd.nextFloat()-.5f)*Ui.dpf(.9f);}}
        float link=Ui.dpf(120);
        for(int i=0;i<n;i++){px[i]+=vx[i];py[i]+=vy[i];
            if(px[i]<0||px[i]>w)vx[i]=-vx[i];if(py[i]<0||py[i]>h)vy[i]=-vy[i];}
        p.setStrokeWidth(Ui.dpf(1));
        float off=-parallax*w*.1f;
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
            float dx=px[i]-px[j],dy=py[i]-py[j],d=(float)Math.sqrt(dx*dx+dy*dy);
            if(d<link){p.setColor(Theme.a((i+j)%3==0?th.secondary:th.accent,(int)(120*(1-d/link))));
                c.drawLine(px[i]+off,py[i],px[j]+off,py[j],p);}
        }
        for(int i=0;i<n;i++){p.setColor(i%4==0?th.secondary:th.accent);c.drawCircle(px[i]+off,py[i],Ui.dpf(i%5==0?3:1.8f),p);}
    }

    private void drawHex(Canvas c,int w,int h,float t){
        float r=Ui.dpf(26),hw=(float)(Math.sqrt(3)*r);Path hex=new Path();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1));
        int row=0;
        for(float y=-r;y<h+r;y+=r*1.5f,row++){
            for(float x=(row%2==0?0:hw/2)-parallax*hw;x<w+hw;x+=hw){
                hex.reset();
                for(int k=0;k<6;k++){double a=Math.PI/3*k+Math.PI/6;
                    float hx=(float)(x+r*Math.cos(a)),hy=(float)(y+r*Math.sin(a));
                    if(k==0)hex.moveTo(hx,hy);else hex.lineTo(hx,hy);}hex.close();
                float wave=(float)(.5+.5*Math.sin(t*1.6-(x+y)/(w*.35)));
                p.setColor(Theme.a(wave>.85f?th.secondary:th.accent,(int)(18+wave*70)));c.drawPath(hex,p);
                if(wave>.97f){p.setStyle(Paint.Style.FILL);p.setColor(Theme.a(th.accent,40));c.drawPath(hex,p);p.setStyle(Paint.Style.STROKE);}
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawStars(Canvas c,int w,int h){
        int n=160;if(sx==null){sx=new float[n];sy=new float[n];sz=new float[n];
            for(int i=0;i<n;i++){sx[i]=(rnd.nextFloat()-.5f)*w;sy[i]=(rnd.nextFloat()-.5f)*h;sz[i]=rnd.nextFloat()*w;}}
        float cx=w/2f-parallax*w*.2f,cy=h/2f;p.setStrokeWidth(Ui.dpf(1.5f));
        for(int i=0;i<n;i++){
            float pz=sz[i];sz[i]-=Ui.dpf(6);if(sz[i]<1){sz[i]=w;sx[i]=(rnd.nextFloat()-.5f)*w;sy[i]=(rnd.nextFloat()-.5f)*h;pz=sz[i];}
            float k=w*.5f;float x1=cx+sx[i]/sz[i]*k,y1=cy+sy[i]/sz[i]*k,x0=cx+sx[i]/pz*k,y0=cy+sy[i]/pz*k;
            int a=(int)(255*(1-sz[i]/w));p.setColor(Theme.a(i%5==0?th.secondary:th.accent,a));c.drawLine(x0,y0,x1,y1,p);
        }
    }

    private void drawScan(Canvas c,int w,int h,float t){
        if(scanBmp==null){
            scanBmp=Bitmap.createBitmap(1,(int)Ui.dpf(4),Bitmap.Config.ARGB_8888);
            scanBmp.setPixel(0,0,0x16000000);scanBmp.setPixel(0,1,0x10000000);
        }
        Paint sp=new Paint();sp.setShader(new BitmapShader(scanBmp,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT));
        c.drawRect(0,0,w,h,sp);
        float y=(t*.12f%1f)*h;
        p.setShader(new LinearGradient(0,y-Ui.dpf(60),0,y,0x00000000,Theme.a(th.accent,22),Shader.TileMode.CLAMP));
        c.drawRect(0,y-Ui.dpf(60),w,y,p);p.setShader(null);
    }
}
