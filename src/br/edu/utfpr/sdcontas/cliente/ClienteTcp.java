package br.edu.utfpr.sdcontas.cliente;

import br.edu.utfpr.sdcontas.protocolo.Json;
import br.edu.utfpr.sdcontas.protocolo.JsonInvalidoException;
import br.edu.utfpr.sdcontas.protocolo.LogSeguro;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import br.edu.utfpr.sdcontas.protocolo.Transporte;
import br.edu.utfpr.sdcontas.protocolo.Transporte.MensagemGrandeException;
import br.edu.utfpr.sdcontas.protocolo.Transporte.MensagemIncompletaException;
import br.edu.utfpr.sdcontas.protocolo.Transporte.MensagemRecebida;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Envia uma requisição e espera a resposta, abrindo uma conexão TCP nova a cada
 * chamada e fechando-a no fim (requisito da especificação).
 */
public final class ClienteTcp {

    public static final int TEMPO_CONEXAO_MS = 3_000;
    public static final int TEMPO_RESPOSTA_MS = 8_000;

    private volatile String host;
    private volatile int porta;
    private volatile Consumer<String> observador = linha -> { };

    public ClienteTcp(String host, int porta) {
        configurar(host, porta);
    }

    public void configurar(String host, int porta) {
        this.host = host.strip();
        this.porta = porta;
    }

    public String getHost() {
        return host;
    }

    public int getPorta() {
        return porta;
    }

    /** Recebe cada JSON enviado ("→ ...") e recebido ("← ..."), já com senhas mascaradas. */
    public void setObservador(Consumer<String> observador) {
        this.observador = observador == null ? linha -> { } : observador;
    }

    public Resposta enviar(String metodo, Map<String, Object> dados) throws FalhaComunicacao {
        Map<String, Object> requisicao = new LinkedHashMap<>();
        requisicao.put("method", metodo);
        requisicao.put("data", dados);
        String json = Json.escrever(requisicao);
        String destino = host + ":" + porta;

        observador.accept("→ " + destino + "  " + LogSeguro.mascararSenhas(json));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, porta), TEMPO_CONEXAO_MS);
            socket.setSoTimeout(TEMPO_RESPOSTA_MS);
            Transporte.escreverMensagem(new BufferedOutputStream(socket.getOutputStream()), json,
                    StandardCharsets.UTF_8);

            InputStream entrada = new BufferedInputStream(socket.getInputStream());
            MensagemRecebida mensagem = Transporte.lerMensagem(entrada, socket, TEMPO_RESPOSTA_MS);
            if (mensagem == null) {
                throw falha("O servidor fechou a conexão sem responder.", null);
            }
            Transporte.descartarPendentes(entrada);
            observador.accept("← " + destino + "  " + LogSeguro.mascararSenhas(mensagem.texto()));
            try {
                return Resposta.deJson(mensagem.texto());
            } catch (JsonInvalidoException e) {
                throw falha("Resposta inválida do servidor: " + e.getMessage(), e);
            }
        } catch (UnknownHostException e) {
            throw falha("Endereço desconhecido: " + host, e);
        } catch (ConnectException e) {
            throw falha("Conexão recusada em " + destino + ". O servidor está ligado nessa porta?", e);
        } catch (MensagemIncompletaException e) {
            throw falha("O servidor começou a responder mas não terminou a mensagem: " + e.getParcial(), e);
        } catch (MensagemGrandeException e) {
            throw falha("Resposta do servidor grande demais.", e);
        } catch (SocketTimeoutException e) {
            throw falha("Sem resposta de " + destino + " (tempo esgotado).", e);
        } catch (IOException e) {
            throw falha("Falha de comunicação com " + destino + ": " + e.getMessage(), e);
        }
    }

    private FalhaComunicacao falha(String mensagem, Throwable causa) {
        observador.accept("✖ " + mensagem);
        return new FalhaComunicacao(mensagem, causa);
    }
}
