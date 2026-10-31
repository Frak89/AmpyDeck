package it.ampy.ampydeck;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/** Small helpers + the chamfered neon panel used everywhere. */
public final class Ui {
    private Ui(){}
    public static final Typeface MONO=Typeface.MONOSPACE;
    public static final Typeface MONO_BOLD=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);

    public static float density(){return Resources.getSystem().getDisplayMetrics().density;}
    public static int dp(float v){return (int)(v*density()+.5f);}
    public static float dpf(float v){return v*density();}

    public static TextView text(Context c,String s,float sp,int color,boolean bold){
        TextView t=new TextView(c);t.setText(s);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        t.setTextColor(color);t.setTypeface(bold?MONO_BOLD:MONO);t.setIncludeFontPadding(false);
        t.setLetterSpacing(.06f);return t;
    }

    public static TextView button(Context c,Theme th,String s,View.OnClickListener l){
        TextView t=text(c,s,12,th.accent,true);t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14),dp(11),dp(14),dp(11));t.setBackground(new NeonPanel(th,th.accent,.55f));
        Blink bl=Blink.attach(t,th,th.accent,false,4);
        t.setOnClickListener(v->{tick(v);bl.burst();l.onClick(v);});return t;
    }

    public static void tick(View v){
        if(v!=null&&Prefs.get(v.getContext()).haptics())
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
    public static void longTick(View v){
        if(v!=null&&Prefs.get(v.getContext()).haptics())
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
    public static void buzz(Context c,int ms){
        if(!Prefs.get(c).haptics())return;
        try{Vibrator vb=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
            if(vb!=null)vb.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));}
        catch(Throwable ignored){}
    }
    public static void toast(Context c,String s){Toast.makeText(c,s,Toast.LENGTH_SHORT).show();}

    /** Cyberpunk panel: chamfered corners, translucent fill, neon edge, corner ticks. */
    public static final class NeonPanel extends Drawable {
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),edge=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        private final Theme th;private final int edgeColor;private final float fillAlpha;
        public boolean scan=false;public float cut=dpf(10);
        public NeonPanel(Theme th,int edgeColor,float fillAlpha){
            this.th=th;this.edgeColor=edgeColor;this.fillAlpha=fillAlpha;
            edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(dpf(1.2f));
        }
        @Override public void draw(Canvas c){
            Rect b=getBounds();if(b.isEmpty())return;
            float l=b.left+1,t=b.top+1,r=b.right-1,bo=b.bottom-1,k=Math.min(cut,Math.min(b.width(),b.height())/3f);
            path.reset();path.moveTo(l+k,t);path.lineTo(r,t);path.lineTo(r,bo-k);path.lineTo(r-k,bo);
            path.lineTo(l,bo);path.lineTo(l,t+k);path.close();
            if(th.isGlass){
                fill.setShader(new LinearGradient(0,t,0,bo,Theme.a(th.panel2,(int)(255*fillAlpha)),
                    Theme.a(th.panel,(int)(220*fillAlpha)),Shader.TileMode.CLAMP));
            }else{fill.setShader(null);fill.setColor(Theme.a(th.panel,(int)(255*fillAlpha)));}
            c.drawPath(path,fill);
            if(scan){
                fill.setShader(null);fill.setColor(0x0affffff);
                for(float y=t;y<bo;y+=dpf(3))c.drawRect(l,y,r,y+dpf(1),fill);
            }
            edge.setColor(Theme.a(edgeColor,150));edge.setStrokeWidth(dpf(1));c.drawPath(path,edge);
            edge.setColor(edgeColor);edge.setStrokeWidth(dpf(2.2f));
            float m=dpf(14);
            c.drawLine(l+k,t,l+k+m,t,edge);c.drawLine(r-k,bo,r-k-m,bo,edge);
            edge.setColor(th.secondary);
            c.drawLine(r,t,r,t+m,edge);c.drawLine(l,bo,l,bo-m,edge);
        }
        @Override public void setAlpha(int a){fill.setAlpha(a);edge.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    /** Neon chip with blink: burst on tap, continuous while "selected". */
    public static TextView chip(Context c,Theme th,String s,int col,View.OnClickListener l){
        TextView t=text(c,s,11,col,true);t.setPadding(dp(10),dp(8),dp(10),dp(8));
        t.setBackground(new NeonPanel(th,col,.5f));Blink bl=Blink.attach(t,th,col,false,4);t.setTag(0x7f0a0003,bl);
        t.setOnClickListener(v->{tick(v);bl.burst();l.onClick(v);});return t;
    }
    public static void setSelected(View chip,boolean on){
        Object o=chip.getTag(0x7f0a0003);if(o instanceof Blink)((Blink)o).setContinuous(on);
    }

    public static boolean atLeast(int api){return Build.VERSION.SDK_INT>=api;}
}
