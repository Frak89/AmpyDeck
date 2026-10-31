package it.ampy.ampydeck;

import android.content.Context;
import android.graphics.*;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.*;

/**
 * The AmpyDeck key surface: SwiftKey-style prediction bar + toolbar, QWERTY with symbol hints,
 * accent popups, flow typing with a neon trail, space-bar cursor, swipe-left delete,
 * emoji and clipboard panels. Everything is drawn here in the AmpyLink / AmpyRocket neon style;
 * the animated background sits behind this (transparent) view.
 */
public final class KeyboardView extends View {
    public interface Listener {
        void onText(String s);
        void onCode(int code);
        void onDeleteWord();
        void onCursor(int dir);
        void onFlow(List<PointF> path,Map<Character,PointF> keys,float keyW);
        void onPick(int slot);
        void onForget(int slot);
        void onToolbar(String action);
        void onSpaceLongPress();
        void onShiftChanged();
        void onClip(String text);
        void onClearClips();
        void onPageChanged(int page);
        void onMove(int keyCode,boolean select);
    }

    private static final Random R=new Random();
    private final Listener L;
    private Theme th;private Prefs pr;
    private String keyStyle;private boolean hints,preview,flowOn,trailOn,haptics,sound,emojiKey,numberRow;private int glitch;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),tp=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AudioManager audio;

    // geometry
    private float barH,areaH,unitW,bottomPad;private int page=Layouts.LETTERS;
    private List<Layouts.Row> rows=new ArrayList<>();private final ArrayList<Layouts.Key> keys=new ArrayList<>();
    private final float[] rowTop=new float[8],rowBot=new float[8];

    // state
    private int shift=0;private long lastShiftTap;
    private String enterLabel="⏎",spaceLabel="AMPYDECK";
    private final String[] sugg={"","",""};private final GlitchTextView.Scrambler[] sc=new GlitchTextView.Scrambler[3];
    private boolean centerIsCorrection;private boolean toolbar;private int barPressed=-1;private long barDownAt;
    private List<String> clips=new ArrayList<>();
    private int emojiTab=1;private float panelScroll=0;private ArrayList<String> recents=new ArrayList<>();

    // touch
    private Layouts.Key downKey;private int activeId=-1;private float downX,downY,lastX,lastY;
    private boolean longFired,flowing,cursorMode,delSwipe,panelDragging,inBar,inPanel;private float cursorAnchor;
    private String[] popItems;private int popSel=-1;private final RectF popRect=new RectF();private float popCell;
    private int delRepeats;
    private final ArrayList<PointF> flowPath=new ArrayList<>();
    private final ArrayList<float[]> trail=new ArrayList<>();   // x,y,time

    // glitch / entry decode
    private long[] lockAt=new long[0];private long decodeUntil;private int glitchKey=-1;private long glitchUntil;

    private final Runnable longPress=this::onLongPress;
    private final Runnable repeatDel=new Runnable(){@Override public void run(){
        if(downKey==null||downKey.code!=Layouts.DEL||delSwipe)return;
        delRepeats++;
        if(delRepeats>22)L.onDeleteWord();else L.onCode(Layouts.DEL);
        tap(Layouts.DEL);
        postDelayed(this,delRepeats>22?160:delRepeats>8?38:70);
    }};
    private final Runnable idle=new Runnable(){@Override public void run(){
        if(!isAttachedToWindow())return;
        if(glitch>0&&!keys.isEmpty()&&page!=Layouts.EMOJIS&&page!=Layouts.CLIP){glitchKey=R.nextInt(keys.size());
            glitchUntil=SystemClock.uptimeMillis()+90+R.nextInt(70*glitch+60);invalidate();}
        if(glitch>0)postDelayed(this,1800+R.nextInt(7000/Math.max(1,glitch)));
    }};

    public KeyboardView(Context c,Listener l){
        super(c);L=l;audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        for(int i=0;i<3;i++){sc[i]=new GlitchTextView.Scrambler();sc[i].stepMs=24;sc[i].spreadMs=150;}
        reload();
    }

    public void reload(){
        pr=Prefs.get(getContext());th=Theme.load(getContext());
        keyStyle=pr.keyStyle();hints=pr.hints();preview=pr.popup();flowOn=pr.flow();trailOn=pr.trail();
        haptics=pr.haptics();sound=pr.sound();glitch=pr.glitch();numberRow=pr.numberRow();emojiKey=pr.b("emoji_key",true);
        recents=new ArrayList<>(Arrays.asList(pr.s("emoji_recent","").split("\u0001")));recents.remove("");
        buildRows();requestLayout();invalidate();
        removeCallbacks(idle);if(glitch>0)postDelayed(idle,2500);
    }

    private boolean panelPage(){return page==Layouts.EMOJIS||page==Layouts.CLIP||page==Layouts.TRACKPAD;}
    // ------------------------------------------------------------------ public API
    public int page(){return page;}
    public void setPage(int pg){
        if(pg==page)return;page=pg;panelScroll=0;padSelect=false;buildRows();layoutKeys();invalidate();L.onPageChanged(pg);
    }
    public int shift(){return shift;}
    public void setShift(int s){if(s!=shift){shift=s;invalidate();}}
    public void setEnterLabel(String s){enterLabel=s;invalidate();}
    public void setSpaceLabel(String s){spaceLabel=s;invalidate();}
    public void setClips(List<String> c){clips=new ArrayList<>(c);if(page==Layouts.CLIP)invalidate();}
    public void setSuggestions(String a,String b,String c,boolean correction){
        String[] n={a==null?"":a,b==null?"":b,c==null?"":c};
        for(int i=0;i<3;i++)if(!n[i].equals(sugg[i])){sugg[i]=n[i];sc[i].set(n[i],glitch>0&&!n[i].isEmpty());}
        centerIsCorrection=correction;invalidate();
    }
    public String suggestion(int i){return sugg[i];}

    /** Starts the AmpyRocket letter-decode on every key cap (used by the entry animation). */
    public void startDecode(long durMs){
        long now=SystemClock.uptimeMillis();lockAt=new long[keys.size()];
        for(Layouts.Key k:keys){float fx=getWidth()>0?k.r.centerX()/getWidth():.5f,fy=getHeight()>0?k.r.centerY()/getHeight():.5f;
            lockAt[k.index]=now+(long)(durMs*(.25f+.45f*fx*.6f+.45f*fy*.4f))+R.nextInt(160);}
        decodeUntil=now+durMs+200;for(GlitchTextView.Scrambler s:sc)s.replay();
        postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------ layout
    private void buildRows(){
        int pg=panelPage()?Layouts.LETTERS:page;
        rows=Layouts.build(pg,numberRow,emojiKey);keys.clear();for(Layouts.Row r:rows)keys.addAll(r.keys);
        lockAt=new long[keys.size()];
    }
    private float keyH(){
        boolean land=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        return Ui.dpf(land?40:54)*pr.height();
    }
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws);barH=Ui.dpf(44);bottomPad=Ui.dpf(6);
        areaH=keyH()*((numberRow?.82f:0)+4f);
        setMeasuredDimension(w,(int)(barH+areaH+bottomPad));
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){layoutKeys();}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layoutKeys();}

    private void layoutKeys(){
        int W=getWidth();if(W==0)return;
        float sum=0;for(Layouts.Row r:rows)sum+=r.weight;
        float y=barH;float gap=Ui.dpf(3.2f);unitW=W/10f;
        for(int ri=0;ri<rows.size();ri++){
            Layouts.Row r=rows.get(ri);float h=areaH*r.weight/sum;
            float units=r.indent*2;for(Layouts.Key k:r.keys)units+=k.w;
            float u=W/Math.max(10f,units);float x=(W-units*u)/2f+r.indent*u;
            for(Layouts.Key k:r.keys){
                k.r.set(x+gap/2,y+gap/2,x+k.w*u-gap/2,y+h-gap/2);x+=k.w*u;
                shapeKey(k);
            }
            rowTop[ri]=y;rowBot[ri]=y+h;y+=h;
        }
        removeCallbacks(logKeys);postDelayed(logKeys,300);
    }
    /** Key map on logcat (tag AmpyDeckKeys) — used by the CI emulator smoke test to tap real keys. */
    private final Runnable logKeys=()->{
        if(!isAttachedToWindow())return;int[] o=new int[2];getLocationOnScreen(o);
        StringBuilder b=new StringBuilder("PAGE ").append(page).append('\n');
        {android.view.WindowInsets wi=getRootWindowInsets();View rv=getRootView();int[] ro=new int[2];rv.getLocationOnScreen(ro);
         android.graphics.Rect vis=new android.graphics.Rect();getGlobalVisibleRect(vis);
         b.append("GEOM kvY=").append(o[1]).append(" kvH=").append(getHeight()).append(" parentH=").append(((View)getParent()).getHeight())
          .append(" rootY=").append(ro[1]).append(" rootH=").append(rv.getHeight()).append(" vis=").append(vis.toShortString())
          .append(" screenH=").append(getResources().getDisplayMetrics().heightPixels)
          .append(" nav=").append(wi==null?"null":android.os.Build.VERSION.SDK_INT>=30?String.valueOf(wi.getInsets(android.view.WindowInsets.Type.navigationBars())):"?")
          .append(" chain=");
         View v=this;while(v!=null){b.append(v.getClass().getSimpleName()).append('@').append((int)v.getY()).append('/').append(v.getHeight()).append('>');
             v=v.getParent() instanceof View?(View)v.getParent():null;}
         b.append('\n');}
        for(Layouts.Key k:keys){String id=k.code==Layouts.NONE?k.text:k.code==Layouts.SPACE?"SPACE":k.code==Layouts.DEL?"DEL":k.code==Layouts.ENTER?"ENTER":
            k.code==Layouts.SHIFT?"SHIFT":k.code==Layouts.SYM?"SYM":k.code==Layouts.ABC?"ABC":k.code==Layouts.MORE?"MORE":k.code==Layouts.EMOJI?"EMOJI":"?";
            b.append("K ").append(id).append(' ').append((int)(o[0]+k.r.centerX())).append(' ').append((int)(o[1]+k.r.centerY())).append('\n');}
        float w=(getWidth()-barH*2)/3f;
        for(int i=0;i<3;i++)b.append("K B").append(i).append(' ').append((int)(o[0]+barH+w*i+w/2)).append(' ').append((int)(o[1]+barH/2)).append('\n');
        b.append("K TOOLBAR ").append((int)(o[0]+barH/2)).append(' ').append((int)(o[1]+barH/2)).append('\n');
        b.append("K HIDE ").append((int)(o[0]+getWidth()-barH/2)).append(' ').append((int)(o[1]+barH/2)).append('\n');
        float bt=panelBottomRowTop();b.append("K PANELABC ").append((int)(o[0]+getWidth()/(float)bottomCells()/2)).append(' ').append((int)(o[1]+bt+(getHeight()-bottomPad-bt)/2)).append('\n');
        b.append("K PANEL0 ").append((int)(o[0]+emojiCell()/2)).append(' ').append((int)(o[1]+barH+emojiCell()*.43f)).append('\n');
        for(String l:b.toString().split("\n"))android.util.Log.i("AmpyDeckKeys",l);
    };
    private void shapeKey(Layouts.Key k){
        k.path.reset();RectF r=k.r;
        if("neon".equals(keyStyle)||"outline".equals(keyStyle)){
            float c=Math.min(Ui.dpf(7),Math.min(r.width(),r.height())/3f);
            k.path.moveTo(r.left+c,r.top);k.path.lineTo(r.right,r.top);k.path.lineTo(r.right,r.bottom-c);
            k.path.lineTo(r.right-c,r.bottom);k.path.lineTo(r.left,r.bottom);k.path.lineTo(r.left,r.top+c);k.path.close();
        }else{float rad=Math.min(Ui.dpf(Math.max(4,th.round*.6f)),r.height()/2.5f);k.path.addRoundRect(r,rad,rad,Path.Direction.CW);}
    }

    private Layouts.Key keyAt(float x,float y){
        for(int ri=0;ri<rows.size();ri++){
            if(y<rowTop[ri]||y>=rowBot[ri]&&ri<rows.size()-1)continue;
            Layouts.Key best=null;float bd=Float.MAX_VALUE;
            for(Layouts.Key k:rows.get(ri).keys){
                if(x>=k.r.left-Ui.dpf(2)&&x<=k.r.right+Ui.dpf(2))return k;
                float d=Math.min(Math.abs(x-k.r.left),Math.abs(x-k.r.right));if(d<bd){bd=d;best=k;}
            }
            return best;
        }
        return null;
    }
    private Map<Character,PointF> keyCenters(){
        HashMap<Character,PointF> m=new HashMap<>();
        for(Layouts.Key k:keys)if(k.letter())m.put(Character.toLowerCase(k.text.charAt(0)),new PointF(k.r.centerX(),k.r.centerY()));
        return m;
    }

    // ------------------------------------------------------------------ feedback
    private void tap(int code){
        if(haptics)performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if(sound&&audio!=null){int fx=code==Layouts.DEL?AudioManager.FX_KEYPRESS_DELETE:code==Layouts.SPACE?AudioManager.FX_KEYPRESS_SPACEBAR:
            code==Layouts.ENTER?AudioManager.FX_KEYPRESS_RETURN:AudioManager.FX_KEYPRESS_STANDARD;audio.playSoundEffect(fx,-1f);}
    }

    // ------------------------------------------------------------------ touch
    @Override public boolean onTouchEvent(MotionEvent e){
        try{return touch(e);}catch(Throwable t){CrashLog.log("touch",t);resetTouch();return true;}
    }
    private boolean touch(MotionEvent e){
        int a=e.getActionMasked();
        switch(a){
            case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_POINTER_DOWN:{
                int i=e.getActionIndex();
                if(activeId!=-1){ // rollover typing: the previous finger's key fires now
                    if(downKey!=null&&!flowing&&popItems==null&&!cursorMode&&!delSwipe&&!longFired&&downKey.code!=Layouts.DEL)fire(downKey);
                    resetTouch();
                }
                activeId=e.getPointerId(i);down(e.getX(i),e.getY(i));return true;
            }
            case MotionEvent.ACTION_MOVE:{
                int i=e.findPointerIndex(activeId);if(i<0)return true;
                for(int h=0;h<e.getHistorySize();h++)move(e.getHistoricalX(i,h),e.getHistoricalY(i,h));
                move(e.getX(i),e.getY(i));return true;
            }
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_POINTER_UP:{
                int i=e.getActionIndex();if(e.getPointerId(i)!=activeId)return true;
                up(e.getX(i),e.getY(i));resetTouch();return true;
            }
            case MotionEvent.ACTION_CANCEL:resetTouch();invalidate();return true;
        }
        return true;
    }
    private void resetTouch(){
        removeCallbacks(longPress);removeCallbacks(repeatDel);
        activeId=-1;downKey=null;downKeyAtDown=null;swipeHide=false;longFired=flowing=cursorMode=delSwipe=panelDragging=inBar=inPanel=false;
        popItems=null;popSel=-1;barPressed=-1;delRepeats=0;dyAcc=0;padTouch=false;invalidate();
    }

    private void down(float x,float y){
        downX=lastX=x;downY=lastY=y;
        if(y<barH){inBar=true;barPressed=barSlot(x);barDownAt=SystemClock.uptimeMillis();tap(0);
            if(barPressed>=1&&barPressed<=3&&!toolbar)postDelayed(longPress,520);invalidate();return;}
        if(panelPage()){inPanel=true;tap(0);padAx=padAy=0;invalidate();return;}
        Layouts.Key k=keyAt(x,y);if(k==null)return;press(k);
    }
    private Layouts.Key downKeyAtDown;private boolean swipeHide;
    private void press(Layouts.Key k){
        downKey=k;downKeyAtDown=k;swipeHide=false;tap(k.code);longFired=false;
        if(k.code==Layouts.DEL){L.onCode(Layouts.DEL);postDelayed(repeatDel,400);}
        else if(k.code==Layouts.SPACE||(k.popup!=null&&k.popup.length>0))postDelayed(longPress,k.code==Layouts.SPACE?600:330);
        else if(k.code==Layouts.SYM)postDelayed(longPress,450);   // hold ?123 = trackpad
        invalidate();
    }
    private void move(float x,float y){
        float px=lastX,py=lastY;
        float dx=x-downX,dy=y-downY;lastX=x;lastY=y;
        if(inBar)return;
        if(inPanel&&page==Layouts.TRACKPAD){padMove(x,y,x-px,y-py);return;}
        if(inPanel){if(!panelDragging&&Math.abs(dy)>Ui.dpf(8))panelDragging=true;
            if(panelDragging){panelScroll-=y-(downY+dyAcc);dyAcc=y-downY;clampScroll();invalidate();}return;}
        if(downKey==null)return;
        if(popItems!=null){popSel=popIndex(x);invalidate();return;}
        // swipe up from the bottom row = hide the keyboard
        if(!cursorMode&&!flowing&&rows.size()>0&&rows.get(rows.size()-1).keys.contains(downKeyAtDown)){
            float rowH=rowBot[rows.size()-1]-rowTop[rows.size()-1];
            boolean up=-dy>rowH*1.7f&&-dy>Math.abs(dx)*1.6f;
            if(up!=swipeHide){swipeHide=up;removeCallbacks(longPress);invalidate();}
            if(swipeHide||-dy>rowH*.6f&&-dy>Math.abs(dx))return;
        }
        if(downKey.code==Layouts.SPACE){
            if(!cursorMode&&Math.abs(dx)>Ui.dpf(16)){cursorMode=true;removeCallbacks(longPress);cursorAnchor=x;}
            if(cursorMode){float step=Ui.dpf(11);
                while(x-cursorAnchor>step){L.onCursor(1);cursorAnchor+=step;tap(0);}
                while(cursorAnchor-x>step){L.onCursor(-1);cursorAnchor-=step;tap(0);}
                invalidate();}
            return;
        }
        if(downKey.code==Layouts.DEL){
            if(!delSwipe&&dx<-Ui.dpf(36)){delSwipe=true;removeCallbacks(repeatDel);invalidate();}
            return;
        }
        if(flowOn&&page==Layouts.LETTERS&&downKey.letter()&&!longFired){
            if(!flowing&&Math.hypot(dx,dy)>unitW*.6f){flowing=true;removeCallbacks(longPress);flowPath.clear();
                flowPath.add(new PointF(downX,downY));trail.add(new float[]{downX,downY,SystemClock.uptimeMillis()});}
            if(flowing){PointF last=flowPath.get(flowPath.size()-1);
                if(Math.hypot(x-last.x,y-last.y)>Ui.dpf(2.5f)){flowPath.add(new PointF(x,y));
                    trail.add(new float[]{x,y,SystemClock.uptimeMillis()});
                    Layouts.Key k=keyAt(x,y);if(k!=null&&k!=downKey&&k.letter()){downKey=k;}
                    invalidate();}
            }
            return;
        }
        // finger slides to another key (flow off / symbols)
        Layouts.Key k=keyAt(x,y);
        if(k!=null&&k!=downKey&&!longFired&&downKey.code!=Layouts.DEL){
            removeCallbacks(longPress);downKey=k;
            if(k.code==Layouts.SPACE||(k.popup!=null&&k.popup.length>0))postDelayed(longPress,330);
            invalidate();
        }
    }
    private float dyAcc=0;
    private void up(float x,float y){
        removeCallbacks(longPress);removeCallbacks(repeatDel);
        if(inBar){barUp(x);dyAcc=0;return;}
        if(inPanel){if(!panelDragging)panelTap(x,y);dyAcc=0;return;}
        if(downKey==null)return;
        if(swipeHide){L.onToolbar("hide");return;}
        if(popItems!=null){if(popSel>=0&&popSel<popItems.length){L.onText(popItems[popSel]);if(shift==1){shift=0;L.onShiftChanged();}}return;}
        if(flowing){flowPath.add(new PointF(x,y));L.onFlow(new ArrayList<>(flowPath),keyCenters(),unitW);
            if(shift==1){shift=0;}return;}
        if(cursorMode||longFired)return;
        if(delSwipe){L.onDeleteWord();return;}
        if(downKey.code==Layouts.DEL)return;
        fire(downKey);
    }

    private void fire(Layouts.Key k){
        switch(k.code){
            case Layouts.SHIFT:{
                long now=SystemClock.uptimeMillis();
                if(shift==0)shift=1;else if(shift==1)shift=(now-lastShiftTap<380)?2:0;else shift=0;
                lastShiftTap=now;L.onShiftChanged();invalidate();return;
            }
            case Layouts.SYM:setPage(Layouts.SYM1);return;
            case Layouts.ABC:setPage(Layouts.LETTERS);return;
            case Layouts.MORE:setPage(Layouts.SYM2);return;
            case Layouts.EMOJI:setPage(Layouts.EMOJIS);return;
            case Layouts.PAD:setPage(Layouts.TRACKPAD);return;
            case Layouts.SPACE:case Layouts.ENTER:L.onCode(k.code);return;
            default:{
                String t=k.text;if(t==null)return;
                if(shift>0&&k.letter())t=t.toUpperCase(Locale.ROOT);
                L.onText(t);
                if(shift==1&&k.letter()){shift=0;L.onShiftChanged();}
            }
        }
    }

    private void onLongPress(){
        if(inBar){if(barPressed>=1&&barPressed<=3&&!sugg[barPressed-1].isEmpty()){longFired=true;
            if(haptics)performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);L.onForget(barPressed-1);barPressed=-1;invalidate();}return;}
        Layouts.Key k=downKey;if(k==null)return;
        if(k.code==Layouts.SYM){longFired=true;if(haptics)performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);setPage(Layouts.TRACKPAD);return;}
        if(k.code==Layouts.SPACE){longFired=true;if(haptics)performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);L.onSpaceLongPress();return;}
        if(k.popup==null||k.popup.length==0)return;
        longFired=true;if(haptics)performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if(k.popup.length==1){L.onText(k.popup[0]);return;}
        String[] items=k.popup.clone();
        if(shift>0)for(int i=0;i<items.length;i++)if(items[i].length()==1&&Character.isLetter(items[i].charAt(0)))items[i]=items[i].toUpperCase(Locale.ROOT);
        popItems=items;
        float cell=Math.min(unitW*.95f,(getWidth()-Ui.dpf(12))/items.length);popCell=cell;
        float h=(k.r.height())*1.05f,w=cell*items.length;
        float left=k.r.centerX()-cell/2f; // first item (the hint) sits right above the key
        if(left+w>getWidth()-Ui.dpf(4))left=getWidth()-Ui.dpf(4)-w;if(left<Ui.dpf(4))left=Ui.dpf(4);
        float top=Math.max(0,k.r.top-h-Ui.dpf(6));popRect.set(left,top,left+w,top+h);
        popSel=popIndex(lastX);invalidate();
    }
    private int popIndex(float x){if(popItems==null)return -1;int i=(int)((x-popRect.left)/popCell);return Math.max(0,Math.min(popItems.length-1,i));}

    // ------------------------------------------------------------------ bar
    private static final String[] TOOLS={"settings","emoji","clip","theme","bg","replay","hide"};
    private static final String[] TOOL_ICONS={"⚙","☺","⎘","◐","▦","✦","⌄"};
    private static final String[] TOOL_NAMES={"SETUP","EMOJI","CLIP","TEMA","SFONDO","FX","CHIUDI"};
    /** 0 = toolbar toggle, 1..3 = slots, 4 = hide; in toolbar mode 10+i = tool i. */
    private int barSlot(float x){
        float W=getWidth();
        if(x<barH)return 0;
        if(toolbar){float w=(W-barH)/TOOLS.length;return 10+Math.min(TOOLS.length-1,(int)((x-barH)/w));}
        if(x>W-barH)return 4;
        float w=(W-barH*2)/3f;return 1+Math.min(2,(int)((x-barH)/w));
    }
    private void barUp(float x){
        int s=barPressed;barPressed=-1;if(s<0||longFired)return;
        if(s==0){toolbar=!toolbar;invalidate();return;}
        if(s==4){L.onToolbar("hide");return;}
        if(s>=10){toolbar=false;L.onToolbar(TOOLS[s-10]);invalidate();return;}
        if(!sugg[s-1].isEmpty())L.onPick(s-1);
    }
    public void closeToolbar(){toolbar=false;invalidate();}

    // ------------------------------------------------------------------ panels (emoji / clipboard)
    private float panelBottomRowTop(){return getHeight()-bottomPad-keyH()*.95f;}
    private int emojiCols(){return getWidth()>Ui.dpf(560)?11:8;}
    private float emojiCell(){return getWidth()/(float)emojiCols();}
    private String[] emojiList(){return emojiTab==0?recents.toArray(new String[0]):Layouts.EMOJI_SETS[emojiTab];}
    private float panelContentH(){
        if(page==Layouts.CLIP)return Math.max(1,clips.size())*Ui.dpf(46)+Ui.dpf(8);
        int n=emojiList().length;int rowsN=(n+emojiCols()-1)/emojiCols();return rowsN*emojiCell()*.86f+Ui.dpf(8);
    }
    private void clampScroll(){float max=Math.max(0,panelContentH()-(panelBottomRowTop()-barH));panelScroll=Math.max(0,Math.min(max,panelScroll));}
    private int bottomCells(){return page==Layouts.EMOJIS?Layouts.EMOJI_TABS.length+2:page==Layouts.TRACKPAD?PAD_CELLS.length:3;}
    private static final String[] PAD_CELLS={"ABC","?123","SEL","⇤","⇥","TUTTO","COPIA","INCOLLA","⌫"};

    // ------------------------------------------------------------------ trackpad (laptop style)
    private float padAx,padAy;private boolean padSelect;private boolean padTouch;
    private void padMove(float x,float y,float ddx,float ddy){
        if(downY>=panelBottomRowTop())return;
        if(!panelDragging&&Math.hypot(x-downX,y-downY)>Ui.dpf(4))panelDragging=true;
        if(!panelDragging)return;
        padTouch=true;
        float speed=(float)Math.hypot(ddx,ddy);float accel=speed>Ui.dpf(14)?2.2f:speed>Ui.dpf(7)?1.5f:1f;
        padAx+=ddx*accel;padAy+=ddy*accel;
        float sx=Ui.dpf(13),sy=Ui.dpf(24);
        while(padAx>sx){L.onMove(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,padSelect);padAx-=sx;tap(0);}
        while(padAx<-sx){L.onMove(android.view.KeyEvent.KEYCODE_DPAD_LEFT,padSelect);padAx+=sx;tap(0);}
        while(padAy>sy){L.onMove(android.view.KeyEvent.KEYCODE_DPAD_DOWN,padSelect);padAy-=sy;tap(0);}
        while(padAy<-sy){L.onMove(android.view.KeyEvent.KEYCODE_DPAD_UP,padSelect);padAy+=sy;tap(0);}
        trail.add(new float[]{x,y,SystemClock.uptimeMillis()});
        invalidate();
    }

    private void panelTap(float x,float y){
        float bt=panelBottomRowTop();
        if(y>=bt){
            int n=bottomCells();float w=getWidth()/(float)n;int i=Math.min(n-1,(int)(x/w));
            if(i==0){setPage(Layouts.LETTERS);return;}
            if(i==n-1){L.onCode(Layouts.DEL);return;}
            if(page==Layouts.TRACKPAD){
                switch(i){
                    case 1:setPage(Layouts.SYM1);break;
                    case 2:padSelect=!padSelect;invalidate();break;
                    case 3:L.onMove(android.view.KeyEvent.KEYCODE_MOVE_HOME,padSelect);break;
                    case 4:L.onMove(android.view.KeyEvent.KEYCODE_MOVE_END,padSelect);break;
                    case 5:L.onToolbar("selectall");break;
                    case 6:L.onToolbar("copy");break;
                    case 7:L.onToolbar("paste");break;
                }
                return;
            }
            if(page==Layouts.EMOJIS){emojiTab=i-1;panelScroll=0;invalidate();}
            else L.onClearClips();
            return;
        }
        if(page==Layouts.TRACKPAD)return;
        float cy=y-barH+panelScroll;
        if(page==Layouts.CLIP){int i=(int)((cy-Ui.dpf(4))/Ui.dpf(46));if(i>=0&&i<clips.size())L.onClip(clips.get(i));return;}
        float cell=emojiCell();int col=(int)(x/cell),row=(int)((cy-Ui.dpf(4))/(cell*.86f));int i=row*emojiCols()+col;
        String[] l=emojiList();if(i<0||i>=l.length||row<0)return;
        String em=l[i];L.onText(em);
        recents.remove(em);recents.add(0,em);while(recents.size()>40)recents.remove(recents.size()-1);
        pr.put("emoji_recent",String.join("\u0001",recents));
    }

    // ------------------------------------------------------------------ drawing
    @Override protected void onDraw(Canvas c){
        try{drawAll(c);}catch(Throwable t){CrashLog.log("draw",t);}
    }
    private void drawAll(Canvas c){
        long now=SystemClock.uptimeMillis();boolean anim=false;
        drawBar(c,now);
        if(panelPage())drawPanel(c);
        else{
            boolean decoding=now<decodeUntil;anim|=decoding;
            for(Layouts.Key k:keys)drawKey(c,k,now,decoding);
            if(downKey!=null&&preview&&popItems==null&&!flowing&&!cursorMode&&!downKey.special&&!longFired)drawPreview(c,downKey);
            if(popItems!=null)drawPopup(c);
            if(swipeHide)drawHideHint(c);
        }
        if(now<glitchUntil)anim=true;
        anim|=drawTrail(c,now);
        for(GlitchTextView.Scrambler s:sc)if(s.active())anim=true;
        if(anim)postInvalidateOnAnimation();
    }

    private int edgeOf(Layouts.Key k){return k.code==Layouts.ENTER?th.accent:k.special?th.secondary:th.accent;}

    private void keyBody(Canvas c,Path path,RectF r,int edge,boolean pressed,boolean strong){
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        switch(keyStyle){
            case "glass":
                p.setShader(new LinearGradient(0,r.top,0,r.bottom,Theme.a(th.panel2,pressed?230:175),Theme.a(th.panel,pressed?220:150),Shader.TileMode.CLAMP));
                c.drawPath(path,p);p.setShader(null);
                p.setColor(0x22ffffff);c.save();c.clipPath(path);c.drawRect(r.left,r.top,r.right,r.top+r.height()*.42f,p);c.restore();
                break;
            case "outline":p.setColor(Theme.a(th.bg,pressed?120:40));c.drawPath(path,p);break;
            case "flat":p.setColor(Theme.a(th.panel,235));c.drawPath(path,p);break;
            default:p.setColor(Theme.a(th.panel,strong?200:165));c.drawPath(path,p);
                p.setColor(0x08ffffff);for(float y=r.top+Ui.dpf(2);y<r.bottom;y+=Ui.dpf(3))c.drawRect(r.left+Ui.dpf(2),y,r.right-Ui.dpf(2),y+Ui.dpf(1),p);
        }
        if(strong){p.setColor(Theme.a(edge,pressed?120:55));c.drawPath(path,p);}
        if(pressed){p.setColor(Theme.a(edge,95));c.drawPath(path,p);}
        p.setStyle(Paint.Style.STROKE);
        if(pressed){p.setStrokeWidth(Ui.dpf(5));p.setColor(Theme.a(edge,55));c.drawPath(path,p);}
        if(!"flat".equals(keyStyle)||pressed){
            p.setStrokeWidth(Ui.dpf(pressed?1.8f:1f));p.setColor(Theme.a(edge,pressed?255:"outline".equals(keyStyle)?200:120));c.drawPath(path,p);}
        if("neon".equals(keyStyle)||"outline".equals(keyStyle)){
            float m=Math.min(Ui.dpf(9),r.width()/4f);p.setStrokeWidth(Ui.dpf(2));
            float cut=Math.min(Ui.dpf(7),Math.min(r.width(),r.height())/3f);
            p.setColor(edge);c.drawLine(r.left+cut,r.top,r.left+cut+m,r.top,p);
            p.setColor(th.secondary);c.drawLine(r.right,r.bottom-cut,r.right,r.bottom-cut-m*.8f,p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private String capLabel(Layouts.Key k){
        switch(k.code){
            case Layouts.SHIFT:return shift==2?"⇪":shift==1?"⬆":"⇧";
            case Layouts.ENTER:return enterLabel;
            case Layouts.SPACE:return cursorMode?"◀  ▶":spaceLabel;
            case Layouts.DEL:return delSwipe?"«⌫":"⌫";
        }
        if(k.letter()&&shift>0)return k.label.toUpperCase(Locale.ROOT);
        return k.label;
    }

    private void drawKey(Canvas c,Layouts.Key k,long now,boolean decoding){
        boolean pressed=k==downKey&&!flowing||(k.code==Layouts.SHIFT&&shift==2);
        boolean strong=k.code==Layouts.ENTER||(k.code==Layouts.SHIFT&&shift>0);
        int edge=edgeOf(k);
        keyBody(c,k.path,k.r,edge,pressed,strong);
        if(flowing&&k.letter()&&k==downKey){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.5f));p.setColor(th.secondary);c.drawPath(k.path,p);p.setStyle(Paint.Style.FILL);}
        String label=capLabel(k);
        boolean scramble=decoding&&k.index<lockAt.length&&now<lockAt[k.index]&&!label.isEmpty();
        if(scramble&&k.code!=Layouts.SPACE)label=String.valueOf(GlitchTextView.GLYPHS.charAt(R.nextInt(GlitchTextView.GLYPHS.length())));
        boolean gk=k.index==glitchKey&&now<glitchUntil;
        float size;
        if(k.code==Layouts.SPACE){size=k.r.height()*.24f;tp.setTypeface(Ui.MONO_BOLD);}
        else if(k.special&&label.length()>1){size=k.r.height()*.27f;tp.setTypeface(Ui.MONO_BOLD);}
        else{size=k.r.height()*(k.special?.42f:.40f);tp.setTypeface(Ui.MONO_BOLD);}
        tp.setTextSize(size);tp.setTextAlign(Paint.Align.CENTER);tp.setLetterSpacing(k.code==Layouts.SPACE?.18f:0);
        float tw=tp.measureText(label);if(tw>k.r.width()*.86f){tp.setTextSize(size*k.r.width()*.86f/tw);}
        Paint.FontMetrics fm=tp.getFontMetrics();
        float cx=k.r.centerX(),cy=k.r.centerY()-(fm.ascent+fm.descent)/2f;
        if(hints&&k.hint!=null&&!k.special&&k.code==Layouts.NONE&&page==Layouts.LETTERS)cy+=k.r.height()*.06f;
        int fg=k.code==Layouts.SPACE?Theme.a(th.muted,cursorMode?255:200):k.code==Layouts.ENTER?th.accent:k.special?th.secondary:th.fg;
        if(pressed&&k.code!=Layouts.SHIFT)fg=th.fg;
        float split=gk||scramble?Ui.dpf(1.6f+R.nextFloat()*2.4f):glitch>0?Ui.dpf(.8f):0;
        float jx=gk?(R.nextFloat()-.5f)*Ui.dpf(6):0;
        if(split>0){
            tp.setColor(Theme.a(th.secondary,gk||scramble?190:85));c.drawText(label,cx-split+jx,cy,tp);
            tp.setColor(Theme.a(th.accent,gk||scramble?190:85));c.drawText(label,cx+split+jx,cy,tp);
        }
        if(gk){ // slice tear like GlitchTextView
            c.save();c.clipRect(k.r.left,k.r.top,k.r.right,k.r.centerY());c.translate(Ui.dpf(R.nextInt(7)-3),0);
            tp.setColor(fg);c.drawText(label,cx,cy,tp);c.restore();
            c.save();c.clipRect(k.r.left,k.r.centerY(),k.r.right,k.r.bottom);tp.setColor(fg);c.drawText(label,cx,cy,tp);c.restore();
        }else{tp.setColor(fg);c.drawText(label,cx,cy,tp);}
        if(hints&&k.hint!=null&&page==Layouts.LETTERS&&k.code==Layouts.NONE){
            String h=k.hint;if(shift>0&&h.length()==1&&Character.isLetter(h.charAt(0)))h=h.toUpperCase(Locale.ROOT);
            tp.setTextSize(k.r.height()*.2f);tp.setTypeface(Ui.MONO);tp.setTextAlign(Paint.Align.RIGHT);tp.setColor(Theme.a(th.secondary,190));
            c.drawText(h,k.r.right-Ui.dpf(5),k.r.top+k.r.height()*.26f,tp);
        }
        tp.setLetterSpacing(0);
    }

    private final Path tmp=new Path();private final RectF tr=new RectF();
    private void chamfer(Path path,RectF r,float cut){
        path.reset();path.moveTo(r.left+cut,r.top);path.lineTo(r.right,r.top);path.lineTo(r.right,r.bottom-cut);
        path.lineTo(r.right-cut,r.bottom);path.lineTo(r.left,r.bottom);path.lineTo(r.left,r.top+cut);path.close();
    }
    private void bubble(Canvas c,RectF r,int edge){
        chamfer(tmp,r,Ui.dpf(8));
        p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,r.top,0,r.bottom,Theme.a(th.panel2,250),Theme.a(th.panel,250),Shader.TileMode.CLAMP));
        c.drawPath(tmp,p);p.setShader(null);
        p.setColor(0x0cffffff);for(float y=r.top+Ui.dpf(2);y<r.bottom;y+=Ui.dpf(3))c.drawRect(r.left,y,r.right,y+Ui.dpf(1),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(5));p.setColor(Theme.a(edge,50));c.drawPath(tmp,p);
        p.setStrokeWidth(Ui.dpf(1.5f));p.setColor(edge);c.drawPath(tmp,p);
        p.setStrokeWidth(Ui.dpf(2.4f));p.setColor(th.secondary);float m=Ui.dpf(10);
        c.drawLine(r.right,r.top,r.right,r.top+m,p);c.drawLine(r.left,r.bottom,r.left,r.bottom-m,p);p.setStyle(Paint.Style.FILL);
    }
    private void drawPreview(Canvas c,Layouts.Key k){
        float w=k.r.width()*1.25f,h=k.r.height()*1.2f;
        float left=Math.max(Ui.dpf(2),Math.min(getWidth()-w-Ui.dpf(2),k.r.centerX()-w/2));
        float top=Math.max(0,k.r.top-h-Ui.dpf(4));
        tr.set(left,top,left+w,top+h);bubble(c,tr,th.accent);
        String l=capLabel(k);tp.setTypeface(Ui.MONO_BOLD);tp.setTextAlign(Paint.Align.CENTER);tp.setTextSize(h*.5f);
        Paint.FontMetrics fm=tp.getFontMetrics();float y=tr.centerY()-(fm.ascent+fm.descent)/2f;
        if(glitch>0){tp.setColor(Theme.a(th.secondary,140));c.drawText(l,tr.centerX()-Ui.dpf(1.5f),y,tp);
            tp.setColor(Theme.a(th.accent,140));c.drawText(l,tr.centerX()+Ui.dpf(1.5f),y,tp);}
        tp.setColor(th.fg);c.drawText(l,tr.centerX(),y,tp);
    }
    private void drawPopup(Canvas c){
        bubble(c,popRect,th.secondary);
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<popItems.length;i++){
            float l=popRect.left+i*popCell;tr.set(l+Ui.dpf(3),popRect.top+Ui.dpf(4),l+popCell-Ui.dpf(3),popRect.bottom-Ui.dpf(4));
            if(i==popSel){chamfer(tmp,tr,Ui.dpf(5));p.setColor(Theme.a(th.accent,200));c.drawPath(tmp,p);}
            String s=popItems[i];tp.setTextSize(Math.min(popRect.height()*.42f,popCell*(s.length()>1?.4f:.7f)));
            Paint.FontMetrics fm=tp.getFontMetrics();
            tp.setColor(i==popSel?th.bg:th.fg);c.drawText(s,tr.centerX(),tr.centerY()-(fm.ascent+fm.descent)/2f,tp);
        }
    }

    private static int lerp(int a,int b,float t){
        t=Math.max(0,Math.min(1,t));
        int aa=a>>>24,ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255,ba=b>>>24,br=(b>>16)&255,bgc=(b>>8)&255,bb=b&255;
        return ((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bgc-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }
    /** Comet trail: one continuous stroke, colour 1 (accent) at the head fading into colour 2 (secondary) at the tail. */
    private boolean drawTrail(Canvas c,long now){
        if(trail.isEmpty())return false;
        long life=flowing?650:300;
        while(!trail.isEmpty()&&now-(long)trail.get(0)[2]>life)trail.remove(0);
        if(!trailOn&&page!=Layouts.TRACKPAD){if(!flowing)trail.clear();return flowing;}
        int n=trail.size();if(n<2)return true;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        float maxW=Ui.dpf(page==Layouts.TRACKPAD?5:7);
        for(int pass=0;pass<2;pass++){           // pass 0 = soft glow, pass 1 = core
            for(int i=1;i<n;i++){
                float[] a=trail.get(i-1),b=trail.get(i);
                float pos=i/(float)(n-1);                       // 0 = tail, 1 = head
                float age=Math.min(1,(now-b[2])/(float)life);float fade=1-age*age;
                int col=lerp(th.secondary,th.accent,pos);
                float w=maxW*(.15f+.85f*pos)*fade+Ui.dpf(.6f);
                float mx0=i>1?(trail.get(i-2)[0]+a[0])/2f:a[0],my0=i>1?(trail.get(i-2)[1]+a[1])/2f:a[1];
                float mx1=(a[0]+b[0])/2f,my1=(a[1]+b[1])/2f;
                tmp.reset();tmp.moveTo(mx0,my0);tmp.quadTo(a[0],a[1],mx1,my1);
                if(i==n-1)tmp.lineTo(b[0],b[1]);
                if(pass==0){p.setStrokeWidth(w*2.6f);p.setColor(Theme.a(col,(int)(55*fade*(.3f+.7f*pos))));}
                else{p.setStrokeWidth(w);p.setColor(Theme.a(col,(int)(250*fade*(.25f+.75f*pos))));}
                c.drawPath(tmp,p);
            }
        }
        float[] h=trail.get(n-1);p.setStyle(Paint.Style.FILL);       // bright head
        p.setColor(Theme.a(th.accent,90));c.drawCircle(h[0],h[1],maxW*1.4f,p);
        p.setColor(Theme.a(th.fg,230));c.drawCircle(h[0],h[1],maxW*.45f,p);
        p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeJoin(Paint.Join.MITER);
        return true;
    }

    private void drawBar(Canvas c,long now){
        float W=getWidth();
        p.setStyle(Paint.Style.FILL);p.setShader(null);
        p.setColor(Theme.a(th.bg,150));c.drawRect(0,0,W,barH,p);
        p.setColor(Theme.a(th.accent,110));c.drawRect(0,barH-Ui.dpf(1),W,barH,p);
        p.setColor(th.accent);c.drawRect(0,barH-Ui.dpf(2),Ui.dpf(26),barH,p);
        p.setColor(th.secondary);c.drawRect(W-Ui.dpf(26),barH-Ui.dpf(2),W,barH,p);
        // toolbar toggle (AmpyDeck glyph)
        if(barPressed==0){p.setColor(Theme.a(th.accent,70));c.drawRect(0,0,barH,barH-Ui.dpf(2),p);}
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextAlign(Paint.Align.CENTER);tp.setTextSize(barH*.42f);
        String g=toolbar?"‹":"⧉";
        tp.setColor(Theme.a(th.secondary,150));c.drawText(g,barH/2f-Ui.dpf(1),barH*.66f,tp);
        tp.setColor(th.accent);c.drawText(g,barH/2f+Ui.dpf(1),barH*.66f,tp);
        if(toolbar){
            float w=(W-barH)/TOOLS.length;
            for(int i=0;i<TOOLS.length;i++){
                float x=barH+i*w;
                if(barPressed==10+i){p.setColor(Theme.a(th.accent,70));c.drawRect(x,0,x+w,barH-Ui.dpf(2),p);}
                tp.setTextSize(barH*.4f);tp.setColor(i%2==0?th.accent:th.secondary);c.drawText(TOOL_ICONS[i],x+w/2,barH*.52f,tp);
                tp.setTextSize(barH*.17f);tp.setColor(th.muted);c.drawText(TOOL_NAMES[i],x+w/2,barH*.86f,tp);
            }
            return;
        }
        float w=(W-barH*2)/3f;
        for(int i=0;i<3;i++){
            float x=barH+i*w;
            if(barPressed==1+i){p.setColor(Theme.a(th.accent,70));c.drawRect(x,0,x+w,barH-Ui.dpf(2),p);}
            if(i>0){p.setColor(Theme.a(th.border,200));c.drawRect(x,barH*.25f,x+Ui.dpf(1),barH*.75f,p);}
            String s=sc[i].active()?sc[i].frame():sugg[i];if(s.isEmpty())continue;
            boolean center=i==1;
            tp.setTypeface(center?Ui.MONO_BOLD:Ui.MONO);tp.setTextSize(barH*(center?.38f:.34f));
            float tw=tp.measureText(s);if(tw>w-Ui.dpf(10))tp.setTextSize(tp.getTextSize()*(w-Ui.dpf(10))/tw);
            int col=center?(centerIsCorrection?th.accent:th.fg):th.muted;
            float y=barH*.62f;
            if(glitch>0&&center){tp.setColor(Theme.a(th.secondary,110));c.drawText(s,x+w/2-Ui.dpf(1),y,tp);}
            tp.setColor(col);c.drawText(s,x+w/2,y,tp);
            if(center&&centerIsCorrection){float uw=Math.min(tp.measureText(s),w-Ui.dpf(10));
                p.setColor(Theme.a(th.accent,200));c.drawRect(x+w/2-uw/2,barH*.74f,x+w/2+uw/2,barH*.74f+Ui.dpf(1.5f),p);}
        }
        // hide keyboard: framed chevron, top-right (replaces the system "v" at the bottom)
        float x=W-barH;
        tr.set(x+Ui.dpf(5),Ui.dpf(6),W-Ui.dpf(5),barH-Ui.dpf(8));chamfer(tmp,tr,Ui.dpf(5));
        p.setStyle(Paint.Style.FILL);p.setColor(Theme.a(th.secondary,barPressed==4?120:40));c.drawPath(tmp,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.2f));p.setColor(Theme.a(th.secondary,220));c.drawPath(tmp,p);
        p.setStrokeWidth(Ui.dpf(2.4f));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(th.fg);
        float cx=tr.centerX(),cy=tr.centerY(),hw=tr.width()*.22f;
        c.drawLine(cx-hw,cy-hw*.45f,cx,cy+hw*.55f,p);c.drawLine(cx,cy+hw*.55f,cx+hw,cy-hw*.45f,p);
        p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
    }

    private void drawPanel(Canvas c){
        float W=getWidth(),bt=panelBottomRowTop();
        c.save();c.clipRect(0,barH,W,bt);
        tp.setTextAlign(Paint.Align.CENTER);
        if(page==Layouts.TRACKPAD)drawPad(c,W,bt);
        else if(page==Layouts.EMOJIS){
            String[] l=emojiList();float cell=emojiCell(),ch=cell*.86f;int cols=emojiCols();
            tp.setTypeface(Typeface.DEFAULT);tp.setTextSize(ch*.56f);tp.setColor(0xffffffff);
            if(l.length==0){tp.setTypeface(Ui.MONO);tp.setTextSize(Ui.dpf(13));tp.setColor(th.muted);
                c.drawText("// NESSUNA EMOJI RECENTE",W/2,barH+(bt-barH)/2,tp);}
            for(int i=0;i<l.length;i++){
                float x=(i%cols)*cell+cell/2,y=barH+Ui.dpf(4)+(i/cols)*ch-panelScroll;
                if(y+ch<barH||y>bt)continue;
                if(l[i].length()>4&&!Character.isSurrogate(l[i].charAt(0))){tp.setTextSize(ch*.24f);}else tp.setTextSize(ch*.56f);
                Paint.FontMetrics fm=tp.getFontMetrics();c.drawText(l[i],x,y+ch/2-(fm.ascent+fm.descent)/2f,tp);
            }
        }else{
            float rh=Ui.dpf(46);
            if(clips.isEmpty()){tp.setTypeface(Ui.MONO);tp.setTextSize(Ui.dpf(12.5f));tp.setColor(th.muted);
                c.drawText("// COPIA UN TESTO: COMPARE QUI",W/2,barH+(bt-barH)/2,tp);}
            tp.setTextAlign(Paint.Align.LEFT);
            for(int i=0;i<clips.size();i++){
                float y=barH+Ui.dpf(4)+i*rh-panelScroll;if(y+rh<barH||y>bt)continue;
                tr.set(Ui.dpf(8),y+Ui.dpf(3),W-Ui.dpf(8),y+rh-Ui.dpf(3));chamfer(tmp,tr,Ui.dpf(6));
                p.setStyle(Paint.Style.FILL);p.setColor(Theme.a(th.panel,175));c.drawPath(tmp,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1));p.setColor(Theme.a(i==0?th.accent:th.border,200));c.drawPath(tmp,p);p.setStyle(Paint.Style.FILL);
                String s=clips.get(i).replace('\n',' ');tp.setTypeface(Ui.MONO);tp.setTextSize(Ui.dpf(13));tp.setColor(th.fg);
                int n=tp.breakText(s,true,tr.width()-Ui.dpf(40),null);if(n<s.length())s=s.substring(0,Math.max(0,n-1))+"…";
                c.drawText(s,tr.left+Ui.dpf(30),tr.centerY()+Ui.dpf(4.5f),tp);
                tp.setColor(i==0?th.accent:th.secondary);tp.setTypeface(Ui.MONO_BOLD);c.drawText("⎘",tr.left+Ui.dpf(9),tr.centerY()+Ui.dpf(5),tp);
            }
        }
        c.restore();
        // bottom row
        int n=bottomCells();float w=W/(float)n,h=getHeight()-bottomPad-bt;
        for(int i=0;i<n;i++){
            tr.set(i*w+Ui.dpf(1.6f),bt+Ui.dpf(1.6f),(i+1)*w-Ui.dpf(1.6f),bt+h-Ui.dpf(1.6f));
            String lab;int edge=th.secondary;boolean sel=false;
            if(i==0)lab="ABC";else if(i==n-1)lab="⌫";
            else if(page==Layouts.EMOJIS){lab=Layouts.EMOJI_TABS[i-1];sel=emojiTab==i-1;edge=th.accent;}
            else if(page==Layouts.TRACKPAD){lab=PAD_CELLS[i];sel=i==2&&padSelect;edge=i==2?th.accent:th.secondary;}
            else lab="✕ SVUOTA";
            chamfer(tmp,tr,Math.min(Ui.dpf(6),tr.width()/4));
            keyBody(c,tmp,tr,edge,false,sel);
            tp.setTextAlign(Paint.Align.CENTER);
            boolean emoji=page==Layouts.EMOJIS&&i>0&&i<n-1;
            tp.setTypeface(emoji?Typeface.DEFAULT:Ui.MONO_BOLD);tp.setTextSize(Math.min(h*.36f,tr.width()*(lab.length()>3?.16f:.42f)));
            tp.setColor(emoji?(sel?0xffffffff:0xaaffffff):i==0?th.secondary:th.fg);
            Paint.FontMetrics fm=tp.getFontMetrics();c.drawText(lab,tr.centerX(),tr.centerY()-(fm.ascent+fm.descent)/2f,tp);
        }
    }

    private void drawPad(Canvas c,float W,float bt){
        tr.set(Ui.dpf(8),barH+Ui.dpf(6),W-Ui.dpf(8),bt-Ui.dpf(6));chamfer(tmp,tr,Ui.dpf(12));
        p.setStyle(Paint.Style.FILL);p.setColor(Theme.a(th.panel,padTouch?190:150));c.drawPath(tmp,p);
        c.save();c.clipPath(tmp);
        p.setColor(Theme.a(th.accent,22));float g=Ui.dpf(22);
        for(float gx=tr.left;gx<tr.right;gx+=g)c.drawRect(gx,tr.top,gx+Ui.dpf(1),tr.bottom,p);
        for(float gy=tr.top;gy<tr.bottom;gy+=g)c.drawRect(tr.left,gy,tr.right,gy+Ui.dpf(1),p);
        p.setColor(0x08ffffff);for(float y=tr.top;y<tr.bottom;y+=Ui.dpf(3))c.drawRect(tr.left,y,tr.right,y+Ui.dpf(1),p);
        c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.2f));p.setColor(Theme.a(padSelect?th.warn:th.accent,190));c.drawPath(tmp,p);
        p.setStrokeWidth(Ui.dpf(2.4f));float m=Ui.dpf(18);
        p.setColor(th.accent);c.drawLine(tr.left+Ui.dpf(12),tr.top,tr.left+Ui.dpf(12)+m,tr.top,p);
        p.setColor(th.secondary);c.drawLine(tr.right,tr.bottom-Ui.dpf(12),tr.right,tr.bottom-Ui.dpf(12)-m,p);
        p.setStyle(Paint.Style.FILL);
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextAlign(Paint.Align.CENTER);
        if(!padTouch){
            tp.setTextSize(Ui.dpf(13));tp.setColor(Theme.a(th.fg,170));
            c.drawText(padSelect?"◧ SELEZIONE ATTIVA":"⌖ TRACKPAD",tr.centerX(),tr.centerY()-Ui.dpf(4),tp);
            tp.setTypeface(Ui.MONO);tp.setTextSize(Ui.dpf(10.5f));tp.setColor(th.muted);
            c.drawText("trascina: ◀ ▶ lettere · ▲ ▼ righe",tr.centerX(),tr.centerY()+Ui.dpf(14),tp);
        }else{
            float r=Ui.dpf(16);p.setColor(Theme.a(padSelect?th.warn:th.accent,50));c.drawCircle(lastX,lastY,r*1.8f,p);
            p.setColor(Theme.a(padSelect?th.warn:th.accent,140));c.drawCircle(lastX,lastY,r,p);
            p.setColor(th.fg);c.drawCircle(lastX,lastY,Ui.dpf(4),p);
        }
    }
    private void drawHideHint(Canvas c){
        p.setStyle(Paint.Style.FILL);p.setColor(Theme.a(th.bg,170));c.drawRect(0,barH,getWidth(),getHeight(),p);
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextAlign(Paint.Align.CENTER);tp.setTextSize(Ui.dpf(15));
        float cy=barH+(getHeight()-barH)/2f;
        tp.setColor(Theme.a(th.secondary,160));c.drawText("▲ RILASCIA PER CHIUDERE",getWidth()/2f-Ui.dpf(1.5f),cy,tp);
        tp.setColor(th.accent);c.drawText("▲ RILASCIA PER CHIUDERE",getWidth()/2f+Ui.dpf(1.5f),cy,tp);
    }

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(idle);if(glitch>0)postDelayed(idle,2500);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(idle);removeCallbacks(longPress);removeCallbacks(repeatDel);super.onDetachedFromWindow();}
}
