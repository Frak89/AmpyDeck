package it.ampy.ampydeck;

import android.graphics.*;
import android.view.View;

/** "Magic lamp" genie morph: bitmap mesh pulled between two rects, bottom rows lead. */
public final class GenieFx {
    public static final int MW=12,MH=24;
    private GenieFx(){}

    static float ease(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}

    /** Fills verts ((MW+1)*(MH+1)*2) for morph progress t (0 = big rect, 1 = small rect). */
    public static void mesh(float[] verts,RectF big,RectF small,float t){
        int k=0;
        for(int r=0;r<=MH;r++){
            float v=r/(float)MH;
            float lt=ease(t*1.7f-(1-v)*.7f);           // horizontal squeeze: rows near the target go first
            float yt=ease(t*1.35f-.35f+v*.0f);          // vertical travel slightly delayed
            float l=big.left+(small.left-big.left)*lt,rr=big.right+(small.right-big.right)*lt;
            float y=big.top+big.height()*v;float ty=small.top+small.height()*v;
            y=y+(ty-y)*yt;
            for(int cI=0;cI<=MW;cI++){float u=cI/(float)MW;verts[k++]=l+(rr-l)*u;verts[k++]=y;}
        }
    }

    /** Overlay view that plays a genie between rects and calls back at the end. */
    public static final class Overlay extends View {
        private final Bitmap bmp;private final RectF big,small;private final boolean reverse;private final Runnable end;
        private final float[] verts=new float[(MW+1)*(MH+1)*2];private final long start;private final long dur;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final int glow;
        /** reverse=false: big → small (minimize). reverse=true: small → big (open). */
        public Overlay(android.content.Context c,Bitmap bmp,RectF big,RectF small,boolean reverse,long dur,int glow,Runnable end){
            super(c);this.bmp=bmp;this.big=big;this.small=small;this.reverse=reverse;this.end=end;this.dur=dur;this.glow=glow;
            start=System.currentTimeMillis();
        }
        @Override protected void onDraw(Canvas c){
            float t=(System.currentTimeMillis()-start)/(float)dur;boolean done=t>=1;t=Math.min(1,t);
            mesh(verts,big,small,reverse?1-t:t);
            p.setAlpha((int)(255*(reverse?Math.min(1,t*3):1-Math.max(0,t-.8f)*5)));
            p.setColorFilter(null);
            c.drawBitmapMesh(bmp,MW,MH,verts,0,null,0,p);
            // neon edges along the mesh outline
            Paint e=new Paint(Paint.ANTI_ALIAS_FLAG);e.setStyle(Paint.Style.STROKE);e.setStrokeWidth(Ui.dpf(2));e.setColor(Theme.a(glow,160));
            Path left=new Path(),right=new Path();
            for(int r=0;r<=MH;r++){int i=r*(MW+1)*2,j=i+MW*2;
                if(r==0){left.moveTo(verts[i],verts[i+1]);right.moveTo(verts[j],verts[j+1]);}
                else{left.lineTo(verts[i],verts[i+1]);right.lineTo(verts[j],verts[j+1]);}}
            c.drawPath(left,e);c.drawPath(right,e);
            if(done){post(()->{if(end!=null)end.run();});}else postInvalidateOnAnimation();
        }
    }
}
