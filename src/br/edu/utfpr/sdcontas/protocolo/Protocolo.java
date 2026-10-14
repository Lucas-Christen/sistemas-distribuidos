package br.edu.utfpr.sdcontas.protocolo;

/**
 * Tudo o que foi combinado com a turma (planilha "Protocolo de Troca de Mensagens"
 * e documento de requisitos): nomes dos métodos, status codes e mensagens.
 *
 * <p>Os textos das mensagens são copiados da especificação. Não altere sem
 * combinar com a turma: clientes de outras pessoas podem depender deles
 * (ex.: detectar "Sessão expirada ou encerrada.").
 */
public final class Protocolo {

    private Protocolo() {
    }

    public static final int PORTA_PADRAO = 21500;

    // ---------------------------------------------------------------- métodos
    public static final String LOGIN = "login";
    public static final String REGISTER = "register";
    public static final String LOGOUT = "logout";
    public static final String GET_USER = "getuser";
    public static final String UPDATE_USER_NAME = "updateusername";
    public static final String UPDATE_USER_PASSWORD = "updateuserpassword";
    public static final String DELETE_USER = "deleteuser";

    // ----------------------------------------------------------- status codes
    public static final int OK = 200;
    public static final int CRIADO = 201;
    public static final int REQUISICAO_INVALIDA = 400;
    public static final int NAO_AUTORIZADO = 401;
    public static final int NAO_ENCONTRADO = 404;
    public static final int CONFLITO = 409;
    public static final int ERRO_INTERNO = 500;

    /** Significado de cada status, conforme a aba "StatusCode" da planilha. */
    public static String significado(int status) {
        switch (status) {
            case OK: return "Sucesso";
            case CRIADO: return "Criado";
            case REQUISICAO_INVALIDA: return "Campos errados";
            case NAO_AUTORIZADO: return "Não autorizado";
            case NAO_ENCONTRADO: return "Não encontrou";
            case CONFLITO: return "Conflito";
            case ERRO_INTERNO: return "Erro de servidor";
            default: return "Status " + status;
        }
    }

    // ------------------------------------------- mensagens da especificação
    public static final String MSG_LOGIN_OK = "Sucesso no Login";
    public static final String MSG_LOGIN_INVALIDO = "Usuario ou senhas inválidos";
    public static final String MSG_CADASTRO_OK = "Usuário criado com sucesso";
    public static final String MSG_USERNAME_EM_USO = "O username já está em uso.";
    public static final String MSG_LOGOUT_OK = "Usuário deslogado com sucesso";
    public static final String MSG_TOKEN_AUSENTE = "Token de autenticação não fornecido.";
    public static final String MSG_SESSAO_INVALIDA = "Sessão expirada ou encerrada.";
    public static final String MSG_USUARIO_ENCONTRADO = "Usuário encontrado com sucesso";
    public static final String MSG_USUARIO_NAO_ENCONTRADO = "Usuário não encontrado";
    public static final String MSG_NOME_ATUALIZADO = "Nome do usuário atualizado com sucesso";
    public static final String MSG_NOME_FORA_DO_PADRAO = "Nome do usuário fora do padrão";
    public static final String MSG_SENHA_ATUALIZADA = "Senha atualizada com sucesso";
    public static final String MSG_SENHA_FORA_DO_PADRAO = "Senha fora do padrão";
    public static final String MSG_USUARIO_DELETADO = "Usuário deletado com sucesso";
    public static final String MSG_OPERACAO_NAO_AUTORIZADA = "Operação não autorizada";

    public static String msgCampoForaDoPadrao(String campo) {
        return "O campo " + campo + " não está no padrão esperado.";
    }

    // ---------------- mensagens para situações que a especificação não define
    public static final String MSG_SENHA_ATUAL_INCORRETA = "Senha atual incorreta";
    public static final String MSG_JSON_MALFORMADO = "JSON malformado";
    public static final String MSG_REQUISICAO_NAO_OBJETO = "A requisição deve ser um objeto JSON.";
    public static final String MSG_METODO_AUSENTE = "O campo 'method' é obrigatório.";
    public static final String MSG_DATA_INVALIDO = "O campo 'data' deve ser um objeto JSON.";
    public static final String MSG_MENSAGEM_GRANDE = "Mensagem maior que o limite permitido.";
    public static final String MSG_ERRO_INTERNO = "Erro interno no servidor";
    public static final String MSG_SOBRECARGA = "Servidor sobrecarregado. Tente novamente.";

    public static String msgMetodoDesconhecido(String metodo) {
        return "Método desconhecido: " + metodo;
    }
}
