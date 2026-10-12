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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthActivity extends Activity {
    private static final int NAVY = Color.rgb(9,18,34);
    private static final int NAVY2 = Color.rgb(23,39,68);
    private static final int TEAL = Color.rgb(0,181,173);
    private static final int BG = Color.rgb(245,247,250);
    private static final int TEXT = Color.rgb(18,27,43);
    private static final int MUTED = Color.rgb(100,116,139);
    private static final int BORDER = Color.rgb(226,232,240);

    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root;
    private EditText email, password;
    private Button login;
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

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

        TextView title = text("Acesso ao catálogo", 22, TEXT, true);
        root.addView(title,matchWrap());
        TextView info = text("Usa a conta do catálogo ProScreen/Celly do fabricante. A ITM Cutter utiliza a sessão apenas para obter categorias, marcas, modelos, imagens e ficheiros de corte.", 13, MUTED, false);
        info.setPadding(0,dp(8),0,dp(18));
        root.addView(info,matchWrap());

        email = new EditText(this);
        email.setHint("Email");
        email.setSingleLine(true);
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setPadding(dp(14),0,dp(14),0);
        email.setBackground(round(Color.WHITE,14,BORDER));
        root.addView(email,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        password = new EditText(this);
        password.setHint("Palavra-passe");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setPadding(dp(14),0,dp(14),0);
        password.setBackground(round(Color.WHITE,14,BORDER));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); pp.setMargins(0,dp(12),0,0);
        root.addView(password,pp);

        login = new Button(this);
        login.setText("ENTRAR NO CATÁLOGO");
        login.setTextColor(Color.WHITE);
        login.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        login.setTextSize(16);
        login.setBackground(round(TEAL,14,TEAL));
        login.setOnClickListener(v -> authenticate(email.getText().toString().trim(),password.getText().toString(),false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)); lp.setMargins(0,dp(18),0,0);
        root.addView(login,lp);

        progress = new ProgressBar(this);
        progress.setVisibility(ProgressBar.GONE);
        LinearLayout.LayoutParams prog = new LinearLayout.LayoutParams(dp(40),dp(40)); prog.setMargins(0,dp(18),0,0);
        root.addView(progress,prog);

        status = text("",13,MUTED,false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8),dp(12),dp(8),0);
        root.addView(status,matchWrap());

        TextView note = text("Se ainda não tens conta OEM, cria-a uma vez na aplicação ProScreen do fabricante. Depois usa aqui o mesmo email e palavra-passe.",12,MUTED,false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(6),dp(22),dp(6),0);
        root.addView(note,matchWrap());

        setContentView(root);
    }

    private void authenticate(String e, String p, boolean automatic) {
        if (e.isEmpty() || p.isEmpty()) {
            if (!automatic) new AlertDialog.Builder(this).setTitle("Dados em falta").setMessage("Introduz email e palavra-passe da conta OEM.").setPositiveButton("OK",null).show();
            return;
        }
        login.setEnabled(false);
        progress.setVisibility(ProgressBar.VISIBLE);
        status.setText(automatic ? "A renovar sessão do catálogo…" : "A iniciar sessão no catálogo…");
        work.execute(() -> {
            try {
                String s = CatalogClient.login(e,p);
                prefs.edit().putString("email",e).putString("password",p).putString("session",s).apply();
                runOnUiThread(this::openCutter);
            } catch (Exception ex) {
                String m = ex.getMessage()==null ? ex.getClass().getSimpleName() : ex.getMessage();
                runOnUiThread(() -> {
                    login.setEnabled(true);
                    progress.setVisibility(ProgressBar.GONE);
                    status.setText(m);
                    if (!automatic) new AlertDialog.Builder(this).setTitle("Não foi possível entrar").setMessage(m).setPositiveButton("OK",null).show();
                });
            }
        });
    }

    private void openCutter() {
        progress.setVisibility(ProgressBar.GONE);
        status.setText("Sessão iniciada.");
        startActivity(new Intent(this,MainActivity.class));
        finish();
    }

    private TextView text(String s,float sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int fill,float radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override protected void onDestroy(){work.shutdownNow();super.onDestroy();}
}
