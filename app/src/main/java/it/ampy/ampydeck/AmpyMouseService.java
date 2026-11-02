package it.ampy.ampydeck;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * AmpyDeck Mouse: draws the chosen pointer over every app and performs taps / long presses /
 * scrolls where it points. Driven by the keyboard's MOUSE touchpad (same ":ime" process).
 * It reads nothing on screen: it only shows a pointer and replays the gestures you ask for.
 */
public final class AmpyMouseService extends AccessibilityService {
    private static AmpyMouseService inst;
    public static AmpyMouseService get(){return inst;}

    private WindowManager wm;private PointerView view;private boolean shown;
    private float px=-1,py=-1;private int sw,sh;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();inst=this;CrashLog.install(this);
        wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(android.content.Intent i){hide();inst=null;return super.onUnbind(i);}
    @Override public void onDestroy(){hide();inst=null;super.onDestroy();}

    private void screen(){
        if(Build.VERSION.SDK_INT>=30){Rect b=wm.getMaximumWindowMetrics().getBounds();sw=b.width();sh=b.height();}
        else{android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);sw=dm.widthPixels;sh=dm.heightPixels;}
    }

    public void show(){
        try{
            if(shown)return;screen();
            if(px<0){px=sw/2f;py=sh*.4f;}
            view=new PointerView(this);
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            if(Build.VERSION.SDK_INT>=30)lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setTitle("AmpyDeck pointer");
            wm.addView(view,lp);shown=true;
        }catch(Throwable t){CrashLog.log("mouse-show",t);}
    }
    public void hide(){
        try{if(shown&&view!=null)wm.removeView(view);}catch(Throwable ignored){}
        shown=false;view=null;
    }
    public boolean isShown(){return shown;}

    public void move(float dx,float dy){
        if(!shown)show();
        px=Math.max(0,Math.min(sw-1,px+dx));py=Math.max(0,Math.min(sh-1,py+dy));
        if(view!=null)view.invalidate();
    }

    private void gesture(Path path,long dur){
        try{
            GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,dur)).build();
            dispatchGesture(g,null,null);
        }catch(Throwable t){CrashLog.log("mouse-gesture",t);}
    }
    public void click(boolean longPress){
        Path p=new Path();p.moveTo(px,py);p.lineTo(px+0.5f,py+0.5f);
        gesture(p,longPress?700:45);
        if(view!=null)view.ripple(longPress);
    }
    /** Positive dy = content moves up (like dragging the page up with a finger). */
    public void scroll(float dy){
        float len=Math.max(-sh*.45f,Math.min(sh*.45f,dy));
        float y0=Math.max(sh*.15f,Math.min(sh*.85f,py)),y1=Math.max(4,Math.min(sh-4,y0+len));
        Path p=new Path();p.moveTo(px,y0);p.lineTo(px,y1);gesture(p,220);
    }
    public void global(String what){
        switch(what){
            case "back":performGlobalAction(GLOBAL_ACTION_BACK);break;
            case "home":performGlobalAction(GLOBAL_ACTION_HOME);break;
            case "recents":performGlobalAction(GLOBAL_ACTION_RECENTS);break;
        }
    }

    /** Full-screen, touch-transparent layer that only paints the pointer. */
    private final class PointerView extends View {
        private long rippleAt;private boolean rippleLong;
        PointerView(Context c){super(c);}
        void ripple(boolean l){rippleAt=SystemClock.uptimeMillis();rippleLong=l;postInvalidateOnAnimation();}
        @Override protected void onDraw(Canvas c){
            Prefs pr=Prefs.get(getContext());Theme th=Theme.load(getContext());
            long dt=SystemClock.uptimeMillis()-rippleAt;long life=rippleLong?750:380;
            if(dt<life){float t=dt/(float)life;Paint rp=new Paint(Paint.ANTI_ALIAS_FLAG);rp.setStyle(Paint.Style.STROKE);
                rp.setStrokeWidth(Ui.dpf(3));rp.setColor(Theme.a(th.accent,(int)(220*(1-t))));c.drawCircle(px,py,Ui.dpf(8+34*t),rp);
                rp.setColor(Theme.a(th.secondary,(int)(160*(1-t))));c.drawCircle(px,py,Ui.dpf(4+20*t),rp);postInvalidateOnAnimation();}
            Pointers.draw(c,pr.s("pointer","neon"),px,py,Ui.dpf(26)*pr.f("pointer_size",1f),Pointers.color(th,pr.s("pointer_color","accent")),th);
        }
    }
}
