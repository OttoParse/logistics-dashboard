package pt.itm.cutter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class AuthActivity extends Activity {
    private static final int NAVY = Color.rgb(9,18,34);
    private static final int NAVY2 = Color.rgb(23,39,68);
    private static final int TEAL = Color.rgb(0,181,173);
    private static final int BG = Color.rgb(245,247,250);
    private static final int TEXT = Color.rgb(18,27,43);
    private static final int MUTED = Color.rgb(100,116,139);
    private static final int BORDER = Color.rgb(226,232,240);
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private EditText email, password;
    private Button login, register;
    private ProgressBar progress;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        prefs = getSharedPreferences("itm_catalog_auth", MODE_PRIVATE);
        buildUi();

        String e = prefs.getString("email", "");
        String p = prefs.getString("password", "");
        String s = prefs.getString("session", "");
        CatalogClient.configureAuth(e, p, s);
        email.setText(e);
        password.setText(p);

        if (!e.isEmpty() && !p.isEmpty()) authenticate(e, p, true);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(32));
        root.setBackgroundColor(BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(22), dp(22), dp(22), dp(22));
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{NAVY,NAVY2}));
        TextView tag = text("ITM", 11, Color.rgb(94,234,212), true);
        tag.setLetterSpacing(.22f);
        head.addView(tag, wrap());
        head.addView(text("Cutter", 30, Color.WHITE, true), wrap());
        TextView sub = text("Catálogo OEM + controlo direto da plotter", 13, Color.rgb(203,213,225), false);
        sub.setPadding(0, dp(6), 0, 0);
        head.addView(sub, matchWrap());
        LinearLayout.LayoutParams hp = matchWrap(); hp.setMargins(0,0,0,dp(22));
        root.addView(head,hp);

        root.addView(text("Acesso ao catálogo", 22, TEXT, true),matchWrap());
        TextView info = text("Entra com a conta OEM ou cria uma conta para a tua própria plotter.", 13, MUTED, false);
        info.setPadding(0,dp(8),0,dp(18));
        root.addView(info,matchWrap());

        email = field("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(email,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        password = field("Palavra-passe", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); pp.setMargins(0,dp(12),0,0);
        root.addView(password,pp);

        login = button("ENTRAR NO CATÁLOGO", TEAL);
        login.setOnClickListener(v -> authenticate(email.getText().toString().trim(),password.getText().toString(),false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)); lp.setMargins(0,dp(18),0,0);
        root.addView(login,lp);

        register = button("CRIAR CONTA OEM", NAVY2);
        register.setOnClickListener(v -> showRegister());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)); rp.setMargins(0,dp(10),0,0);
        root.addView(register,rp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams prog = new LinearLayout.LayoutParams(dp(40),dp(40)); prog.setMargins(0,dp(18),0,0);
        root.addView(progress,prog);

        status = text("",13,MUTED,false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8),dp(12),dp(8),0);
        root.addView(status,matchWrap());

        TextView serial = text("Plotter: " + CatalogClient.DEFAULT_SERIAL,12,MUTED,true);
        serial.setGravity(Gravity.CENTER);
        serial.setPadding(dp(6),dp(22),dp(6),0);
        root.addView(serial,matchWrap());

        setContentView(scroll);
    }

    private void showRegister() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(12),dp(20),dp(12));
        scroll.addView(box);

        EditText serial = field("Número de série", InputType.TYPE_CLASS_TEXT);
        serial.setText(CatalogClient.DEFAULT_SERIAL);
        box.addView(serial,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        EditText regEmail = field("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        addWithTop(box,regEmail,10,52);

        Button sendCode = button("ENVIAR CÓDIGO POR EMAIL", NAVY2);
        addWithTop(box,sendCode,10,50);

        EditText code = field("Código recebido por email", InputType.TYPE_CLASS_TEXT);
        addWithTop(box,code,10,52);

        EditText regPass = field("Palavra-passe", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addWithTop(box,regPass,10,52);

        TextView rule = text("8+ caracteres, maiúscula, minúscula, número e símbolo.",11,MUTED,false);
        rule.setPadding(dp(4),dp(5),dp(4),0);
        box.addView(rule,matchWrap());

        EditText company = field("Empresa", InputType.TYPE_CLASS_TEXT);
        company.setText("ITM");
        addWithTop(box,company,10,52);

        EditText country = field("País", InputType.TYPE_CLASS_TEXT);
        country.setText("Portugal");
        addWithTop(box,country,10,52);

        TextView regStatus = text("",12,MUTED,false);
        regStatus.setGravity(Gravity.CENTER);
        regStatus.setPadding(0,dp(8),0,0);
        box.addView(regStatus,matchWrap());

        Button create = button("CRIAR CONTA", TEAL);
        addWithTop(box,create,14,54);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Criar conta OEM")
                .setView(scroll)
                .setNegativeButton("Cancelar",null)
                .create();

        sendCode.setOnClickListener(v -> {
            String e = regEmail.getText().toString().trim();
            if (e.isEmpty()) { regStatus.setText("Introduz primeiro o email."); return; }
            sendCode.setEnabled(false);
            regStatus.setText("A validar a plotter e enviar o código…");
            work.execute(() -> {
                try {
                    CatalogClient.validateSerial(serial.getText().toString().trim());
                    CatalogClient.sendMailCode(e);
                    runOnUiThread(() -> { sendCode.setEnabled(true); regStatus.setText("Código enviado. Verifica o email."); });
                } catch (Exception ex) {
                    String m=safe(ex);
                    runOnUiThread(() -> { sendCode.setEnabled(true); regStatus.setText(m); });
                }
            });
        });

        create.setOnClickListener(v -> {
            String e=regEmail.getText().toString().trim();
            String c=code.getText().toString().trim();
            String p=regPass.getText().toString();
            String sn=serial.getText().toString().trim();
            String co=company.getText().toString().trim();
            String countryName=country.getText().toString().trim();
            if(e.isEmpty()||c.isEmpty()||p.isEmpty()||sn.isEmpty()){regStatus.setText("Preenche email, código, palavra-passe e número de série.");return;}
            if(!PASSWORD.matcher(p).matches()){regStatus.setText("A palavra-passe não cumpre os requisitos.");return;}
            create.setEnabled(false); sendCode.setEnabled(false); regStatus.setText("A criar a conta…");
            work.execute(() -> {
                try {
                    CatalogClient.validateSerial(sn);
                    CatalogClient.register(e,c,p,sn,co,countryName);
                    String session=CatalogClient.login(e,p);
                    prefs.edit().putString("email",e).putString("password",p).putString("session",session).apply();
                    runOnUiThread(() -> { dialog.dismiss(); email.setText(e); password.setText(p); openCutter(); });
                } catch(Exception ex) {
                    String m=safe(ex);
                    runOnUiThread(() -> { create.setEnabled(true); sendCode.setEnabled(true); regStatus.setText(m); });
                }
            });
        });
        dialog.show();
    }

    private void authenticate(String e, String p, boolean automatic) {
        if (e.isEmpty() || p.isEmpty()) {
            if (!automatic) new AlertDialog.Builder(this).setTitle("Dados em falta").setMessage("Introduz email e palavra-passe da conta OEM.").setPositiveButton("OK",null).show();
            return;
        }
        login.setEnabled(false);
        register.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText(automatic ? "A renovar sessão do catálogo…" : "A iniciar sessão no catálogo…");
        work.execute(() -> {
            try {
                String s = CatalogClient.login(e,p);
                prefs.edit().putString("email",e).putString("password",p).putString("session",s).apply();
                runOnUiThread(this::openCutter);
            } catch (Exception ex) {
                String m = safe(ex);
                runOnUiThread(() -> {
                    login.setEnabled(true);
                    register.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    status.setText(m);
                    if (!automatic) new AlertDialog.Builder(this).setTitle("Não foi possível entrar").setMessage(m).setPositiveButton("OK",null).show();
                });
            }
        });
    }

    private void openCutter() {
        progress.setVisibility(View.GONE);
        status.setText("Sessão iniciada.");
        startActivity(new Intent(this,MainActivity.class));
        finish();
    }

    private EditText field(String hint,int inputType){
        EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setInputType(inputType);e.setPadding(dp(14),0,dp(14),0);e.setBackground(round(Color.WHITE,14,BORDER));return e;
    }
    private Button button(String label,int bg){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextSize(14);b.setBackground(round(bg,14,bg));return b;}
    private void addWithTop(LinearLayout box,View v,int top,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(h));p.setMargins(0,dp(top),0,0);box.addView(v,p);}
    private TextView text(String s,float sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int fill,float radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}

    @Override protected void onDestroy(){work.shutdownNow();super.onDestroy();}
}
