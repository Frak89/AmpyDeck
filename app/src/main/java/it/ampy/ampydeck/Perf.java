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
    public static synchronized void dump(){
        StringBuilder b=new StringBuilder();
        for(Map.Entry<String,long[]> e:m.entrySet()){long[] v=e.getValue();
            b.append(e.getKey()).append(" n=").append(v[1]).append(" avg=").append(String.format(java.util.Locale.ROOT,"%.1f",v[0]/1e6/v[1]))
             .append(" max=").append(String.format(java.util.Locale.ROOT,"%.0f",v[2]/1e6)).append(" | ");
            if(b.length()>700){android.util.Log.i("AmpyDeckPerf",b.toString());b.setLength(0);}}
        if(b.length()>0)android.util.Log.i("AmpyDeckPerf",b.toString());
        m.clear();
    }
}
