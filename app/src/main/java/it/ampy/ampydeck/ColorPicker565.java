package it.ampy.ampydeck;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import java.util.Locale;

/**
 * 16-bit colour picker (RGB565 = 65,536 colours): a quantised hue × lightness palette, a grey row,
 * three exact sliders (R 0-31, G 0-63, B 0-31) and a hex field. Every result is snapped to 565.
 */
public final class ColorPicker565 {
    public interface Done{void on(int color);}
    private ColorPicker565(){}

    public static void show(Context c,Theme th,String title,int initial,Done done){
        final int[] v={Theme.to565(initial)};
        Dialog d=new Dialog(c);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        Ui.NeonPanel bg=new Ui.NeonPanel(th,th.accent,.97f);bg.scan=true;box.setBackground(bg);
        box.setPadding(Ui.dp(16),Ui.dp(14),Ui.dp(16),Ui.dp(14));
        GlitchTextView t=new GlitchTextView(c,th,13,th.secondary,true);t.alignLeft();t.setText("// "+title.toUpperCase(Locale.ROOT));box.addView(t);

        // preview + codes
        LinearLayout head=new LinearLayout(c);head.setGravity(Gravity.CENTER_VERTICAL);
        View sw=new View(c);head.addView(sw,new LinearLayout.LayoutParams(Ui.dp(64),Ui.dp(44)));
        TextView codes=Ui.text(c,"",12,th.fg,true);codes.setPadding(Ui.dp(12),0,0,0);head.addView(codes,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);hl.topMargin=Ui.dp(10);box.addView(head,hl);

        Palette pal=new Palette(c,th);
        LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,Ui.dp(150));pl.topMargin=Ui.dp(10);box.addView(pal,pl);

        SeekBar[] bars=new SeekBar[3];TextView[] lbl=new TextView[3];
        String[] names={"R","G","B"};int[] max={31,63,31};int[] tint={0xffff4d4d,0xff4dff7a,0xff4d8bff};
        for(int i=0;i<3;i++){
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
            lbl[i]=Ui.text(c,"",11,tint[i],true);row.addView(lbl[i],new LinearLayout.LayoutParams(Ui.dp(64),-2));
            SeekBar sb=new SeekBar(c);sb.setMax(max[i]);
            sb.setProgressTintList(ColorStateList.valueOf(tint[i]));sb.setThumbTintList(ColorStateList.valueOf(tint[i]));
            row.addView(sb,new LinearLayout.LayoutParams(0,-2,1));bars[i]=sb;
            LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2);rl.topMargin=Ui.dp(4);box.addView(row,rl);
        }
        EditText hex=new EditText(c);hex.setSingleLine(true);hex.setTypeface(Ui.MONO);hex.setTextColor(th.fg);hex.setHint("#RRGGBB");
        hex.setHintTextColor(th.muted);hex.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setBackgroundTintList(ColorStateList.valueOf(th.accent));
        LinearLayout.LayoutParams xl=new LinearLayout.LayoutParams(-1,-2);xl.topMargin=Ui.dp(6);box.addView(hex,xl);

        final boolean[] lock={false};
        Runnable refresh=()->{
            int col=Theme.from565(v[0]);sw.setBackgroundColor(col);
            codes.setText(String.format(Locale.ROOT,"#%06X\nRGB565 0x%04X",col&0xffffff,v[0]));
            lock[0]=true;
            bars[0].setProgress((v[0]>>11)&31);bars[1].setProgress((v[0]>>5)&63);bars[2].setProgress(v[0]&31);
            lbl[0].setText("R "+((v[0]>>11)&31)+"/31");lbl[1].setText("G "+((v[0]>>5)&63)+"/63");lbl[2].setText("B "+(v[0]&31)+"/31");
            if(!hex.hasFocus())hex.setText(String.format(Locale.ROOT,"#%06X",col&0xffffff));
            lock[0]=false;pal.sel=col;pal.invalidate();
        };
        for(int i=0;i<3;i++){final int k=i;bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int p,boolean user){
                if(lock[0]||!user)return;
                int r=(v[0]>>11)&31,g=(v[0]>>5)&63,b=v[0]&31;if(k==0)r=p;else if(k==1)g=p;else b=p;
                v[0]=r<<11|g<<5|b;refresh.run();}
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}});}
        hex.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c2){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c2){}
            @Override public void afterTextChanged(android.text.Editable e){
                if(lock[0]||!hex.hasFocus())return;String s=e.toString().trim().replace("#","");
                if(s.length()==6)try{v[0]=Theme.to565(0xff000000|Integer.parseInt(s,16));refresh.run();}catch(Throwable ignored){}}});
        pal.pick=col->{v[0]=Theme.to565(col);hex.clearFocus();refresh.run();};

        LinearLayout btns=new LinearLayout(c);btns.setGravity(Gravity.END);
        btns.addView(Ui.button(c,th,"ANNULLA",x->d.dismiss()));
        View sp=new View(c);btns.addView(sp,new LinearLayout.LayoutParams(Ui.dp(10),1));
        btns.addView(Ui.button(c,th,"OK",x->{d.dismiss();done.on(Theme.from565(v[0]));}));
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.topMargin=Ui.dp(12);box.addView(btns,bl);

        ScrollView scr=new ScrollView(c);scr.addView(box);d.setContentView(scr);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Ui.dp(340),ViewGroup.LayoutParams.WRAP_CONTENT);}
        refresh.run();d.show();
    }

    /** Hue (columns) × lightness (rows) grid + a grey ramp, every cell already snapped to RGB565. */
    private static final class Palette extends View {
        interface Pick{void on(int c);}
        Pick pick;int sel;private final Theme th;
        private static final int COLS=24,ROWS=9;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Palette(Context c,Theme th){super(c);this.th=th;}
        private int cell(int col,int row){
            if(row==ROWS){float g=col/(float)(COLS-1);int v=(int)(255*g);return Theme.q565(0xff000000|v<<16|v<<8|v);}
            float hue=col*360f/COLS;float light=.88f-row*.085f;float sat=row<2?.75f:1f;
            return Theme.q565(hsl(hue,sat,light));
        }
        private static int hsl(float h,float s,float l){
            float c=(1-Math.abs(2*l-1))*s,x=c*(1-Math.abs((h/60f)%2-1)),m=l-c/2;float r=0,g=0,b=0;
            if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else{r=c;b=x;}
            return 0xff000000|((int)((r+m)*255))<<16|((int)((g+m)*255))<<8|(int)((b+m)*255);
        }
        @Override protected void onDraw(Canvas c){
            float cw=getWidth()/(float)COLS,ch=getHeight()/(float)(ROWS+1);
            for(int r=0;r<=ROWS;r++)for(int k=0;k<COLS;k++){
                int col=cell(k,r);p.setStyle(Paint.Style.FILL);p.setColor(col);
                c.drawRect(k*cw,r*ch,(k+1)*cw,(r+1)*ch,p);
                if(col==sel){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(2));p.setColor(0xffffffff);
                    c.drawRect(k*cw+1,r*ch+1,(k+1)*cw-1,(r+1)*ch-1,p);}
            }
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1));p.setColor(Theme.a(th.accent,160));c.drawRect(0,0,getWidth(),getHeight(),p);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){
                getParent().requestDisallowInterceptTouchEvent(true);
                int k=(int)(e.getX()/(getWidth()/(float)COLS)),r=(int)(e.getY()/(getHeight()/(float)(ROWS+1)));
                k=Math.max(0,Math.min(COLS-1,k));r=Math.max(0,Math.min(ROWS,r));
                if(pick!=null)pick.on(cell(k,r));
            }
            return true;
        }
    }
}
