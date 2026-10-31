package it.ampy.ampydeck;

import android.content.Context;
import android.content.SharedPreferences;

/** Every user-tunable switch of AmpyDeck lives here (same keys as AmpyRocket where shared). */
public final class Prefs {
    public static final String FILE="ampydeck_settings";

    // Animated backgrounds (same engine as AmpyRocket)
    public static final String[] BG={"grid","rain","particles","hex","starfield","plain"};
    public static final String[] BG_NAMES={"GRIGLIA SYNTHWAVE","PIOGGIA DIGITALE","RETE DI PARTICELLE",
        "ALVEARE ESAGONALE","WARP / STARFIELD","SOLO TEMA (statico)"};
    // Keyboard entry animation
    public static final String[] ENTRY={"glitchgenie","genie","glitch","none"};
    public static final String[] ENTRY_NAMES={"GLITCH + GENIE (AmpyRocket)","SOLO GENIE / LAMPADA","SOLO GLITCH","NESSUNA"};
    // Dictionary languages
    public static final String[] LANG={"it","en","iten"};
    public static final String[] LANG_NAMES={"ITALIANO","ENGLISH","ITALIANO + ENGLISH"};
    // Key style
    public static final String[] KEY_STYLE={"neon","glass","outline","flat"};
    public static final String[] KEY_STYLE_NAMES={"PANNELLO NEON (AmpyLink)","VETRO","SOLO CONTORNO","PIATTO"};

    private final SharedPreferences sp;
    private Prefs(Context c){sp=c.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    public static Prefs get(Context c){return new Prefs(c);}
    public SharedPreferences raw(){return sp;}

    public String s(String k,String d){return sp.getString(k,d);}
    public int i(String k,int d){return sp.getInt(k,d);}
    public boolean b(String k,boolean d){return sp.getBoolean(k,d);}
    public float f(String k,float d){return sp.getFloat(k,d);}
    public void put(String k,Object v){
        SharedPreferences.Editor e=sp.edit();
        if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
        else if(v instanceof Integer)e.putInt(k,(Integer)v);
        else if(v instanceof Float)e.putFloat(k,(Float)v);
        else e.putString(k,v==null?null:String.valueOf(v));
        e.apply();
    }

    public String theme(){return s("theme","cyberpunk");}
    public String background(){return s("background","grid");}
    public boolean scanlines(){return b("scanlines",true);}
    public int bgDim(){return i("bg_dim",60);}
    public int glitch(){return i("glitch_level",2);}
    public boolean haptics(){return b("haptics",true);}
    public boolean sound(){return b("sound",false);}
    public boolean ecoFps(){return b("eco_fps",false);}
    public String entry(){return s("entry_fx","glitchgenie");}
    public int entryMs(){return i("entry_ms",700);}
    public String lang(){return s("lang","iten");}
    public String keyStyle(){return s("key_style","neon");}
    public float height(){return f("kb_height",1f);}
    public boolean numberRow(){return b("number_row",true);}
    public boolean hints(){return b("hints",true);}
    public boolean popup(){return b("key_popup",true);}
    public boolean autocorrect(){return b("autocorrect",true);}
    public boolean autoCaps(){return b("auto_caps",true);}
    public boolean doubleSpace(){return b("double_space",true);}
    public boolean flow(){return b("flow",true);}
    public boolean learn(){return b("learn",true);}
    public boolean trail(){return b("trail",true);}
    public boolean clipHistory(){return b("clip_history",true);}
}
