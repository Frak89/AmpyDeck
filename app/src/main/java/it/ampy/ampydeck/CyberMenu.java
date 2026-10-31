package it.ampy.ampydeck;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

/** Neon popup menus and dialogs (long-press menus, pickers, text input). */
public final class CyberMenu {
    public interface Pick{void on(int index);}
    public interface TextDone{void on(String text);}

    private final Context c;private final Theme th;private final String title;
    private final List<String> labels=new ArrayList<>();private final List<Runnable> actions=new ArrayList<>();
    private final List<Drawable> icons=new ArrayList<>();
    private final List<Integer> colors=new ArrayList<>();
    private final List<Boolean> sel=new ArrayList<>();

    private static final int R_BLINK=0x7f0a0002;

    /** Menu background: liquid glass (iOS-like) or neon chamfered panel. */
    public static android.graphics.drawable.Drawable panel(Context c,Theme th,int edge){
        Ui.NeonPanel bg=new Ui.NeonPanel(th,edge,.96f);bg.scan=true;return bg;
    }

    public CyberMenu(Context c,Theme th,String title){this.c=c;this.th=th;this.title=title;}
    public CyberMenu add(String label,Runnable r){return add(label,null,th.fg,r);}
    public CyberMenu add(String label,Drawable icon,int color,Runnable r){labels.add(label);icons.add(icon);colors.add(color);actions.add(r);sel.add(false);return this;}
    public CyberMenu addSelected(String label,Runnable r){add(label,null,th.accent,r);sel.set(sel.size()-1,true);return this;}
    public CyberMenu danger(String label,Runnable r){return add(label,null,th.bad,r);}

    /** Shows anchored above/below a view, as a PopupWindow. */
    public PopupWindow showAt(View anchor){
        LinearLayout box=build(null);
        PopupWindow pw=new PopupWindow(box,Ui.dp(260),ViewGroup.LayoutParams.WRAP_CONTENT,true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));pw.setElevation(Ui.dpf(12));
        pw.setAnimationStyle(android.R.style.Animation_Dialog);
        bindClicks(box,pw::dismiss);
        int[] loc=new int[2];anchor.getLocationOnScreen(loc);
        box.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(260),View.MeasureSpec.EXACTLY),View.MeasureSpec.UNSPECIFIED);
        int h=box.getMeasuredHeight();int sw=anchor.getResources().getDisplayMetrics().widthPixels;
        int x=Math.max(Ui.dp(8),Math.min(sw-Ui.dp(268),loc[0]+anchor.getWidth()/2-Ui.dp(130)));
        int y=loc[1]-h-Ui.dp(6);if(y<Ui.dp(40))y=loc[1]+anchor.getHeight()+Ui.dp(6);
        pw.showAtLocation(anchor,Gravity.TOP|Gravity.START,x,y);
        return pw;
    }

    /** Shows centered as a dialog. */
    public Dialog show(){
        Dialog d=new Dialog(c);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=build(null);ScrollView sv=new ScrollView(c);sv.addView(box);
        d.setContentView(sv);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Ui.dp(300),ViewGroup.LayoutParams.WRAP_CONTENT);}
        bindClicks(box,d::dismiss);d.show();return d;
    }

    private LinearLayout build(View extra){
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(panel(c,th,th.accent));
        box.setPadding(Ui.dp(6),Ui.dp(10),Ui.dp(6),Ui.dp(10));
        if(title!=null){
            GlitchTextView t=new GlitchTextView(c,th,12,th.secondary,true);t.alignLeft();t.setText("// "+title.toUpperCase());
            t.setPadding(Ui.dp(12),Ui.dp(2),Ui.dp(12),Ui.dp(8));box.addView(t,new LinearLayout.LayoutParams(-1,-2));
        }
        if(extra!=null)box.addView(extra);
        for(int i=0;i<labels.size();i++){
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(12),Ui.dp(11),Ui.dp(12),Ui.dp(11));row.setTag(i);
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Theme.a(th.accent,70)),null,new ColorDrawable(Color.WHITE)));
            if(sel.get(i))Blink.attach(row,th,th.accent,true,12);
            else row.setTag(R_BLINK,Blink.attach(row,th,colors.get(i)==th.bad?th.bad:th.accent,false,12));
            if(icons.get(i)!=null){ImageView iv=new ImageView(c);iv.setImageDrawable(icons.get(i));
                row.addView(iv,new LinearLayout.LayoutParams(Ui.dp(22),Ui.dp(22)));
                View sp=new View(c);row.addView(sp,new LinearLayout.LayoutParams(Ui.dp(10),1));}
            else{TextView arrow=Ui.text(c,"▸ ",12,th.accent,true);row.addView(arrow);}
            TextView tv=Ui.text(c,labels.get(i),13,colors.get(i),false);tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tv,new LinearLayout.LayoutParams(0,-2,1));
            box.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        return box;
    }
    private void bindClicks(LinearLayout box,Runnable dismiss){
        for(int i=0;i<box.getChildCount();i++){
            View v=box.getChildAt(i);if(!(v.getTag() instanceof Integer))continue;
            int idx=(Integer)v.getTag();
            v.setOnClickListener(x->{Ui.tick(x);Object bl=x.getTag(R_BLINK);if(bl instanceof Blink)((Blink)bl).burst();
                x.postDelayed(()->{dismiss.run();actions.get(idx).run();},170);});
        }
    }

    public static void choose(Context c,Theme th,String title,String[] items,int selected,Pick pick){
        CyberMenu m=new CyberMenu(c,th,title);
        for(int i=0;i<items.length;i++){final int k=i;if(i==selected)m.addSelected("◉ "+items[i],()->pick.on(k));else m.add("○ "+items[i],null,th.fg,()->pick.on(k));}
        m.show();
    }

    public static void input(Context c,Theme th,String title,String initial,TextDone done){
        Dialog d=new Dialog(c);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(panel(c,th,th.accent));box.setPadding(Ui.dp(18),Ui.dp(16),Ui.dp(18),Ui.dp(16));
        box.addView(Ui.text(c,"// "+title.toUpperCase(),12,th.secondary,true));
        EditText e=new EditText(c);e.setText(initial==null?"":initial);e.setTextColor(th.fg);e.setHintTextColor(th.muted);
        e.setTypeface(Ui.MONO);e.setSingleLine(true);e.setSelectAllOnFocus(true);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(th.accent));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(12);box.addView(e,lp);
        LinearLayout btns=new LinearLayout(c);btns.setGravity(Gravity.END);
        btns.addView(Ui.button(c,th,"ANNULLA",v->d.dismiss()));
        View sp=new View(c);btns.addView(sp,new LinearLayout.LayoutParams(Ui.dp(10),1));
        btns.addView(Ui.button(c,th,"OK",v->{d.dismiss();done.on(e.getText().toString());}));
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.topMargin=Ui.dp(14);box.addView(btns,bl);
        d.setContentView(box);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Ui.dp(310),ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);}
        d.show();e.requestFocus();
    }

    public static void notice(Context c,Theme th,String title,String msg){
        Dialog d=new Dialog(c);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(panel(c,th,th.secondary));box.setPadding(Ui.dp(18),Ui.dp(16),Ui.dp(18),Ui.dp(16));
        GlitchTextView t=new GlitchTextView(c,th,13,th.accent,true);t.alignLeft();t.setText(title.toUpperCase());box.addView(t);
        TextView m=Ui.text(c,msg,12.5f,th.fg,false);m.setLineSpacing(Ui.dpf(3),1f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(10);box.addView(m,lp);
        TextView ok=Ui.button(c,th,"OK",v->d.dismiss());
        LinearLayout.LayoutParams ol=new LinearLayout.LayoutParams(-2,-2);ol.topMargin=Ui.dp(14);ol.gravity=Gravity.END;box.addView(ok,ol);
        d.setContentView(box);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Ui.dp(320),ViewGroup.LayoutParams.WRAP_CONTENT);}
        d.show();
    }
}
