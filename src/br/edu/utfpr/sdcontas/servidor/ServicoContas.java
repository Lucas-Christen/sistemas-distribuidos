package br.edu.utfpr.sdcontas.servidor;

import static br.edu.utfpr.sdcontas.protocolo.Protocolo.*;

import br.edu.utfpr.sdcontas.protocolo.Dados;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import br.edu.utfpr.sdcontas.protocolo.Validacao;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Regras de negócio de cada método do protocolo. Recebe o objeto {@code data}
 * da requisição e devolve a {@link Resposta} já com status e mensagem da especificação.
 */
public final class ServicoContas {

    private final RepositorioUsuarios repositorio;
    private final Sessoes sessoes;
    private final RegistroLog log;

    public ServicoContas(RepositorioUsuarios repositorio, Sessoes sessoes, RegistroLog log) {
        this.repositorio = repositorio;
        this.sessoes = sessoes;
        this.log = log;
    }

    // ------------------------------------------------------------------ login
    public Resposta login(Map<String, Object> dados) {
        String username = Dados.texto(dados, "username");
        String senha = Dados.texto(dados, "password");

        Optional<Usuario> usuario = repositorio.buscar(username);
        // Mesmo quando o usuário não existe, calcula um hash: assim o tempo de resposta
        // não denuncia quais usernames estão cadastrados.
        String hash = usuario.map(Usuario::password).orElse(Senhas.HASH_FICTICIO);
        boolean senhaConfere = Senhas.confere(senha, hash);
        if (usuario.isEmpty() || !senhaConfere) {
            return Resposta.erro(NAO_AUTORIZADO, MSG_LOGIN_INVALIDO);
        }

        String token = sessoes.abrir(username);
        if (!repositorio.existe(username)) { // conta excluída enquanto o login acontecia
            sessoes.encerrar(token);
            return Resposta.erro(NAO_AUTORIZADO, MSG_LOGIN_INVALIDO);
        }
        log.cliente("Login: " + username + " (token " + token + ", sessões ativas: " + sessoes.quantidade() + ")");
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("token", token);
        return Resposta.ok(MSG_LOGIN_OK, resposta);
    }

    // --------------------------------------------------------------- register
    public Resposta register(Map<String, Object> dados) {
        String nome = normalizar(Dados.texto(dados, "name"));
        String username = Dados.texto(dados, "username");
        String senha = Dados.texto(dados, "password");

        // A especificação manda checar o conflito antes de validar os campos.
        if (repositorio.existe(username)) {
            return Resposta.erro(CONFLITO, MSG_USERNAME_EM_USO);
        }
        if (!Validacao.nomeValido(nome)) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgCampoForaDoPadrao("name"));
        }
        if (!Validacao.usernameValido(username)) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgCampoForaDoPadrao("username"));
        }
        if (!Validacao.senhaValida(senha)) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgCampoForaDoPadrao("password"));
        }
        if (!repositorio.inserir(new Usuario(nome, username, Senhas.gerarHash(senha)))) {
            return Resposta.erro(CONFLITO, MSG_USERNAME_EM_USO); // outro cadastro igual chegou junto
        }
        log.info("Usuário cadastrado: " + username);
        return Resposta.criado(MSG_CADASTRO_OK);
    }

    // ----------------------------------------------------------------- logout
    public Resposta logout(Map<String, Object> dados) {
        Autenticacao auth = autenticar(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        sessoes.encerrar(auth.token());
        log.cliente("Logout: " + auth.username() + " (sessões ativas: " + sessoes.quantidade() + ")");
        return Resposta.ok(MSG_LOGOUT_OK);
    }

    // ---------------------------------------------------------------- getuser
    public Resposta getUser(Map<String, Object> dados) {
        Autenticacao auth = autenticarDono(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        Optional<Usuario> usuario = repositorio.buscar(auth.username());
        if (usuario.isEmpty()) {
            return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("name", usuario.get().name());
        resposta.put("username", usuario.get().username());
        return Resposta.ok(MSG_USUARIO_ENCONTRADO, resposta);
    }

    // --------------------------------------------------------- updateusername
    public Resposta updateUserName(Map<String, Object> dados) {
        Autenticacao auth = autenticarDono(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        String nome = normalizar(Dados.texto(dados, "name"));
        if (!Validacao.nomeValido(nome)) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_NOME_FORA_DO_PADRAO);
        }
        if (!repositorio.atualizarNome(auth.username(), nome)) {
            return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
        }
        return Resposta.ok(MSG_NOME_ATUALIZADO);
    }

    // ----------------------------------------------------- updateuserpassword
    public Resposta updateUserPassword(Map<String, Object> dados) {
        Autenticacao auth = autenticarDono(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        Optional<Usuario> usuario = repositorio.buscar(auth.username());
        if (usuario.isEmpty()) {
            return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
        }
        String senhaAtual = Dados.texto(dados, "oldPassword");
        String novaSenha = Dados.texto(dados, "newPassword");
        if (!Senhas.confere(senhaAtual, usuario.get().password())) {
            return Resposta.erro(NAO_AUTORIZADO, MSG_SENHA_ATUAL_INCORRETA);
        }
        if (!Validacao.senhaValida(novaSenha)) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_SENHA_FORA_DO_PADRAO);
        }
        if (!repositorio.atualizarSenha(auth.username(), Senhas.gerarHash(novaSenha))) {
            return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
        }
        return Resposta.ok(MSG_SENHA_ATUALIZADA);
    }

    // ------------------------------------------------------------- deleteuser
    public Resposta deleteUser(Map<String, Object> dados) {
        Autenticacao auth = autenticarDono(dados);
        if (auth.falhou()) {
            return auth.erro();
        }
        if (!repositorio.remover(auth.username())) {
            return Resposta.erro(NAO_ENCONTRADO, MSG_USUARIO_NAO_ENCONTRADO);
        }
        int derrubadas = sessoes.encerrarTodasDe(auth.username());
        log.cliente("Conta excluída: " + auth.username() + " (" + derrubadas + " sessão(ões) encerrada(s))");
        return Resposta.ok(MSG_USUARIO_DELETADO);
    }

    // ------------------------------------------------------- autenticação

    /** Resultado da checagem do token: ou o usuário dono do token, ou a resposta de erro. */
    private record Autenticacao(String token, String username, Resposta erro) {
        boolean falhou() {
            return erro != null;
        }
    }

    /**
     * Valida o token. Ausente/vazio → 400; qualquer outro valor que não seja um
     * login ativo (inclusive texto que nem é UUID) → 401 "Sessão expirada ou encerrada.".
     */
    private Autenticacao autenticar(Map<String, Object> dados) {
        Object valor = Dados.campo(dados, "token");
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            return new Autenticacao(null, null, Resposta.erro(REQUISICAO_INVALIDA, MSG_TOKEN_AUSENTE));
        }
        String token = valor instanceof String s ? s.strip() : String.valueOf(valor);
        Optional<String> username = sessoes.usuarioDoToken(token);
        if (username.isEmpty()) {
            return new Autenticacao(null, null, Resposta.erro(NAO_AUTORIZADO, MSG_SESSAO_INVALIDA));
        }
        return new Autenticacao(token, username.get(), null);
    }

    /**
     * Valida o token e confere se o {@code username} enviado (o ID da operação, em negrito
     * na planilha) é o dono do token. Se o cliente não mandar username, vale o dono do token.
     * Operar sobre outro usuário seria função de admin, que ainda não foi especificada → 401.
     */
    private Autenticacao autenticarDono(Map<String, Object> dados) {
        Autenticacao auth = autenticar(dados);
        if (auth.falhou()) {
            return auth;
        }
        Object enviado = Dados.campo(dados, "username");
        boolean naoInformado = enviado == null || (enviado instanceof String s && s.isBlank());
        if (!naoInformado && !auth.username().equals(enviado instanceof String s ? s.strip() : enviado)) {
            return new Autenticacao(null, null, Resposta.erro(NAO_AUTORIZADO, MSG_OPERACAO_NAO_AUTORIZADA));
        }
        return auth;
    }

    /** "José" pode chegar como "Jose" + acento combinado; normaliza para a forma composta. */
    private static String normalizar(String texto) {
        return texto == null ? null : Normalizer.normalize(texto, Normalizer.Form.NFC);
    }
}
