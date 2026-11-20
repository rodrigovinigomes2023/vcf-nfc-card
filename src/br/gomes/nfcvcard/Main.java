package br.gomes.nfcvcard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import io.nayuki.qrcodegen.QrCode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main extends Activity implements Hce.Ouvinte {
    static final int BG = 0xFFF7F6F2, FG = 0xFF1A1A1A, MUTED = 0xFF5F5E5A, CARD = 0xFFFFFFFF,
        LINHA = 0xFFD6D3CA, ACCENT = 0xFF1D3FD8, VERDE = 0xFF15803D, LARANJA = 0xFFC2410C, PERIGO = 0xFFB42318;
    static final int LISTA = 0, ENCOSTAR = 1, EDITAR = 2;
    static final int PEDIR_FOTO = 1;

    Perfis perfis;
    Typeface tTit, tReg, tMed, tSemi;
    int tela = LISTA;

    // ---------- ciclo de vida ----------

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        tTit = fonte("InterTight.ttf", 700);
        tReg = fonte("Inter.ttf", 400);
        tMed = fonte("Inter.ttf", 500);
        tSemi = fonte("Inter.ttf", 600);
        perfis = new Perfis(this);
        telaLista();
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            NfcAdapter a = NfcAdapter.getDefaultAdapter(this);
            if (a != null) CardEmulation.getInstance(a).setPreferredService(this, new ComponentName(this, Hce.class));
        } catch (Exception ignored) { }
        if (tela == ENCOSTAR) Hce.ouvinte = this;
    }

    @Override protected void onPause() {
        super.onPause();
        Hce.ouvinte = null;
        try {
            NfcAdapter a = NfcAdapter.getDefaultAdapter(this);
            if (a != null) CardEmulation.getInstance(a).unsetPreferredService(this);
        } catch (Exception ignored) { }
    }

    @Override public void onBackPressed() {
        if (tela == ENCOSTAR) telaLista();
        else if (tela == EDITAR) { if (origem != null && perfis.busca(origem.id) != null) telaEncostar(perfis.busca(origem.id)); else telaLista(); }
        else super.onBackPressed();
    }

    // ---------- utilidades de layout ----------

    Typeface fonte(String arq, int peso) {
        try { return new Typeface.Builder(getAssets(), "fontes/" + arq).setFontVariationSettings("'wght' " + peso).build(); }
        catch (Exception e) { return Typeface.DEFAULT; }
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    TextView txt(String s, float sp, Typeface tf, int cor) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTypeface(tf); t.setTextColor(cor);
        t.setIncludeFontPadding(false);
        return t;
    }

    TextView titulo(String s, float sp, int cor) {
        TextView t = txt(s, sp, tTit, cor);
        t.setLetterSpacing(-0.02f);
        return t;
    }

    static GradientDrawable fundo(int cor, float raioPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor); g.setCornerRadius(raioPx);
        return g;
    }

    GradientDrawable borda(int cor, int corBorda, float raioDp, boolean tracejada) {
        GradientDrawable g = fundo(cor, dp(raioDp));
        if (tracejada) g.setStroke(dp(1.5f), corBorda, dp(5), dp(4));
        else g.setStroke(dp(1.5f), corBorda);
        return g;
    }

    LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout lin() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    LinearLayout.LayoutParams lpm(int w, int h, int top) { LinearLayout.LayoutParams p = lp(w, h); p.topMargin = top; return p; }
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    View botaoIcone(int tipo, int cor, Runnable acao, String descricao) {
        Desenho.Icone i = new Desenho.Icone(this, tipo, cor, dp(22));
        i.setLayoutParams(lp(dp(48), dp(48)));
        i.setContentDescription(descricao);
        i.setOnClickListener(v -> acao.run());
        i.setBackground(fundo(0x00000000, dp(24)));
        return i;
    }

    void barras(int cor, boolean clara) {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(cor);
        w.setNavigationBarColor(cor);
        w.getDecorView().setSystemUiVisibility(clara
            ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
    }

    static boolean clara(int cor) {
        double l = (0.299 * Color.red(cor) + 0.587 * Color.green(cor) + 0.114 * Color.blue(cor)) / 255;
        return l > 0.62;
    }

    static Bitmap fotoBitmap(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] d = Base64.decode(b64, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (Exception e) { return null; }
    }

    static Bitmap redondo(Bitmap src, int px) {
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap s = Bitmap.createScaledBitmap(src, px, px, true);
        p.setShader(new BitmapShader(s, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawCircle(px / 2f, px / 2f, px / 2f, p);
        return out;
    }

    /** Bolinha com a foto/logo, ou as iniciais. */
    View avatar(Perfis.Perfil p, int ladoDp, int fundoIniciais, int corIniciais) {
        int px = dp(ladoDp);
        Bitmap b = fotoBitmap(p.foto);
        if (b != null) {
            ImageView i = new ImageView(this);
            i.setImageBitmap(redondo(b, px));
            i.setLayoutParams(lp(px, px));
            return i;
        }
        TextView t = txt(p.iniciais(), ladoDp * 0.36f, tTit, corIniciais);
        t.setGravity(Gravity.CENTER);
        t.setBackground(fundo(fundoIniciais, px / 2f));
        t.setLayoutParams(lp(px, px));
        return t;
    }

    // ================= TELA 1: MEUS PERFIS =================

    void telaLista() {
        tela = LISTA; Hce.ouvinte = null;
        barras(BG, true);
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(BG);
        ScrollView sv = new ScrollView(this);
        LinearLayout c = col();
        c.setPadding(dp(24), dp(20), dp(24), dp(120));
        sv.addView(c);
        f.addView(sv);

        LinearLayout topo = lin();
        ImageView logo = new ImageView(this);
        try (InputStream in = getAssets().open("logo.png")) { logo.setImageBitmap(BitmapFactory.decodeStream(in)); } catch (Exception ignored) { }
        logo.setContentDescription("Logo do VCF NFC Card");
        topo.addView(logo, lp(dp(40), dp(40)));
        TextView marca = titulo("VCF NFC Card", 22, FG);
        LinearLayout.LayoutParams ml = lp(WRAP, WRAP); ml.leftMargin = dp(10);
        topo.addView(marca, ml);
        c.addView(topo);

        c.addView(titulo("Meus perfis", 30, FG), lpm(WRAP, WRAP, dp(28)));
        c.addView(txt(perfis.lista.isEmpty() ? "Crie um perfil para começar." : "Toque num perfil para compartilhar.", 15, tReg, MUTED), lpm(WRAP, WRAP, dp(8)));

        Perfis.Perfil ativo = perfis.ativo();
        for (Perfis.Perfil p : perfis.lista) c.addView(cartao(p, ativo != null && p.id.equals(ativo.id)), lpm(MATCH, dp(150), dp(14)));

        if (perfis.lista.isEmpty()) {
            LinearLayout vazio = col();
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(dp(24), dp(32), dp(24), dp(32));
            vazio.setBackground(borda(0x00000000, LINHA, 20, true));
            vazio.addView(txt("Nenhum perfil ainda", 17, tSemi, FG));
            TextView d = txt("Coloque seu nome, telefone, foto…\ne compartilhe só de encostar.", 14, tReg, MUTED);
            d.setGravity(Gravity.CENTER); d.setLineSpacing(0, 1.3f);
            vazio.addView(d, lpm(WRAP, WRAP, dp(8)));
            vazio.setOnClickListener(v -> telaEditar(null));
            c.addView(vazio, lpm(MATCH, WRAP, dp(20)));
        }

        LinearLayout fab = lin();
        fab.setBackground(fundo(FG, dp(18)));
        fab.setPadding(dp(18), 0, dp(22), 0);
        fab.addView(new Desenho.Icone(this, Desenho.MAIS, BG, dp(20)), lp(dp(24), dp(24)));
        TextView ft = txt("Novo perfil", 16, tSemi, BG);
        LinearLayout.LayoutParams fl = lp(WRAP, WRAP); fl.leftMargin = dp(8);
        fab.addView(ft, fl);
        fab.setOnClickListener(v -> telaEditar(null));
        fab.setElevation(dp(4));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(WRAP, dp(56), Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, dp(24), dp(28));
        f.addView(fab, fp);
        setContentView(f);
    }

    View cartao(Perfis.Perfil p, boolean ativo) {
        boolean cl = clara(p.cor);
        int tc = cl ? 0xFF2B1600 : Color.WHITE;
        LinearLayout k = col();
        k.setBackground(fundo(p.cor, dp(20)));
        k.setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout top = lin();
        TextView chip = txt(p.rotulo, 13, tSemi, tc);
        chip.setBackground(fundo(cl ? 0x1A000000 : 0x2EFFFFFF, dp(99)));
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        top.addView(chip);
        View esp = new View(this);
        top.addView(esp, new LinearLayout.LayoutParams(0, 1, 1));
        if (ativo) {
            LinearLayout at = lin();
            View bol = new View(this);
            bol.setBackground(fundo(cl ? VERDE : 0xFF7CF29A, dp(4)));
            at.addView(bol, lp(dp(8), dp(8)));
            TextView a = txt("Ativo", 12, tSemi, tc);
            LinearLayout.LayoutParams al = lp(WRAP, WRAP); al.leftMargin = dp(6);
            at.addView(a, al);
            top.addView(at);
        }
        k.addView(top);
        k.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout base = lin();
        base.addView(avatar(p, 52, cl ? 0x1A000000 : 0x38FFFFFF, tc));
        LinearLayout nomes = col();
        TextView n = titulo(p.v("n").isEmpty() ? "Sem nome" : p.v("n"), 20, tc);
        n.setSingleLine(true); n.setEllipsize(TextUtils.TruncateAt.END);
        nomes.addView(n);
        String sub = p.subtitulo();
        if (!sub.isEmpty()) {
            TextView s = txt(sub, 14, tReg, tc);
            s.setAlpha(0.85f); s.setSingleLine(true); s.setEllipsize(TextUtils.TruncateAt.END);
            nomes.addView(s, lpm(WRAP, WRAP, dp(4)));
        }
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, WRAP, 1); nl.leftMargin = dp(14);
        base.addView(nomes, nl);
        k.addView(base);

        k.setOnClickListener(v -> { perfis.ativo = p.id; perfis.salvar(); telaEncostar(p); });
        k.setOnLongClickListener(v -> { telaEditar(p); return true; });
        k.setElevation(dp(1));
        return k;
    }

    // ================= TELA 2: PRONTO PARA ENCOSTAR =================

    Perfis.Perfil atual;
    LinearLayout encRaiz, encBase, encResumo;
    TextView encTit, encTxt, encRot;
    View encVoltar, encEditar;
    Desenho.Anel anel;
    int estado = Desenho.Anel.ESPERA;

    void telaEncostar(Perfis.Perfil p) {
        tela = ENCOSTAR; atual = p;
        perfis.ativo = p.id; perfis.salvar();

        encRaiz = col();
        LinearLayout topo = lin();
        topo.setPadding(dp(8), dp(12), dp(8), 0);
        encVoltar = botaoIcone(Desenho.VOLTAR, FG, this::telaLista, "Voltar");
        topo.addView(encVoltar);
        encRot = txt(p.rotulo, 16, tSemi, FG);
        encRot.setGravity(Gravity.CENTER);
        topo.addView(encRot, new LinearLayout.LayoutParams(0, WRAP, 1));
        encEditar = botaoIcone(Desenho.EDITAR, FG, () -> telaEditar(atual), "Editar perfil");
        topo.addView(encEditar);
        encRaiz.addView(topo);

        LinearLayout meio = col();
        meio.setGravity(Gravity.CENTER);
        meio.setPadding(dp(24), 0, dp(24), 0);
        anel = new Desenho.Anel(this, tTit);
        anel.cor = clara(p.cor) ? FG : p.cor;
        meio.addView(anel, lp(dp(240), dp(240)));
        encTit = titulo("", 28, FG);
        encTit.setGravity(Gravity.CENTER);
        meio.addView(encTit, lpm(WRAP, WRAP, dp(32)));
        encTxt = txt("", 15, tReg, MUTED);
        encTxt.setGravity(Gravity.CENTER); encTxt.setLineSpacing(0, 1.35f);
        meio.addView(encTxt, lpm(WRAP, WRAP, dp(10)));

        encResumo = lin();
        encResumo.setBackground(fundo(CARD, dp(18)));
        encResumo.setPadding(dp(16), dp(14), dp(16), dp(14));
        encResumo.addView(avatar(p, 48, p.cor, clara(p.cor) ? 0xFF2B1600 : Color.WHITE));
        LinearLayout rn = col();
        TextView rnn = txt(p.v("n"), 16, tSemi, FG);
        rnn.setSingleLine(true); rnn.setEllipsize(TextUtils.TruncateAt.END);
        rn.addView(rnn);
        rn.addView(txt(resumoCampos(p), 13, tReg, MUTED), lpm(WRAP, WRAP, dp(4)));
        TextView ok = txt("Funciona em iPhone e Android", 12, tSemi, VERDE);
        rn.addView(ok, lpm(WRAP, WRAP, dp(6)));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, WRAP, 1); rl.leftMargin = dp(14);
        encResumo.addView(rn, rl);
        meio.addView(encResumo, lpm(MATCH, WRAP, dp(32)));
        encRaiz.addView(meio, new LinearLayout.LayoutParams(MATCH, 0, 1));

        encBase = lin();
        encBase.setPadding(dp(24), dp(8), dp(24), dp(32));
        encRaiz.addView(encBase, lp(MATCH, WRAP));

        setContentView(encRaiz);
        Hce.ouvinte = this;
        espera();
    }

    String resumoCampos(Perfis.Perfil p) {
        StringBuilder s = new StringBuilder();
        String[][] r = {{"t", "Telefone"}, {"w", "WhatsApp"}, {"e", "E-mail"}, {"u", "Site"}, {"ig", "Instagram"}, {"li", "LinkedIn"}};
        int n = 0;
        for (String[] x : r) if (!p.v(x[0]).isEmpty()) { if (n++ > 0) s.append(" · "); s.append(x[1]); if (n == 3) break; }
        int resto = 0;
        for (Map.Entry<String, String> e : p.dados.entrySet()) if (!e.getValue().trim().isEmpty() && !e.getKey().equals("n")) resto++;
        if (resto > n && n > 0) s.append(" +").append(resto - n);
        if (!p.foto.isEmpty()) s.append(n > 0 ? " · foto" : "Foto");
        return s.length() == 0 ? "Só o nome" : s.toString();
    }

    void corDaTela(int bg, int fg, int muted, boolean claraBarras) {
        encRaiz.setBackgroundColor(bg);
        barras(bg, claraBarras);
        encTit.setTextColor(fg); encTxt.setTextColor(muted); encRot.setTextColor(fg);
        ((Desenho.Icone) encVoltar).cor = fg; encVoltar.invalidate();
        ((Desenho.Icone) encEditar).cor = fg; encEditar.invalidate();
    }

    void espera() {
        estado = Desenho.Anel.ESPERA;
        corDaTela(BG, FG, MUTED, true);
        anel.set(Desenho.Anel.ESPERA, 0);
        encTit.setText("Pronto para encostar");
        encTxt.setText("Aproxime a traseira deste celular\nda traseira do outro.");
        encResumo.setVisibility(View.VISIBLE);
        encBase.removeAllViews();
        encBase.addView(botao("QR code", Desenho.QR, false, FG, this::mostraQr), new LinearLayout.LayoutParams(0, dp(52), 1));
        View sp = new View(this);
        encBase.addView(sp, lp(dp(12), 1));
        encBase.addView(botao("Enviar link", Desenho.ENVIAR, true, FG, this::enviaLink), new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    int corLendo() { return clara(atual.cor) ? FG : atual.cor; }

    @Override public void comecou() {
        if (tela != ENCOSTAR) return;
        estado = Desenho.Anel.LENDO;
        corDaTela(corLendo(), Color.WHITE, 0xE6FFFFFF, false);
        anel.set(Desenho.Anel.LENDO, 0);
        encTit.setText("Segure…");
        encTxt.setText("Não afaste os celulares.\nVai vibrar quando terminar.");
        encResumo.setVisibility(View.GONE);
        encBase.removeAllViews();
    }

    @Override public void progresso(int lidos, int total) {
        if (tela != ENCOSTAR) return;
        if (estado != Desenho.Anel.LENDO) comecou();
        anel.set(Desenho.Anel.LENDO, total == 0 ? 0 : (float) lidos / total);
    }

    @Override public void terminou() {
        if (tela != ENCOSTAR) return;
        estado = Desenho.Anel.PRONTO;
        vibra(new long[]{0, 220});
        corDaTela(VERDE, Color.WHITE, 0xEBFFFFFF, false);
        anel.set(Desenho.Anel.PRONTO, 1);
        encTit.setText("Pronto!");
        encTxt.setText("Pode afastar. O contato abriu\nno outro celular.");
        encResumo.setVisibility(View.GONE);
        encBase.removeAllViews();
        View b = botao("Concluir", -1, false, Color.WHITE, this::espera);
        b.setBackground(borda(0x00000000, 0x80FFFFFF, 16, false));
        encBase.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    @Override public void afastou(int lidos, int total) {
        if (tela != ENCOSTAR || estado == Desenho.Anel.PRONTO) return;
        estado = Desenho.Anel.CEDO;
        vibra(new long[]{0, 60, 80, 60});
        corDaTela(BG, FG, MUTED, true);
        anel.set(Desenho.Anel.CEDO, total == 0 ? 0 : (float) lidos / total);
        encTit.setText("Afastou cedo");
        encTxt.setText("Encoste de novo e segure\naté vibrar.");
        encResumo.setVisibility(View.GONE);
        encBase.removeAllViews();
        encBase.addView(botao("Tentar de novo", -1, true, FG, this::espera), new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    void vibra(long[] padrao) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(padrao, -1));
        } catch (Exception ignored) { }
    }

    View botao(String texto, int icone, boolean cheio, int cor, Runnable acao) {
        LinearLayout b = lin();
        b.setGravity(Gravity.CENTER);
        int corTxt = cheio ? (cor == FG ? BG : Color.WHITE) : cor;
        b.setBackground(cheio ? fundo(cor, dp(16)) : borda(0x00000000, LINHA, 16, false));
        if (icone >= 0) {
            b.addView(new Desenho.Icone(this, icone, corTxt, dp(18)), lp(dp(20), dp(20)));
            b.addView(new View(this), lp(dp(8), 1));
        }
        b.addView(txt(texto, 15, tSemi, corTxt));
        b.setOnClickListener(v -> acao.run());
        return b;
    }

    void mostraQr() {
        String url = "https://" + atual.linkSemFoto();
        Bitmap bmp;
        try {
            QrCode q = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
            int borda = 3, n = q.size + borda * 2;
            bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++)
                bmp.setPixel(x, y, q.getModule(x - borda, y - borda) ? FG : Color.WHITE);
        } catch (Exception e) {
            Toast.makeText(this, "Dados grandes demais para QR code", Toast.LENGTH_LONG).show();
            return;
        }
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(fundo(CARD, dp(24)));
        c.setPadding(dp(24), dp(24), dp(24), dp(20));
        c.addView(titulo(atual.v("n"), 20, FG));
        ImageView iv = new ImageView(this);
        Bitmap big = Bitmap.createScaledBitmap(bmp, bmp.getWidth() * 12, bmp.getHeight() * 12, false);
        iv.setImageBitmap(big);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        c.addView(iv, lpm(dp(260), dp(260), dp(16)));
        TextView t = txt("Aponte a câmera do outro celular.\nO QR code vai sem a foto.", 14, tReg, MUTED);
        t.setGravity(Gravity.CENTER); t.setLineSpacing(0, 1.3f);
        c.addView(t, lpm(WRAP, WRAP, dp(14)));
        d.setContentView(c);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        d.show();
    }

    void enviaLink() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "https://" + atual.link());
        startActivity(Intent.createChooser(i, "Enviar meu contato"));
    }

    // ================= TELA 3: EDITAR PERFIL =================

    Perfis.Perfil origem, ed;
    final LinkedHashMap<String, EditText> entradas = new LinkedHashMap<>();
    EditText entradaRotulo;
    String focarCampo;

    void telaEditar(Perfis.Perfil p) {
        origem = p;
        try { ed = p == null ? null : Perfis.Perfil.de(p.json()); } catch (Exception e) { ed = null; }
        if (ed == null) {
            ed = new Perfis.Perfil();
            ed.rotulo = perfis.lista.isEmpty() ? "Pessoal" : "Novo perfil";
            ed.cor = Perfis.CORES[perfis.lista.size() % Perfis.CORES.length];
        }
        montaEditar();
    }

    void sincroniza() {
        if (entradaRotulo != null) ed.rotulo = entradaRotulo.getText().toString().trim();
        for (Map.Entry<String, EditText> e : entradas.entrySet()) ed.dados.put(e.getKey(), e.getValue().getText().toString());
    }

    void montaEditar() {
        tela = EDITAR; Hce.ouvinte = null;
        barras(BG, true);
        entradas.clear();
        LinearLayout raiz = col();
        raiz.setBackgroundColor(BG);

        LinearLayout topo = lin();
        topo.setPadding(dp(8), dp(12), dp(16), dp(8));
        topo.addView(botaoIcone(Desenho.FECHAR, FG, this::onBackPressed, "Fechar"));
        TextView tt = titulo(origem == null ? "Novo perfil" : "Editar perfil", 18, FG);
        tt.setGravity(Gravity.CENTER);
        topo.addView(tt, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView salvar = txt("Salvar", 15, tSemi, Color.WHITE);
        salvar.setGravity(Gravity.CENTER);
        salvar.setBackground(fundo(ACCENT, dp(12)));
        salvar.setPadding(dp(16), 0, dp(16), 0);
        salvar.setOnClickListener(v -> salvaPerfil());
        topo.addView(salvar, lp(WRAP, dp(40)));
        raiz.addView(topo);

        ScrollView sv = new ScrollView(this);
        LinearLayout c = col();
        c.setPadding(dp(24), dp(8), dp(24), dp(40));
        sv.addView(c);
        raiz.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1));

        // foto + nome do perfil
        LinearLayout l1 = lin();
        LinearLayout fcol = col();
        fcol.setGravity(Gravity.CENTER_HORIZONTAL);
        View foto;
        Bitmap fb = fotoBitmap(ed.foto);
        if (fb != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(redondo(fb, dp(72)));
            foto = iv;
        } else {
            FrameLayout fr = new FrameLayout(this);
            GradientDrawable g = borda(CARD, LINHA, 36, true);
            g.setShape(GradientDrawable.OVAL);
            fr.setBackground(g);
            fr.addView(new Desenho.Icone(this, Desenho.CAMERA, MUTED, dp(26)), new FrameLayout.LayoutParams(MATCH, MATCH));
            foto = fr;
        }
        foto.setContentDescription("Escolher foto ou logo");
        foto.setOnClickListener(v -> pedeFoto());
        fcol.addView(foto, lp(dp(72), dp(72)));
        TextView fl = txt(fb != null ? "Trocar" : "Foto ou logo", 11, tMed, MUTED);
        fl.setOnClickListener(v -> pedeFoto());
        fcol.addView(fl, lpm(WRAP, WRAP, dp(6)));
        if (fb != null) {
            TextView rm = txt("Remover", 11, tMed, PERIGO);
            rm.setPadding(0, dp(4), 0, dp(4));
            rm.setOnClickListener(v -> { sincroniza(); ed.foto = ""; montaEditar(); });
            fcol.addView(rm);
        }
        l1.addView(fcol);
        LinearLayout nr = col();
        nr.addView(txt("Nome do perfil", 13, tMed, MUTED));
        entradaRotulo = new EditText(this);
        entradaRotulo.setText(ed.rotulo);
        entradaRotulo.setHint("Ex.: Pessoal, Trabalho, Loja");
        entradaRotulo.setSingleLine(true);
        entradaRotulo.setTextSize(16); entradaRotulo.setTypeface(tMed); entradaRotulo.setTextColor(FG);
        entradaRotulo.setBackground(borda(CARD, LINHA, 12, false));
        entradaRotulo.setPadding(dp(14), 0, dp(14), 0);
        nr.addView(entradaRotulo, lpm(MATCH, dp(46), dp(6)));
        LinearLayout.LayoutParams nrl = new LinearLayout.LayoutParams(0, WRAP, 1); nrl.leftMargin = dp(16);
        l1.addView(nr, nrl);
        c.addView(l1);

        // cor
        c.addView(txt("Cor", 13, tMed, MUTED), lpm(WRAP, WRAP, dp(22)));
        LinearLayout cores = lin();
        for (int cor : Perfis.CORES) {
            View s = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL); g.setColor(cor);
            if (cor == ed.cor) g.setStroke(dp(3), BG);
            s.setBackground(g);
            FrameLayout anelSel = new FrameLayout(this);
            if (cor == ed.cor) { GradientDrawable a = new GradientDrawable(); a.setShape(GradientDrawable.OVAL); a.setStroke(dp(2), cor); anelSel.setBackground(a); }
            anelSel.setPadding(dp(3), dp(3), dp(3), dp(3));
            anelSel.addView(s, new FrameLayout.LayoutParams(MATCH, MATCH));
            anelSel.setOnClickListener(v -> { sincroniza(); ed.cor = cor; montaEditar(); });
            LinearLayout.LayoutParams sl = lp(dp(42), dp(42)); sl.rightMargin = dp(8);
            cores.addView(anelSel, sl);
        }
        c.addView(cores, lpm(WRAP, WRAP, dp(8)));

        // grupos
        c.addView(grupo(new String[]{"n", "c", "o"}, false), lpm(MATCH, WRAP, dp(22)));
        c.addView(grupo(new String[]{"t", "w", "e"}, false), lpm(MATCH, WRAP, dp(14)));
        if (!ed.extras.isEmpty()) c.addView(grupo(ed.extras.toArray(new String[0]), true), lpm(MATCH, WRAP, dp(14)));

        View add = botao("Adicionar campo", Desenho.MAIS, false, FG, this::escolheCampo);
        add.setBackground(borda(0x00000000, LINHA, 14, true));
        c.addView(add, lpm(MATCH, dp(50), dp(18)));
        TextView dica = txt("Site, redes sociais, endereço, aniversário…", 12, tReg, MUTED);
        dica.setGravity(Gravity.CENTER);
        c.addView(dica, lpm(MATCH, WRAP, dp(8)));

        TextView tam = txt(tamanhoTexto(), 12, tReg, MUTED);
        tam.setGravity(Gravity.CENTER);
        c.addView(tam, lpm(MATCH, WRAP, dp(20)));

        if (origem != null) {
            View ex = botao("Excluir perfil", Desenho.LIXO, false, PERIGO, this::excluiPerfil);
            ex.setBackground(null);
            c.addView(ex, lpm(MATCH, dp(48), dp(16)));
        }
        setContentView(raiz);

        if (focarCampo != null && entradas.containsKey(focarCampo)) {
            EditText e = entradas.get(focarCampo);
            e.requestFocus();
            e.post(() -> {
                sv.smoothScrollTo(0, Math.max(0, ((View) e.getParent()).getTop() + ((View) e.getParent().getParent()).getTop() - dp(80)));
                android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (im != null) im.showSoftInput(e, 0);
            });
            focarCampo = null;
        }
    }

    String tamanhoTexto() {
        int n = ed.link().length();
        if (ed.foto.isEmpty()) return "Leitura instantânea";
        double s = n / 4100.0; // ~240 B a cada 50 ms, medido no moto g54
        return String.format(java.util.Locale.ROOT, "Com foto: encostar por ~%.1f s", Math.max(0.3, s)).replace('.', ',');
    }

    View grupo(String[] chaves, boolean removiveis) {
        LinearLayout g = col();
        g.setBackground(fundo(CARD, dp(18)));
        g.setPadding(dp(16), dp(2), dp(8), dp(2));
        for (int i = 0; i < chaves.length; i++) {
            Perfis.Campo cp = Perfis.campo(chaves[i]);
            if (cp == null) continue;
            LinearLayout linha = lin();
            LinearLayout cc = col();
            cc.setPadding(0, dp(12), 0, dp(10));
            cc.addView(txt(cp.rotulo, 12, tMed, MUTED));
            EditText e = new EditText(this);
            e.setText(ed.v(cp.chave));
            e.setHint(cp.dica);
            e.setHintTextColor(0xFFA8A69F);
            e.setInputType(cp.teclado);
            if ((cp.teclado & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) e.setSingleLine(true);
            else { e.setMaxLines(4); e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)}); }
            e.setTextSize(16); e.setTypeface(tMed); e.setTextColor(FG);
            e.setBackground(null);
            e.setPadding(0, dp(4), 0, 0);
            cc.addView(e, lp(MATCH, WRAP));
            entradas.put(cp.chave, e);
            linha.addView(cc, new LinearLayout.LayoutParams(0, WRAP, 1));
            if (removiveis) {
                final String k = cp.chave;
                linha.addView(botaoIcone(Desenho.FECHAR, MUTED, () -> { sincroniza(); ed.extras.remove(k); ed.dados.remove(k); montaEditar(); }, "Remover " + cp.rotulo));
            }
            g.addView(linha);
            if (i < chaves.length - 1) {
                View sep = new View(this);
                sep.setBackgroundColor(0xFFE7E4DC);
                LinearLayout.LayoutParams sl = lp(MATCH, dp(1)); sl.rightMargin = dp(8);
                g.addView(sep, sl);
            }
        }
        return g;
    }

    void escolheCampo() {
        sincroniza();
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout c = col();
        GradientDrawable g = new GradientDrawable();
        g.setColor(BG);
        float r = dp(28);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        c.setBackground(g);
        c.setPadding(dp(20), dp(12), dp(20), dp(32));
        View alca = new View(this);
        alca.setBackground(fundo(LINHA, dp(3)));
        LinearLayout.LayoutParams al = lp(dp(40), dp(5)); al.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(alca, al);
        LinearLayout cab = lin();
        cab.addView(titulo("Adicionar campo", 22, FG), new LinearLayout.LayoutParams(0, WRAP, 1));
        cab.addView(botaoIcone(Desenho.FECHAR, FG, d::dismiss, "Fechar"));
        c.addView(cab, lpm(MATCH, WRAP, dp(8)));

        String grupoAtual = "";
        Fluxo fluxo = null;
        for (Perfis.Campo cp : Perfis.EXTRAS) {
            if (ed.extras.contains(cp.chave)) continue;
            if (!cp.grupo.equals(grupoAtual)) {
                grupoAtual = cp.grupo;
                TextView gt = txt(cp.grupo, 12, tSemi, MUTED);
                gt.setLetterSpacing(0.06f);
                c.addView(gt, lpm(WRAP, WRAP, dp(16)));
                fluxo = new Fluxo(this, dp(8));
                c.addView(fluxo, lpm(MATCH, WRAP, dp(8)));
            }
            LinearLayout chip = lin();
            chip.setBackground(borda(CARD, LINHA, 99, false));
            chip.setPadding(dp(12), 0, dp(14), 0);
            chip.addView(new Desenho.Icone(this, Desenho.MAIS, FG, dp(14)), lp(dp(16), dp(16)));
            chip.addView(new View(this), lp(dp(6), 1));
            chip.addView(txt(cp.rotulo, 14, tMed, FG));
            final String k = cp.chave;
            chip.setOnClickListener(v -> { d.dismiss(); ed.extras.add(k); focarCampo = k; montaEditar(); });
            fluxo.addView(chip, new ViewGroup.LayoutParams(WRAP, dp(42)));
        }
        if (fluxo == null) c.addView(txt("Todos os campos já foram adicionados.", 14, tReg, MUTED), lpm(WRAP, WRAP, dp(16)));
        TextView rod = txt("Só os campos preenchidos vão para quem recebe.", 12, tReg, MUTED);
        c.addView(rod, lpm(WRAP, WRAP, dp(18)));
        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(MATCH, WRAP);
        }
        d.show();
    }

    void salvaPerfil() {
        sincroniza();
        if (ed.v("n").isEmpty()) { Toast.makeText(this, "Coloque pelo menos o nome", Toast.LENGTH_SHORT).show(); return; }
        if (ed.rotulo.isEmpty()) ed.rotulo = "Perfil";
        // tira extras vazios
        for (java.util.Iterator<String> it = ed.extras.iterator(); it.hasNext(); ) {
            String k = it.next();
            if (ed.v(k).isEmpty()) { it.remove(); ed.dados.remove(k); }
        }
        boolean achou = false;
        for (int i = 0; i < perfis.lista.size(); i++)
            if (perfis.lista.get(i).id.equals(ed.id)) { perfis.lista.set(i, ed); achou = true; }
        if (!achou) perfis.lista.add(ed);
        perfis.ativo = ed.id;
        perfis.salvar();
        telaEncostar(ed);
    }

    void excluiPerfil() {
        new AlertDialog.Builder(this)
            .setTitle("Excluir \"" + ed.rotulo + "\"?")
            .setMessage("Os dados deste perfil saem do celular.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir", (x, y) -> {
                for (int i = perfis.lista.size() - 1; i >= 0; i--) if (perfis.lista.get(i).id.equals(ed.id)) perfis.lista.remove(i);
                if (ed.id.equals(perfis.ativo)) perfis.ativo = perfis.lista.isEmpty() ? "" : perfis.lista.get(0).id;
                perfis.salvar();
                origem = null;
                telaLista();
            })
            .show();
    }

    // ---------- foto ----------

    void pedeFoto() {
        sincroniza();
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "Escolher foto ou logo"), PEDIR_FOTO); }
        catch (Exception e) { Toast.makeText(this, "Não achei um app de fotos", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PEDIR_FOTO || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            String f = preparaFoto(data.getData());
            if (f == null) { Toast.makeText(this, "Não consegui abrir essa imagem", Toast.LENGTH_SHORT).show(); return; }
            ed.foto = f;
            montaEditar();
        } catch (Exception e) {
            Toast.makeText(this, "Não consegui abrir essa imagem", Toast.LENGTH_SHORT).show();
        }
    }

    /** Recorta no centro, gira conforme EXIF, reduz e comprime até ~6 KB. */
    String preparaFoto(android.net.Uri u) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
        int lado = Math.min(o.outWidth, o.outHeight);
        if (lado <= 0) return null;
        int amostra = 1;
        while (lado / (amostra * 2) >= 400) amostra *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = amostra;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o); }
        if (b == null) return null;
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(u)) {
            android.media.ExifInterface ex = new android.media.ExifInterface(in);
            int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            rot = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
        } catch (Exception ignored) { }
        int s = Math.min(b.getWidth(), b.getHeight());
        Matrix m = new Matrix();
        m.postRotate(rot);
        Bitmap q = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s, m, true);
        for (int px : new int[]{176, 144, 120}) {
            Bitmap r = Bitmap.createScaledBitmap(q, px, px, true);
            for (int qual = 84; qual >= 34; qual -= 6) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                r.compress(Bitmap.CompressFormat.JPEG, qual, bo);
                if (bo.size() <= 6200) return Base64.encodeToString(bo.toByteArray(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            }
        }
        return null;
    }

    /** Layout que quebra linha (para os chips). */
    static class Fluxo extends ViewGroup {
        final int gap;
        Fluxo(Context c, int gap) { super(c); this.gap = gap; }
        @Override protected void onMeasure(int ws, int hs) {
            int max = MeasureSpec.getSize(ws), x = 0, y = 0, linhaH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                measureChild(v, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), hs);
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                if (x > 0 && x + w > max) { x = 0; y += linhaH + gap; linhaH = 0; }
                x += w + gap; linhaH = Math.max(linhaH, h);
            }
            setMeasuredDimension(max, y + linhaH);
        }
        @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
            int max = r - l, x = 0, y = 0, linhaH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                if (x > 0 && x + w > max) { x = 0; y += linhaH + gap; linhaH = 0; }
                v.layout(x, y, x + w, y + h);
                x += w + gap; linhaH = Math.max(linhaH, h);
            }
        }
    }
}
