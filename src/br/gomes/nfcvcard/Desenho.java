package br.gomes.nfcvcard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Ícones de traço (grade 24×24) e o círculo da tela de encostar. */
public class Desenho {

    static final int ONDAS = 0, VOLTAR = 1, EDITAR = 2, FECHAR = 3, MAIS = 4, QR = 5, ENVIAR = 6,
        CAMERA = 7, CHECK = 8, REFAZER = 9, LIXO = 10;

    /** Desenha o ícone `tipo` centrado em (cx, cy), com lado `lado` px. */
    static void icone(Canvas c, int tipo, float cx, float cy, float lado, int cor, float espessura) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(cor);
        float s = lado / 24f;
        p.setStrokeWidth(espessura);
        c.save();
        c.translate(cx - lado / 2, cy - lado / 2);
        c.scale(s, s);
        Path h = new Path();
        switch (tipo) {
            case ONDAS:
                for (float r : new float[]{3.5f, 8f, 12.5f}) {
                    RectF o = new RectF(4 - r, 12 - r, 4 + r, 12 + r);
                    h.addArc(o, -48, 96);
                }
                break;
            case VOLTAR: h.moveTo(15, 18); h.lineTo(9, 12); h.lineTo(15, 6); break;
            case EDITAR:
                h.moveTo(4, 20); h.lineTo(5, 16); h.lineTo(16.5f, 4.5f); h.lineTo(19.5f, 7.5f); h.lineTo(8, 19); h.close();
                h.moveTo(13, 20); h.lineTo(21, 20);
                break;
            case FECHAR: h.moveTo(6, 6); h.lineTo(18, 18); h.moveTo(18, 6); h.lineTo(6, 18); break;
            case MAIS: h.moveTo(12, 5); h.lineTo(12, 19); h.moveTo(5, 12); h.lineTo(19, 12); break;
            case QR:
                h.addRect(3, 3, 10, 10, Path.Direction.CW); h.addRect(14, 3, 21, 10, Path.Direction.CW);
                h.addRect(3, 14, 10, 21, Path.Direction.CW); h.addRect(14, 14, 17, 17, Path.Direction.CW);
                h.moveTo(21, 14); h.lineTo(21, 14.1f); h.moveTo(14, 21); h.lineTo(14, 21.1f);
                h.moveTo(17, 21); h.lineTo(21, 21); h.lineTo(21, 18);
                break;
            case ENVIAR:
                h.moveTo(4, 12); h.lineTo(4, 20); h.lineTo(20, 20); h.lineTo(20, 12);
                h.moveTo(12, 3); h.lineTo(12, 15); h.moveTo(7, 8); h.lineTo(12, 3); h.lineTo(17, 8);
                break;
            case CAMERA:
                h.moveTo(3, 8); h.lineTo(6.5f, 8); h.lineTo(8.5f, 5); h.lineTo(15.5f, 5); h.lineTo(17.5f, 8);
                h.lineTo(21, 8); h.lineTo(21, 19); h.lineTo(3, 19); h.close();
                h.addCircle(12, 13, 3.5f, Path.Direction.CW);
                break;
            case CHECK: h.moveTo(5, 12.5f); h.lineTo(9.5f, 17); h.lineTo(19, 7.5f); break;
            case REFAZER:
                h.addArc(new RectF(3, 3, 21, 21), 200, 290);
                h.moveTo(3, 4); h.lineTo(3.6f, 9.4f); h.lineTo(9, 8.6f);
                break;
            case LIXO:
                h.moveTo(4, 7); h.lineTo(20, 7); h.moveTo(9, 7); h.lineTo(9, 4); h.lineTo(15, 4); h.lineTo(15, 7);
                h.moveTo(6, 7); h.lineTo(7, 20); h.lineTo(17, 20); h.lineTo(18, 7);
                break;
        }
        c.drawPath(h, p);
        c.restore();
    }

    /** View de um ícone só. */
    static class Icone extends View {
        int tipo, cor; float lado, esp = 2f;
        Icone(Context c, int tipo, int cor, float ladoPx) { super(c); this.tipo = tipo; this.cor = cor; this.lado = ladoPx; }
        @Override protected void onDraw(Canvas c) { icone(c, tipo, getWidth() / 2f, getHeight() / 2f, lado, cor, esp); }
    }

    /** O círculo grande: espera (pulsando), lendo (progresso), pronto (check), cedo (parou). */
    static class Anel extends View {
        static final int ESPERA = 0, LENDO = 1, PRONTO = 2, CEDO = 3;
        int estado = ESPERA, cor = 0xFF1D3FD8, corCedo = 0xFFC2410C, verde = 0xFF15803D;
        float progresso = 0, pulso = 0;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        final ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
        Typeface fonte;

        Anel(Context c, Typeface fonte) {
            super(c);
            this.fonte = fonte;
            anim.setDuration(1600); anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> { pulso = (float) a.getAnimatedValue(); if (estado == ESPERA) invalidate(); });
            anim.start();
        }

        void set(int estado, float progresso) { this.estado = estado; this.progresso = progresso; invalidate(); }

        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); anim.cancel(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, R = Math.min(w, h) / 2;
            float d = getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.FILL); p.setAlpha(255);
            switch (estado) {
                case ESPERA: {
                    // ondas saindo do centro
                    for (int i = 0; i < 2; i++) {
                        float f = (pulso + i * 0.5f) % 1f;
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * d);
                        p.setColor(cor); p.setAlpha((int) (90 * (1 - f)));
                        c.drawCircle(cx, cy, R * (0.45f + 0.55f * f), p);
                    }
                    p.setStyle(Paint.Style.FILL); p.setColor(cor); p.setAlpha(255);
                    c.drawCircle(cx, cy, R * 0.45f, p);
                    icone(c, ONDAS, cx + R * 0.05f, cy, R * 0.42f, Color.WHITE, 2.2f);
                    break;
                }
                case LENDO: case CEDO: {
                    boolean cedo = estado == CEDO;
                    float esp = 14 * d, r = R - esp;
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(esp); p.setStrokeCap(Paint.Cap.ROUND);
                    p.setColor(cedo ? 0xFFE7E3D9 : 0x38FFFFFF);
                    c.drawCircle(cx, cy, r, p);
                    p.setColor(cedo ? corCedo : Color.WHITE);
                    c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), -90, 360 * Math.max(0.01f, progresso), false, p);
                    if (cedo) icone(c, REFAZER, cx, cy, R * 0.5f, corCedo, 2f);
                    else {
                        t.setColor(Color.WHITE); t.setTextAlign(Paint.Align.CENTER); t.setTypeface(fonte);
                        t.setTextSize(R * 0.5f);
                        String s = Math.round(progresso * 100) + "%";
                        Rect b = new Rect(); t.getTextBounds(s, 0, s.length(), b);
                        c.drawText(s, cx, cy + b.height() / 2f, t);
                    }
                    break;
                }
                case PRONTO: {
                    p.setColor(0x2EFFFFFF); c.drawCircle(cx, cy, R, p);
                    p.setColor(Color.WHITE); c.drawCircle(cx, cy, R * 0.67f, p);
                    icone(c, CHECK, cx, cy, R * 0.75f, verde, 2.6f);
                    break;
                }
            }
        }
    }
}
