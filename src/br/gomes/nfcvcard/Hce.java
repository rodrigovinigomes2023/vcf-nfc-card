package br.gomes.nfcvcard;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Emula uma tag NFC Forum Type 4 com um registro NDEF URI (link do perfil ativo). */
public class Hce extends HostApduService {
    static final byte[] OK = {(byte) 0x90, 0x00};
    static final byte[] ERRO = {0x6A, (byte) 0x82};
    static final byte[] AID = {(byte) 0xD2, 0x76, 0x00, 0x00, (byte) 0x85, 0x01, 0x01};

    /** Quem quer saber do andamento da leitura (a tela "Pronto para encostar"). */
    interface Ouvinte {
        void comecou();
        void progresso(int lidos, int total);
        void terminou();
        void afastou(int lidos, int total);
    }
    static volatile Ouvinte ouvinte;
    static final Handler principal = new Handler(Looper.getMainLooper());

    int arquivo = 0; // 1 = CC, 2 = NDEF
    byte[] ndef;
    int maiorLido = 0;
    boolean completo = false, comecou = false;

    byte[] cc() {
        int max = ndef.length;
        return new byte[]{0x00, 0x0F, 0x20, 0x00, (byte) 0xF0, 0x00, (byte) 0xF0,
            0x04, 0x06, (byte) 0xE1, 0x04, (byte) (max >> 8), (byte) max, 0x00, (byte) 0xFF};
    }

    byte[] montaNdef() {
        Perfis.Perfil p = new Perfis(this).ativo();
        String link = p == null ? "cartao-nfc-nine.vercel.app/v" : p.link();
        byte[] uri = link.getBytes(StandardCharsets.UTF_8);
        int plen = 1 + uri.length;
        int rec = 1 + 1 + 4 + 1 + plen;
        byte[] f = new byte[2 + rec];
        f[0] = (byte) (rec >> 8); f[1] = (byte) rec;
        int i = 2;
        f[i++] = (byte) 0xC1;            // MB | ME | TNF=well-known, registro longo
        f[i++] = 1;
        f[i++] = (byte) (plen >> 24); f[i++] = (byte) (plen >> 16);
        f[i++] = (byte) (plen >> 8);  f[i++] = (byte) plen;
        f[i++] = 'U';
        f[i++] = 0x04;                   // prefixo "https://"
        System.arraycopy(uri, 0, f, i, uri.length);
        return f;
    }

    static void avisa(Runnable r) { if (ouvinte != null) principal.post(r); }

    static final byte[] TAMANHO_ERRADO = {0x67, 0x00};
    static final byte[] FALHA = {0x6F, 0x00};

    @Override public byte[] processCommandApdu(byte[] c, Bundle extras) {
        // Revisão v2.0 (A1): nenhum comando malformado pode derrubar o app.
        try { return responde(c); } catch (Throwable t) { return FALHA; }
    }

    byte[] responde(byte[] c) {
        if (c == null || c.length < 4) return ERRO;
        int ins = c[1] & 0xFF;
        if (ins == 0xA4) { // SELECT
            // Revisão v2.0 (A2): só entrega o cartão com a tela "Pronto para encostar" aberta.
            if (ouvinte == null) { ndef = null; arquivo = 0; return ERRO; }
            int lc = c.length > 4 ? c[4] & 0xFF : 0;
            if (c.length < 5 + lc) return TAMANHO_ERRADO;
            byte[] d = Arrays.copyOfRange(c, 5, 5 + lc);
            if (c[2] == 0x04 && Arrays.equals(d, AID)) {
                if (ndef == null) ndef = montaNdef();
                arquivo = 0;
                return OK;
            }
            if (d.length == 2 && d[0] == (byte) 0xE1 && d[1] == 0x03) { arquivo = 1; return OK; }
            if (d.length == 2 && d[0] == (byte) 0xE1 && d[1] == 0x04) { arquivo = 2; return OK; }
            return ERRO;
        }
        if (ins == 0xB0 && arquivo != 0 && ndef != null) { // READ BINARY
            byte[] f = arquivo == 1 ? cc() : ndef;
            int off = ((c[2] & 0xFF) << 8) | (c[3] & 0xFF);
            int le = c.length > 4 ? c[4] & 0xFF : 0;
            if (le == 0) le = 256;
            if (off > f.length) return new byte[]{0x6B, 0x00};
            int n = Math.min(le, f.length - off);
            byte[] r = new byte[n + 2];
            System.arraycopy(f, off, r, 0, n);
            r[n] = (byte) 0x90; r[n + 1] = 0x00;
            if (arquivo == 2) {
                final int total = f.length;
                if (!comecou) { comecou = true; avisa(() -> { if (ouvinte != null) ouvinte.comecou(); }); }
                if (off + n > maiorLido) {
                    maiorLido = off + n;
                    final int lidos = maiorLido;
                    avisa(() -> { if (ouvinte != null) ouvinte.progresso(lidos, total); });
                }
                if (maiorLido >= total && !completo) {
                    completo = true;
                    avisa(() -> { if (ouvinte != null) ouvinte.terminou(); });
                }
            }
            return r;
        }
        return new byte[]{0x6D, 0x00};
    }

    @Override public void onDeactivated(int reason) {
        if (comecou && !completo && ndef != null) {
            final int lidos = maiorLido, total = ndef.length;
            avisa(() -> { if (ouvinte != null) ouvinte.afastou(lidos, total); });
        }
        arquivo = 0; ndef = null; maiorLido = 0; completo = false; comecou = false;
    }
}
