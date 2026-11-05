package it.ampy.ampydeck;

import android.content.Context;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Word store for predictions, autocorrect and flow typing.
 *
 * Built for smooth typing: the base lists ship pre-merged and pre-sorted (no sorting or big maps
 * at load), lookups are binary searches on plain arrays (no per-word HashMap entries / boxed
 * Integers, ~half the memory), and correction / suggestion search allocate nothing per candidate,
 * so the garbage collector has no reason to pause the keyboard while you type.
 * The user's own words and word pairs (bigrams) are learnt and stored privately in files/.
 */
public final class Dictionary {
    /** Below this (Zipf×10) a listed word is considered rare: still valid, but autocorrect may override it. */
    public static final int WEAK=28;
    private static Dictionary inst;private static String instLang;

    /** Sorted by normalized form, then by decreasing frequency. Never mutated after load. */
    public volatile String[] words=new String[0],norm=new String[0];public volatile int[] freq=new int[0];
    private volatile HashMap<Integer,int[]> buckets=new HashMap<>();
    /** User words missing from the base lists: small sorted side index. */
    private final TreeMap<String,String> extra=new TreeMap<>();
    private final ConcurrentHashMap<String,Integer> user=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Map<String,Integer>> bigrams=new ConcurrentHashMap<>();
    private final Set<String> blocked=ConcurrentHashMap.newKeySet();
    private final File userFile,bigramFile,blockFile;private volatile boolean dirty;
    public final String lang;
    public volatile boolean ready;
    public volatile long loadMs;

    public static synchronized Dictionary get(Context c){
        String l=Prefs.get(c).lang();
        if(inst==null||!l.equals(instLang)){
            if(inst!=null)inst.save();
            inst=new Dictionary(c.getApplicationContext(),l);instLang=l;
        }
        return inst;
    }
    public static synchronized void reset(){if(inst!=null)inst.save();inst=null;}
    /** Forgets the in-memory copy without saving (files were wiped by Settings). */
    public static synchronized void drop(){inst=null;}

    private Dictionary(Context c,String lang){
        this.lang=lang;
        userFile=new File(c.getFilesDir(),"user_words.txt");bigramFile=new File(c.getFilesDir(),"bigrams.txt");
        blockFile=new File(c.getFilesDir(),"blocked_words.txt");
        new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            long t=System.currentTimeMillis();try{load(c);}catch(Throwable e){CrashLog.log("dict-load",e);}
            loadMs=System.currentTimeMillis()-t;ready=true;},"ampydeck-dict").start();
    }

    // ------------------------------------------------------------------ loading
    private void load(Context c){
        try(BufferedReader r=reader(blockFile)){String ln;while(r!=null&&(ln=r.readLine())!=null)if(!ln.isEmpty())blocked.add(ln);}catch(Throwable ignored){}
        String asset="it".equals(lang)?"dict_it.txt":"en".equals(lang)?"dict_en.txt":"dict_iten.txt";
        ArrayList<String> W=new ArrayList<>(140000);int[] F=new int[140000];int n=0;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getAssets().open(asset),StandardCharsets.UTF_8),1<<16)){
            String ln;while((ln=r.readLine())!=null){int t=ln.indexOf('\t');if(t<=0)continue;
                String w=ln.substring(0,t);if(!blocked.isEmpty()&&blocked.contains(w))continue;
                if(n==F.length)F=Arrays.copyOf(F,n*2);
                W.add(w);F[n++]=parse(ln,t+1);}
        }catch(Throwable e){CrashLog.log("dict-asset",e);}
        String[] words=W.toArray(new String[0]);String[] norm=new String[n];int[] freq=Arrays.copyOf(F,n);
        for(int i=0;i<n;i++)norm[i]=normalize(words[i]);   // same String instance when unchanged (most words)
        // safety net: the asset is pre-sorted, re-sort only if this device normalizes differently
        boolean sorted=true;for(int i=1;i<n;i++)if(norm[i-1].compareTo(norm[i])>0){sorted=false;break;}
        if(!sorted){Integer[] ord=new Integer[n];for(int i=0;i<n;i++)ord[i]=i;final String[] N0=norm;final int[] F0=freq;
            Arrays.sort(ord,(a,b)->{int x=N0[a].compareTo(N0[b]);return x!=0?x:Integer.compare(F0[b],F0[a]);});
            String[] w2=new String[n],n2=new String[n];int[] f2=new int[n];
            for(int i=0;i<n;i++){w2[i]=words[ord[i]];n2[i]=norm[ord[i]];f2[i]=freq[ord[i]];}words=w2;norm=n2;freq=f2;}
        // buckets: (first letter, length) -> indices, built without boxing
        HashMap<Integer,int[]> cnt=new HashMap<>();
        for(int i=0;i<n;i++){String s=norm[i];if(s.isEmpty())continue;int k=bucketKey(s.charAt(0),s.length());int[] v=cnt.get(k);if(v==null)cnt.put(k,v=new int[1]);v[0]++;}
        HashMap<Integer,int[]> bk=new HashMap<>(cnt.size()*2);HashMap<Integer,int[]> fill=new HashMap<>(cnt.size()*2);
        for(Map.Entry<Integer,int[]> e:cnt.entrySet()){bk.put(e.getKey(),new int[e.getValue()[0]]);fill.put(e.getKey(),new int[1]);}
        for(int i=0;i<n;i++){String s=norm[i];if(s.isEmpty())continue;int k=bucketKey(s.charAt(0),s.length());int[] pos=fill.get(k);bk.get(k)[pos[0]++]=i;}
        this.norm=norm;this.freq=freq;this.buckets=bk;this.words=words;   // words last: readers check words.length
        // user data
        try(BufferedReader r=reader(userFile)){String ln;while(r!=null&&(ln=r.readLine())!=null){
            int t=ln.indexOf('\t');if(t>0)user.put(ln.substring(0,t),parse(ln,t+1));}}catch(Throwable ignored){}
        try(BufferedReader r=reader(bigramFile)){String ln;while(r!=null&&(ln=r.readLine())!=null){
            String[] p=ln.split("\t");if(p.length==3)bigrams.computeIfAbsent(p[0],k->new ConcurrentHashMap<>()).put(p[1],parse(p[2],0));}}catch(Throwable ignored){}
        synchronized(extra){for(String w:user.keySet())if(exactIdx(w)<0&&!blocked.contains(w))extra.put(normalize(w),w);}
    }
    private static BufferedReader reader(File f)throws IOException{
        if(!f.exists())return null;return new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8));
    }
    private static int parse(String s,int from){int v=0;boolean neg=false;for(int i=from;i<s.length();i++){char c=s.charAt(i);if(c=='-')neg=true;else if(c>='0'&&c<='9')v=v*10+(c-'0');else break;}return neg?-v:v;}
    private static int bucketKey(char c,int len){return (c<<8)|Math.min(len,255);}

    // ------------------------------------------------------------------ text helpers
    private static char fold(char ch){
        switch(ch){
            case 'à':case 'á':case 'â':case 'ä':case 'ã':case 'å':return 'a';
            case 'è':case 'é':case 'ê':case 'ë':return 'e';
            case 'ì':case 'í':case 'î':case 'ï':return 'i';
            case 'ò':case 'ó':case 'ô':case 'ö':case 'õ':return 'o';
            case 'ù':case 'ú':case 'û':case 'ü':return 'u';
            case 'ç':return 'c';case 'ñ':return 'n';case '’':return '\'';
            default:return ch;
        }
    }
    /** Lower-case + accent folding. Returns the same String when nothing changes (no garbage). */
    public static String normalize(String s){
        int i=0,L=s.length();
        for(;i<L;i++){char c=s.charAt(i);if(fold(Character.toLowerCase(c))!=c)break;}
        if(i==L)return s;
        char[] b=s.toCharArray();for(;i<L;i++)b[i]=fold(Character.toLowerCase(b[i]));
        return new String(b);
    }
    /** Applies the capitalisation of what the user typed to a dictionary word. */
    public static String matchCase(String typed,String w){
        if(typed==null||typed.isEmpty()||w.isEmpty())return w;
        boolean allUp=typed.length()>1;for(int i=0;i<typed.length();i++)if(Character.isLowerCase(typed.charAt(i))){allUp=false;break;}
        if(allUp)return w.toUpperCase(Locale.ROOT);
        if(Character.isUpperCase(typed.charAt(0)))return Character.toUpperCase(w.charAt(0))+w.substring(1);
        return w;
    }

    private int lower(String[] N,String n){int lo=0,hi=N.length;while(lo<hi){int mid=(lo+hi)>>>1;if(N[mid].compareTo(n)<0)lo=mid+1;else hi=mid;}return lo;}
    /** Index of exactly this word in the base list, or -1. */
    private int exactIdx(String w){
        String[] N=norm,W=words;if(W.length!=N.length)return -1;
        String n=normalize(w);for(int k=lower(N,n);k<N.length&&N[k].equals(n);k++)if(W[k].equals(w))return k;return -1;
    }
    /** Most frequent base word with this normalized form, or -1. */
    private int bestOfNorm(String n){String[] N=norm;int k=lower(N,n);return k<N.length&&N[k].equals(n)?k:-1;}

    public boolean known(String w){
        if(w==null||w.isEmpty())return false;
        if(user.containsKey(w))return true;
        if(exactIdx(w)>=0)return true;
        String l=w.toLowerCase(Locale.ROOT);
        return exactIdx(l)>=0||exactIdx(Character.toUpperCase(l.charAt(0))+l.substring(1))>=0;
    }
    public boolean hasPrefix(String p){String[] N=norm;String n=normalize(p);int i=lower(N,n);return i<N.length&&N[i].startsWith(n);}
    /** Lock-free: best frequency among words starting with this prefix (0 if none). Used for tap precision. */
    public float prefixWeight(String prefix){
        String[] N=norm;int[] F=freq;if(!ready||N.length!=F.length||prefix.isEmpty())return 0;
        String p=normalize(prefix);int lo=lower(N,p);
        int best=0;for(int k=lo;k<N.length&&k<lo+400&&N[k].startsWith(p);k++)if(F[k]>best)best=F[k];
        return best;
    }

    private int score(int i,String prev,Map<String,Integer> bg){
        String w=words[i];int s=freq[i]*10;
        if(!user.isEmpty()){Integer u=user.get(w);if(u!=null)s+=(int)(60*Math.log(1+u));}
        if(bg!=null){Integer c=bg.get(w);if(c!=null)s+=(int)(160*Math.log(1+c))+80;}
        return s;
    }
    private Map<String,Integer> pairsAfter(String prev){return prev==null?null:bigrams.get(prev.toLowerCase(Locale.ROOT));}

    /** Ranked completions for the word being typed (prefix may be empty = next word prediction). */
    public List<String> suggest(String prefix,String prev,int n){
        ArrayList<String> out=new ArrayList<>();if(!ready)return out;
        if(prefix==null||prefix.isEmpty())return predictNext(prev,n);
        String[] N=norm,W=words;
        String p=normalize(prefix);int i=lower(N,p);Map<String,Integer> bg=pairsAfter(prev);
        // primitive top-K (no per-candidate objects)
        int K=n*3;int[] ti=new int[K];int[] ts=new int[K];int tn=0;
        for(int k=i;k<N.length&&N[k].startsWith(p);k++){
            if(!blocked.isEmpty()&&blocked.contains(W[k]))continue;
            int s=score(k,prev,bg);if(N[k].length()==p.length())s+=150;
            if(N[k].length()>p.length()+9)s-=60;
            if(tn<K){int j=tn++;while(j>0&&ts[j-1]<s){ti[j]=ti[j-1];ts[j]=ts[j-1];j--;}ti[j]=k;ts[j]=s;}
            else if(s>ts[K-1]){int j=K-1;while(j>0&&ts[j-1]<s){ti[j]=ti[j-1];ts[j]=ts[j-1];j--;}ti[j]=k;ts[j]=s;}
        }
        // learnt words that are not in the base lists
        ArrayList<String> exl=new ArrayList<>();ArrayList<Integer> exs=new ArrayList<>();
        synchronized(extra){for(Map.Entry<String,String> e:extra.subMap(p,true,p+'￿',true).entrySet()){
            Integer u=user.get(e.getValue());int sc=340+(int)(60*Math.log(1+(u==null?1:u)));
            if(e.getKey().length()==p.length())sc+=150;if(bg!=null&&bg.containsKey(e.getValue()))sc+=240;
            exl.add(e.getValue());exs.add(sc);}}
        HashSet<String> seen=new HashSet<>();
        int a=0,b=0;
        while(out.size()<n&&(a<tn||b<exl.size())){
            String base;
            if(b<exl.size()&&(a>=tn||exs.get(b)>ts[a])){base=exl.get(b++);}else base=W[ti[a++]];
            if(!seen.add(normalize(base)))continue;String w=matchCase(prefix,base);if(!out.contains(w))out.add(w);
        }
        return out;
    }

    private static final String[] START_IT={"Ciao","Sì","Grazie","Non","Ok","Io"},START_EN={"I","The","Thanks","Hi","Ok","Yes"};
    /** Snapshot of learnt words outside the base lists (for flow typing). */
    public List<String> extraWords(){synchronized(extra){return extra.isEmpty()?Collections.emptyList():new ArrayList<>(extra.values());}}
    public boolean isBlocked(String w){return !blocked.isEmpty()&&blocked.contains(w);}

    public List<String> predictNext(String prev,int n){
        ArrayList<String> out=new ArrayList<>();
        if(prev!=null&&!prev.isEmpty()){
            Map<String,Integer> bg=pairsAfter(prev);
            if(bg!=null){ArrayList<Map.Entry<String,Integer>> e=new ArrayList<>(bg.entrySet());
                e.sort((x,y)->Integer.compare(y.getValue(),x.getValue()));
                for(Map.Entry<String,Integer> x:e){if(!blocked.contains(x.getKey()))out.add(x.getKey());if(out.size()>=n)return out;}}
            String[] common="it".equals(lang)||"iten".equals(lang)?new String[]{"di","e","che","il","non","a","per","la"}:new String[]{"the","to","and","a","of","is","in","you"};
            for(String w:common){if(!out.contains(w))out.add(w);if(out.size()>=n)break;}
        }else{
            for(String w:"en".equals(lang)?START_EN:START_IT){out.add(w);if(out.size()>=n)break;}
        }
        return out;
    }

    // ------------------------------------------------------------------ autocorrect
    private static final String[] ROWS={"qwertyuiop","asdfghjkl","zxcvbnm"};private static final float[] OFF={0,.5f,1.5f};
    private static final float[][] POS=new float[128][];
    static{for(int r=0;r<3;r++)for(int i=0;i<ROWS[r].length();i++)POS[ROWS[r].charAt(i)]=new float[]{i+OFF[r],r};}
    static float subCost(char a,char b){
        if(a==b)return 0;
        if(a<128&&b<128&&POS[a]!=null&&POS[b]!=null){float dx=POS[a][0]-POS[b][0],dy=POS[a][1]-POS[b][1];
            if(dx*dx+dy*dy<=1.6f)return .55f;}
        return 1f;
    }
    /** Three reusable DP rows per thread: the distance below allocates nothing. */
    private static final ThreadLocal<float[][]> ROWBUF=ThreadLocal.withInitial(()->new float[3][64]);
    /** Proximity-weighted Damerau–Levenshtein on normalized strings, bailing out above max. */
    static float distance(String a,String b,float max){
        int n=a.length(),m=b.length();if(Math.abs(n-m)>max)return 99;if(m>62||n>62)return 99;
        float[][] R=ROWBUF.get();float[] p2=R[0],p1=R[1],cur=R[2];
        for(int j=0;j<=m;j++)p1[j]=j;
        for(int i=1;i<=n;i++){
            cur[0]=i;float rowMin=cur[0];char ca=a.charAt(i-1);
            for(int j=1;j<=m;j++){
                char cb=b.charAt(j-1);
                float v=Math.min(Math.min(p1[j]+1,cur[j-1]+1),p1[j-1]+subCost(ca,cb));
                if(i>1&&j>1&&ca==b.charAt(j-2)&&a.charAt(i-2)==cb)v=Math.min(v,p2[j-2]+.6f);
                if(ca==cb&&i>1&&j>1&&ca==a.charAt(i-2)&&ca!=b.charAt(j-2))v=Math.min(v,p1[j]+.4f); // doubled letter typo
                cur[j]=v;if(v<rowMin)rowMin=v;}
            if(rowMin>max)return 99;
            float[] t=p2;p2=p1;p1=cur;cur=t;
        }
        return p1[m];
    }

    /** Best replacement for an unknown typed word, or null if nothing convincing. */
    public String correction(String typed,String prev){
        if(!ready||typed==null||typed.length()<2)return null;
        for(int i=0;i<typed.length();i++){char ch=typed.charAt(i);if(!Character.isLetter(ch)&&ch!='\'')return null;}
        String[] W=words,N=norm;int[] F=freq;HashMap<Integer,int[]> BK=buckets;
        String n=normalize(typed);
        String lowerT=typed.toLowerCase(Locale.ROOT);
        int exact=bestOfNorm(n);                       // accents only: perche -> perché
        if(exact>=0&&!W[exact].equalsIgnoreCase(typed)){
            int own=exactIdx(lowerT);
            if(own<0&&!user.containsKey(typed)||own>=0&&F[exact]>=F[own]+8&&!user.containsKey(typed))return matchCase(typed,W[exact]);
        }
        // weak words (rare forms, common typos that slipped into the frequency lists) can still be corrected
        int own=exactIdx(typed);if(own<0)own=exactIdx(lowerT);
        boolean isUser=user.containsKey(typed)||user.containsKey(lowerT);
        if(isUser||own>=0&&F[own]>=WEAK)return null;
        int ownF=own<0?-1:F[own];
        float max=own>=0?1.05f:n.length()<=3?1f:n.length()<=6?1.6f:2.2f;
        int best=-1;float bestScore=Float.MAX_VALUE;Map<String,Integer> bg=pairsAfter(prev);
        char c0=n.charAt(0);
        for(int ci=0;ci<128;ci++){
            char f=(char)ci;if(f!=c0&&subCost(f,c0)>.6f)continue;
            for(int len=Math.max(1,n.length()-2);len<=n.length()+2;len++){
                int[] b=BK.get(bucketKey(f,len));if(b==null)continue;
                for(int i:b){
                    if(i==own)continue;
                    float d=distance(n,N[i],max);if(d>max)continue;
                    float sc=d*320-score(i,prev,bg);if(f!=c0)sc+=120;
                    if(sc<bestScore){bestScore=sc;best=i;}
                }
            }
        }
        if(best>=0&&isBlocked(W[best]))best=-1;
        if(best>=0&&own>=0&&F[best]<ownF+15)return null;
        String extraBest=null;
        synchronized(extra){for(Map.Entry<String,String> e:extra.entrySet()){
            String w=e.getKey();if(Math.abs(w.length()-n.length())>2||w.equals(n))continue;
            float d=distance(n,w,max);if(d>max)continue;Integer u=user.get(e.getValue());
            float sc=d*320-(340+(float)(60*Math.log(1+(u==null?1:u))));
            if(sc<bestScore){bestScore=sc;extraBest=e.getValue();}
        }}
        if(extraBest!=null&&own<0)return matchCase(typed,extraBest);
        if(best<0)return null;
        if(F[best]<30&&!user.containsKey(W[best]))return null;
        return matchCase(typed,W[best]);
    }

    // ------------------------------------------------------------------ learning
    public synchronized void learn(String w,String prev){
        if(w==null||w.length()<2)return;
        for(int i=0;i<w.length();i++){char ch=w.charAt(i);if(!Character.isLetter(ch)&&ch!='\'')return;}
        String lw=w.toLowerCase(Locale.ROOT);
        String key=exactIdx(w)>=0?w:exactIdx(lw)>=0?lw:w;
        if(blocked.remove(key))dirty=true;
        user.merge(key,1,Integer::sum);
        if(prev!=null&&!prev.isEmpty()){
            Map<String,Integer> bg=bigrams.computeIfAbsent(prev.toLowerCase(Locale.ROOT),k->new ConcurrentHashMap<>());
            bg.merge(key,1,Integer::sum);if(bg.size()>40){String low=null;int lv=Integer.MAX_VALUE;
                for(Map.Entry<String,Integer> e:bg.entrySet())if(e.getValue()<lv){lv=e.getValue();low=e.getKey();}bg.remove(low);}
        }
        if(exactIdx(key)<0)synchronized(extra){extra.put(normalize(key),key);}
        dirty=true;
    }
    public synchronized void forget(String w){
        if(w==null)return;user.remove(w);for(Map<String,Integer> b:bigrams.values())b.remove(w);
        blocked.add(w);synchronized(extra){extra.remove(normalize(w));}dirty=true;
    }
    public boolean isUserWord(String w){return user.containsKey(w);}
    public int learntCount(){return user.size();}
    public int size(){return words.length;}
    /** Wipes every learnt word / pair / blocked word; the next get() reloads clean lists. */
    public static synchronized void clearLearnt(Context c){
        if(inst!=null){inst.user.clear();inst.bigrams.clear();inst.blocked.clear();inst.dirty=false;}
        new File(c.getFilesDir(),"user_words.txt").delete();new File(c.getFilesDir(),"bigrams.txt").delete();
        new File(c.getFilesDir(),"blocked_words.txt").delete();inst=null;
    }

    public synchronized void save(){
        if(!dirty)return;dirty=false;
        try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(userFile),StandardCharsets.UTF_8))){
            for(Map.Entry<String,Integer> e:user.entrySet())w.write(e.getKey()+"\t"+e.getValue()+"\n");}catch(Throwable ignored){}
        try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(bigramFile),StandardCharsets.UTF_8))){
            for(Map.Entry<String,Map<String,Integer>> e:bigrams.entrySet())for(Map.Entry<String,Integer> x:e.getValue().entrySet())
                w.write(e.getKey()+"\t"+x.getKey()+"\t"+x.getValue()+"\n");}catch(Throwable ignored){}
        try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(blockFile),StandardCharsets.UTF_8))){
            for(String b:blocked)w.write(b+"\n");}catch(Throwable ignored){}
    }
}
