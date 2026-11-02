package it.ampy.ampydeck;

import android.graphics.*;

/** The selectable on-screen mouse pointers (hotspot = x,y). */
public final class Pointers {
    private Pointers(){}
    public static final String[] KEYS={"neon","arrow","cyber","cross","ring","dot","hand"};
    public static final String[] NAMES={"FRECCIA NEON (AmpyDeck)","FRECCIA CLASSICA","CYBER TRIANGOLO","MIRINO","ANELLO","PUNTO LUMINOSO","MANINA"};
    public static final String[] COLORS={"accent","secondary","white","warn"};
    public static final String[] COLOR_NAMES={"COLORE 1 TEMA","COLORE 2 TEMA","BIANCO","GIALLO"};

    public static int color(Theme th,String k){
        switch(k){case "secondary":return th.secondary;case "white":return 0xffffffff;case "warn":return th.warn;default:return th.accent;}
    }

    private static Path arrow(float x,float y,float s){
        Path p=new Path();p.moveTo(x,y);p.lineTo(x,y+s);p.lineTo(x+s*.27f,y+s*.76f);p.lineTo(x+s*.45f,y+s*1.12f);
        p.lineTo(x+s*.6f,y+s*1.05f);p.lineTo(x+s*.43f,y+s*.7f);p.lineTo(x+s*.75f,y+s*.7f);p.close();return p;
    }

    public static void draw(Canvas c,String style,float x,float y,float s,int col,Theme th){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStrokeJoin(Paint.Join.ROUND);
        switch(style){
            case "arrow":{Path a=arrow(x,y,s);
                p.setColor(0x55000000);c.save();c.translate(Ui.dpf(1.5f),Ui.dpf(2));c.drawPath(a,p);c.restore();
                p.setColor(0xffffffff);c.drawPath(a,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.6f));p.setColor(0xff000000);c.drawPath(a,p);break;}
            case "cyber":{Path t=new Path();t.moveTo(x,y);t.lineTo(x+s*.25f,y+s);t.lineTo(x+s*.42f,y+s*.62f);t.lineTo(x+s*.9f,y+s*.48f);t.close();
                p.setColor(Theme.a(th.secondary,150));c.save();c.translate(-Ui.dpf(2),0);c.drawPath(t,p);c.restore();
                p.setColor(Theme.a(th.accent,150));c.save();c.translate(Ui.dpf(2),0);c.drawPath(t,p);c.restore();
                p.setColor(Theme.a(th.bg,230));c.drawPath(t,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(2));p.setColor(col);c.drawPath(t,p);break;}
            case "cross":{p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(2));p.setColor(0x88000000);c.drawCircle(x,y,s*.42f,p);
                p.setColor(col);c.drawCircle(x,y,s*.4f,p);float g=s*.12f,l=s*.62f;
                c.drawLine(x-l,y,x-g,y,p);c.drawLine(x+g,y,x+l,y,p);c.drawLine(x,y-l,x,y-g,p);c.drawLine(x,y+g,x,y+l,p);
                p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,Ui.dpf(2),p);break;}
            case "ring":{p.setColor(Theme.a(col,60));c.drawCircle(x,y,s*.55f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(2.5f));
                p.setColor(col);c.drawCircle(x,y,s*.45f,p);p.setStyle(Paint.Style.FILL);p.setColor(0xffffffff);c.drawCircle(x,y,Ui.dpf(3),p);break;}
            case "dot":{p.setColor(Theme.a(col,45));c.drawCircle(x,y,s*.6f,p);p.setColor(Theme.a(col,110));c.drawCircle(x,y,s*.34f,p);
                p.setColor(col);c.drawCircle(x,y,s*.18f,p);p.setColor(0xffffffff);c.drawCircle(x,y,s*.07f,p);break;}
            case "hand":{p.setTextSize(s*1.25f);p.setTextAlign(Paint.Align.LEFT);Paint.FontMetrics fm=p.getFontMetrics();
                c.drawText("👆",x-s*.38f,y-fm.ascent*.92f,p);break;}
            default:{Path a=arrow(x,y,s);   // neon
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(7));p.setColor(Theme.a(col,60));c.drawPath(a,p);
                p.setStyle(Paint.Style.FILL);p.setColor(Theme.a(th.bg,225));c.drawPath(a,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(2.2f));p.setColor(col);c.drawPath(a,p);
                p.setStyle(Paint.Style.FILL);p.setColor(th.secondary);c.drawCircle(x,y,Ui.dpf(2.6f),p);}
        }
    }
}
