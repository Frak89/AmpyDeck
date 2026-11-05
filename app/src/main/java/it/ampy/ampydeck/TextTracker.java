package it.ampy.ampydeck;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

/**
 * Keeps a local copy of the text just before the cursor, updated by every edit the keyboard makes.
 * Reads (text before cursor, caps mode) are then answered locally instead of with a blocking
 * round-trip to the app — those round-trips wait for the app's UI thread and were the cause of
 * the keyboard stutter while an app is busy drawing.
 */
public final class TextTracker extends InputConnectionWrapper {
    private static final int KEEP=240;
    private final StringBuilder tail=new StringBuilder();
    private String comp="";private boolean valid,knownStart;
    // absolute position of tail[0] once known, plus the cursor positions our recent edits lead to:
    // lets the keyboard tell its own (late) selection updates from the user moving the cursor
    private int base=-1;private final int[] hist=new int[24];private int hn;
    private void mark(){if(valid&&base>=0){hist[hn%hist.length]=base+tail.length()+comp.length();hn++;}}
    /** 1 = this cursor position is the result of our own edits, 0 = someone else moved it, -1 = unknown. */
    public int ours(int s,int e){
        if(!valid||base<0)return -1;if(s!=e)return 0;
        if(s==base+tail.length()+comp.length())return 1;
        for(int i=0;i<Math.min(hn,hist.length);i++)if(hist[i]==s)return 1;
        return 0;
    }
    /** Anchor absolute positions when the editor is quiet (no edits in flight). */
    public void anchor(int cursor){if(valid){base=cursor-tail.length()-comp.length();if(base<0)base=-1;hn=0;}}

    public TextTracker(){super(null,true);}
    public TextTracker use(InputConnection ic){setTarget(ic);return this;}
    public void invalidate(){valid=false;base=-1;hn=0;}
    public void reset(){valid=false;tail.setLength(0);comp="";knownStart=false;base=-1;hn=0;}

    private void fetch(){
        CharSequence s=super.getTextBeforeCursor(KEEP,0);
        tail.setLength(0);knownStart=false;base=-1;hn=0;
        if(s==null){valid=false;return;}
        String t=s.toString();
        if(!comp.isEmpty()&&t.endsWith(comp))t=t.substring(0,t.length()-comp.length());
        tail.append(t);knownStart=s.length()<KEEP;valid=true;
    }
    private void trim(){if(tail.length()>KEEP){int cut=tail.length()-KEEP;tail.delete(0,cut);knownStart=false;if(base>=0)base+=cut;}}
    private void chop(int n){
        if(n<=0)return;
        if(n>tail.length()){if(knownStart)tail.setLength(0);else valid=false;return;}
        tail.setLength(tail.length()-n);
    }

    @Override public CharSequence getTextBeforeCursor(int n,int flags){
        if(flags!=0)return super.getTextBeforeCursor(n,flags);
        if(!valid)fetch();
        if(!valid)return super.getTextBeforeCursor(n,flags);
        String all=tail+comp;
        if(all.length()<n&&!knownStart){fetch();all=tail+comp;}
        return all.length()<=n?all:all.substring(all.length()-n);
    }
    @Override public int getCursorCapsMode(int req){
        if(!valid)fetch();
        if(!valid)return super.getCursorCapsMode(req);
        String all=tail+comp;
        if(!knownStart&&all.trim().isEmpty())return super.getCursorCapsMode(req);
        return TextUtils.getCapsMode(all,all.length(),req);
    }

    @Override public boolean commitText(CharSequence text,int pos){
        comp="";if(valid){if(pos==1){tail.append(text);trim();}else valid=false;}
        mark();
        return super.commitText(text,pos);
    }
    @Override public boolean setComposingText(CharSequence text,int pos){comp=text==null?"":text.toString();mark();return super.setComposingText(text,pos);}
    @Override public boolean finishComposingText(){if(valid){tail.append(comp);trim();}comp="";mark();return super.finishComposingText();}
    @Override public boolean setComposingRegion(int start,int end){
        int len=end-start;
        if(valid&&len>=0&&len<=tail.length()){comp=tail.substring(tail.length()-len);tail.setLength(tail.length()-len);}
        else valid=false;
        return super.setComposingRegion(start,end);
    }
    @Override public boolean deleteSurroundingText(int before,int after){if(valid)chop(before);mark();return super.deleteSurroundingText(before,after);}
    @Override public boolean sendKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN&&valid){
            switch(e.getKeyCode()){
                case KeyEvent.KEYCODE_DEL:
                    if(!comp.isEmpty()||tail.length()==0)valid=false;
                    else{int n=Character.charCount(tail.codePointBefore(tail.length()));
                        // emoji sequences (ZWJ, variation selectors) are deleted as a cluster by the app
                        if(n==1&&tail.length()>1&&Character.isSurrogate(tail.charAt(tail.length()-1)))valid=false;else chop(n);
                        if(tail.length()>0){char c=tail.charAt(tail.length()-1);if(c=='‍'||c=='️'||Character.isSurrogate(c))valid=false;}}
                    break;
                case KeyEvent.KEYCODE_ENTER:tail.append('\n');trim();break;
                case KeyEvent.KEYCODE_SHIFT_LEFT:case KeyEvent.KEYCODE_SHIFT_RIGHT:break;
                default:valid=false;base=-1;
            }
        }
        if(e.getAction()==KeyEvent.ACTION_UP)mark();
        return super.sendKeyEvent(e);
    }
    @Override public boolean performEditorAction(int a){valid=false;return super.performEditorAction(a);}
    @Override public boolean performContextMenuAction(int id){valid=false;return super.performContextMenuAction(id);}
}
