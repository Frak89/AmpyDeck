package it.ampy.ampydeck;

import java.util.Map;
import java.util.TreeMap;

/** Tiny always-on timing counters (logged as AmpyDeckPerf when the keyboard hides). */
public final class Perf {
    private Perf(){}
    public static final int BG=0,KEYS=1,TOUCH=2,ENTRY=3,SUGGEST=4;
    private static final String[] NAMES={"bg","keys","touch","entry","suggest"};
    private static final TreeMap<String,long[]> m=new TreeMap<>();
    public static void add(int k,long nanos){add(NAMES[k],nanos);}
    public static synchronized void add(String k,long nanos){long[] v=m.get(k);if(v==null){v=new long[3];m.put(k,v);}v[0]+=nanos;v[1]++;if(nanos>v[2])v[2]=nanos;}
    private static java.io.File file;
    public static void init(android.content.Context c){file=new java.io.File(c.getApplicationContext().getFilesDir(),"perf.txt");}
    public static String read(android.content.Context c){
        try{java.io.File f=new java.io.File(c.getApplicationContext().getFilesDir(),"perf.txt");if(!f.exists())return null;
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8);}catch(Throwable t){return null;}
    }
    public static synchronized void dump(){
        StringBuilder b=new StringBuilder();
        for(Map.Entry<String,long[]> e:m.entrySet()){long[] v=e.getValue();
            b.append(e.getKey()).append(" n=").append(v[1]).append(" avg=").append(String.format(java.util.Locale.ROOT,"%.1f",v[0]/1e6/v[1]))
             .append(" max=").append(String.format(java.util.Locale.ROOT,"%.0f",v[2]/1e6)).append(" | ");
            if(b.length()>700){android.util.Log.i("AmpyDeckPerf",b.toString());b.setLength(0);}}
        if(b.length()>0)android.util.Log.i("AmpyDeckPerf",b.toString());
        // human-readable summary for Settings → Diagnostica (last session)
        if(file!=null&&!m.isEmpty()){
            StringBuilder s=new StringBuilder(new java.text.SimpleDateFormat("dd/MM HH:mm",java.util.Locale.ROOT).format(new java.util.Date())).append('\n');
            String[][] show={{"touch","Tocco tasto"},{"L.onText","Lettera/simbolo"},{"L.onCode","Spazio/cancella/invio"},{"L.onPick","Suggerimento"},
                {"L.onFlow","Flow"},{"keys","Disegno tasti"},{"bg","Disegno sfondo"},{"suggest","Calcolo suggerimenti (in background)"},{"ipc.caps","Attese app (maiuscole)"},{"ipc.before","Attese app (testo)"}};
            for(String[] k:show){long[] v=m.get(k[0]);if(v==null)continue;
                s.append(String.format(java.util.Locale.ROOT,"%s: media %.1f ms · max %.0f ms (%d)\n",k[1],v[0]/1e6/v[1],v[2]/1e6,v[1]));}
            Runtime rt=Runtime.getRuntime();
            s.append(String.format(java.util.Locale.ROOT,"Memoria: %d MB usati di %d MB",(rt.totalMemory()-rt.freeMemory())>>20,rt.maxMemory()>>20));
            try{java.nio.file.Files.write(file.toPath(),s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Throwable ignored){}
        }
        m.clear();
    }
}
