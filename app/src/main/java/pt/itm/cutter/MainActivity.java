package pt.itm.cutter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements BluetoothController.Listener {
    private static final int REQ_BT = 40;
    private static final int NAVY = Color.rgb(9,18,34);
    private static final int NAVY2 = Color.rgb(23,39,68);
    private static final int TEAL = Color.rgb(0,181,173);
    private static final int BG = Color.rgb(245,247,250);
    private static final int TEXT = Color.rgb(18,27,43);
    private static final int MUTED = Color.rgb(100,116,139);
    private static final int BORDER = Color.rgb(226,232,240);

    private final ExecutorService work = Executors.newFixedThreadPool(3);
    private final CatalogClient catalog = new CatalogClient();
    private final BluetoothController cutter = new BluetoothController();
    private ImageLoader images;
    private BluetoothAdapter bt;

    private FrameLayout content;
    private TextView title, subtitle, cutterChip;
    private Button back;
    private int screen;
    private Models.Category category;
    private Models.Family family;
    private Models.Product product;
    private List<Models.Category> categories = new ArrayList<>();
    private List<Models.Family> families = new ArrayList<>();
    private List<Models.Product> products = new ArrayList<>();
    private AlertDialog blocker;

    private static final class Card {
        String name, image;
        Object value;
        Card(String n, String i, Object v) { name=n; image=i; value=v; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        bt = BluetoothAdapter.getDefaultAdapter();
        images = new ImageLoader(this);
        buildShell();
        requestBt();
        loadCategories();
    }

    private void buildShell() {
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);

        LinearLayout header = vertical();
        header.setPadding(dp(18),dp(14),dp(18),dp(14));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{NAVY,NAVY2}));
        LinearLayout top = row();
        LinearLayout brand = vertical();
        TextView itm = txt("ITM",10,Color.rgb(94,234,212),true);
        itm.setLetterSpacing(.22f);
        brand.addView(itm,wrap());
        brand.addView(txt("Cutter",27,Color.WHITE,true),wrap());
        top.addView(brand,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        cutterChip = txt("●  CUTTER OFF",11,Color.rgb(203,213,225),true);
        cutterChip.setGravity(Gravity.CENTER);
        cutterChip.setPadding(dp(12),dp(8),dp(12),dp(8));
        cutterChip.setBackground(round(Color.argb(35,255,255,255),18,Color.argb(70,255,255,255)));
        cutterChip.setOnClickListener(v -> showCutterDialog());
        top.addView(cutterChip,wrap());
        header.addView(top,matchWrap());

        LinearLayout nav = row();
        nav.setPadding(0,dp(12),0,0);
        back = btn("‹",Color.WHITE,Color.argb(28,255,255,255));
        back.setTextSize(24);
        back.setVisibility(View.GONE);
        back.setOnClickListener(v -> goBack());
        nav.addView(back,new LinearLayout.LayoutParams(dp(42),dp(38)));
        LinearLayout labels = vertical(); labels.setPadding(dp(10),0,0,0);
        title = txt("Catálogo",20,Color.WHITE,true);
        subtitle = txt("Modelos do fabricante",12,Color.rgb(203,213,225),false);
        labels.addView(title,wrap()); labels.addView(subtitle,wrap());
        nav.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        header.addView(nav,matchWrap());
        root.addView(header,matchWrap());
        content = new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(root);
    }

    private void loadCategories() {
        screen=0; category=null; family=null; product=null; back.setVisibility(View.GONE);
        title.setText("Escolhe o equipamento"); subtitle.setText("Telemóveis, tablets e outras categorias");
        loading("A carregar catálogo…");
        work.execute(() -> {
            try { categories = catalog.categories(); ui(this::showCategories); }
            catch (Exception e) { ui(() -> error("Não consegui carregar o catálogo",safe(e),this::loadCategories)); }
        });
    }

    private void showCategories() {
        screen=0; back.setVisibility(View.GONE);
        title.setText("Escolhe o equipamento"); subtitle.setText(categories.size()+" categorias");
        ArrayList<Card> cards=new ArrayList<>();
        for (Models.Category c:categories) cards.add(new Card(c.name,c.icon,c));
        grid(cards,"Pesquisar categoria…",c -> { category=(Models.Category)c.value; loadFamilies(); });
    }

    private void loadFamilies() {
        screen=1; back.setVisibility(View.VISIBLE);
        title.setText(category.name); subtitle.setText("Marcas / famílias"); loading("A carregar marcas…");
        work.execute(() -> {
            try { families=catalog.families(category); ui(this::showFamilies); }
            catch(Exception e){ ui(() -> error("Não consegui carregar as marcas",safe(e),this::loadFamilies)); }
        });
    }

    private void showFamilies() {
        screen=1; back.setVisibility(View.VISIBLE); title.setText(category.name); subtitle.setText(families.size()+" marcas / famílias");
        ArrayList<Card> cards=new ArrayList<>();
        for(Models.Family f:families) cards.add(new Card(f.name,f.icon,f));
        grid(cards,"Pesquisar marca…",c -> { family=(Models.Family)c.value; loadProducts(); });
    }

    private void loadProducts() {
        screen=2; back.setVisibility(View.VISIBLE); title.setText(family.name); subtitle.setText("Modelos"); loading("A carregar modelos…");
        work.execute(() -> {
            try { products=catalog.products(category,family); ui(this::showProducts); }
            catch(Exception e){ ui(() -> error("Não consegui carregar os modelos",safe(e),this::loadProducts)); }
        });
    }

    private void showProducts() {
        screen=2; back.setVisibility(View.VISIBLE); title.setText(family.name); subtitle.setText(products.size()+" modelos");
        ArrayList<Card> cards=new ArrayList<>();
        for(Models.Product p:products) cards.add(new Card(p.name,!p.cover.isEmpty()?p.cover:p.detail,p));
        grid(cards,"Pesquisar modelo…",c -> { product=(Models.Product)c.value; showProduct(); });
    }

    private void showProduct() {
        screen=3; back.setVisibility(View.VISIBLE); title.setText(product.name); subtitle.setText(family.name);
        ScrollView scroll=new ScrollView(this); LinearLayout box=vertical(); box.setPadding(dp(20),dp(18),dp(20),dp(28));
        ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_INSIDE); image.setBackground(round(Color.WHITE,18,BORDER));
        box.addView(image,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(340)));
        images.load(!product.detail.isEmpty()?product.detail:product.cover,image);
        TextView name=txt(product.name,22,TEXT,true); name.setPadding(0,dp(18),0,dp(6)); box.addView(name,matchWrap());
        String meta=(product.isBack==1?"Traseira":"Frente")+(product.size.isEmpty()?"":"  •  "+product.size);
        box.addView(txt(meta,14,MUTED,false),matchWrap());
        Button cut=btn("CORTAR",Color.WHITE,TEAL); cut.setTextSize(18); cut.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)); bp.setMargins(0,dp(24),0,0); box.addView(cut,bp);
        cut.setOnClickListener(v -> prepareCut());
        TextView hint=txt("O corte é enviado diretamente para a tua PT005/CUTTER_E66F.",12,MUTED,false); hint.setGravity(Gravity.CENTER); hint.setPadding(dp(8),dp(12),dp(8),0); box.addView(hint,matchWrap());
        scroll.addView(box); setContent(scroll);
    }

    private interface Pick { void choose(Card c); }
    private void grid(List<Card> source,String hint,Pick pick) {
        LinearLayout page=vertical(); page.setPadding(dp(14),dp(14),dp(14),dp(14));
        EditText search=new EditText(this); search.setSingleLine(true); search.setHint(hint); search.setTextSize(15); search.setPadding(dp(14),0,dp(14),0); search.setBackground(round(Color.WHITE,14,BORDER));
        page.addView(search,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
        GridView grid=new GridView(this); grid.setNumColumns(2); grid.setHorizontalSpacing(dp(10)); grid.setVerticalSpacing(dp(10)); grid.setPadding(0,dp(12),0,dp(10)); grid.setClipToPadding(false);
        CardAdapter adapter=new CardAdapter(source); grid.setAdapter(adapter); grid.setOnItemClickListener((p,v,pos,id)->pick.choose(adapter.item(pos)));
        page.addView(grid,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){adapter.filter(s.toString());} public void afterTextChanged(Editable e){} });
        setContent(page);
    }

    private final class CardAdapter extends BaseAdapter {
        private final List<Card> all; private final ArrayList<Card> shown=new ArrayList<>();
        CardAdapter(List<Card> a){all=a; shown.addAll(a);} Card item(int p){return shown.get(p);} public int getCount(){return shown.size();} public Object getItem(int p){return item(p);} public long getItemId(int p){return p;}
        void filter(String q){shown.clear(); String x=q.trim().toLowerCase(Locale.ROOT); for(Card c:all) if(x.isEmpty()||c.name.toLowerCase(Locale.ROOT).contains(x)) shown.add(c); notifyDataSetChanged();}
        public View getView(int pos,View convert,ViewGroup parent){
            LinearLayout card=vertical(); card.setPadding(dp(10),dp(10),dp(10),dp(10)); card.setGravity(Gravity.CENTER); card.setBackground(round(Color.WHITE,16,BORDER));
            ImageView image=new ImageView(MainActivity.this); image.setScaleType(ImageView.ScaleType.CENTER_INSIDE); card.addView(image,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(120)));
            TextView label=txt(item(pos).name,14,TEXT,true); label.setGravity(Gravity.CENTER); label.setMaxLines(2); label.setPadding(dp(3),dp(8),dp(3),dp(5)); card.addView(label,matchWrap());
            images.load(item(pos).image,image); return card;
        }
    }

    private void prepareCut() {
        if(!cutter.isConnected()){ new AlertDialog.Builder(this).setTitle("Ligar plotter").setMessage("Liga primeiro a CUTTER_E66F pelo botão CUTTER no topo.").setPositiveButton("Ligar",(d,w)->showCutterDialog()).setNegativeButton("Cancelar",null).show(); return; }
        new AlertDialog.Builder(this).setTitle(product.name).setMessage("Enviar este corte para a plotter?").setNegativeButton("Cancelar",null).setPositiveButton("CORTAR",(d,w)->downloadAndCut()).show();
    }

    private void downloadAndCut() {
        blocking("A preparar o corte…");
        work.execute(() -> {
            try {
                Models.Product p=catalog.detail(product); product=p;
                if(p.file==null||p.file.isEmpty()) throw new Exception("Este modelo não forneceu ficheiro de corte no catálogo.");
                byte[] data=catalog.download(p.file);
                if(!CatalogClient.looksLikeSjc(data)) throw new Exception("O ficheiro recebido não parece ser um corte SJC válido.");
                ui(() -> { hideBlocking(); blocking("A enviar para a plotter…"); cutter.cut(data,this); });
            } catch(Exception e){ ui(() -> { hideBlocking(); dialogError("Não foi possível preparar o corte",safe(e)); }); }
        });
    }

    private void requestBt(){ if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},REQ_BT); }
    private boolean btAllowed(){ return Build.VERSION.SDK_INT<31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; }

    private void showCutterDialog() {
        if(bt==null){dialogError("Bluetooth","Este equipamento não tem Bluetooth.");return;}
        if(!btAllowed()){requestBt(); Toast.makeText(this,"Autoriza o Bluetooth e toca novamente em CUTTER.",Toast.LENGTH_LONG).show();return;}
        if(cutter.isConnected()){
            new AlertDialog.Builder(this).setTitle("Plotter ligada").setMessage("A CUTTER está ligada.").setPositiveButton("Manter",null).setNegativeButton("Desligar",(d,w)->cutter.disconnect()).show(); return;
        }
        if(!bt.isEnabled()){dialogError("Bluetooth desligado","Liga o Bluetooth do telefone e emparelha a CUTTER_E66F nas definições do Android.");return;}
        Set<BluetoothDevice> bonded=bt.getBondedDevices();
        ArrayList<BluetoothDevice> devices=new ArrayList<>(bonded); ArrayList<String> names=new ArrayList<>();
        for(BluetoothDevice d:devices) names.add((d.getName()==null?"Dispositivo":d.getName())+"\n"+d.getAddress());
        if(devices.isEmpty()){dialogError("Sem dispositivos emparelhados","Emparelha primeiro a CUTTER_E66F nas definições Bluetooth do Android.");return;}
        Spinner spinner=new Spinner(this); spinner.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        int preferred=0; for(int i=0;i<devices.size();i++){String n=devices.get(i).getName(); if(n!=null&&n.toUpperCase(Locale.ROOT).contains("CUTTER")){preferred=i;break;}} spinner.setSelection(preferred);
        new AlertDialog.Builder(this).setTitle("Ligar à plotter").setView(spinner).setNegativeButton("Cancelar",null).setPositiveButton("LIGAR",(d,w)->{BluetoothDevice dev=devices.get(spinner.getSelectedItemPosition()); blocking("A ligar à "+(dev.getName()==null?"plotter":dev.getName())+"…"); cutter.connect(dev,this);}).show();
    }

    @Override public void status(String text,boolean connected){ui(() -> {hideBlocking(); cutterChip.setText(connected?"●  CUTTER ON":"●  CUTTER OFF"); cutterChip.setTextColor(connected?Color.rgb(94,234,212):Color.rgb(203,213,225));});}
    @Override public void message(String text){ui(() -> Toast.makeText(this,text,Toast.LENGTH_SHORT).show());}
    @Override public void cutDone(){ui(() -> {hideBlocking(); new AlertDialog.Builder(this).setTitle("Corte enviado").setMessage("O ficheiro foi enviado para a plotter.").setPositiveButton("OK",null).show();});}
    @Override public void error(String text){ui(() -> {hideBlocking(); dialogError("ITM Cutter",text);});}

    private void goBack(){ if(screen==3)showProducts(); else if(screen==2)showFamilies(); else if(screen==1)showCategories(); }
    private void loading(String s){LinearLayout b=vertical();b.setGravity(Gravity.CENTER);ProgressBar p=new ProgressBar(this);b.addView(p,new LinearLayout.LayoutParams(dp(52),dp(52)));TextView t=txt(s,15,MUTED,false);t.setPadding(0,dp(14),0,0);b.addView(t,wrap());setContent(b);}
    private void error(String h,String m,Runnable retry){LinearLayout p=vertical();p.setGravity(Gravity.CENTER);p.setPadding(dp(28),dp(28),dp(28),dp(28));TextView a=txt(h,19,TEXT,true);a.setGravity(Gravity.CENTER);p.addView(a,matchWrap());TextView b=txt(m,13,MUTED,false);b.setGravity(Gravity.CENTER);b.setPadding(0,dp(10),0,dp(18));p.addView(b,matchWrap());Button r=btn("Tentar novamente",Color.WHITE,TEAL);r.setOnClickListener(v->retry.run());p.addView(r,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));setContent(p);}
    private void dialogError(String h,String m){new AlertDialog.Builder(this).setTitle(h).setMessage(m).setPositiveButton("OK",null).show();}
    private void blocking(String s){hideBlocking();LinearLayout r=row();r.setPadding(dp(24),dp(18),dp(24),dp(18));ProgressBar p=new ProgressBar(this);r.addView(p,new LinearLayout.LayoutParams(dp(42),dp(42)));TextView t=txt(s,15,TEXT,false);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(15),0,0,0);r.addView(t,new LinearLayout.LayoutParams(0,dp(48),1f));blocker=new AlertDialog.Builder(this).setView(r).setCancelable(false).create();blocker.show();}
    private void hideBlocking(){if(blocker!=null&&blocker.isShowing())blocker.dismiss();blocker=null;}
    private void setContent(View v){content.removeAllViews();content.addView(v,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView txt(String s,float sp,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button btn(String s,int tc,int bg){Button b=new Button(this);b.setText(s);b.setTextColor(tc);b.setAllCaps(false);b.setTextSize(14);b.setBackground(round(bg,14,bg));return b;}
    private GradientDrawable round(int fill,float radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void ui(Runnable r){runOnUiThread(r);} private static String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}

    @Override protected void onDestroy(){cutter.shutdown();images.shutdown();work.shutdownNow();super.onDestroy();}
}
