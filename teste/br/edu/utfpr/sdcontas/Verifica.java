package br.edu.utfpr.sdcontas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mini biblioteca de asserções, para os testes rodarem só com {@code java} (sem JUnit). */
public final class Verifica {

    private int passou;
    private final List<String> falhas = new ArrayList<>();
    private String grupo = "";

    public void grupo(String nome) {
        grupo = nome;
        System.out.println("\n== " + nome);
    }

    public void igual(String descricao, Object esperado, Object obtido) {
        registrar(descricao, Objects.equals(esperado, obtido), "esperado <" + esperado + "> obtido <" + obtido + ">");
    }

    public void verdadeiro(String descricao, boolean condicao) {
        registrar(descricao, condicao, "condição falsa");
    }

    public void falso(String descricao, boolean condicao) {
        registrar(descricao, !condicao, "condição verdadeira");
    }

    public void lanca(String descricao, Class<? extends Throwable> tipo, Executavel acao) {
        try {
            acao.executar();
            registrar(descricao, false, "não lançou " + tipo.getSimpleName());
        } catch (Throwable t) {
            registrar(descricao, tipo.isInstance(t), "lançou " + t);
        }
    }

    private void registrar(String descricao, boolean ok, String detalhe) {
        if (ok) {
            passou++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add("[" + grupo + "] " + descricao + ": " + detalhe);
            System.out.println("  FALHA " + descricao + " → " + detalhe);
        }
    }

    public boolean resumo() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Passaram: " + passou + "   Falharam: " + falhas.size());
        falhas.forEach(f -> System.out.println("  ✖ " + f));
        return falhas.isEmpty();
    }

    @FunctionalInterface
    public interface Executavel {
        void executar() throws Exception;
    }
}
