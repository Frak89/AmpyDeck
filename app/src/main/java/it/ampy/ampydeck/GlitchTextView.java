package it.ampy.ampydeck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Text that "decodes" letter by letter (random glyph cycling, then lock-in),
 * with chromatic RGB split and horizontal slice tearing in idle glitch bursts.
 */
public class GlitchTextView extends View {
    public static final String GLYPHS="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#$%&@*+=<>/\\|{}[]?!ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄ";
    static final Random R=new Random();

    /** Pure scramble engine, reusable by views that draw text themselves. */
    public static final class Scrambler {
        private String target="";private char[] shown=new char[0];private long[] lockAt=new long[0];
        private long start;public int stepMs=28,spreadMs=420;
        public void set(String s,boolean animate){
            if(s==null)s="";target=s;shown=s.toCharArray();lockAt=new long[s.length()];
            start=SystemClock.uptimeMillis();
            for(int i=0;i<lockAt.length;i++)
                lockAt[i]=animate?start+(long)(spreadMs*(i+1f)/Math.max(1,lockAt.length))+R.nextInt(120):0;
        }
        public void replay(){set(target,true);}
        public String target(){return target;}
        public boolean active(){long n=SystemClock.uptimeMillis();for(long l:lockAt)if(l>n)return true;return false;}
        public String frame(){
            long n=SystemClock.uptimeMillis();
            for(int i=0;i<shown.length;i++){
                char t=target.charAt(i);
                if(n>=lockAt[i]||t==' '||t==':'||t=='.'||t=='/')shown[i]=t;
                else if((n/stepMs+i)%2==0)shown[i]=GLYPHS.charAt(R.nextInt(GLYPHS.length()));
            }
            return new String(shown);
        }
    }

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Scrambler sc=new Scrambler();
    private final Theme th;
    private int color,glowA,glowB;
    private float textSize;
    private boolean center=true,burst=false;
    private long burstUntil=0;
    private int level;
    private final Runnable idle=new Runnable(){@Override public void run(){
        if(!isAttachedToWindow())return;
        if(level>0&&getVisibility()==VISIBLE){burst=true;burstUntil=SystemClock.uptimeMillis()+90+R.nextInt(160*level);
            if(R.nextInt(4)==0)sc.replay();postInvalidateOnAnimation();}
        scheduleIdle();
    }};

    public GlitchTextView(Context c,Theme th,float sizeSp,int color,boolean bold){
        super(c);this.th=th;this.color=color;glowA=th.accent;glowB=th.secondary;
        level=Prefs.get(c).glitch();
        textSize=sizeSp*getResources().getDisplayMetrics().scaledDensity;
        p.setTextSize(textSize);p.setTypeface(bold?Ui.MONO_BOLD:Ui.MONO);p.setLetterSpacing(.08f);
    }
    public GlitchTextView alignLeft(){center=false;return this;}
    public void setColor(int c){color=c;invalidate();}
    public void setGlow(int a,int b){glowA=a;glowB=b;invalidate();}
    public void setTypeface(Typeface tf){p.setTypeface(tf);requestLayout();}
    public void setLevel(int l){level=l;}
    public void setTextSizePx(float px){textSize=px;p.setTextSize(px);requestLayout();invalidate();}
    public String getText(){return sc.target();}

    /** Sets text; when the content changes it decodes with the glitch letter swap. */
    public void setText(String s){setText(s,true);}
    public void setText(String s,boolean animate){
        boolean changed=!s.equals(sc.target());
        if(!changed)return;
        boolean relayout=s.length()!=sc.target().length();
        sc.set(s,animate&&level>0);
        if(relayout)requestLayout();
        postInvalidateOnAnimation();
    }
    public void glitchNow(){sc.replay();burst=true;burstUntil=SystemClock.uptimeMillis()+260;postInvalidateOnAnimation();}

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();scheduleIdle();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(idle);super.onDetachedFromWindow();}
    private void scheduleIdle(){removeCallbacks(idle);if(level>0)postDelayed(idle,2500+R.nextInt(9000/level));}

    @Override protected void onMeasure(int w,int h){
        float tw=p.measureText(sc.target().isEmpty()?" ":sc.target());
        Paint.FontMetrics fm=p.getFontMetrics();
        int mw=(int)Math.ceil(tw+Ui.dpf(8)+getPaddingLeft()+getPaddingRight());
        int mh=(int)Math.ceil(fm.descent-fm.ascent+Ui.dpf(4)+getPaddingTop()+getPaddingBottom());
        setMeasuredDimension(resolveSize(mw,w),resolveSize(mh,h));
    }

    @Override protected void onDraw(Canvas c){
        String s=sc.frame();
        Paint.FontMetrics fm=p.getFontMetrics();
        float tw=p.measureText(s);
        float x=center?(getWidth()-tw)/2f:getPaddingLeft()+Ui.dpf(2);
        float y=(getHeight()-(fm.descent-fm.ascent))/2f-fm.ascent;
        long now=SystemClock.uptimeMillis();
        boolean scrambling=sc.active();
        boolean glitching=burst&&now<burstUntil;
        if(!glitching)burst=false;
        float split=(scrambling||glitching)?Ui.dpf(1.5f+R.nextFloat()*2.5f):Ui.dpf(.9f);
        // chromatic layers
        p.setColor(Theme.a(glowB,glitching?200:120));c.drawText(s,x-split,y,p);
        p.setColor(Theme.a(glowA,glitching?200:120));c.drawText(s,x+split,y,p);
        if(glitching){
            int h=getHeight(),slices=3+R.nextInt(4);
            for(int i=0;i<slices;i++){
                float top=R.nextFloat()*h,bh=Ui.dpf(2+R.nextInt(7));
                c.save();c.clipRect(0,top,getWidth(),top+bh);
                c.translate((R.nextFloat()-.5f)*Ui.dpf(18),0);
                p.setColor(R.nextBoolean()?glowA:color);c.drawText(s,x,y,p);c.restore();
            }
            c.save();c.clipRect(0,0,getWidth(),getHeight()*.45f);c.translate(Ui.dpf(R.nextInt(5)-2),0);
            p.setColor(color);c.drawText(s,x,y,p);c.restore();
            c.save();c.clipRect(0,getHeight()*.45f,getWidth(),getHeight());
            p.setColor(color);c.drawText(s,x,y,p);c.restore();
        }else{
            p.setColor(color);c.drawText(s,x,y,p);
        }
        if(scrambling||glitching)postInvalidateOnAnimation();
    }
}
