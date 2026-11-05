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
        float thr=keyW*1.05f,thr2=thr*thr;
        // key centres in flat arrays (ASCII letters after normalisation)
        float[] kx=new float[128],ky=new float[128];boolean[] has=new boolean[128],S=new boolean[128],E=new boolean[128];
        boolean anyS=false,anyE=false;
        for(Map.Entry<Character,PointF> e:keys.entrySet()){char ch=e.getKey();if(ch>=128)continue;PointF c=e.getValue();
            kx[ch]=c.x;ky[ch]=c.y;has[ch]=true;
            if(dist(c.x,c.y,P[0][0],P[0][1])<thr*1.15f){S[ch]=true;anyS=true;}
            if(dist(c.x,c.y,P[N-1][0],P[N-1][1])<thr*1.15f){E[ch]=true;anyE=true;}}
        if(!anyS||!anyE)return out;
        // dense samples for the in-order check, flattened
        float[][] D2=resample(path,Math.max(N,(int)(pathLen/(keyW*.25f))));
        int DN=D2.length;float[] dx=new float[DN],dy=new float[DN];for(int j=0;j<DN;j++){dx[j]=D2[j][0];dy[j]=D2[j][1];}
        String[] W=d.words,NW=d.norm;int[] F=d.freq;if(W.length!=NW.length)return out;
        List<String> ex=d.extraWords();
        int total=NW.length+ex.size();
        // reusable buffers: no allocation per candidate word
        char[] cw=new char[64];float[] ix=new float[64],iy=new float[64],I=new float[N*2];
        int K=max*3;int[] ti=new int[K];float[] ts=new float[K];int tn=0;
        for(int i=0;i<total;i++){
            String w=i<NW.length?NW[i]:Dictionary.normalize(ex.get(i-NW.length));
            int L=w.length();if(L<2||L>60)continue;
            char first=w.charAt(0);if(first>=128||!S[first])continue;          // cheap early rejects
            int cl=0;char last=0;boolean ok=true;
            for(int k=0;k<L;k++){char ch=w.charAt(k);if(ch=='\'')continue;if(ch>=128||!has[ch]){ok=false;break;}
                if(ch!=last)cw[cl++]=ch;last=ch;}
            if(!ok||cl<1||!E[cw[cl-1]])continue;
            if(cl==1&&pathLen>keyW*1.2f)continue;
            // every letter must be passed, in order
            int at=0;boolean inOrder=true;
            for(int k=0;k<cl;k++){
                float cx=kx[cw[k]],cy=ky[cw[k]];boolean found=false;
                for(int j=at;j<DN;j++){float ddx=cx-dx[j],ddy=cy-dy[j];if(ddx*ddx+ddy*ddy<thr2){at=j;found=true;break;}}
                if(!found){inOrder=false;break;}
            }
            if(!inOrder)continue;
            String word=i<NW.length?W[i]:ex.get(i-NW.length);
            if(d.isBlocked(word))continue;
            // location distance against the ideal polyline
            for(int k=0;k<cl;k++){ix[k]=kx[cw[k]];iy[k]=ky[cw[k]];}
            float il=resampleInto(ix,iy,cl,N,I);
            float loc=0;for(int k=0;k<N;k++)loc+=dist(P[k][0],P[k][1],I[k*2],I[k*2+1]);loc/=N*keyW;
            float ratio=il<1?(pathLen<keyW?1:3):pathLen/il;
            float lenPen=Math.abs((float)Math.log(Math.max(.2f,ratio)))*1.4f;
            int f=i<NW.length?F[i]:40;
            float score=loc*3.2f+lenPen-f*.06f-(d.isUserWord(word)?.5f:0);
            // keep the K lowest scores
            if(tn<K){int j=tn++;while(j>0&&ts[j-1]>score){ti[j]=ti[j-1];ts[j]=ts[j-1];j--;}ti[j]=i;ts[j]=score;}
            else if(score<ts[K-1]){int j=K-1;while(j>0&&ts[j-1]>score){ti[j]=ti[j-1];ts[j]=ts[j-1];j--;}ti[j]=i;ts[j]=score;}
        }
        HashSet<String> seen=new HashSet<>();
        for(int k=0;k<tn;k++){String w=ti[k]<NW.length?W[ti[k]]:ex.get(ti[k]-NW.length);
            if(!seen.add(Dictionary.normalize(w)))continue;out.add(w);if(out.size()>=max)break;}
        return out;
    }

    /** Resamples the polyline (xs,ys,count) to n points into out[2n]; returns its length. */
    static float resampleInto(float[] xs,float[] ys,int count,int n,float[] out){
        float L=0;for(int i=1;i<count;i++)L+=dist(xs[i-1],ys[i-1],xs[i],ys[i]);
        if(count==1||L<1e-3f){for(int i=0;i<n;i++){out[i*2]=xs[0];out[i*2+1]=ys[0];}return L;}
        float step=L/(n-1);int seg=1;float segStart=0;
        for(int i=0;i<n;i++){
            float target=i*step;
            while(seg<count-1&&segStart+dist(xs[seg-1],ys[seg-1],xs[seg],ys[seg])<target){segStart+=dist(xs[seg-1],ys[seg-1],xs[seg],ys[seg]);seg++;}
            float sl=dist(xs[seg-1],ys[seg-1],xs[seg],ys[seg]);
            float t=sl<1e-4f?0:Math.max(0,Math.min(1,(target-segStart)/sl));
            out[i*2]=xs[seg-1]+(xs[seg]-xs[seg-1])*t;out[i*2+1]=ys[seg-1]+(ys[seg]-ys[seg-1])*t;
        }
        return L;
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
