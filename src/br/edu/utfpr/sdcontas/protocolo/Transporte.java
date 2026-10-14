package br.edu.utfpr.sdcontas.protocolo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Como uma mensagem JSON viaja pelo TCP.
 *
 * <p><b>Envio:</b> o JSON compacto seguido de {@code \n}, em UTF-8 (é o que todo mundo
 * que usa {@code readLine()} espera).
 *
 * <p><b>Recebimento:</b> em vez de depender do {@code \n}, a leitura acompanha as chaves
 * do JSON e para quando o objeto de nível 0 fecha. Assim funciona se o outro lado:
 * <ul>
 *   <li>mandar com {@code \n} ou {@code \r\n} (o normal);</li>
 *   <li>esquecer o {@code \n} e ficar esperando a resposta;</li>
 *   <li>mandar o JSON formatado em várias linhas;</li>
 *   <li>mandar uma linha malformada (chaves sem fechar): responde 400 sem esperar o tempo limite;</li>
 *   <li>mandar sem {@code \n} e fechar a conexão;</li>
 *   <li>usar Windows-1252 em vez de UTF-8 (Java 17 no Windows faz isso por padrão).</li>
 * </ul>
 */
public final class Transporte {

    private Transporte() {
    }

    public static final int TAMANHO_MAXIMO = 64 * 1024;
    public static final int ESPERA_APOS_QUEBRA_MS = 300;
    public static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /** Mensagem recebida já decodificada e com o charset detectado. */
    public record MensagemRecebida(String texto, Charset charset) {
    }

    /** O outro lado mandou mais que {@link #TAMANHO_MAXIMO} bytes. */
    public static final class MensagemGrandeException extends IOException {
        private static final long serialVersionUID = 1L;

        public MensagemGrandeException() {
            super("mensagem maior que " + TAMANHO_MAXIMO + " bytes");
        }
    }

    /** O tempo acabou no meio de uma mensagem (começou a chegar, mas não terminou). */
    public static final class MensagemIncompletaException extends IOException {
        private static final long serialVersionUID = 1L;
        private final String parcial;

        public MensagemIncompletaException(String parcial) {
            super("mensagem incompleta");
            this.parcial = parcial;
        }

        public String getParcial() {
            return parcial;
        }
    }

    public static void escreverMensagem(OutputStream saida, String json, Charset charset) throws IOException {
        saida.write((json + "\n").getBytes(charset));
        saida.flush();
    }

    /**
     * Lê uma mensagem JSON completa.
     *
     * @param entrada       fluxo de entrada (de preferência com buffer)
     * @param socket        socket dono do fluxo, usado para aplicar o prazo total (pode ser null)
     * @param prazoTotalMs  tempo máximo para a mensagem inteira chegar (0 = sem prazo total)
     * @return a mensagem, ou {@code null} se a conexão terminou sem nenhum dado
     */
    public static MensagemRecebida lerMensagem(InputStream entrada, Socket socket, long prazoTotalMs)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long limite = prazoTotalMs > 0 ? System.nanoTime() + prazoTotalMs * 1_000_000L : 0;
        int nivel = 0;
        boolean dentroDeTexto = false;
        boolean escapando = false;
        boolean comecou = false;
        try {
            while (true) {
                aplicarPrazo(entrada, socket, limite);
                int b = entrada.read();
                if (b == -1) {
                    break; // o outro lado fechou a conexão
                }
                if (!comecou) {
                    if (b == '\n') {
                        // linha vazia: devolve mensagem vazia (vira "JSON malformado"), não fica esperando
                        return new MensagemRecebida("", StandardCharsets.UTF_8);
                    }
                    if (b == ' ' || b == '\t' || b == '\r') {
                        continue; // espaços antes do JSON
                    }
                    comecou = true;
                }
                if (bytes.size() >= TAMANHO_MAXIMO) {
                    throw new MensagemGrandeException();
                }
                bytes.write(b);

                if (b == '\n') {
                    // Quebra de linha no nível 0 encerra a mensagem. Dentro de um objeto ela pode ser
                    // JSON formatado em várias linhas, ou uma linha malformada (chaves sem fechar):
                    // se depois da quebra o outro lado ficar em silêncio, a mensagem acabou.
                    if (nivel <= 0 || silencioDepoisDaQuebra(entrada, socket)) {
                        break;
                    }
                    escapando = false;
                    continue;
                }
                if (dentroDeTexto) {
                    if (escapando) {
                        escapando = false;
                    } else if (b == '\\') {
                        escapando = true;
                    } else if (b == '"') {
                        dentroDeTexto = false;
                    }
                    continue;
                }
                if (b == '"') {
                    dentroDeTexto = true;
                } else if (b == '{' || b == '[') {
                    nivel++;
                } else if (b == '}' || b == ']') {
                    nivel--;
                    if (nivel <= 0) {
                        break; // o objeto de nível 0 fechou: mensagem completa
                    }
                }
            }
        } catch (SocketTimeoutException tempoEsgotado) {
            if (comecou) {
                throw new MensagemIncompletaException(decodificar(bytes.toByteArray()).texto());
            }
            throw tempoEsgotado;
        }
        if (!comecou) {
            return null;
        }
        return decodificar(bytes.toByteArray());
    }

    /**
     * Depois de um {@code \n} no meio do objeto: chegam mais bytes em até
     * {@link #ESPERA_APOS_QUEBRA_MS}? Se não, a mensagem terminou ali (malformada).
     * JSON formatado em várias linhas chega todo junto, então não é afetado.
     */
    private static boolean silencioDepoisDaQuebra(InputStream entrada, Socket socket) throws IOException {
        if (entrada.available() > 0 || socket == null || !entrada.markSupported()) {
            return false;
        }
        int anterior = socket.getSoTimeout();
        socket.setSoTimeout(ESPERA_APOS_QUEBRA_MS);
        entrada.mark(1);
        try {
            int proximo = entrada.read();
            entrada.reset();
            return proximo == -1;
        } catch (SocketTimeoutException silencio) {
            return true;
        } finally {
            socket.setSoTimeout(anterior);
        }
    }

    /** Descarta bytes que já chegaram e sobraram (ex.: o {@code \n} depois do objeto). */
    public static void descartarPendentes(InputStream entrada) {
        try {
            byte[] lixo = new byte[512];
            while (entrada.available() > 0 && entrada.read(lixo) > 0) {
                // só descartando
            }
        } catch (IOException ignorada) {
            // a conexão vai ser fechada de qualquer forma
        }
    }

    private static void aplicarPrazo(InputStream entrada, Socket socket, long limite) throws IOException {
        if (socket == null || limite == 0 || entrada.available() > 0) {
            return;
        }
        long restanteMs = (limite - System.nanoTime()) / 1_000_000L;
        if (restanteMs <= 0) {
            throw new SocketTimeoutException("prazo total esgotado");
        }
        int atual = socket.getSoTimeout();
        if (atual == 0 || atual > restanteMs) {
            socket.setSoTimeout((int) Math.max(1, restanteMs));
        }
    }

    /** Tenta UTF-8 estrito; se os bytes não forem UTF-8 válido, usa Windows-1252. */
    static MensagemRecebida decodificar(byte[] bytes) {
        int inicio = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            inicio = 3; // BOM do UTF-8 (alguns editores/sistemas colocam)
        }
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, inicio, bytes.length - inicio))
                    .toString();
            return new MensagemRecebida(texto.strip(), StandardCharsets.UTF_8);
        } catch (CharacterCodingException naoEhUtf8) {
            String texto = new String(bytes, inicio, bytes.length - inicio, WINDOWS_1252);
            return new MensagemRecebida(texto.strip(), WINDOWS_1252);
        }
    }
}
