package it.ampy.ampydeck;

import android.service.textservice.SpellCheckerService;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AmpyDeck spell checker: the red underline in apps comes from the system spell checker.
 * Selecting this one makes it use AmpyDeck's Italian/English dictionary (~136k forms) plus
 * every word the keyboard has learnt, with AmpyDeck's corrections as suggestions.
 */
public final class AmpySpellChecker extends SpellCheckerService {
    @Override public void onCreate(){super.onCreate();CrashLog.install(this);Dictionary.get(this);}
    @Override public Session createSession(){return new AmpySession();}

    private final class AmpySession extends Session {
        @Override public void onCreate(){}

        @Override public SuggestionsInfo onGetSuggestions(TextInfo ti,int limit){
            try{return check(ti.getText(),limit,false);}
            catch(Throwable t){CrashLog.log("spell",t);return ok();}
        }

        @Override public SentenceSuggestionsInfo[] onGetSentenceSuggestionsMultiple(TextInfo[] infos,int limit){
            SentenceSuggestionsInfo[] out=new SentenceSuggestionsInfo[infos.length];
            for(int n=0;n<infos.length;n++){
                String s=infos[n].getText();
                ArrayList<SuggestionsInfo> si=new ArrayList<>();List<Integer> off=new ArrayList<>(),len=new ArrayList<>();
                try{
                    int i=0;
                    while(i<s.length()){
                        if(!Character.isLetter(s.charAt(i))){i++;continue;}
                        int st=i;while(i<s.length()&&Character.isLetter(s.charAt(i)))i++;
                        String w=s.substring(st,i);
                        boolean elided=i<s.length()&&(s.charAt(i)=='\''||s.charAt(i)=='’');
                        SuggestionsInfo r=check(w,limit,elided);
                        r.setCookieAndSequence(infos[n].getCookie(),infos[n].getSequence());
                        si.add(r);off.add(st);len.add(w.length());
                    }
                }catch(Throwable t){CrashLog.log("spell-sentence",t);}
                int[] o=new int[off.size()],l=new int[len.size()];
                for(int k=0;k<o.length;k++){o[k]=off.get(k);l[k]=len.get(k);}
                out[n]=new SentenceSuggestionsInfo(si.toArray(new SuggestionsInfo[0]),o,l);
            }
            return out;
        }

        private SuggestionsInfo ok(){return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY,new String[0]);}

        /** Results cached per word: re-checks of the same text are instant and never compete with typing. */
        private final android.util.LruCache<String,String[]> cache=new android.util.LruCache<>(600);
        private SuggestionsInfo check(String w,int limit,boolean elided){
            String key=w+(elided?"'":"");String[] hit=cache.get(key);
            if(hit!=null)return hit.length==1&&hit[0]==null?ok():typo(hit);
            SuggestionsInfo r=checkRaw(w,limit,elided);
            if((r.getSuggestionsAttributes()&SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY)!=0){
                if(Dictionary.get(AmpySpellChecker.this).ready)cache.put(key,new String[]{null});}
            else{String[] sg=new String[r.getSuggestionsCount()];for(int i=0;i<sg.length;i++)sg[i]=r.getSuggestionAt(i);cache.put(key,sg);}
            return r;
        }
        private SuggestionsInfo typo(String[] sug){
            int attr=SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO|(sug.length==0?0:SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS);
            return new SuggestionsInfo(attr,sug.clone());
        }
        private SuggestionsInfo checkRaw(String w,int limit,boolean elided){
            Dictionary d=Dictionary.get(AmpySpellChecker.this);
            if(!d.ready||w==null||w.length()<2)return ok();
            for(int i=0;i<w.length();i++)if(!Character.isLetter(w.charAt(i))&&w.charAt(i)!='\'')return ok();
            if(d.known(w))return ok();
            if(elided&&(w.length()<=5||d.known(w+"'")))return ok();          // dell'anno, quest'anno, l'acqua
            if(Character.isUpperCase(w.charAt(0))&&w.length()>1&&!d.known(w.toLowerCase(Locale.ROOT))
                &&!w.equals(w.toUpperCase(Locale.ROOT)))return ok();            // proper names
            ArrayList<String> sug=new ArrayList<>();
            String c=d.correction(w,null);if(c!=null)sug.add(c);
            for(String x:d.suggest(w.length()>3?w.substring(0,w.length()-1):w,null,4))if(!sug.contains(x)&&!x.equalsIgnoreCase(w))sug.add(x);
            while(sug.size()>Math.max(1,limit))sug.remove(sug.size()-1);
            int attr=SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO|(sug.isEmpty()?0:SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS);
            return new SuggestionsInfo(attr,sug.toArray(new String[0]));
        }
    }
}
