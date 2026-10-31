package it.ampy.ampydeck;

import android.graphics.PointF;

import java.util.*;

/**
 * Flow / swipe typing: the finger path is matched against the ideal path of every
 * dictionary word whose first and last letters sit under the start and end of the stroke
 * (SHARK2-style location channel + word frequency).
 */
public final class FlowDecoder {
    private static final int N=40;

    public static List<String> decode(Dictionary d,List<PointF> path,Map<Character,PointF> keys,float keyW,String prev,int max){
        ArrayList<String> out=new ArrayList<>();
        if(path.size()<2||!d.ready)return out;
        float[][] P=resample(path,N);
        float pathLen=length(path);
        float thr=keyW*1.05f;
        // letters near the start / end, and per-letter nearness along the path
        HashSet<Character> S=new HashSet<>(),E=new HashSet<>();
        for(Map.Entry<Character,PointF> e:keys.entrySet()){
            PointF c=e.getValue();
            if(dist(c.x,c.y,P[0][0],P[0][1])<thr*1.15f)S.add(e.getKey());
            if(dist(c.x,c.y,P[N-1][0],P[N-1][1])<thr*1.15f)E.add(e.getKey());
        }
        if(S.isEmpty()||E.isEmpty())return out;
        // dense samples for the in-order check
        float[][] D=resample(path,Math.max(N,(int)(pathLen/(keyW*.25f))));
        PriorityQueue<Object[]> pq=new PriorityQueue<>((a,b)->Float.compare((Float)b[1],(Float)a[1]));
        String[] W;String[] NW;int[] F;
        synchronized(d){W=d.words;NW=d.norm;F=d.freq;}
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<NW.length;i++){
            String w=NW[i];if(w.length()<2)continue;
            sb.setLength(0);char last=0;boolean ok=true;
            for(int k=0;k<w.length();k++){char ch=w.charAt(k);if(ch=='\'')continue;if(!keys.containsKey(ch)){ok=false;break;}
                if(ch!=last)sb.append(ch);last=ch;}
            if(!ok||sb.length()<1)continue;
            String cw=sb.toString();
            if(!S.contains(cw.charAt(0))||!E.contains(cw.charAt(cw.length()-1)))continue;
            if(cw.length()==1&&pathLen>keyW*1.2f)continue;
            // every letter must be passed, in order
            int at=0;boolean inOrder=true;
            for(int k=0;k<cw.length();k++){
                PointF c=keys.get(cw.charAt(k));boolean found=false;
                for(int j=at;j<D.length;j++)if(dist(c.x,c.y,D[j][0],D[j][1])<thr){at=j;found=true;break;}
                if(!found){inOrder=false;break;}
            }
            if(!inOrder)continue;
            // location distance against the ideal polyline
            ArrayList<PointF> ideal=new ArrayList<>(cw.length());
            for(int k=0;k<cw.length();k++)ideal.add(keys.get(cw.charAt(k)));
            float[][] I=resample(ideal,N);
            float loc=0;for(int k=0;k<N;k++)loc+=dist(P[k][0],P[k][1],I[k][0],I[k][1]);loc/=N*keyW;
            float il=length(ideal);float ratio=il<1?(pathLen<keyW?1:3):pathLen/il;
            float lenPen=Math.abs((float)Math.log(Math.max(.2f,ratio)))*1.4f;
            float score=loc*3.2f+lenPen-F[i]*.06f-(d.isUserWord(W[i])?.5f:0);
            pq.add(new Object[]{W[i],score});if(pq.size()>max*3)pq.poll();
        }
        ArrayList<Object[]> l=new ArrayList<>(pq);l.sort((a,b)->Float.compare((Float)a[1],(Float)b[1]));
        HashSet<String> seen=new HashSet<>();
        for(Object[] o:l){String w=(String)o[0];if(!seen.add(Dictionary.normalize(w)))continue;out.add(w);if(out.size()>=max)break;}
        return out;
    }

    static float dist(float ax,float ay,float bx,float by){float dx=ax-bx,dy=ay-by;return (float)Math.sqrt(dx*dx+dy*dy);}
    static float length(List<PointF> p){float s=0;for(int i=1;i<p.size();i++)s+=dist(p.get(i-1).x,p.get(i-1).y,p.get(i).x,p.get(i).y);return s;}

    /** Resamples a polyline to n evenly spaced points (a single point repeats). */
    static float[][] resample(List<PointF> p,int n){
        float[][] r=new float[n][2];float L=length(p);
        if(p.size()==1||L<1e-3f){for(int i=0;i<n;i++){r[i][0]=p.get(0).x;r[i][1]=p.get(0).y;}return r;}
        float step=L/(n-1);int seg=1;float segStart=0;
        for(int i=0;i<n;i++){
            float target=i*step;
            while(seg<p.size()-1&&segStart+dist(p.get(seg-1).x,p.get(seg-1).y,p.get(seg).x,p.get(seg).y)<target){
                segStart+=dist(p.get(seg-1).x,p.get(seg-1).y,p.get(seg).x,p.get(seg).y);seg++;}
            PointF a=p.get(seg-1),b=p.get(seg);float sl=dist(a.x,a.y,b.x,b.y);
            float t=sl<1e-4f?0:Math.max(0,Math.min(1,(target-segStart)/sl));
            r[i][0]=a.x+(b.x-a.x)*t;r[i][1]=a.y+(b.y-a.y)*t;
        }
        return r;
    }
}
