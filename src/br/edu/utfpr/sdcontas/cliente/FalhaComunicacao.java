package br.edu.utfpr.sdcontas.cliente;

/** Não foi possível falar com o servidor (desligado, tempo esgotado, resposta inválida...). */
public class FalhaComunicacao extends Exception {

    private static final long serialVersionUID = 1L;

    public FalhaComunicacao(String mensagem) {
        super(mensagem);
    }

    public FalhaComunicacao(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
