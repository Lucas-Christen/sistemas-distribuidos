package br.edu.utfpr.sdcontas.servidor;

import static br.edu.utfpr.sdcontas.protocolo.Protocolo.*;

import br.edu.utfpr.sdcontas.protocolo.Dados;
import br.edu.utfpr.sdcontas.protocolo.Json;
import br.edu.utfpr.sdcontas.protocolo.JsonInvalidoException;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Recebe o texto da requisição, valida o envelope {@code {"method", "data"}} e
 * encaminha para a operação certa através de uma tabela método → função.
 */
public final class Despachante {

    private final Map<String, Function<Map<String, Object>, Resposta>> rotas = new HashMap<>();
    private final RegistroLog log;

    public Despachante(ServicoContas servico, RegistroLog log) {
        this.log = log;
        rotas.put(LOGIN, servico::login);
        rotas.put(REGISTER, servico::register);
        rotas.put(LOGOUT, servico::logout);
        rotas.put(GET_USER, servico::getUser);
        rotas.put(UPDATE_USER_NAME, servico::updateUserName);
        rotas.put(UPDATE_USER_PASSWORD, servico::updateUserPassword);
        rotas.put(DELETE_USER, servico::deleteUser);
    }

    public Resposta processar(String textoRequisicao) {
        Object raiz;
        try {
            raiz = Json.ler(textoRequisicao);
        } catch (JsonInvalidoException e) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_JSON_MALFORMADO);
        }
        Map<String, Object> requisicao = Dados.comoObjeto(raiz);
        if (requisicao == null) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_REQUISICAO_NAO_OBJETO);
        }

        Object metodoBruto = Dados.campo(requisicao, "method");
        if (!(metodoBruto instanceof String metodoTexto) || metodoTexto.isBlank()) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_METODO_AUSENTE);
        }
        // A especificação manda o cliente usar minúsculo; aceitar "Login" não custa nada.
        String metodo = metodoTexto.strip().toLowerCase(Locale.ROOT);

        Object dataBruto = Dados.campo(requisicao, "data");
        Map<String, Object> dados = dataBruto == null ? new LinkedHashMap<>() : Dados.comoObjeto(dataBruto);
        if (dados == null) {
            return Resposta.erro(REQUISICAO_INVALIDA, MSG_DATA_INVALIDO);
        }

        Function<Map<String, Object>, Resposta> operacao = rotas.get(metodo);
        if (operacao == null) {
            return Resposta.erro(REQUISICAO_INVALIDA, msgMetodoDesconhecido(metodoTexto));
        }
        try {
            return operacao.apply(dados);
        } catch (RuntimeException e) {
            log.erro("Falha ao processar '" + metodo + "': " + e);
            return Resposta.erro(ERRO_INTERNO, MSG_ERRO_INTERNO);
        }
    }
}
