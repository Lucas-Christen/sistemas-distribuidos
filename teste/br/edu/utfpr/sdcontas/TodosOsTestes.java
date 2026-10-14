package br.edu.utfpr.sdcontas;

/** Roda todos os testes: {@code java -cp out br.edu.utfpr.sdcontas.TodosOsTestes}. */
public final class TodosOsTestes {

    private TodosOsTestes() {
    }

    public static void main(String[] args) throws Exception {
        Verifica v = new Verifica();
        TesteProtocolo.executar(v);
        TesteServidor.executar(v);
        System.exit(v.resumo() ? 0 : 1);
    }
}
