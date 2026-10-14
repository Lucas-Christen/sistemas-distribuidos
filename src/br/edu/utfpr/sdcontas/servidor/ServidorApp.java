package br.edu.utfpr.sdcontas.servidor;

import br.edu.utfpr.sdcontas.protocolo.Protocolo;
import br.edu.utfpr.sdcontas.servidor.ui.JanelaServidor;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Ponto de entrada do servidor.
 *
 * <pre>
 *   java -jar servidor.jar                  abre a janela
 *   java -jar servidor.jar --console        pergunta a porta no terminal
 *   java -jar servidor.jar --console 21500  sobe direto na porta 21500
 *   --dados pasta                           onde fica usuarios.json (padrão: ./dados)
 * </pre>
 */
public final class ServidorApp {

    private ServidorApp() {
    }

    public static void main(String[] args) throws IOException {
        boolean console = GraphicsEnvironment.isHeadless();
        Integer porta = null;
        Path pastaDados = Path.of("dados");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--console" -> console = true;
                case "--dados" -> pastaDados = Path.of(args[++i]);
                default -> porta = lerPorta(args[i]);
            }
        }
        Path arquivo = pastaDados.resolve("usuarios.json");

        if (!console) {
            int portaInicial = porta == null ? Protocolo.PORTA_PADRAO : porta;
            SwingUtilities.invokeLater(() -> {
                JanelaServidor.aplicarVisual();
                new JanelaServidor(portaInicial, arquivo).setVisible(true);
            });
            return;
        }

        if (porta == null) {
            porta = perguntarPorta();
        }
        ServidorContas servidor = new ServidorContas(porta, arquivo, new RegistroLog());
        servidor.iniciar();
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar));
        System.out.println("Pressione Ctrl+C para encerrar.");
    }

    private static int perguntarPorta() throws IOException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Qual porta o servidor deve usar? [" + Protocolo.PORTA_PADRAO + "] ");
        String linha = teclado.readLine();
        if (linha == null || linha.isBlank()) {
            return Protocolo.PORTA_PADRAO;
        }
        return lerPorta(linha.strip());
    }

    private static int lerPorta(String texto) {
        try {
            int porta = Integer.parseInt(texto);
            if (porta >= 0 && porta <= 65535) {
                return porta;
            }
        } catch (NumberFormatException ignorada) {
            // mensagem abaixo
        }
        System.err.println("Porta inválida: '" + texto + "'. Use um número entre 1 e 65535.");
        System.exit(2);
        return -1;
    }
}
