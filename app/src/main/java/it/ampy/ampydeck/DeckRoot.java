package it.ampy.ampydeck;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Random;

/**
 * Input view root: AmpyRocket animated background behind the key surface, plus the
 * entry animation — the whole keyboard is rendered off-screen and played through the
 * AmpyRocket "Neon Genie" mesh (rising out of a slot at the bottom) followed by a glitch
 * burst (RGB split, slice tearing, scan flash) while the key caps decode letter by letter.
 */
public final class DeckRoot extends FrameLayout {
    private static final Random R=new Random();
    public final CyberBackgroundView bg;public final KeyboardView kv;
    private Theme th;private String mode="glitchgenie";private long dur=700;
    private boolean entering;private long start;
    private Bitmap buf;private Canvas bufCanvas;
    private final float[] verts=new float[(GenieFx.MW+1)*(GenieFx.MH+1)*2];
    private final Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),ep=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint red=new Paint(Paint.FILTER_BITMAP_FLAG),cyan=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF big=new RectF(),small=new RectF();private final Rect src=new Rect(),dst=new Rect();

    public DeckRoot(Context c,KeyboardView.Listener l){
        super(c);
        bg=new CyberBackgroundView(c);kv=new KeyboardView(c,l);
        addView(bg,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        addView(kv,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        setWillNotDraw(false);
        // channel-isolating filters for the chromatic split
        red.setColorFilter(new ColorMatrixColorFilter(new float[]{1,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0, 0,0,0,.75f,0}));
        cyan.setColorFilter(new ColorMatrixColorFilter(new float[]{0,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,.75f,0}));
        ep.setStyle(Paint.Style.STROKE);
        reload();
    }

    private int navPad;
    /**
     * Apps targeting Android 15+ can get the IME window pushed down by the navigation-bar height:
     * the bottom key row then sits under the system bar (its "v" hide button steals taps on
     * ?123 / , / ☺). After the window settles we measure where the keys really end and lift
     * them above the bar by exactly the overlap; devices without the issue are left untouched.
     */
    public void checkFit(){
        try{
            if(!isAttachedToWindow()||kv.getHeight()==0)return;
            int[] o=new int[2];kv.getLocationOnScreen(o);int kvBottom=o[1]+kv.getHeight();
            int screenH,nav=0;
            android.view.WindowManager wm=(android.view.WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            if(android.os.Build.VERSION.SDK_INT>=30){
                screenH=wm.getCurrentWindowMetrics().getBounds().height();
                android.view.WindowInsets wi=getRootWindowInsets();
                if(wi!=null)nav=Math.max(wi.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom,
                    wi.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom);
            }else{android.util.DisplayMetrics dm=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);screenH=dm.heightPixels;
                android.view.WindowInsets wi=getRootWindowInsets();if(wi!=null)nav=wi.getStableInsetBottom();}
            int limit=screenH-nav;int np=navPad;
            if(kvBottom>limit+2)np=navPad+(kvBottom-limit);
            else if(kvBottom<limit-2&&navPad>0)np=Math.max(0,navPad-(limit-kvBottom));
            android.util.Log.i("AmpyDeckKeys","FIT kvBottom="+kvBottom+" limit="+limit+" nav="+nav+" navPad="+navPad+"->"+np);
            if(np!=navPad){navPad=np;requestLayout();}
        }catch(Throwable t){CrashLog.log("checkFit",t);}
    }
    public void scheduleFit(){removeCallbacks(fit);postDelayed(fit,450);postDelayed(fit2,1300);postDelayed(fit3,2600);}
    private final Runnable fit=this::checkFit,fit2=this::checkFit,fit3=this::checkFit;

    /** Height is the key surface's (+ nav bar); the background just fills it (never the whole screen). */
    @Override protected void onMeasure(int ws,int hs){
        kv.measure(ws,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        int w=MeasureSpec.getSize(ws),h=kv.getMeasuredHeight()+navPad;
        bg.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
        setMeasuredDimension(w,h);
    }

    public void reload(){
        Prefs pr=Prefs.get(getContext());th=Theme.load(getContext());mode=pr.entry();dur=Math.max(250,pr.entryMs());
        bg.reload();kv.reload();invalidate();
    }
    public void setRunning(boolean r){bg.setRunning(r);}

    /** Plays the configured entry animation (called each time the keyboard window appears). */
    public void playEntry(){
        if("none".equals(mode)){kv.startDecode(0);return;}
        entering=true;start=SystemClock.uptimeMillis();
        if(!"genie".equals(mode))kv.startDecode((long)(dur*.9f));
        bg.pulse();
        invalidate();
    }

    @Override protected void dispatchDraw(Canvas c){
        if(!entering){super.dispatchDraw(c);return;}
        try{entryDraw(c);}catch(Throwable t){CrashLog.log("entryFx",t);entering=false;super.dispatchDraw(c);}
    }
    private void entryDraw(Canvas c){
        if(!entering||getWidth()==0||getHeight()==0){super.dispatchDraw(c);return;}
        int w=getWidth(),h=getHeight();
        float t=(SystemClock.uptimeMillis()-start)/(float)dur;
        if(t>=1){entering=false;super.dispatchDraw(c);return;}
        if(buf==null||buf.getWidth()!=w||buf.getHeight()!=h){
            if(buf!=null)buf.recycle();buf=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bufCanvas=new Canvas(buf);}
        buf.eraseColor(0);
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);if(v.getVisibility()!=VISIBLE)continue;
            bufCanvas.save();bufCanvas.translate(v.getLeft(),v.getTop());v.draw(bufCanvas);bufCanvas.restore();}

        boolean genie=!"glitch".equals(mode),glitch=!"genie".equals(mode);
        float split=genie&&glitch?.58f:genie?1f:0f;   // share of time spent on the genie
        if(genie&&t<split){
            float g=t/split;
            big.set(0,0,w,h);
            float sw=w*.16f,sh=Math.max(Ui.dpf(10),h*.07f);
            small.set(w/2f-sw/2f,h-sh,w/2f+sw/2f,h);
            GenieFx.mesh(verts,big,small,1-GenieFx.ease(g));
            bp.setAlpha((int)(255*Math.min(1,.25f+g*1.6f)));
            c.drawBitmapMesh(buf,GenieFx.MW,GenieFx.MH,verts,0,null,0,bp);
            drawMeshEdges(c,1-g);
            if(glitch&&R.nextInt(3)==0)tear(c,w,h,.35f,Ui.dpf(10));      // a few early sparks
        }else{
            float gt=glitch?(t-split)/(1-split):1;
            float amp=1-GenieFx.ease(gt);
            float off=Ui.dpf(2+R.nextFloat()*7)*amp;
            // chromatic ghosts
            c.drawBitmap(buf,-off,0,red);c.drawBitmap(buf,off,Ui.dpf(R.nextInt(3)-1)*amp,cyan);
            bp.setAlpha(255);
            if(amp>.08f){
                // base image cut in horizontal bands, some displaced
                int bands=6+R.nextInt(6);float y=0;
                while(y<h){
                    float bh=Ui.dpf(4+R.nextInt(26));float dx=R.nextInt(4)==0?(R.nextFloat()-.5f)*Ui.dpf(46)*amp:0;
                    src.set(0,(int)y,w,(int)Math.min(h,y+bh));dst.set((int)dx,(int)y,(int)(w+dx),(int)Math.min(h,y+bh));
                    c.drawBitmap(buf,src,dst,bp);y+=bh;
                    if(--bands<0&&R.nextBoolean())break;
                }
                if(y<h){src.set(0,(int)y,w,h);c.drawBitmap(buf,src,src,bp);}
                tear(c,w,h,amp,Ui.dpf(30));
                // scan flash sweeping up
                float sy=h*(1-gt);
                ep.setStyle(Paint.Style.FILL);ep.setShader(new LinearGradient(0,sy,0,sy+Ui.dpf(26),Theme.a(th.accent,(int)(150*amp)),0,Shader.TileMode.CLAMP));
                c.drawRect(0,sy,w,sy+Ui.dpf(26),ep);ep.setShader(null);ep.setStyle(Paint.Style.STROKE);
            }else c.drawBitmap(buf,0,0,bp);
        }
        postInvalidateOnAnimation();
    }

    /** Neon outline of the genie mesh (left and right edges + top edge). */
    private void drawMeshEdges(Canvas c,float strength){
        ep.setStyle(Paint.Style.STROKE);ep.setStrokeWidth(Ui.dpf(2));
        Path left=new Path(),right=new Path(),top=new Path();
        for(int r=0;r<=GenieFx.MH;r++){int i=r*(GenieFx.MW+1)*2,j=i+GenieFx.MW*2;
            if(r==0){left.moveTo(verts[i],verts[i+1]);right.moveTo(verts[j],verts[j+1]);}
            else{left.lineTo(verts[i],verts[i+1]);right.lineTo(verts[j],verts[j+1]);}}
        for(int k=0;k<=GenieFx.MW;k++){if(k==0)top.moveTo(verts[0],verts[1]);else top.lineTo(verts[k*2],verts[k*2+1]);}
        int a=(int)(220*Math.max(.25f,strength));
        ep.setColor(Theme.a(th.accent,a/3));ep.setStrokeWidth(Ui.dpf(6));c.drawPath(left,ep);c.drawPath(right,ep);
        ep.setStrokeWidth(Ui.dpf(2));ep.setColor(Theme.a(th.accent,a));c.drawPath(left,ep);
        ep.setColor(Theme.a(th.secondary,a));c.drawPath(right,ep);c.drawPath(top,ep);
    }

    /** Random torn slices + neon glitch bars. */
    private void tear(Canvas c,int w,int h,float amp,float maxShift){
        int n=2+R.nextInt(4);
        for(int i=0;i<n;i++){
            int y=R.nextInt(Math.max(1,h)),bh=(int)Ui.dpf(2+R.nextInt(10));float dx=(R.nextFloat()-.5f)*2*maxShift*amp;
            src.set(0,y,w,Math.min(h,y+bh));dst.set((int)dx,y,(int)(w+dx),Math.min(h,y+bh));
            c.drawBitmap(buf,src,dst,R.nextBoolean()?cyan:red);
            if(R.nextInt(3)==0){ep.setStyle(Paint.Style.FILL);ep.setColor(Theme.a(R.nextBoolean()?th.accent:th.secondary,(int)(170*amp)));
                float bx=R.nextFloat()*w;c.drawRect(bx,y,bx+Ui.dpf(30+R.nextInt(120)),y+Ui.dpf(2),ep);ep.setStyle(Paint.Style.STROKE);}
        }
    }

    @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(buf!=null){buf.recycle();buf=null;}}
}
