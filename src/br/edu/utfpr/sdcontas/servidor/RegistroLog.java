package br.edu.utfpr.sdcontas.servidor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Log do servidor no formato pedido pela especificação ({@code [SERVIDOR]} para
 * requisição/resposta e {@code [CLIENTE]} para login/logout), com horário.
 * Escreve no console e avisa quem estiver ouvindo (a janela do servidor).
 */
public final class RegistroLog {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<Consumer<String>> ouvintes = new CopyOnWriteArrayList<>();
    private final boolean console;

    public RegistroLog() {
        this(true);
    }

    public RegistroLog(boolean console) {
        this.console = console;
    }

    public void adicionarOuvinte(Consumer<String> ouvinte) {
        ouvintes.add(ouvinte);
    }

    public void servidor(String mensagem) {
        registrar("[SERVIDOR] " + mensagem);
    }

    public void cliente(String mensagem) {
        registrar("[CLIENTE] " + mensagem);
    }

    public void info(String mensagem) {
        registrar(mensagem);
    }

    public void erro(String mensagem) {
        registrar("[ERRO] " + mensagem);
    }

    private void registrar(String mensagem) {
        String linha = "[" + LocalTime.now().format(HORA) + "] " + mensagem;
        if (console) {
            System.out.println(linha);
        }
        for (Consumer<String> ouvinte : ouvintes) {
            ouvinte.accept(linha);
        }
    }
}
