package it.ampy.ampydeck;

import android.graphics.Path;
import android.graphics.RectF;

/** Key and pop-up shapes (shared by the keyboard, the previews and the settings sample). */
public final class Shapes {
    private Shapes(){}
    public static final String[] KEY={"auto","chamfer","rounded","pill","square","circle","hex","octagon","slant","notch"};
    public static final String[] KEY_NAMES={"AUTOMATICA (dallo stile)","SMUSSATA AMPYLINK","ARROTONDATA","PILLOLA","QUADRATA","CERCHIO",
        "ESAGONO","OTTAGONO","OBLIQUA","SMUSSATA SPECCHIO"};
    public static final String[] POPUP={"chamfer","drop","rounded","pill","square","circle","hex","octagon","slant","notch"};
    public static final String[] POPUP_NAMES={"SMUSSATA AMPYLINK","FUMETTO / GOCCIA","ARROTONDATA","PILLOLA","QUADRATA","CERCHIO",
        "ESAGONO","OTTAGONO","OBLIQUA","SMUSSATA SPECCHIO"};

    /** Builds shape s inside r into p (p is reset). */
    public static void path(Path p,RectF r,String s){
        p.reset();
        float l=r.left,t=r.top,rr=r.right,b=r.bottom,w=r.width(),h=r.height(),cx=r.centerX(),cy=r.centerY();
        switch(s==null?"chamfer":s){
            case "rounded":{float rad=Math.min(Ui.dpf(10),Math.min(w,h)/3f);p.addRoundRect(r,rad,rad,Path.Direction.CW);break;}
            case "pill":{float rad=Math.min(w,h)/2f;p.addRoundRect(r,rad,rad,Path.Direction.CW);break;}
            case "square":p.addRect(r,Path.Direction.CW);break;
            case "circle":p.addCircle(cx,cy,Math.min(w,h)/2f*.98f,Path.Direction.CW);break;
            case "hex":{float k=Math.min(h/2f,w*.24f);
                p.moveTo(l+k,t);p.lineTo(rr-k,t);p.lineTo(rr,cy);p.lineTo(rr-k,b);p.lineTo(l+k,b);p.lineTo(l,cy);p.close();break;}
            case "octagon":{float k=Math.min(w,h)*.28f;
                p.moveTo(l+k,t);p.lineTo(rr-k,t);p.lineTo(rr,t+k);p.lineTo(rr,b-k);p.lineTo(rr-k,b);p.lineTo(l+k,b);p.lineTo(l,b-k);p.lineTo(l,t+k);p.close();break;}
            case "slant":{float k=Math.min(w*.18f,h*.3f);
                p.moveTo(l+k,t);p.lineTo(rr,t);p.lineTo(rr-k,b);p.lineTo(l,b);p.close();break;}
            case "notch":{float k=Math.min(Ui.dpf(9),Math.min(w,h)/3f);   // mirrored chamfer: top-right + bottom-left
                p.moveTo(l,t);p.lineTo(rr-k,t);p.lineTo(rr,t+k);p.lineTo(rr,b);p.lineTo(l+k,b);p.lineTo(l,b-k);p.close();break;}
            case "drop":{float tail=Math.min(h*.18f,Ui.dpf(12));float bb=b-tail;float rad=Math.min(Ui.dpf(12),Math.min(w,bb-t)/3f);
                p.addRoundRect(new RectF(l,t,rr,bb),rad,rad,Path.Direction.CW);
                Path tp=new Path();tp.moveTo(cx-tail,bb-1);tp.lineTo(cx,b);tp.lineTo(cx+tail,bb-1);tp.close();p.op(tp,Path.Op.UNION);break;}
            default:{float k=Math.min(Ui.dpf(7),Math.min(w,h)/3f);
                p.moveTo(l+k,t);p.lineTo(rr,t);p.lineTo(rr,b-k);p.lineTo(rr-k,b);p.lineTo(l,b);p.lineTo(l,t+k);p.close();}
        }
    }
}
