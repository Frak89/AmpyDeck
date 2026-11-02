package it.ampy.ampydeck;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.*;

/**
 * AmpyDeck input method: SwiftKey-like engine (composing word, 3-slot predictions,
 * autocorrect on space with backspace-undo, learning, flow typing, smart punctuation,
 * double-space period, auto-capitalisation, clipboard history) on the neon AmpyDeck surface.
 */
public final class AmpyDeckIME extends InputMethodService implements KeyboardView.Listener {
    private DeckRoot root;private KeyboardView kv;private Prefs pr;private Dictionary dict;
    private final StringBuilder composing=new StringBuilder();
    private boolean predict=true,learn=true,autoCaps=true,autocorrect=true;
    private boolean autoSpaced;           // last action inserted a trailing space we may swallow
    private boolean flowComposing;        // composing word came from a flow gesture
    private boolean noCorrectOnce;        // user reverted a correction: keep the next word verbatim
    private String undoOriginal,undoReplacement;   // last autocorrect, reversible with ⌫
    private long lastSpaceAt;private int enterAction=EditorInfo.IME_ACTION_NONE;private boolean multiLine;
    private final ArrayList<String> clips=new ArrayList<>();private ClipboardManager cm;
    private boolean prefsDirty;
    private HandlerThread worker;private Handler bg;private final Handler ui=new Handler(Looper.getMainLooper());
    private int suggestSeq=0;private volatile String cachedTyped,cachedCorr;private List<String> flowAlternates=null;

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener=()->{
        try{
            if(!Prefs.get(this).clipHistory()||cm==null)return;
            ClipData d=cm.getPrimaryClip();if(d==null||d.getItemCount()==0)return;
            if(Build.VERSION.SDK_INT>=33&&d.getDescription()!=null&&d.getDescription().getExtras()!=null
                &&d.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE"))return;
            CharSequence t=d.getItemAt(0).coerceToText(this);if(t==null)return;String s=t.toString();
            if(s.trim().isEmpty()||s.length()>5000)return;
            clips.remove(s);clips.add(0,s);while(clips.size()>15)clips.remove(clips.size()-1);
            if(kv!=null)kv.setClips(clips);
        }catch(Throwable ignored){}
    };

    @Override public void onCreate(){
        super.onCreate();CrashLog.install(this);pr=Prefs.get(this);lastRev=pr.rev();lastDictReset=pr.i("dict_reset",0);
        dict=Dictionary.get(this);
        worker=new HandlerThread("ampydeck-predict");worker.start();bg=new Handler(worker.getLooper());
        cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null)cm.addPrimaryClipChangedListener(clipListener);
    }
    @Override public void onDestroy(){
        if(cm!=null)cm.removePrimaryClipChangedListener(clipListener);
        dict.save();worker.quitSafely();super.onDestroy();
    }
    private int lastRev,lastDictReset;
    /** Settings are edited in another process: pick up changes when the keyboard (re)appears. */
    private void syncPrefs(){
        pr=Prefs.get(this);
        if(pr.rev()!=lastRev){lastRev=pr.rev();prefsDirty=true;}
        int dr=pr.i("dict_reset",0);if(dr!=lastDictReset){lastDictReset=dr;Dictionary.drop();prefsDirty=true;}
        applyPrefs();
    }
    private void applyPrefs(){
        if(!prefsDirty)return;prefsDirty=false;
        dict=Dictionary.get(this);
        if(root!=null){root.reload();kv.setClips(clips);updateWindowColors();}
    }

    @Override public View onCreateInputView(){
        root=new DeckRoot(this,new SafeListener(this));kv=root.kv;kv.setClips(clips);prefsDirty=false;
        hideSystemImeBar();
        return root;
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    /** Show the on-screen keyboard even when a hardware keyboard is attached (like Gboard does). */
    @Override public boolean onEvaluateInputViewShown(){super.onEvaluateInputViewShown();return true;}

    /**
     * Android 16+: hide the system-drawn IME bar (the "v" back chevron and the keyboard-switch globe)
     * and draw our own strip with the AmpyDeck settings button in its place.
     */
    private void hideSystemImeBar(){
        boolean ok=false;
        if(Build.VERSION.SDK_INT>=36){
            try{Window w=getWindow().getWindow();
                if(w!=null){w.getInsetsController().hide(android.view.WindowInsets.Type.captionBar());ok=true;}}
            catch(Throwable t){CrashLog.log("hideCaptionBar",t);}
        }
        if(root!=null)root.setStrip(ok,()->kv.setPage(kv.page()==Layouts.MOUSE?Layouts.LETTERS:Layouts.MOUSE));
    }
    /** The system delegates the IME switcher to us when its bar is hidden: it lives in the ⧉ toolbar (⌨ TASTIERE). */
    @Override public void onCustomImeSwitcherButtonRequestedVisible(boolean visible){}

    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        try{startView(info,restarting);}catch(Throwable t){CrashLog.log("startInputView",t);}
    }
    private void startView(EditorInfo info,boolean restarting){
        syncPrefs();
        dict=Dictionary.get(this);
        composing.setLength(0);autoSpaced=false;flowComposing=false;undoOriginal=null;noCorrectOnce=false;flowAlternates=null;
        int cls=info.inputType&InputType.TYPE_MASK_CLASS,var=info.inputType&InputType.TYPE_MASK_VARIATION;
        boolean password=cls==InputType.TYPE_CLASS_TEXT&&(var==InputType.TYPE_TEXT_VARIATION_PASSWORD||var==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            ||var==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)||cls==InputType.TYPE_CLASS_NUMBER&&var==InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        boolean noSugg=(info.inputType&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0;
        boolean techy=cls==InputType.TYPE_CLASS_TEXT&&(var==InputType.TYPE_TEXT_VARIATION_URI||var==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            ||var==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS||var==InputType.TYPE_TEXT_VARIATION_FILTER);
        predict=cls==InputType.TYPE_CLASS_TEXT&&!password&&!techy&&!noSugg;
        learn=predict&&pr.learn()&&(Build.VERSION.SDK_INT<26||(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)==0);
        autocorrect=predict&&pr.autocorrect();
        autoCaps=pr.autoCaps()&&!password&&!techy;
        multiLine=(info.inputType&InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0;
        enterAction=info.imeOptions&EditorInfo.IME_MASK_ACTION;
        if((info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0)enterAction=EditorInfo.IME_ACTION_NONE;
        if(kv!=null){
            kv.closeToolbar();
            kv.setEnterLabel(enterLabel());
            kv.setSpaceLabel(spaceLabel());
            boolean numeric=cls==InputType.TYPE_CLASS_NUMBER||cls==InputType.TYPE_CLASS_PHONE||cls==InputType.TYPE_CLASS_DATETIME;
            kv.setPage(numeric?Layouts.SYM1:Layouts.LETTERS);
            if(kv.shift()!=2)kv.setShift(0);
            updateShift();
            root.setRunning(true);
        }
        updateWindowColors();
        hideSystemImeBar();
        editWord=null;
        refreshSuggestions();
    }
    @Override public void onWindowShown(){super.onWindowShown();try{if(root!=null){root.setRunning(true);root.playEntry();root.scheduleFit();}}catch(Throwable t){CrashLog.log("windowShown",t);}}
    @Override public void onWindowHidden(){super.onWindowHidden();AmpyMouseService ms=AmpyMouseService.get();if(ms!=null)ms.hide();if(kv!=null&&kv.page()==Layouts.MOUSE)kv.setPage(Layouts.LETTERS);if(root!=null)root.setRunning(false);dict.save();}
    @Override public void onFinishInputView(boolean finishing){
        super.onFinishInputView(finishing);
        if(composing.length()>0){InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.finishComposingText();composing.setLength(0);}
        dict.save();
    }

    private void updateWindowColors(){
        try{Window w=getWindow().getWindow();if(w!=null){Theme th=Theme.load(this);w.setNavigationBarColor(th.bg);}}catch(Throwable ignored){}
    }
    private String enterLabel(){
        if(multiLine&&enterAction!=EditorInfo.IME_ACTION_SEND)return "⏎";
        switch(enterAction){
            case EditorInfo.IME_ACTION_GO:return "GO";
            case EditorInfo.IME_ACTION_SEARCH:return "⌕";
            case EditorInfo.IME_ACTION_SEND:return "➤";
            case EditorInfo.IME_ACTION_NEXT:return "⇥";
            case EditorInfo.IME_ACTION_DONE:return "✓";
            case EditorInfo.IME_ACTION_PREVIOUS:return "⇤";
            default:return "⏎";
        }
    }
    private String spaceLabel(){
        String l=pr.lang();return "it".equals(l)?"ITALIANO":"en".equals(l)?"ENGLISH":"ITA · ENG";
    }

    // ------------------------------------------------------------------ text context helpers
    private InputConnection ic(){lastSelfEdit=SystemClock.uptimeMillis();return getCurrentInputConnection();}
    private String before(int n){InputConnection ic=ic();if(ic==null)return "";CharSequence s=ic.getTextBeforeCursor(n,0);return s==null?"":s.toString();}
    private static boolean wordChar(char c){return Character.isLetter(c)||c=='\''||c=='’';}
    /** Word before the composing/current word (for bigrams). */
    private String prevWord(){
        String b=before(80);
        int i=b.length();
        while(i>0&&wordChar(b.charAt(i-1)))i--;   // skip the word being typed (composing text is in the editor)
        while(i>0&&!wordChar(b.charAt(i-1))){char c=b.charAt(i-1);if(c=='.'||c=='!'||c=='?'||c=='\n')return null;i--;}
        int e=i;while(i>0&&wordChar(b.charAt(i-1)))i--;
        return e>i?b.substring(i,e):null;
    }

    /** For a flow stroke the next word follows the last full word before the cursor. */
    private String prevWordForFlow(){
        if(flowComposing)return composing.toString();
        String b=before(80);int i=b.length();
        while(i>0&&!wordChar(b.charAt(i-1))){char c=b.charAt(i-1);if(c=='.'||c=='!'||c=='?'||c=='\n')return null;i--;}
        int e=i;while(i>0&&wordChar(b.charAt(i-1)))i--;
        return e>i?b.substring(i,e):null;
    }

    private void updateShift(){
        if(kv==null||kv.page()!=Layouts.LETTERS)return;
        if(kv.shift()==2)return;
        int caps=0;
        if(autoCaps&&composing.length()==0){InputConnection ic=ic();EditorInfo ei=getCurrentInputEditorInfo();
            if(ic!=null&&ei!=null&&ei.inputType!=0)caps=ic.getCursorCapsMode(ei.inputType);}
        kv.setShift(caps!=0?1:0);
    }

    // ------------------------------------------------------------------ suggestions
    private String editWord;private int editStart,editEnd;private long lastSelfEdit;
    private void refreshSuggestions(){
        if(kv==null)return;
        if(!predict){kv.setSuggestions("","","",false);return;}
        if(editWord!=null&&composing.length()==0){
            final String w=editWord;final int seq=++suggestSeq;
            bg.post(CrashLog.safe("editSuggest",()->{
                Dictionary d=dict;String corr=d.correction(w,null);List<String> l=d.suggest(w,null,4);
                ArrayList<String> alt=new ArrayList<>();
                if(corr!=null)alt.add(corr);for(String x:l)if(!x.equals(w)&&!alt.contains(x))alt.add(x);
                String a=alt.size()>1?alt.get(1):"",b=alt.size()>0?alt.get(0):w,c=alt.size()>2?alt.get(2):"";
                ui.post(CrashLog.safe("editSuggest-ui",()->{if(seq==suggestSeq&&kv!=null)kv.setSuggestions(a,b,c,corr!=null);}));
            }));
            return;
        }
        if(flowComposing&&flowAlternates!=null){
            List<String> a=flowAlternates;String cur=composing.toString();
            ArrayList<String> rest=new ArrayList<>();for(String s:a)if(!s.equalsIgnoreCase(cur))rest.add(s);
            kv.setSuggestions(rest.size()>0?rest.get(0):"",cur,rest.size()>1?rest.get(1):"",false);return;
        }
        final String typed=composing.toString();final String prev=prevWord();final boolean ac=autocorrect&&!noCorrectOnce;
        final int seq=++suggestSeq;
        bg.post(CrashLog.safe("suggest",()->{
            Dictionary d=dict;
            List<String> list=d.suggest(typed,prev,5);
            String corr=ac&&!typed.isEmpty()?d.correction(typed,prev):null;
            cachedTyped=typed;cachedCorr=corr;
            String[] out=arrange(typed,list,corr);boolean isCorr=corr!=null;
            ui.post(CrashLog.safe("suggest-ui",()->{if(seq==suggestSeq&&kv!=null)kv.setSuggestions(out[0],out[1],out[2],isCorr);}));
        }));
    }
    /** SwiftKey arrangement: centre = what space will commit, left = verbatim, right = next best. */
    private static String[] arrange(String typed,List<String> list,String corr){
        String[] o={"","",""};
        if(typed.isEmpty()){
            if(list.size()>0)o[1]=list.get(0);if(list.size()>1)o[0]=list.get(1);if(list.size()>2)o[2]=list.get(2);return o;
        }
        ArrayList<String> rest=new ArrayList<>();for(String s:list)if(!s.equals(typed)&&!s.equals(corr))rest.add(s);
        if(corr!=null){o[0]="\""+typed+"\"";o[1]=corr;o[2]=rest.size()>0?rest.get(0):"";}
        else{o[1]=typed;o[0]=rest.size()>0?rest.get(0):"";o[2]=rest.size()>1?rest.get(1):"";}
        return o;
    }

    // ------------------------------------------------------------------ committing
    private void setComposing(){InputConnection ic=ic();if(ic!=null)ic.setComposingText(composing,1);}

    /** Commits the composing word (autocorrected when asked) and learns it. Returns what was committed. */
    private String commitWord(boolean correct,String trailing){
        InputConnection ic=ic();if(ic==null)return "";
        String typed=composing.toString();String out=typed;
        if(!typed.isEmpty()){
            if(correct&&autocorrect&&!noCorrectOnce&&!flowComposing){
                String c=typed.equals(cachedTyped)?cachedCorr:dict.correction(typed,prevWord());
                if(c!=null&&!c.equals(typed)){out=c;undoOriginal=typed;undoReplacement=c+trailing;}else undoOriginal=null;
            }else undoOriginal=null;
            if(learn&&(out.equals(typed)||flowComposing))learnLater(out,prevWord());
        }
        ic.commitText(out+trailing,1);
        composing.setLength(0);noCorrectOnce=false;flowComposing=false;flowAlternates=null;
        return out;
    }

    /** Learning may rebuild the word index: keep it off the UI thread. */
    private void learnLater(String w,String prev){final Dictionary d=dict;bg.post(CrashLog.safe("learn '"+w+"'",()->d.learn(w,prev)));}

    private void typeLetter(String s){
        InputConnection ic=ic();if(ic==null)return;
        editWord=null;
        if(!predict){ic.commitText(s,1);return;}
        if(flowComposing){commitWord(false,"");ic.commitText(" ",1);}   // tap after a flow word starts a new word
        if(composing.length()==0){
            // resume a word the cursor is glued to (SwiftKey edits words in place)
            String b=before(48);int i=b.length();while(i>0&&wordChar(b.charAt(i-1)))i--;
            CharSequence after=ic.getTextAfterCursor(1,0);
            if(i<b.length()&&(after==null||after.length()==0||!wordChar(after.charAt(0)))){
                String w=b.substring(i);
                android.view.inputmethod.ExtractedText et=ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(),0);
                if(et!=null&&et.selectionStart==et.selectionEnd){
                    int cur=et.startOffset+et.selectionStart;ic.setComposingRegion(cur-w.length(),cur);composing.append(w);}
            }
        }
        composing.append(s);setComposing();autoSpaced=false;undoOriginal=null;
    }

    @Override public void onText(String s){
        InputConnection ic=ic();if(ic==null)return;
        char c=s.length()==1?s.charAt(0):0;
        if(c!=0&&Character.isLetter(c)||c=='\''&&composing.length()>0){typeLetter(s);afterInput();return;}
        boolean punct=c!=0&&".,!?;:)]}%»”".indexOf(c)>=0;
        ic.beginBatchEdit();
        if(composing.length()>0)commitWord(punct,"");
        if(punct&&autoSpaced){CharSequence b=ic.getTextBeforeCursor(1,0);if(b!=null&&b.length()==1&&b.charAt(0)==' ')ic.deleteSurroundingText(1,0);}
        ic.commitText(s,1);
        boolean addSpace=autoSpaced&&c!=0&&".,!?;:".indexOf(c)>=0;
        if(addSpace)ic.commitText(" ",1);
        autoSpaced=addSpace;undoOriginal=null;
        ic.endBatchEdit();
        afterInput();
    }

    @Override public void onCode(int code){
        InputConnection ic=ic();if(ic==null)return;
        switch(code){
            case Layouts.DEL:{
                if(undoOriginal!=null){  // revert last autocorrect (SwiftKey ⌫ undo)
                    CharSequence b=ic.getTextBeforeCursor(undoReplacement.length(),0);
                    if(b!=null&&b.toString().equals(undoReplacement)){
                        ic.beginBatchEdit();ic.deleteSurroundingText(undoReplacement.length(),0);
                        composing.setLength(0);composing.append(undoOriginal);setComposing();ic.endBatchEdit();
                        undoOriginal=null;noCorrectOnce=true;autoSpaced=false;afterInput();return;
                    }
                    undoOriginal=null;
                }
                if(composing.length()>0){
                    composing.deleteCharAt(composing.length()-1);
                    if(flowComposing){composing.setLength(0);ic.commitText("",1);flowComposing=false;flowAlternates=null;}
                    else if(composing.length()==0)ic.commitText("",1);else setComposing();
                }else{
                    CharSequence sel=ic.getSelectedText(0);
                    if(sel!=null&&sel.length()>0)ic.commitText("",1);
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
                autoSpaced=false;afterInput();return;
            }
            case Layouts.SPACE:{
                long now=SystemClock.uptimeMillis();
                ic.beginBatchEdit();
                if(composing.length()>0){commitWord(true," ");autoSpaced=false;}
                else{
                    String b=before(3);
                    if(pr.doubleSpace()&&now-lastSpaceAt<650&&b.length()>=2&&b.charAt(b.length()-1)==' '
                        &&Character.isLetterOrDigit(b.charAt(b.length()-2))){
                        ic.deleteSurroundingText(1,0);ic.commitText(". ",1);
                    }else ic.commitText(" ",1);
                    autoSpaced=false;undoOriginal=null;
                }
                ic.endBatchEdit();lastSpaceAt=now;afterInput();return;
            }
            case Layouts.ENTER:{
                if(composing.length()>0)commitWord(false,"");
                if(enterAction!=EditorInfo.IME_ACTION_NONE&&enterAction!=EditorInfo.IME_ACTION_UNSPECIFIED&&!multiLine)ic.performEditorAction(enterAction);
                else if(enterAction==EditorInfo.IME_ACTION_SEND&&multiLine)ic.performEditorAction(enterAction);
                else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                autoSpaced=false;undoOriginal=null;afterInput();return;
            }
        }
    }

    @Override public void onDeleteWord(){
        InputConnection ic=ic();if(ic==null)return;
        ic.beginBatchEdit();
        if(composing.length()>0){composing.setLength(0);ic.commitText("",1);flowComposing=false;}
        else{
            String b=before(64);int i=b.length();
            while(i>0&&Character.isWhitespace(b.charAt(i-1)))i--;
            if(i>0&&!wordChar(b.charAt(i-1))&&!Character.isLetterOrDigit(b.charAt(i-1)))i--;
            else while(i>0&&(wordChar(b.charAt(i-1))||Character.isDigit(b.charAt(i-1))))i--;
            int n=b.length()-i;if(n>0)ic.deleteSurroundingText(n,0);
        }
        ic.endBatchEdit();undoOriginal=null;autoSpaced=false;afterInput();
    }

    @Override public void onCursor(int dir){
        InputConnection ic=ic();if(ic==null)return;
        if(composing.length()>0){ic.finishComposingText();composing.setLength(0);flowComposing=false;}
        sendDownUpKeyEvents(dir<0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);
        undoOriginal=null;autoSpaced=false;
    }

    /** Trackpad page: arrow / home / end keys, holding shift when selection mode is on. */
    @Override public void onMove(int keyCode,boolean select){
        InputConnection ic=ic();if(ic==null)return;
        if(composing.length()>0){ic.finishComposingText();composing.setLength(0);flowComposing=false;flowAlternates=null;}
        if(select){
            long t=SystemClock.uptimeMillis();int meta=KeyEvent.META_SHIFT_ON|KeyEvent.META_SHIFT_LEFT_ON;
            ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT,0,meta));
            ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,keyCode,0,meta));
            ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,keyCode,0,meta));
            ic.sendKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_SHIFT_LEFT,0,0));
        }else sendDownUpKeyEvents(keyCode);
        undoOriginal=null;autoSpaced=false;
    }

    @Override public void onFlow(List<PointF> path,Map<Character,PointF> keys,float keyW){
        final int shift=kv.shift();
        final String prev=composing.length()>0?composing.toString():prevWordForFlow();
        bg.post(CrashLog.safe("flow",()->{
            List<String> words=FlowDecoder.decode(dict,path,keys,keyW,prev,4);
            ui.post(CrashLog.safe("flow-ui",()->applyFlow(words,shift)));
        }));
    }
    private void applyFlow(List<String> words,int shift){
        InputConnection ic=ic();if(ic==null||words.isEmpty())return;
        ic.beginBatchEdit();
        if(composing.length()>0)commitWord(!flowComposing,"");
        String b=before(1);
        if(b.length()>0&&!Character.isWhitespace(b.charAt(0))&&"([{\"'«“".indexOf(b.charAt(0))<0)ic.commitText(" ",1);
        ArrayList<String> ws=new ArrayList<>();
        for(String w:words){String x=shift==2?w.toUpperCase(Locale.ROOT):shift==1?Character.toUpperCase(w.charAt(0))+w.substring(1):w;ws.add(x);}
        composing.setLength(0);composing.append(ws.get(0));setComposing();
        ic.endBatchEdit();
        flowComposing=true;flowAlternates=ws;autoSpaced=false;undoOriginal=null;
        if(kv.shift()==1)kv.setShift(0);
        refreshSuggestions();
    }

    @Override public void onPick(int slot){
        InputConnection ic=ic();if(ic==null)return;
        String s=kv.suggestion(slot);if(s.isEmpty())return;
        if(editWord!=null&&composing.length()==0){   // replace the tapped word
            String w=Dictionary.matchCase(editWord,s.replace("\"",""));
            ic.beginBatchEdit();ic.setComposingRegion(editStart,editEnd);ic.commitText(w,1);ic.endBatchEdit();
            if(learn)learnLater(w,null);
            editWord=null;afterInput();return;
        }
        if(s.length()>1&&s.startsWith("\"")&&s.endsWith("\""))s=s.substring(1,s.length()-1);
        ic.beginBatchEdit();
        String prev=prevWord();
        if(composing.length()==0){
            String b=before(1);if(b.length()>0&&!Character.isWhitespace(b.charAt(0)))ic.commitText(" ",1);
        }
        ic.commitText(s+" ",1);
        ic.endBatchEdit();
        if(learn)learnLater(s,prev);
        composing.setLength(0);flowComposing=false;flowAlternates=null;noCorrectOnce=false;undoOriginal=null;autoSpaced=true;
        afterInput();
    }
    @Override public void onForget(int slot){
        String s=kv.suggestion(slot);if(s.startsWith("\""))return;
        final Dictionary d=dict;bg.post(CrashLog.safe("forget",()->{d.forget(s);ui.post(this::refreshSuggestions);}));Ui.toast(this,"« "+s+" » rimossa dai suggerimenti");refreshSuggestions();
    }

    @Override public void onToolbar(String action){
        switch(action){
            case "hide":requestHideSelf(0);break;
            case "settings":startActivity(new Intent(this,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));break;
            case "emoji":kv.setPage(Layouts.EMOJIS);break;
            case "clip":kv.setPage(Layouts.CLIP);break;
            case "theme":{int i=(Arrays.asList(Theme.KEYS).indexOf(pr.theme())+1)%Theme.KEYS.length;pr.put("theme",Theme.KEYS[i]);
                syncPrefs();Ui.toast(this,Theme.NAMES[i]);break;}
            case "bg":{int i=(Arrays.asList(Prefs.BG).indexOf(pr.background())+1)%Prefs.BG.length;pr.put("background",Prefs.BG[i]);
                syncPrefs();Ui.toast(this,Prefs.BG_NAMES[i]);break;}
            case "replay":root.playEntry();break;
            case "switch":onSpaceLongPress();break;
            case "mouse":kv.setPage(Layouts.MOUSE);break;
            case "selectall":case "copy":case "paste":{
                InputConnection ic=ic();if(ic==null)break;
                if(composing.length()>0){ic.finishComposingText();composing.setLength(0);flowComposing=false;}
                ic.performContextMenuAction("copy".equals(action)?android.R.id.copy:"paste".equals(action)?android.R.id.paste:android.R.id.selectAll);
                if("copy".equals(action))Ui.toast(this,"Copiato");
                afterInput();break;}
        }
    }
    @Override public void onSpaceLongPress(){
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null)imm.showInputMethodPicker();
    }
    @Override public void onShiftChanged(){}
    @Override public float letterWeight(String letter){
        if(!predict)return 0;
        Dictionary d=dict;return d.prefixWeight(composing.toString()+letter.toLowerCase(Locale.ROOT));
    }
    @Override public void onClip(String text){
        InputConnection ic=ic();if(ic==null)return;
        if(composing.length()>0)commitWord(false,"");
        ic.commitText(text,1);autoSpaced=false;afterInput();
    }
    @Override public void onClearClips(){clips.clear();kv.setClips(clips);}
    @Override public void onPageChanged(int page){
        if(page==Layouts.LETTERS)updateShift();
        if(root!=null)root.invalidate();
        AmpyMouseService m=AmpyMouseService.get();
        if(page==Layouts.MOUSE){
            if(m==null){
                Ui.toast(this,"Attiva \"AmpyDeck Mouse\" in Accessibilità per il puntatore a schermo");
                try{startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Throwable ignored){}
                kv.post(()->kv.setPage(Layouts.LETTERS));return;
            }
            m.show();
        }else if(m!=null)m.hide();
    }
    @Override public void onMouseMove(float dx,float dy){AmpyMouseService m=AmpyMouseService.get();if(m!=null)m.move(dx,dy);}
    @Override public void onMouse(String action,float v){
        AmpyMouseService m=AmpyMouseService.get();if(m==null)return;
        switch(action){
            case "click":m.click(false);break;
            case "long":m.click(true);break;
            case "scroll":m.scroll(-v);break;
            default:m.global(action);
        }
    }

    private void afterInput(){updateShift();refreshSuggestions();}

    @Override public void onUpdateSelection(int oss,int ose,int nss,int nse,int cs,int ce){
        super.onUpdateSelection(oss,ose,nss,nse,cs,ce);
        try{selectionMoved(oss,ose,nss,nse,cs,ce);}catch(Throwable t){CrashLog.log("selection",t);}
    }
    private void selectionMoved(int oss,int ose,int nss,int nse,int cs,int ce){
        if(composing.length()>0&&(nss!=ce||nse!=ce)){
            // cursor jumped away from the word being composed
            composing.setLength(0);flowComposing=false;flowAlternates=null;
            InputConnection ic=ic();if(ic!=null)ic.finishComposingText();
            undoOriginal=null;autoSpaced=false;updateShift();refreshSuggestions();
        }else if(composing.length()==0&&(nss!=ose||nse!=ose)&&cs==-1){
            if(Math.abs(nss-oss)>1||nss!=nse){undoOriginal=null;autoSpaced=false;}
            editWord=null;
            // a selection change we did not cause = the user tapped somewhere in the text
            if(nss==nse&&predict&&SystemClock.uptimeMillis()-lastSelfEdit>250)findEditWord(nss);
            updateShift();refreshSuggestions();
        }
    }

    /** Cursor placed inside / at the end of a word (e.g. a red-underlined one): offer fixes for it. */
    private void findEditWord(int cur){
        InputConnection ic=ic();if(ic==null)return;
        CharSequence b=ic.getTextBeforeCursor(40,0),a=ic.getTextAfterCursor(40,0);if(b==null||a==null)return;
        int i=b.length();while(i>0&&wordChar(b.charAt(i-1)))i--;
        int j=0;while(j<a.length()&&wordChar(a.charAt(j)))j++;
        String w=b.subSequence(i,b.length()).toString()+a.subSequence(0,j);
        if(w.length()<2)return;
        editWord=w;editStart=cur-(b.length()-i);editEnd=cur+j;
    }

    static boolean isEnabled(Context c){
        InputMethodManager imm=(InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm==null)return false;
        for(android.view.inputmethod.InputMethodInfo i:imm.getEnabledInputMethodList())if(i.getPackageName().equals(c.getPackageName()))return true;
        return false;
    }
    static boolean isCurrent(Context c){
        String id=android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        return id!=null&&id.startsWith(c.getPackageName()+"/");
    }
    static String safe(String s){return TextUtils.isEmpty(s)?"":s;}
}
