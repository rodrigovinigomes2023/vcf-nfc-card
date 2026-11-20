package br.gomes.nfcvcard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Perfis guardados no aparelho (SharedPreferences, JSON). Nada sai daqui a não ser pelo link. */
public class Perfis {
    static final String SITE = "cartao-nfc-nine.vercel.app/v#";

    /** Um campo: chave interna (= chave no link), rótulo, dica e tipo de teclado. */
    static class Campo {
        final String chave, rotulo, dica, grupo; final int teclado;
        Campo(String chave, String rotulo, String dica, String grupo, int teclado) {
            this.chave = chave; this.rotulo = rotulo; this.dica = dica; this.grupo = grupo; this.teclado = teclado;
        }
    }

    static final int TXT = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    static final int TEL = android.text.InputType.TYPE_CLASS_PHONE;
    static final int MAIL = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    static final int URL = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI;
    static final int LIVRE = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    static final int DATA = android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_DATE;

    /** Sempre visíveis. */
    static final Campo[] FIXOS = {
        new Campo("n", "Nome", "Seu nome completo", "BÁSICO", TXT),
        new Campo("c", "Cargo", "Ex.: Desenvolvedor", "BÁSICO", TXT),
        new Campo("o", "Empresa", "Nome da empresa", "BÁSICO", TXT),
        new Campo("t", "Telefone", "(48) 9 0000-0000", "CONTATO", TEL),
        new Campo("w", "WhatsApp", "(48) 9 0000-0000", "CONTATO", TEL),
        new Campo("e", "E-mail", "voce@exemplo.com", "CONTATO", MAIL),
    };

    /** Entram por "Adicionar campo". */
    static final Campo[] EXTRAS = {
        new Campo("a", "Apelido", "Como te chamam", "BÁSICO", TXT),
        new Campo("t2", "Outro telefone", "(48) 9 0000-0000", "CONTATO", TEL),
        new Campo("tf", "Telefone fixo", "(48) 3000-0000", "CONTATO", TEL),
        new Campo("e2", "E-mail do trabalho", "voce@empresa.com", "CONTATO", MAIL),
        new Campo("u", "Site", "seusite.com.br", "ONLINE", URL),
        new Campo("ig", "Instagram", "@usuario", "ONLINE", URL),
        new Campo("li", "LinkedIn", "linkedin.com/in/usuario", "ONLINE", URL),
        new Campo("tk", "TikTok", "@usuario", "ONLINE", URL),
        new Campo("yt", "YouTube", "@canal", "ONLINE", URL),
        new Campo("gh", "GitHub", "usuario", "ONLINE", URL),
        new Campo("fb", "Facebook", "usuario", "ONLINE", URL),
        new Campo("end", "Endereço", "Rua, nº, bairro, cidade", "OUTROS", TXT),
        new Campo("bd", "Aniversário", "dd/mm/aaaa", "OUTROS", DATA),
        new Campo("obs", "Observação", "Ex.: atendo de seg a sex, 9h–18h", "OUTROS", LIVRE),
    };

    static Campo campo(String chave) {
        for (Campo c : FIXOS) if (c.chave.equals(chave)) return c;
        for (Campo c : EXTRAS) if (c.chave.equals(chave)) return c;
        return null;
    }

    static final int[] CORES = {0xFF1D3FD8, 0xFF1F2430, 0xFF0F766E, 0xFF7C3AED, 0xFFFFB547};

    static class Perfil {
        String id = Long.toString(System.currentTimeMillis(), 36);
        String rotulo = "Pessoal";
        int cor = CORES[0];
        String foto = "";                          // JPEG em base64url, sem "="
        LinkedHashMap<String, String> dados = new LinkedHashMap<>();
        List<String> extras = new ArrayList<>();   // chaves extras que aparecem na edição

        String v(String k) { String s = dados.get(k); return s == null ? "" : s.trim(); }

        String iniciais() {
            String[] p = v("n").split("\\s+");
            String r = "";
            if (p.length > 0 && !p[0].isEmpty()) r += p[0].substring(0, 1);
            if (p.length > 1 && !p[p.length - 1].isEmpty()) r += p[p.length - 1].substring(0, 1);
            return r.isEmpty() ? "?" : r.toUpperCase();
        }

        String subtitulo() {
            String c = v("c"), o = v("o");
            if (!c.isEmpty() && !o.isEmpty()) return c + " · " + o;
            return c.isEmpty() ? o : c;
        }

        /** O link que vai pelo NFC: página fixa + dados depois do #. */
        String link() {
            StringBuilder s = new StringBuilder(SITE);
            for (Map.Entry<String, String> e : dados.entrySet()) {
                String x = e.getValue() == null ? "" : e.getValue().trim();
                if (x.isEmpty()) continue;
                s.append(e.getKey()).append('=').append(cod(x)).append('&');
            }
            s.append("k=").append(String.format("%06X", cor & 0xFFFFFF));
            if (!foto.isEmpty()) s.append("&f=").append(foto);
            return s.toString();
        }

        /** Mesmo link sem a foto (para QR code, que não comporta a foto). */
        String linkSemFoto() {
            String l = link();
            int i = l.indexOf("&f=");
            return i < 0 ? l : l.substring(0, i);
        }

        JSONObject json() throws Exception {
            JSONObject j = new JSONObject();
            j.put("id", id); j.put("rotulo", rotulo); j.put("cor", cor); j.put("foto", foto);
            JSONObject d = new JSONObject();
            for (Map.Entry<String, String> e : dados.entrySet()) d.put(e.getKey(), e.getValue());
            j.put("dados", d);
            j.put("extras", new JSONArray(extras));
            return j;
        }

        static Perfil de(JSONObject j) throws Exception {
            Perfil p = new Perfil();
            p.id = j.getString("id"); p.rotulo = j.optString("rotulo", "Perfil");
            p.cor = j.optInt("cor", CORES[0]); p.foto = j.optString("foto", "");
            JSONObject d = j.optJSONObject("dados");
            if (d != null) {
                java.util.Iterator<String> it = d.keys();
                while (it.hasNext()) { String k = it.next(); p.dados.put(k, d.optString(k, "")); }
            }
            JSONArray x = j.optJSONArray("extras");
            if (x != null) for (int i = 0; i < x.length(); i++) p.extras.add(x.getString(i));
            return p;
        }
    }

    static String cod(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    // ---------- guardar / carregar ----------

    final List<Perfil> lista = new ArrayList<>();
    String ativo = "";
    private final SharedPreferences sp;

    Perfis(Context c) {
        sp = c.getSharedPreferences("v", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(sp.getString("perfis", "[]"));
            for (int i = 0; i < a.length(); i++) lista.add(Perfil.de(a.getJSONObject(i)));
        } catch (Exception e) { lista.clear(); }
        ativo = sp.getString("ativo", "");
        if (lista.isEmpty()) migraAntigo();
    }

    /** Traz os dados da versão 1 do app (um cartão só) para um perfil "Pessoal". */
    private void migraAntigo() {
        String nome = sp.getString("nome", "");
        if (nome.isEmpty()) return;
        Perfil p = new Perfil();
        p.dados.put("n", nome);
        p.dados.put("t", sp.getString("telefone", ""));
        p.dados.put("e", sp.getString("email", ""));
        p.dados.put("o", sp.getString("empresa", ""));
        String site = sp.getString("site", "");
        if (!site.isEmpty()) { p.dados.put("u", site); p.extras.add("u"); }
        lista.add(p);
        ativo = p.id;
        salvar();
    }

    Perfil ativo() {
        for (Perfil p : lista) if (p.id.equals(ativo)) return p;
        return lista.isEmpty() ? null : lista.get(0);
    }

    Perfil busca(String id) {
        for (Perfil p : lista) if (p.id.equals(id)) return p;
        return null;
    }

    void salvar() {
        try {
            JSONArray a = new JSONArray();
            for (Perfil p : lista) a.put(p.json());
            sp.edit().putString("perfis", a.toString()).putString("ativo", ativo).apply();
        } catch (Exception ignored) { }
    }
}
