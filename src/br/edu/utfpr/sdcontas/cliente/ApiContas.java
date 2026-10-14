package br.edu.utfpr.sdcontas.cliente;

import static br.edu.utfpr.sdcontas.protocolo.Protocolo.*;

import br.edu.utfpr.sdcontas.protocolo.Resposta;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operações do protocolo do lado do cliente, guardando o token da sessão em memória.
 * Monta as requisições exatamente com as chaves da planilha.
 */
public final class ApiContas {

    private final ClienteTcp tcp;
    private volatile String token;
    private volatile String username;

    public ApiContas(ClienteTcp tcp) {
        this.tcp = tcp;
    }

    public ClienteTcp getTcp() {
        return tcp;
    }

    public boolean isLogado() {
        return token != null;
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    /** login → em caso de 200, guarda o token recebido em {@code data.token}. */
    public Resposta login(String username, String senha) throws FalhaComunicacao {
        Resposta r = tcp.enviar(LOGIN, mapa("username", username, "password", senha));
        if (r.statusCode() == OK) {
            String recebido = r.textoEmData("token");
            if (recebido == null) {
                throw new FalhaComunicacao("O servidor respondeu 200 ao login, mas sem data.token.");
            }
            this.token = recebido;
            this.username = username;
        }
        return r;
    }

    public Resposta cadastrar(String nome, String username, String senha) throws FalhaComunicacao {
        return tcp.enviar(REGISTER, mapa("name", nome, "username", username, "password", senha));
    }

    public Resposta consultar() throws FalhaComunicacao {
        return tcp.enviar(GET_USER, mapa("token", token, "username", username));
    }

    public Resposta atualizarNome(String novoNome) throws FalhaComunicacao {
        return tcp.enviar(UPDATE_USER_NAME, mapa("token", token, "username", username, "name", novoNome));
    }

    public Resposta atualizarSenha(String senhaAtual, String novaSenha) throws FalhaComunicacao {
        return tcp.enviar(UPDATE_USER_PASSWORD, mapa("token", token, "username", username,
                "oldPassword", senhaAtual, "newPassword", novaSenha));
    }

    /** deleteuser → em caso de 200 a sessão local também acaba. */
    public Resposta excluirConta() throws FalhaComunicacao {
        Resposta r = tcp.enviar(DELETE_USER, mapa("token", token, "username", username));
        if (r.statusCode() == OK) {
            encerrarSessaoLocal();
        }
        return r;
    }

    /**
     * logout → a sessão local acaba sempre que o servidor responder, mesmo com erro
     * (se o token já não vale lá, também não deve valer aqui).
     */
    public Resposta logout() throws FalhaComunicacao {
        try {
            return tcp.enviar(LOGOUT, mapa("token", token));
        } finally {
            encerrarSessaoLocal();
        }
    }

    /**
     * Pergunta ao servidor se o token ainda vale (via getuser). Usado quando chega um
     * 401 em outra operação: assim o cliente não depende do texto da mensagem para
     * saber se foi "sessão expirada" ou outro motivo (ex.: senha atual errada).
     */
    public boolean sessaoContinuaValida() {
        try {
            return consultar().statusCode() != NAO_AUTORIZADO;
        } catch (FalhaComunicacao e) {
            return true; // não deu para saber; não derruba o usuário por causa da rede
        }
    }

    public void encerrarSessaoLocal() {
        token = null;
        username = null;
    }

    private static Map<String, Object> mapa(Object... chavesEValores) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            m.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return m;
    }
}
