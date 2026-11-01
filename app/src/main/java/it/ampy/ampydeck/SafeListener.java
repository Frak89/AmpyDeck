package it.ampy.ampydeck;

import android.graphics.PointF;

import java.util.List;
import java.util.Map;

/** Forwards key events to the engine, catching anything that would otherwise kill the keyboard. */
final class SafeListener implements KeyboardView.Listener {
    private final KeyboardView.Listener d;
    SafeListener(KeyboardView.Listener d){this.d=d;}
    private interface Call{void run();}
    private static void x(String w,Call c){try{c.run();}catch(Throwable e){CrashLog.log(w,e);}}

    @Override public void onText(String s){x("onText '"+s+"'",()->d.onText(s));}
    @Override public void onCode(int code){x("onCode "+code,()->d.onCode(code));}
    @Override public void onDeleteWord(){x("onDeleteWord",d::onDeleteWord);}
    @Override public void onCursor(int dir){x("onCursor",()->d.onCursor(dir));}
    @Override public void onFlow(List<PointF> p,Map<Character,PointF> k,float w){x("onFlow",()->d.onFlow(p,k,w));}
    @Override public void onPick(int slot){x("onPick "+slot,()->d.onPick(slot));}
    @Override public void onForget(int slot){x("onForget",()->d.onForget(slot));}
    @Override public void onToolbar(String a){x("onToolbar "+a,()->d.onToolbar(a));}
    @Override public void onSpaceLongPress(){x("onSpaceLongPress",d::onSpaceLongPress);}
    @Override public void onShiftChanged(){x("onShiftChanged",d::onShiftChanged);}
    @Override public void onClip(String t){x("onClip",()->d.onClip(t));}
    @Override public void onClearClips(){x("onClearClips",d::onClearClips);}
    @Override public void onMove(int k,boolean sel){x("onMove",()->d.onMove(k,sel));}
    @Override public float letterWeight(String l){try{return d.letterWeight(l);}catch(Throwable e){return 0;}}
    @Override public void onPageChanged(int p){x("onPageChanged",()->d.onPageChanged(p));}
}
