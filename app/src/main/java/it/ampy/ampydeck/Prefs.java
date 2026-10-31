package it.ampy.ampydeck;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every user-tunable switch of AmpyDeck. The keyboard runs in its own process (":ime"), so
 * settings live in a small JSON file both processes re-read when it changes (a revision
 * counter tells the keyboard to re-theme itself).
 */
public final class Prefs {
    public static final String FILE="ampydeck_prefs.json";

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

    private static final Object LOCK=new Object();
    private static HashMap<String,Object> cache;private static long cacheMtime=-1,cacheLen=-1;
    private final File file;

    private Prefs(Context c){file=new File(c.getApplicationContext().getFilesDir(),FILE);load(c.getApplicationContext());}
    public static Prefs get(Context c){return new Prefs(c);}

    private void load(Context c){
        synchronized(LOCK){
            if(!file.exists()){
                if(cache==null){cache=new HashMap<>();
                    // one-time migration from the first release (SharedPreferences)
                    try{SharedPreferences old=c.getSharedPreferences("ampydeck_settings",Context.MODE_PRIVATE);
                        for(Map.Entry<String,?> e:old.getAll().entrySet())if(e.getValue()!=null)cache.put(e.getKey(),e.getValue());
                        if(!cache.isEmpty())write();}catch(Throwable ignored){}}
                return;
            }
            long m=file.lastModified(),l=file.length();
            if(cache!=null&&m==cacheMtime&&l==cacheLen)return;
            HashMap<String,Object> map=new HashMap<>();
            try(InputStream in=new FileInputStream(file)){
                ByteArrayOutputStream bo=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)bo.write(b,0,n);
                JSONObject o=new JSONObject(bo.toString("UTF-8"));
                for(Iterator<String> it=o.keys();it.hasNext();){String k=it.next();map.put(k,o.get(k));}
                cache=map;cacheMtime=m;cacheLen=l;
            }catch(Throwable t){if(cache==null)cache=new HashMap<>();}
        }
    }
    private void write(){
        try{
            JSONObject o=new JSONObject();for(Map.Entry<String,Object> e:cache.entrySet())o.put(e.getKey(),e.getValue());
            File tmp=new File(file.getPath()+".tmp");
            try(OutputStream os=new FileOutputStream(tmp)){os.write(o.toString().getBytes(StandardCharsets.UTF_8));}
            if(!tmp.renameTo(file)){try(OutputStream os=new FileOutputStream(file)){os.write(o.toString().getBytes(StandardCharsets.UTF_8));}}
            cacheMtime=file.lastModified();cacheLen=file.length();
        }catch(Throwable ignored){}
    }

    private Object v(String k){synchronized(LOCK){return cache==null?null:cache.get(k);}}
    public String s(String k,String d){Object o=v(k);return o==null||o==JSONObject.NULL?d:String.valueOf(o);}
    public int i(String k,int d){Object o=v(k);return o instanceof Number?((Number)o).intValue():d;}
    public boolean b(String k,boolean d){Object o=v(k);return o instanceof Boolean?(Boolean)o:d;}
    public float f(String k,float d){Object o=v(k);return o instanceof Number?((Number)o).floatValue():d;}
    public void put(String k,Object val){
        synchronized(LOCK){
            if(cache==null)cache=new HashMap<>();
            if(val==null)cache.remove(k);else cache.put(k,val instanceof Float?(double)(Float)val:val);
            if(!"emoji_recent".equals(k))cache.put("_rev",i("_rev",0)+1);
            write();
        }
    }
    /** Changes whenever a setting (other than emoji recents) changes, from any process. */
    public int rev(){return i("_rev",0);}

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
