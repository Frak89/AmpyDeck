package it.ampy.ampydeck;

/** Tiny always-on timing counters (logged as AmpyDeckPerf when the keyboard hides). */
public final class Perf {
    private Perf(){}
    private static final String[] NAMES={"bg","keys","touch","entry","suggest"};
    private static final long[] sum=new long[5],max=new long[5];private static final int[] n=new int[5];
    public static final int BG=0,KEYS=1,TOUCH=2,ENTRY=3,SUGGEST=4;
    public static synchronized void add(int k,long nanos){sum[k]+=nanos;n[k]++;if(nanos>max[k])max[k]=nanos;}
    public static synchronized void dump(){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<5;i++){if(n[i]==0)continue;b.append(NAMES[i]).append(" n=").append(n[i]).append(" avg=").append(String.format(java.util.Locale.ROOT,"%.2f",sum[i]/1e6/n[i]))
            .append("ms max=").append(String.format(java.util.Locale.ROOT,"%.1f",max[i]/1e6)).append("ms  ");sum[i]=0;n[i]=0;max[i]=0;}
        if(b.length()>0)android.util.Log.i("AmpyDeckPerf",b.toString());
    }
}
