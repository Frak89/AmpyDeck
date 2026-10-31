package it.ampy.ampydeck;

import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** Key maps: Italian QWERTY (SwiftKey-like hints and accent popups), two symbol pages, emoji sets. */
public final class Layouts {
    private Layouts(){}

    public static final int SHIFT=-1,ABC=-3,SYM=-2,MORE=-6,DEL=-5,ENTER=-4,EMOJI=-7,SPACE=32,NONE=0;
    public static final int LETTERS=0,SYM1=1,SYM2=2,EMOJIS=3,CLIP=4;

    public static final class Key {
        public String label;public final int code;public String text;public float w;
        public String hint;public String[] popup;public final boolean special;
        public final RectF r=new RectF();public final Path path=new Path();public int index;
        Key(String label,int code,String text,float w,boolean special){this.label=label;this.code=code;this.text=text;this.w=w;this.special=special;}
        public boolean letter(){return code==NONE&&text!=null&&text.length()==1&&Character.isLetter(text.charAt(0));}
    }
    public static final class Row{public final List<Key> keys=new ArrayList<>();public float weight=1f;public float indent=0;}

    // SwiftKey-style symbol hints on the letters (with number row on)
    private static final String HINT_SRC="qwertyuiopasdfghjklzxcvbnm";
    private static final String HINT_SYM ="%^~|[]<>{}@#&*-+=()_$\"':;/";
    private static final String HINT_NUM ="1234567890";
    private static String accents(char c){
        switch(c){
            case 'a':return "àáâäæãåā";case 'e':return "èéêë€ēę";case 'i':return "ìíîïī";case 'o':return "òóôöõøœō";
            case 'u':return "ùúûüū";case 'c':return "çćč";case 'n':return "ñń";case 's':return "ßšś$";case 'z':return "žźż";
            case 'y':return "ýÿ";case 'l':return "ł";case 'd':return "ð";case 'g':return "ğ";case 'r':return "ř";case 't':return "þ";
            default:return "";
        }
    }

    private static Key letter(char c,boolean numberRow){
        Key k=new Key(String.valueOf(c),NONE,String.valueOf(c),1f,false);
        int i=HINT_SRC.indexOf(c);
        String hint=i<0?null:(!numberRow&&i<10)?String.valueOf(HINT_NUM.charAt(i)):String.valueOf(HINT_SYM.charAt(i));
        k.hint=hint;String acc=accents(c);
        ArrayList<String> pop=new ArrayList<>();
        if(hint!=null)pop.add(hint);
        if(!numberRow&&i>=0&&i<10)pop.add(String.valueOf(HINT_SYM.charAt(i)));
        for(int j=0;j<acc.length();j++)pop.add(String.valueOf(acc.charAt(j)));
        k.popup=pop.toArray(new String[0]);
        return k;
    }
    private static Key ch(String s){return new Key(s,NONE,s,1f,false);}
    private static Key ch(String s,String... pop){Key k=ch(s);k.popup=pop;k.hint=pop.length>0?pop[0]:null;return k;}
    private static Key sp(String label,int code,float w){return new Key(label,code,null,w,true);}

    private static Row numberRow(float weight){
        Row r=new Row();r.weight=weight;
        String[][] pops={{"¹","½","⅓","¼","⅛"},{"²","⅔"},{"³","¾","⅜"},{"⁴"},{"⁵","⅝"},{"⁶"},{"⁷","⅞"},{"⁸"},{"⁹"},{"⁰","°","∅","ⁿ"}};
        String nums="1234567890";
        for(int i=0;i<10;i++){Key k=ch(String.valueOf(nums.charAt(i)));k.popup=pops[i];r.keys.add(k);}
        return r;
    }

    private static Row bottom(boolean letters,boolean emojiKey){
        Row r=new Row();
        r.keys.add(sp(letters?"?123":"ABC",letters?SYM:ABC,1.5f));
        r.keys.add(ch(",",",",";",":","…","¿","¡"));
        if(emojiKey)r.keys.add(sp("☺",EMOJI,1f));
        r.keys.add(sp("",SPACE,emojiKey?4f:5f));
        r.keys.add(ch(".",".","?","!","…","·","•"));
        r.keys.add(sp("⏎",ENTER,1.5f));
        // SwiftKey: long-press on period shows the punctuation sheet
        r.keys.get(r.keys.size()-2).popup=new String[]{"?","!","…","'","\"","-","(",")","/","·"};
        r.keys.get(r.keys.size()-2).hint="?!";
        r.keys.get(1).popup=new String[]{";",":","…","¿","¡","«","»"};r.keys.get(1).hint=null;
        return r;
    }

    public static List<Row> build(int page,boolean numberRow,boolean emojiKey){
        ArrayList<Row> rows=new ArrayList<>();
        switch(page){
            case SYM1:{
                rows.add(numberRow(1f));
                Row a=new Row();for(String s:new String[]{"@","#","€","_","&","-","+","(",")","/"})a.keys.add(ch(s));rows.add(a);
                a.keys.get(2).popup=new String[]{"$","£","¥","¢","₿","₹"};a.keys.get(5).popup=new String[]{"–","—","·"};
                a.keys.get(7).popup=new String[]{"[","{","<"};a.keys.get(8).popup=new String[]{"]","}",">"};
                Row b=new Row();b.keys.add(sp("=\\<",MORE,1.5f));
                for(String s:new String[]{"*","\"","'",":",";","!","?"})b.keys.add(ch(s));
                b.keys.get(2).popup=new String[]{"«","»","“","”","„"};b.keys.get(3).popup=new String[]{"‘","’","`"};
                b.keys.get(6).popup=new String[]{"¡","‽"};b.keys.get(7).popup=new String[]{"¿","‽"};
                b.keys.add(sp("⌫",DEL,1.5f));rows.add(b);
                rows.add(bottom(false,emojiKey));break;
            }
            case SYM2:{
                Row n=new Row();for(String s:new String[]{"~","`","|","•","√","π","÷","×","¶","∆"})n.keys.add(ch(s));rows.add(n);
                Row a=new Row();for(String s:new String[]{"£","¥","$","¢","^","°","=","{","}","\\"})a.keys.add(ch(s));rows.add(a);
                Row b=new Row();b.keys.add(sp("?123",SYM,1.5f));
                for(String s:new String[]{"%","©","®","™","✓","[","]"})b.keys.add(ch(s));
                b.keys.add(sp("⌫",DEL,1.5f));
                Row c=new Row();for(String s:new String[]{"<",">","±","≠","≈","∞","§","µ","←","→"})c.keys.add(ch(s));
                rows.add(c);rows.add(b);
                rows.add(bottom(false,emojiKey));break;
            }
            default:{
                if(numberRow)rows.add(numberRow(.82f));
                Row q=new Row();for(char c:"qwertyuiop".toCharArray())q.keys.add(letter(c,numberRow));rows.add(q);
                Row a=new Row();a.indent=.5f;for(char c:"asdfghjkl".toCharArray())a.keys.add(letter(c,numberRow));rows.add(a);
                Row z=new Row();z.keys.add(sp("⇧",SHIFT,1.5f));
                for(char c:"zxcvbnm".toCharArray())z.keys.add(letter(c,numberRow));
                z.keys.add(sp("⌫",DEL,1.5f));rows.add(z);
                rows.add(bottom(true,emojiKey));
            }
        }
        int idx=0;for(Row r:rows)for(Key k:r.keys)k.index=idx++;
        return rows;
    }

    // ------------------------------------------------------------------ emoji
    public static final String[] EMOJI_TABS={"🕘","😀","👍","❤","🐱","🍕","⚽","💡","⚡"};
    public static final String[][] EMOJI_SETS={
        {}, // recents
        ("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖").split(" "),
        ("👍 👎 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐 🖖 👋 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 💪 🦾 🧠 👀 👁 👅 👄 💋 🫶 🙋 🙆 🙅 🤷 🤦 🙇 💁 🧑‍💻 👨‍💻 👩‍💻 🕺 💃 🏃 🚶 🧍 🧎").split(" "),
        ("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 🔥 ✨ ⭐ 🌟 💫 💥 💢 💦 💨 🕳 💬 💭 💤 ✅ ❌ ❗ ❓ ‼️ ⁉️ 💯 🔞 ♻️ ⚠️ 🚫 ⛔ 🆗 🆕 🆒 🔝").split(" "),
        ("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷 🦂 🐢 🐍 🦎 🐙 🦑 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🐘 🦒 🐪 🐄 🐎 🐖 🐑 🐐 🐕 🐈 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌷 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌚 🌙 🌎 🪐 ⚡ ☄️ 🌈 ☀️ ⛅ ☁️ 🌧 ⛈ 🌩 ❄️ ☃️ 🌊").split(" "),
        ("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶 🌽 🥕 🧄 🧅 🥔 🥐 🥖 🍞 🥨 🧀 🥚 🍳 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍰 🎂 🧁 🍮 🍭 🍬 🍫 🍿 🍩 🍪 ☕ 🍵 🥤 🧋 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾").split(" "),
        ("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🥋 ⛳ 🎣 🤿 🎽 🛹 ⛸ 🎿 🏂 🏋️ 🚴 🏆 🥇 🥈 🥉 🏅 🎮 🕹 🎲 ♟ 🎯 🎳 🎸 🎹 🥁 🎷 🎺 🎻 🎤 🎧 🎬 🎨 🎭 🎪 🎟 🚗 🏎 🚓 🚑 🚒 🚲 🛵 🏍 ✈️ 🚀 🛸 🚁 ⛵ 🚢 🏠 🏢 🏰 🗼 🗽 🌋 🏝 🏖").split(" "),
        ("⌚ 📱 💻 ⌨️ 🖥 🖨 🖱 💾 💿 📷 📹 🎥 📺 📻 🔋 🔌 💡 🔦 🕯 🧯 💸 💵 💶 💳 💎 ⚖️ 🔧 🔨 ⚙️ 🧲 🔫 💣 🔪 🛡 🔮 🧿 💊 💉 🧬 🦠 🧪 🌡 🧹 🧻 🚽 🛁 🔑 🗝 🚪 🛋 🛏 🧸 🎁 🎈 🎉 🎊 ✉️ 📦 📝 📅 📌 📎 ✂️ 🔒 🔓 ❤️‍🔥").split(" "),
        ("⚡ ⌁ ⏻ ☢ ☣ ⚛ ♾ ✦ ✧ ◆ ◇ ◈ ▲ △ ▼ ▽ ◀ ▶ ■ □ ▣ ▢ ● ○ ◉ ◎ ★ ☆ ⬢ ⬡ ⌬ ⏣ ⟁ ⟐ ⧉ ⊕ ⊗ ⊘ ⌘ ⌥ ⇧ ⏎ ⌫ ∞ ≡ ≈ ♪ ♫ ☯ ☮ ✝ ☪ ✡ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ ﾂ ツ ノ ಠ_ಠ ¯\\_(ツ)_/¯ (╯°□°)╯︵┻━┻ ( ͡° ͜ʖ ͡°)").split(" ")
    };
}
