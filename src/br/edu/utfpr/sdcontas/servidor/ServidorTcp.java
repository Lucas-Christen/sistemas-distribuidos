package br.edu.utfpr.sdcontas.servidor;

import br.edu.utfpr.sdcontas.protocolo.Protocolo;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import br.edu.utfpr.sdcontas.protocolo.Transporte;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Socket servidor: uma thread só aceita conexões e entrega cada uma a um pool
 * de threads com limite. Se o pool lotar, o cliente recebe 500 na hora em vez
 * de ficar esperando.
 */
public final class ServidorTcp {

    public static final int TEMPO_LIMITE_PADRAO_MS = 30_000; // folga para testes digitando no telnet/nc
    private static final int THREADS_MIN = 4;
    private static final int THREADS_MAX = 64;

    private final int portaPedida;
    private final Despachante despachante;
    private final RegistroLog log;
    private final int tempoLimiteMs;

    private volatile ServerSocket serverSocket;
    private ThreadPoolExecutor pool;
    private Thread aceitador;

    public ServidorTcp(int porta, Despachante despachante, RegistroLog log, int tempoLimiteMs) {
        this.portaPedida = porta;
        this.despachante = despachante;
        this.log = log;
        this.tempoLimiteMs = tempoLimiteMs;
    }

    public synchronized void iniciar() throws IOException {
        if (serverSocket != null) {
            return;
        }
        serverSocket = new ServerSocket(portaPedida); // escuta em todas as interfaces de rede
        pool = new ThreadPoolExecutor(THREADS_MIN, THREADS_MAX, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), fabrica("atendimento"));
        aceitador = fabrica("aceitador").newThread(this::aceitarConexoes);
        aceitador.start();
        log.info("Servidor iniciado na porta " + getPorta());
    }

    public synchronized void parar() {
        if (serverSocket == null) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException ignorada) {
            // já estava fechado
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        serverSocket = null;
        log.info("Servidor parado");
    }

    public boolean isRodando() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    /** Porta real (útil quando se pede a porta 0 e o sistema escolhe uma livre). */
    public int getPorta() {
        ServerSocket s = serverSocket;
        return s == null ? portaPedida : s.getLocalPort();
    }

    private void aceitarConexoes() {
        ServerSocket servidor = serverSocket;
        while (servidor != null && !servidor.isClosed()) {
            try {
                Socket cliente = servidor.accept();
                try {
                    pool.execute(new Atendimento(cliente, despachante, log, tempoLimiteMs));
                } catch (RejectedExecutionException lotado) {
                    recusar(cliente);
                }
            } catch (IOException e) {
                if (!servidor.isClosed()) {
                    log.erro("Falha ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }

    private void recusar(Socket cliente) {
        log.erro("Pool cheio: recusando " + cliente.getInetAddress().getHostAddress());
        try (Socket s = cliente) {
            String json = Resposta.erro(Protocolo.ERRO_INTERNO, Protocolo.MSG_SOBRECARGA).paraJson();
            Transporte.escreverMensagem(s.getOutputStream(), json, StandardCharsets.UTF_8);
        } catch (IOException ignorada) {
            // cliente já foi embora
        }
    }

    private static ThreadFactory fabrica(String prefixo) {
        AtomicInteger contador = new AtomicInteger();
        return tarefa -> {
            Thread t = new Thread(tarefa, prefixo + "-" + contador.incrementAndGet());
            t.setDaemon(!prefixo.equals("aceitador")); // o aceitador mantém a JVM viva no modo console
            return t;
        };
    }
}
