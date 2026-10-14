package br.edu.utfpr.sdcontas.servidor;

/**
 * Modelo de dados do documento de requisitos: {@code String name, String username, String password}.
 * Aqui {@code password} guarda o <b>hash</b> da senha (ver {@link Senhas}), nunca a senha em si.
 */
public record Usuario(String name, String username, String password) {

    public Usuario comNome(String novoNome) {
        return new Usuario(novoNome, username, password);
    }

    public Usuario comSenha(String novoHash) {
        return new Usuario(name, username, novoHash);
    }
}
