package br.edu.utfpr.sdcontas.protocolo;

/** Texto recebido não é um JSON válido. */
public class JsonInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int posicao;

    public JsonInvalidoException(String motivo, int posicao) {
        super(motivo + " (posição " + posicao + ")");
        this.posicao = posicao;
    }

    public int getPosicao() {
        return posicao;
    }
}
