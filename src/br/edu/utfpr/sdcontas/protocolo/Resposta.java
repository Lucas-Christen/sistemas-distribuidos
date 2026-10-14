package br.edu.utfpr.sdcontas.protocolo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resposta padrão do servidor: {@code {"statusCode": int, "message": String, "data": T|null}}.
 */
public record Resposta(int statusCode, String message, Object data) {

    public static Resposta ok(String mensagem) {
        return new Resposta(Protocolo.OK, mensagem, null);
    }

    public static Resposta ok(String mensagem, Object dados) {
        return new Resposta(Protocolo.OK, mensagem, dados);
    }

    public static Resposta criado(String mensagem) {
        return new Resposta(Protocolo.CRIADO, mensagem, null);
    }

    public static Resposta erro(int status, String mensagem) {
        return new Resposta(status, mensagem, null);
    }

    public boolean sucesso() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** JSON enviado pelo servidor. A chave {@code data} sempre aparece, mesmo quando é {@code null}. */
    public String paraJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("statusCode", statusCode);
        json.put("message", message == null ? "" : message);
        json.put("data", data);
        return Json.escrever(json);
    }

    /**
     * Lê a resposta de um servidor qualquer (usado pelo cliente), aceitando variações
     * comuns em outras implementações: {@code statusCode} como 200, 200.0 ou "200";
     * {@code message} ausente; {@code data} ausente ou como texto com JSON dentro.
     */
    public static Resposta deJson(String texto) {
        Map<String, Object> objeto = Dados.comoObjeto(Json.ler(texto));
        if (objeto == null) {
            throw new JsonInvalidoException("a resposta não é um objeto JSON", 0);
        }
        int status = converterStatus(Dados.campo(objeto, "statusCode"));
        Object mensagem = Dados.campo(objeto, "message");
        String textoMensagem = mensagem == null ? Protocolo.significado(status)
                : mensagem instanceof String s ? s : Json.escrever(mensagem);
        Object dados = Dados.campo(objeto, "data");
        Map<String, Object> dadosObjeto = Dados.comoObjeto(dados);
        return new Resposta(status, textoMensagem, dadosObjeto != null ? dadosObjeto : dados);
    }

    private static int converterStatus(Object valor) {
        if (valor instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d)) {
                return (int) d;
            }
        } else if (valor instanceof String s) {
            try {
                double d = Double.parseDouble(s.strip());
                if (d == Math.rint(d)) {
                    return (int) d;
                }
            } catch (NumberFormatException ignorada) {
                // cai no erro abaixo
            }
        }
        throw new JsonInvalidoException("resposta sem statusCode inteiro", 0);
    }

    /** Campo de texto dentro de {@code data} (ex.: "token", "name"), ou {@code null}. */
    public String textoEmData(String campo) {
        Map<String, Object> objeto = Dados.comoObjeto(data);
        if (objeto == null) {
            return null;
        }
        Object valor = Dados.campo(objeto, campo);
        if (valor == null) {
            return null;
        }
        String texto = valor instanceof String s ? s : String.valueOf(valor);
        return texto.isBlank() ? null : texto.strip();
    }
}
