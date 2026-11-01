package it.ampy.ampydeck;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.Arrays;

/** AmpyDeck control deck: activation, look, entry FX, typing engine, dictionary. */
public final class SettingsActivity extends Activity {
    private Prefs pr;private Theme th;private LinearLayout list;private ScrollView sv;

    @Override protected void onCreate(Bundle b){super.onCreate(b);CrashLog.install(this);pr=Prefs.get(this);build();
        if(getIntent().getBooleanExtra("smoke",false)){focusTest();spellProbe();}}
    /** CI diagnostics: ask the system spell checker directly and log what answers. */
    private void spellProbe(){
        try{
            android.view.textservice.TextServicesManager tsm=(android.view.textservice.TextServicesManager)getSystemService(TEXT_SERVICES_MANAGER_SERVICE);
            if(Build.VERSION.SDK_INT>=31)android.util.Log.i("AmpyDeckSpell","current="+(tsm.getCurrentSpellCheckerInfo()==null?null:tsm.getCurrentSpellCheckerInfo().getId())+" enabled="+tsm.isSpellCheckerEnabled());
            android.view.textservice.SpellCheckerSession ss=tsm.newSpellCheckerSession(null,java.util.Locale.US,new android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener(){
                @Override public void onGetSuggestions(android.view.textservice.SuggestionsInfo[] r){android.util.Log.i("AmpyDeckSpell","probe words "+r.length);}
                @Override public void onGetSentenceSuggestions(android.view.textservice.SentenceSuggestionsInfo[] r){
                    StringBuilder b=new StringBuilder();for(android.view.textservice.SentenceSuggestionsInfo x:r)if(x!=null)for(int i=0;i<x.getSuggestionsCount();i++)
                        b.append(x.getOffsetAt(i)).append(':').append(x.getSuggestionsInfoAt(i).getSuggestionsAttributes()).append(' ');
                    android.util.Log.i("AmpyDeckSpell","probe sentence "+b);}
            },true);
            android.util.Log.i("AmpyDeckSpell","session="+ss);
            if(ss!=null)testField.postDelayed(()->ss.getSentenceSuggestions(new android.view.textservice.TextInfo[]{new android.view.textservice.TextInfo("ciao kzqwx grazei")},3),3000);
        }catch(Throwable t){android.util.Log.e("AmpyDeckSpell","probe",t);}
    }
    private EditText testField;
    /** Used by the CI smoke test: focus the test field and open the keyboard. */
    private void focusTest(){
        if(testField==null)return;testField.postDelayed(()->{testField.requestFocus();
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showSoftInput(testField,InputMethodManager.SHOW_IMPLICIT);},800);}
    @Override protected void onResume(){super.onResume();if(list!=null&&statusKey()!=builtStatus)build();}
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);
        // rebuild only when the activation status really changed (coming back from system screens):
        // rebuilding while typing would destroy the test field and close the keyboard
        if(f&&list!=null&&statusKey()!=builtStatus)build();}
    private int builtStatus=-1;
    private int statusKey(){return (AmpyDeckIME.isEnabled(this)?1:0)|(AmpyDeckIME.isCurrent(this)?2:0)|(spellOn()?4:0);}
    private boolean spellOn(){String s=Settings.Secure.getString(getContentResolver(),"selected_spell_checker");return s!=null&&s.startsWith(getPackageName()+"/");}

    private void build(){
        int scroll=sv!=null?sv.getScrollY():0;
        th=Theme.load(this);builtStatus=statusKey();
        getWindow().setStatusBarColor(th.bg);getWindow().setNavigationBarColor(th.bg);
        FrameLayout root=new FrameLayout(this);
        CyberBackgroundView bg=new CyberBackgroundView(this);root.addView(bg);
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);root.addView(col);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(Ui.dp(16),Ui.dp(18),Ui.dp(16),Ui.dp(10));
        GlitchTextView t=new GlitchTextView(this,th,16,th.fg,true);t.alignLeft();t.setText("⌨ AMPYDECK // CONTROL DECK");
        head.addView(t,new LinearLayout.LayoutParams(0,-2,1));col.addView(head);
        sv=new ScrollView(this);col.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(Ui.dp(14),0,Ui.dp(14),Ui.dp(40));sv.addView(list);
        setContentView(root);
        // Android 15+ draws apps edge-to-edge: keep the deck clear of status/nav bars and the keyboard
        col.setOnApplyWindowInsetsListener((v,ins)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=ins.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()),
                ime=ins.getInsets(android.view.WindowInsets.Type.ime());top=bars.top;bottom=Math.max(bars.bottom,ime.bottom);}
            else{top=ins.getSystemWindowInsetTop();bottom=ins.getSystemWindowInsetBottom();}
            v.setPadding(0,top,0,bottom);return ins;});

        section("00 / ATTIVAZIONE");
        boolean en=AmpyDeckIME.isEnabled(this),cur=AmpyDeckIME.isCurrent(this);
        status("1 · Abilita AmpyDeck tra le tastiere",en,()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        status("2 · Scegli AmpyDeck come tastiera attiva",cur,()->{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(imm!=null)imm.showInputMethodPicker();});
        status("3 · Controllo ortografico AmpyDeck (sottolineatura rossa)",spellOn(),()->{
            try{startActivity(new Intent().setClassName("com.android.settings","com.android.settings.Settings$SpellCheckersSettingsActivity"));}
            catch(Throwable e){try{startActivity(new Intent("android.settings.SPELL_CHECKER_SETTINGS"));}
                catch(Throwable e2){startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));}}});
        EditText test=new EditText(this);testField=test;test.setHint("Prova qui: tocca e scrivi / swipe…");test.setTextColor(th.fg);test.setHintTextColor(th.muted);
        test.setTypeface(Ui.MONO);test.setPadding(Ui.dp(14),Ui.dp(14),Ui.dp(14),Ui.dp(14));test.setMinLines(2);
        test.setBackground(new Ui.NeonPanel(th,th.secondary,.6f));
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=Ui.dp(8);list.addView(test,tl);

        section("01 / LOOK (AmpyLink · AmpyRocket)");
        choice("Tema",Theme.NAMES,Theme.KEYS,"theme","cyberpunk");
        choice("Sfondo animato",Prefs.BG_NAMES,Prefs.BG,"background","grid");
        toggle("Scanlines CRT","scanlines",true);
        intChoice("Intensità sfondo tema",new String[]{"20%","40%","60%","80%","100%"},new int[]{20,40,60,80,100},"bg_dim",60);
        choice("Stile tasti",Prefs.KEY_STYLE_NAMES,Prefs.KEY_STYLE,"key_style","neon");
        floatChoice("Altezza tastiera",new String[]{"85%","92%","100%","108%","116%","125%"},new float[]{.85f,.92f,1f,1.08f,1.16f,1.25f},"kb_height",1f);
        toggle("Riga dei numeri","number_row",true);
        toggle("Simboli sui tasti (pressione lunga)","hints",true);
        toggle("Tasto emoji in basso","emoji_key",true);
        toggle("Anteprima tasto premuto","key_popup",true);

        section("02 / ENTRATA FX");
        choice("Animazione apertura",Prefs.ENTRY_NAMES,Prefs.ENTRY,"entry_fx","glitchgenie");
        intChoice("Durata",new String[]{"400 ms","550 ms","700 ms","900 ms","1200 ms"},new int[]{400,550,700,900,1200},"entry_ms",700);
        intChoice("Intensità glitch",new String[]{"OFF","LEGGERO","MEDIO","FORTE"},new int[]{0,1,2,3},"glitch_level",2);
        toggle("Risparmio batteria animazioni (30 fps)","eco_fps",false);

        section("03 / SCRITTURA");
        choice("Lingua dizionario",Prefs.LANG_NAMES,Prefs.LANG,"lang","iten");
        toggle("Autocorrezione (⌫ subito dopo per annullare)","autocorrect",true);
        toggle("Maiuscola automatica","auto_caps",true);
        toggle("Doppio spazio = punto","double_space",true);
        toggle("Flow: scrivi scorrendo sulle lettere","flow",true);
        toggle("Scia neon del flow","trail",true);
        toggle("Impara le mie parole","learn",true);
        toggle("Cronologia appunti (solo in memoria)","clip_history",true);
        toggle("Vibrazione tasti","haptics",true);
        toggle("Suono tasti","sound",false);

        section("04 / DIZIONARIO");
        Dictionary d=Dictionary.get(this);
        item("Parole imparate: "+d.learntCount(),"Tieni premuto un suggerimento nella barra per rimuoverlo.",()->{});
        item("Cancella parole e abitudini imparate","Azzera dizionario personale e coppie di parole.",()->{
            Dictionary.clearLearnt(this);pr.put("dict_reset",pr.i("dict_reset",0)+1);Ui.toast(this,"Dizionario personale azzerato");build();});

        section("05 / GESTI RAPIDI");
        item("Barra spaziatrice","Scorri a destra/sinistra: muovi il cursore. Tieni premuto: TRACKPAD (muovi il dito, rilascia per tornare). Dal basso verso l'alto: chiudi.",()->{});
        item("⌫ Cancella","Tieni premuto: ripeti (poi parole intere). Scorri a sinistra: cancella la parola.",()->{});
        item("⇧ Maiuscole","Un tocco: maiuscola singola. Doppio tocco: BLOCCO MAIUSCOLE.",()->{});
        item("⧉ Toolbar","Tocca ⧉ nella barra: impostazioni, emoji, appunti, tema, sfondo, replay FX, cambio tastiera.",()->{});
        item("Parola sottolineata","Tocca una parola già scritta: la barra mostra le correzioni, toccane una per sostituirla.",()->{});

        section("06 / DIAGNOSTICA");
        String crash=CrashLog.read(this);
        if(crash==null)item("Nessun errore registrato","Se la tastiera si chiude da sola, qui compare il motivo.",()->{});
        else{
            item("⚠ Ultimo errore — tocca per copiarlo",crash.length()>600?crash.substring(0,600)+"…":crash,()->{
                android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("AmpyDeck crash",crash));Ui.toast(this,"Errore copiato");});
            item("Cancella registro errori","",()->{CrashLog.clear(this);build();});
        }

        section("07 / INFO");
        item("AmpyDeck 1.0 — Neon Keys","Tastiera cyberpunk della famiglia Ampy (AmpyLink, AmpyRocket). Nessun permesso di rete: tutto resta sul telefono.",()->{});
        sv.post(()->sv.scrollTo(0,scroll));
    }

    // --------------------------------------------------------- rows (same look as AmpyRocket)
    private void section(String s){
        GlitchTextView t=new GlitchTextView(this,th,12,th.secondary,true);t.alignLeft();t.setText(s);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(22);lp.bottomMargin=Ui.dp(6);list.addView(t,lp);
    }
    private LinearLayout row(String title,String sub,String value,int valCol,Runnable click){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(Ui.dp(14),Ui.dp(12),Ui.dp(14),Ui.dp(12));
        r.setBackground(new Ui.NeonPanel(th,Theme.a(th.accent,120),.55f));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(Ui.text(this,title,13,th.fg,false));
        if(sub!=null&&!sub.isEmpty()){TextView s=Ui.text(this,sub,10.5f,th.muted,false);s.setPadding(0,Ui.dp(4),0,0);txt.addView(s);}
        r.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        if(value!=null){TextView v=Ui.text(this,value,11,valCol,true);v.setGravity(Gravity.END);v.setPadding(Ui.dp(8),Ui.dp(6),Ui.dp(8),Ui.dp(6));
            r.addView(v,new LinearLayout.LayoutParams(-2,-2));
            if("[ ON ]".equals(value))Blink.attach(v,th,th.accent,true,4);}
        Blink bl=Blink.attach(r,th,th.accent,false,6);
        r.setOnClickListener(v->{Ui.tick(v);bl.burst();v.postDelayed(click,140);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(5);list.addView(r,lp);return r;
    }
    private void item(String t,String s,Runnable r){row(t,s,null,0,r);}
    private void status(String t,boolean ok,Runnable r){row(t,null,ok?"● OK":"○ ATTIVA",ok?th.accent:th.warn,r);}
    private void toggle(String t,String key,boolean def){
        boolean on=pr.b(key,def);row(t,null,on?"[ ON ]":"[ OFF ]",on?th.accent:th.muted,()->{pr.put(key,!pr.b(key,def));build();});
    }
    private void choice(String t,String[] names,String[] keys,String key,String def){
        int sel=Math.max(0,Arrays.asList(keys).indexOf(pr.s(key,def)));
        row(t,null,names[sel],th.accent,()->CyberMenu.choose(this,th,t,names,sel,i->{pr.put(key,keys[i]);build();}));
    }
    private void intChoice(String t,String[] names,int[] vals,String key,int def){
        int cur=pr.i(key,def),sel=0;for(int i=0;i<vals.length;i++)if(vals[i]==cur)sel=i;final int s=sel;
        row(t,null,names[sel],th.accent,()->CyberMenu.choose(this,th,t,names,s,i->{pr.put(key,vals[i]);build();}));
    }
    private void floatChoice(String t,String[] names,float[] vals,String key,float def){
        float cur=pr.f(key,def);int sel=0;for(int i=0;i<vals.length;i++)if(Math.abs(vals[i]-cur)<.01f)sel=i;final int s=sel;
        row(t,null,names[sel],th.accent,()->CyberMenu.choose(this,th,t,names,s,i->{pr.put(key,vals[i]);build();}));
    }
}
