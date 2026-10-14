package br.edu.utfpr.sdcontas.servidor;

import br.edu.utfpr.sdcontas.protocolo.LogSeguro;
import br.edu.utfpr.sdcontas.protocolo.Protocolo;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import br.edu.utfpr.sdcontas.protocolo.Transporte;
import br.edu.utfpr.sdcontas.protocolo.Transporte.MensagemGrandeException;
import br.edu.utfpr.sdcontas.protocolo.Transporte.MensagemIncompletaException;
import br.edu.utfpr.sdcontas.protocolo.Transporte.MensagemRecebida;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Atende UMA conexão: lê uma requisição, responde e encerra (requisito "uma conexão
 * TCP por requisição, encerrada pelos dois lados").
 */
final class Atendimento implements Runnable {

    private final Socket socket;
    private final Despachante despachante;
    private final RegistroLog log;
    private final int tempoLimiteMs;

    Atendimento(Socket socket, Despachante despachante, RegistroLog log, int tempoLimiteMs) {
        this.socket = socket;
        this.despachante = despachante;
        this.log = log;
        this.tempoLimiteMs = tempoLimiteMs;
    }

    @Override
    public void run() {
        String origem = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        log.info("Cliente conectado: " + origem);
        try (Socket s = socket) {
            s.setSoTimeout(tempoLimiteMs);
            InputStream entrada = new BufferedInputStream(s.getInputStream());
            OutputStream saida = new BufferedOutputStream(s.getOutputStream());

            Resposta resposta;
            Charset charset = StandardCharsets.UTF_8;
            try {
                MensagemRecebida mensagem = Transporte.lerMensagem(entrada, s, tempoLimiteMs);
                if (mensagem == null) {
                    log.info("Cliente " + origem + " fechou a conexão sem enviar nada");
                    return;
                }
                charset = mensagem.charset();
                String aviso = charset.equals(StandardCharsets.UTF_8) ? "" : "  (recebida em " + charset + ")";
                log.servidor("Requisição recebida: " + LogSeguro.mascararSenhas(mensagem.texto()) + aviso);
                resposta = despachante.processar(mensagem.texto());
            } catch (MensagemGrandeException e) {
                log.erro("Requisição de " + origem + " passou do limite de tamanho");
                resposta = Resposta.erro(Protocolo.REQUISICAO_INVALIDA, Protocolo.MSG_MENSAGEM_GRANDE);
            } catch (MensagemIncompletaException e) {
                log.servidor("Requisição incompleta (tempo esgotado): " + LogSeguro.mascararSenhas(e.getParcial()));
                resposta = Resposta.erro(Protocolo.REQUISICAO_INVALIDA, Protocolo.MSG_JSON_MALFORMADO);
            } catch (SocketTimeoutException e) {
                log.info("Cliente " + origem + " não enviou nada em " + tempoLimiteMs / 1000 + " s; conexão encerrada");
                return;
            }

            String json = resposta.paraJson();
            // Responde no mesmo charset em que a requisição chegou (UTF-8 quase sempre).
            Transporte.escreverMensagem(saida, json, charset);
            log.servidor("Resposta enviada: " + json);
            encerrarComCalma(s, entrada);
        } catch (IOException e) {
            log.erro("Falha de comunicação com " + origem + ": " + e.getMessage());
        } finally {
            log.info("Cliente desconectado: " + origem);
        }
    }

    /**
     * Fecha a escrita (o cliente recebe fim de fluxo) e consome o que sobrou na entrada
     * antes de fechar. Fechar com bytes não lidos faz o sistema mandar RST, e alguns
     * clientes perdem a resposta por causa disso.
     */
    private static void encerrarComCalma(Socket s, InputStream entrada) {
        try {
            s.shutdownOutput();
            s.setSoTimeout(500);
            long limite = System.nanoTime() + 1_000_000_000L;
            byte[] lixo = new byte[1024];
            while (System.nanoTime() < limite && entrada.read(lixo) != -1) {
                // descartando até o cliente fechar
            }
        } catch (IOException ignorada) {
            // tempo esgotado ou cliente já fechou: tanto faz, vamos fechar
        }
    }
}
