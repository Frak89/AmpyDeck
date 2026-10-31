package it.ampy.ampydeck;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps the keyboard alive: errors inside key handling / drawing are caught and written to
 * files/last_crash.txt (shown in Settings → Diagnostica) instead of killing the input method.
 */
public final class CrashLog {
    private static File file;private static boolean installed;
    private CrashLog(){}

    public static synchronized void install(Context c){
        file=new File(c.getApplicationContext().getFilesDir(),"last_crash.txt");
        if(installed)return;installed=true;
        final Thread.UncaughtExceptionHandler prev=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{write("FATAL in "+t.getName(),e);if(prev!=null)prev.uncaughtException(t,e);});
    }
    /** Logs a caught error (the keyboard keeps running). */
    public static void log(String where,Throwable e){Log.e("AmpyDeckCrash",where,e);write(where,e);}
    private static synchronized void write(String where,Throwable e){
        if(file==null)return;
        try(Writer w=new OutputStreamWriter(new FileOutputStream(file,false),StandardCharsets.UTF_8)){
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date())+"  "+where+"\n");
            StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));w.write(sw.toString());
        }catch(Throwable ignored){}
    }
    public static String read(Context c){
        File f=new File(c.getApplicationContext().getFilesDir(),"last_crash.txt");if(!f.exists())return null;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            StringBuilder b=new StringBuilder();String l;int n=0;while((l=r.readLine())!=null&&n++<60)b.append(l).append('\n');return b.toString();
        }catch(Throwable t){return null;}
    }
    public static void clear(Context c){new File(c.getApplicationContext().getFilesDir(),"last_crash.txt").delete();}

    /** Wraps a task so an exception is logged instead of crashing the process. */
    public static Runnable safe(String where,Runnable r){return ()->{try{r.run();}catch(Throwable e){log(where,e);}};}
}
