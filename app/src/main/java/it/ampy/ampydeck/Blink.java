package it.ampy.ampydeck;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.view.View;

/**
 * AmpyRAW-style blink: selected items pulse their neon frame on/off;
 * pressed buttons fire a short blink burst. Speed and on/off live in Settings.
 */
public final class Blink extends Drawable implements Runnable {
    public static final int[] PERIODS={60,100,150,250,400,650,1000,2000};
    public static final String[] PERIOD_NAMES={"60 ms · ultra rapido","100 ms · rapido","150 ms · veloce","250 ms · normale",
        "400 ms · medio","650 ms · lento","1000 ms · 1 secondo","2000 ms · 2 secondi"};

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Theme th;private final int color;
    private boolean continuous;private long burstUntil=0;private final int period;private final boolean enabled,allowContinuous;
    private final float radius;

    public Blink(Context c,Theme th,int color,boolean continuous,float radiusDp){
        this.th=th;this.color=color;this.continuous=continuous;this.radius=Ui.dpf(radiusDp);
        Prefs pr=Prefs.get(c);period=Math.max(40,pr.i("blink_period_ms",250));String mode=pr.s("blink_mode","selected");
        enabled=!"off".equals(mode);allowContinuous="selected".equals(mode);
    }

    public void setContinuous(boolean on){continuous=on;invalidateSelf();}
    /** Short burst of 3 blinks (e.g. on tap). */
    public void burst(){burstUntil=SystemClock.uptimeMillis()+period*6L;invalidateSelf();}

    private boolean active(){return enabled&&(continuous&&allowContinuous||SystemClock.uptimeMillis()<burstUntil);}

    @Override public void draw(Canvas c){
        if(!active())return;
        long t=SystemClock.uptimeMillis();boolean on=(t/period)%2==0;
        Rect b=getBounds();RectF r=new RectF(b);r.inset(Ui.dpf(1.5f),Ui.dpf(1.5f));
        if(on){
            p.setStyle(Paint.Style.FILL);p.setColor(Theme.a(color,58));c.drawRoundRect(r,radius,radius,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(2.2f));p.setColor(color);c.drawRoundRect(r,radius,radius,p);
            p.setStrokeWidth(Ui.dpf(1));p.setColor(Theme.a(0xffffffff,120));r.inset(Ui.dpf(3),Ui.dpf(3));c.drawRoundRect(r,radius,radius,p);
        }else{
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.2f));p.setColor(Theme.a(color,90));c.drawRoundRect(r,radius,radius,p);
        }
        scheduleSelf(this,t-(t%period)+period);
    }
    @Override public void run(){invalidateSelf();}
    @Override public void setAlpha(int a){}
    @Override public void setColorFilter(ColorFilter f){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}

    /** Puts a blink layer on top of the view's current background and returns it. */
    public static Blink attach(View v,Theme th,int color,boolean continuous,float radiusDp){
        Blink bl=new Blink(v.getContext(),th,color,continuous,radiusDp);
        Drawable bg=v.getBackground();
        v.setBackground(bg==null?bl:new LayerDrawable(new Drawable[]{bg,bl}));
        return bl;
    }
}
