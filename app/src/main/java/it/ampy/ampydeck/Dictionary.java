package it.ampy.ampydeck;

import android.content.Context;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Word store for predictions, autocorrect and flow typing.
 * Base lists (wordfreq Zipf ×10) ship in assets; the user's own words and word pairs
 * (bigrams) are learnt while typing and stored privately in the app's files dir.
 */
public final class Dictionary {
    private static Dictionary inst;private static String instLang;

    public String[] words=new String[0],norm=new String[0];public int[] freq=new int[0];
    private final HashMap<String,Integer> index=new HashMap<>();        // exact word -> idx
    private final HashMap<String,Integer> normBest=new HashMap<>();     // normalized -> best idx
    private final HashMap<String,Integer> user=new HashMap<>();         // learnt word -> count
    private final HashMap<String,HashMap<String,Integer>> bigrams=new HashMap<>();
    private final HashSet<String> blocked=new HashSet<>();
    private final File userFile,bigramFile,blockFile;private boolean dirty;
    public final String lang;
    public volatile boolean ready;

    public static synchronized Dictionary get(Context c){
        String l=Prefs.get(c).lang();
        if(inst==null||!l.equals(instLang)){
            if(inst!=null)inst.save();
            inst=new Dictionary(c.getApplicationContext(),l);instLang=l;
        }
        return inst;
    }
    public static synchronized void reset(){if(inst!=null)inst.save();inst=null;}

    private Dictionary(Context c,String lang){
        this.lang=lang;
        userFile=new File(c.getFilesDir(),"user_words.txt");bigramFile=new File(c.getFilesDir(),"bigrams.txt");
        blockFile=new File(c.getFilesDir(),"blocked_words.txt");
        new Thread(()->{load(c);ready=true;},"ampydeck-dict").start();
    }

    // ------------------------------------------------------------------ loading
    private void load(Context c){
        HashMap<String,Integer> m=new HashMap<>();
        if(lang.contains("it"))read(c,"dict_it.txt",m,0);
        if(lang.contains("en"))read(c,"dict_en.txt",m,"iten".equals(lang)?-2:0);
        if(lang.contains("en")){Integer v=m.remove("i");if(v!=null)m.put("I",v);
            for(String w:new ArrayList<>(m.keySet()))if(w.startsWith("i'")){Integer f=m.remove(w);m.put("I"+w.substring(1),f);}}
        // user words
        try(BufferedReader r=reader(userFile)){String ln;while(r!=null&&(ln=r.readLine())!=null){
            String[] p=ln.split("\t");if(p.length==2)user.put(p[0],parse(p[1]));}}catch(Throwable ignored){}
        try(BufferedReader r=reader(bigramFile)){String ln;while(r!=null&&(ln=r.readLine())!=null){
            String[] p=ln.split("\t");if(p.length==3)bigrams.computeIfAbsent(p[0],k->new HashMap<>()).put(p[1],parse(p[2]));}}catch(Throwable ignored){}
        try(BufferedReader r=reader(blockFile)){String ln;while(r!=null&&(ln=r.readLine())!=null)if(!ln.isEmpty())blocked.add(ln);}catch(Throwable ignored){}
        for(String w:user.keySet())if(!m.containsKey(w))m.put(w,34);
        for(String b:blocked)m.remove(b);
        build(m);
    }
    private static BufferedReader reader(File f)throws IOException{
        if(!f.exists())return null;return new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8));
    }
    private static int parse(String s){try{return Integer.parseInt(s.trim());}catch(Throwable t){return 1;}}
    private static void read(Context c,String asset,HashMap<String,Integer> m,int bias){
        try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getAssets().open(asset),StandardCharsets.UTF_8))){
            String ln;while((ln=r.readLine())!=null){int t=ln.indexOf('\t');if(t<=0)continue;
                String w=ln.substring(0,t);int f=parse(ln.substring(t+1))+bias;Integer o=m.get(w);if(o==null||o<f)m.put(w,f);}
        }catch(Throwable ignored){}
    }
    private synchronized void build(HashMap<String,Integer> m){
        Integer[] ord=new Integer[m.size()];String[] w=m.keySet().toArray(new String[0]);String[] n=new String[w.length];
        for(int i=0;i<w.length;i++){n[i]=normalize(w[i]);ord[i]=i;}
        Arrays.sort(ord,(a,b)->{int c=n[a].compareTo(n[b]);return c!=0?c:Integer.compare(m.get(w[b]),m.get(w[a]));});
        String[] W=new String[w.length],N=new String[w.length];int[] F=new int[w.length];
        index.clear();normBest.clear();
        for(int i=0;i<ord.length;i++){W[i]=w[ord[i]];N[i]=n[ord[i]];F[i]=m.get(W[i]);index.put(W[i],i);
            Integer b=normBest.get(N[i]);if(b==null||F[b]<F[i])normBest.put(N[i],i);}
        words=W;norm=N;freq=F;
    }

    // ------------------------------------------------------------------ text helpers
    public static String normalize(String s){
        StringBuilder b=new StringBuilder(s.length());
        for(int i=0;i<s.length();i++){char ch=Character.toLowerCase(s.charAt(i));
            switch(ch){
                case 'à':case 'á':case 'â':case 'ä':case 'ã':case 'å':ch='a';break;
                case 'è':case 'é':case 'ê':case 'ë':ch='e';break;
                case 'ì':case 'í':case 'î':case 'ï':ch='i';break;
                case 'ò':case 'ó':case 'ô':case 'ö':case 'õ':ch='o';break;
                case 'ù':case 'ú':case 'û':case 'ü':ch='u';break;
                case 'ç':ch='c';break;case 'ñ':ch='n';break;case '’':ch='\'';break;
            }
            b.append(ch);}
        return b.toString();
    }
    /** Applies the capitalisation of what the user typed to a dictionary word. */
    public static String matchCase(String typed,String w){
        if(typed==null||typed.isEmpty()||w.isEmpty())return w;
        boolean allUp=typed.length()>1;for(int i=0;i<typed.length();i++)if(Character.isLowerCase(typed.charAt(i))){allUp=false;break;}
        if(allUp)return w.toUpperCase(Locale.ROOT);
        if(Character.isUpperCase(typed.charAt(0)))return Character.toUpperCase(w.charAt(0))+w.substring(1);
        return w;
    }

    public synchronized boolean known(String w){
        if(w==null||w.isEmpty())return false;
        if(index.containsKey(w)||index.containsKey(w.toLowerCase(Locale.ROOT)))return true;
        if(user.containsKey(w))return true;
        String l=w.toLowerCase(Locale.ROOT);
        return index.containsKey(Character.toUpperCase(l.charAt(0))+l.substring(1));
    }
    public synchronized boolean hasPrefix(String p){
        String n=normalize(p);int i=lower(n);return i<norm.length&&norm[i].startsWith(n);
    }
    private int lower(String n){int lo=0,hi=norm.length;while(lo<hi){int mid=(lo+hi)>>>1;if(norm[mid].compareTo(n)<0)lo=mid+1;else hi=mid;}return lo;}

    private int score(int i,String prev){
        String w=words[i];int s=freq[i]*10;
        Integer u=user.get(w);if(u!=null)s+=(int)(60*Math.log(1+u));
        if(prev!=null){HashMap<String,Integer> bg=bigrams.get(prev.toLowerCase(Locale.ROOT));
            if(bg!=null){Integer c=bg.get(w);if(c!=null)s+=(int)(160*Math.log(1+c))+80;}}
        return s;
    }

    /** Ranked completions for the word being typed (prefix may be empty = next word prediction). */
    public synchronized List<String> suggest(String prefix,String prev,int n){
        ArrayList<String> out=new ArrayList<>();if(!ready)return out;
        if(prefix==null||prefix.isEmpty())return predictNext(prev,n);
        String p=normalize(prefix);int i=lower(p);
        PriorityQueue<int[]> pq=new PriorityQueue<>((a,b)->Integer.compare(a[1],b[1]));
        for(int k=i;k<norm.length&&norm[k].startsWith(p);k++){
            int s=score(k,prev);if(norm[k].length()==p.length())s+=150;
            if(norm[k].length()>p.length()+9)s-=60;
            pq.add(new int[]{k,s});if(pq.size()>n*3)pq.poll();
        }
        ArrayList<int[]> l=new ArrayList<>(pq);l.sort((a,b)->Integer.compare(b[1],a[1]));
        HashSet<String> seen=new HashSet<>();
        for(int[] e:l){if(!seen.add(norm[e[0]]))continue;String w=matchCase(prefix,words[e[0]]);if(!out.contains(w))out.add(w);if(out.size()>=n)break;}
        return out;
    }

    private static final String[] START_IT={"Ciao","Sì","Grazie","Non","Ok","Io"},START_EN={"I","The","Thanks","Hi","Ok","Yes"};
    public synchronized List<String> predictNext(String prev,int n){
        ArrayList<String> out=new ArrayList<>();
        if(prev!=null&&!prev.isEmpty()){
            HashMap<String,Integer> bg=bigrams.get(prev.toLowerCase(Locale.ROOT));
            if(bg!=null){ArrayList<Map.Entry<String,Integer>> e=new ArrayList<>(bg.entrySet());
                e.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));
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
    /** Proximity-weighted Damerau–Levenshtein on normalized strings, bailing out above max. */
    static float distance(String a,String b,float max){
        int n=a.length(),m=b.length();if(Math.abs(n-m)>max)return 99;
        float[][] d=new float[n+1][m+1];
        for(int i=0;i<=n;i++)d[i][0]=i;for(int j=0;j<=m;j++)d[0][j]=j;
        for(int i=1;i<=n;i++){float rowMin=99;
            for(int j=1;j<=m;j++){
                char ca=a.charAt(i-1),cb=b.charAt(j-1);
                float v=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+subCost(ca,cb));
                if(i>1&&j>1&&ca==b.charAt(j-2)&&a.charAt(i-2)==cb)v=Math.min(v,d[i-2][j-2]+.6f);
                if(ca==cb&&i>1&&j>1&&ca==a.charAt(i-2)&&ca!=b.charAt(j-2))v=Math.min(v,d[i-1][j]+.4f); // doubled letter typo
                d[i][j]=v;rowMin=Math.min(rowMin,v);}
            if(rowMin>max)return 99;}
        return d[n][m];
    }

    /** Best replacement for an unknown typed word, or null if nothing convincing. */
    public synchronized String correction(String typed,String prev){
        if(!ready||typed==null||typed.length()<2)return null;
        for(int i=0;i<typed.length();i++){char ch=typed.charAt(i);if(!Character.isLetter(ch)&&ch!='\'')return null;}
        String n=normalize(typed);
        Integer exact=normBest.get(n);                 // accents only: perche -> perché
        if(exact!=null&&!words[exact].equalsIgnoreCase(typed)){
            Integer own=index.get(typed.toLowerCase(Locale.ROOT));
            if(own==null&&!user.containsKey(typed)||own!=null&&freq[exact]>=freq[own]+8&&!user.containsKey(typed))return matchCase(typed,words[exact]);
        }
        if(known(typed))return null;
        float max=n.length()<=3?1f:n.length()<=6?1.6f:2.2f;
        int best=-1;float bestScore=Float.MAX_VALUE;
        for(int i=0;i<norm.length;i++){
            String w=norm[i];if(Math.abs(w.length()-n.length())>2)continue;
            char f=w.charAt(0);if(f!=n.charAt(0)&&subCost(f,n.charAt(0))>.6f)continue;
            float d=distance(n,w,max);if(d>max)continue;
            float s=d*320-score(i,prev);
            if(f!=n.charAt(0))s+=120;
            if(s<bestScore){bestScore=s;best=i;}
        }
        if(best<0)return null;
        if(freq[best]<30&&!user.containsKey(words[best]))return null;
        return matchCase(typed,words[best]);
    }

    // ------------------------------------------------------------------ learning
    public synchronized void learn(String w,String prev){
        if(w==null||w.length()<2)return;
        boolean letters=true;for(int i=0;i<w.length();i++){char ch=w.charAt(i);if(!Character.isLetter(ch)&&ch!='\''){letters=false;break;}}
        if(!letters)return;
        String key=index.containsKey(w)?w:index.containsKey(w.toLowerCase(Locale.ROOT))?w.toLowerCase(Locale.ROOT):w;
        if(blocked.remove(key))dirty=true;
        user.merge(key,1,Integer::sum);
        if(prev!=null&&!prev.isEmpty()){
            HashMap<String,Integer> bg=bigrams.computeIfAbsent(prev.toLowerCase(Locale.ROOT),k->new HashMap<>());
            bg.merge(key,1,Integer::sum);if(bg.size()>40){String low=null;int lv=Integer.MAX_VALUE;
                for(Map.Entry<String,Integer> e:bg.entrySet())if(e.getValue()<lv){lv=e.getValue();low=e.getKey();}bg.remove(low);}
        }
        if(!index.containsKey(key)&&ready){HashMap<String,Integer> m=snapshot();m.put(key,34);build(m);}
        dirty=true;
    }
    public synchronized void forget(String w){
        if(w==null)return;user.remove(w);for(HashMap<String,Integer> b:bigrams.values())b.remove(w);
        blocked.add(w);HashMap<String,Integer> m=snapshot();m.remove(w);build(m);dirty=true;
    }
    public synchronized boolean isUserWord(String w){return user.containsKey(w);}
    private HashMap<String,Integer> snapshot(){HashMap<String,Integer> m=new HashMap<>(words.length*2);for(int i=0;i<words.length;i++)m.put(words[i],freq[i]);return m;}
    public synchronized int learntCount(){return user.size();}
    /** Wipes every learnt word / pair / blocked word; the next get() reloads clean lists. */
    public static synchronized void clearLearnt(Context c){
        if(inst!=null){synchronized(inst){inst.user.clear();inst.bigrams.clear();inst.blocked.clear();inst.dirty=false;}}
        new File(c.getFilesDir(),"user_words.txt").delete();new File(c.getFilesDir(),"bigrams.txt").delete();
        new File(c.getFilesDir(),"blocked_words.txt").delete();inst=null;
    }

    public synchronized void save(){
        if(!dirty)return;dirty=false;
        try(Writer w=new OutputStreamWriter(new FileOutputStream(userFile),StandardCharsets.UTF_8)){
            for(Map.Entry<String,Integer> e:user.entrySet())w.write(e.getKey()+"\t"+e.getValue()+"\n");}catch(Throwable ignored){}
        try(Writer w=new OutputStreamWriter(new FileOutputStream(bigramFile),StandardCharsets.UTF_8)){
            for(Map.Entry<String,HashMap<String,Integer>> e:bigrams.entrySet())for(Map.Entry<String,Integer> x:e.getValue().entrySet())
                w.write(e.getKey()+"\t"+x.getKey()+"\t"+x.getValue()+"\n");}catch(Throwable ignored){}
        try(Writer w=new OutputStreamWriter(new FileOutputStream(blockFile),StandardCharsets.UTF_8)){
            for(String b:blocked)w.write(b+"\n");}catch(Throwable ignored){}
    }
}
