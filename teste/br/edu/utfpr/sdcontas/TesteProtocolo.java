package br.edu.utfpr.sdcontas;

import br.edu.utfpr.sdcontas.protocolo.Json;
import br.edu.utfpr.sdcontas.protocolo.JsonInvalidoException;
import br.edu.utfpr.sdcontas.protocolo.LogSeguro;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import br.edu.utfpr.sdcontas.protocolo.Validacao;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Testes de unidade: JSON, validação de campos, leitura tolerante de respostas e log. */
final class TesteProtocolo {

    private TesteProtocolo() {
    }

    @SuppressWarnings("unchecked")
    static void executar(Verifica v) {
        v.grupo("JSON — leitura");
        Map<String, Object> m = (Map<String, Object>) Json.ler(
                " {\"method\":\"login\",\"data\":{\"username\":\"ana\",\"n\":200,\"d\":200.5,\"ok\":true,\"x\":null,\"l\":[1,\"a\"]}} ");
        Map<String, Object> d = (Map<String, Object>) m.get("data");
        v.igual("texto", "login", m.get("method"));
        v.igual("inteiro vira Long", 200L, d.get("n"));
        v.igual("decimal vira Double", 200.5, d.get("d"));
        v.igual("booleano", Boolean.TRUE, d.get("ok"));
        v.verdadeiro("null presente é diferente de ausente", d.containsKey("x") && d.get("x") == null);
        v.igual("array", List.of(1L, "a"), d.get("l"));
        v.igual("escape \\u0027 (Gson manda apóstrofo assim)", "O campo 'method'", Json.ler("\"O campo \\u0027method\\u0027\""));
        v.igual("escapes comuns", "a\"b\\c/d\ne\tf", Json.ler("\"a\\\"b\\\\c\\/d\\ne\\tf\""));
        v.igual("acentos diretos", "Usuário não encontrado", Json.ler("\"Usuário não encontrado\""));
        v.igual("emoji por par substituto", "😀", Json.ler("\"\\ud83d\\ude00\""));
        v.igual("expoente", 1000.0, Json.ler("1e3"));
        v.igual("negativo", -5L, Json.ler("-5"));
        v.igual("JSON multilinha", Map.of("a", 1L), Json.ler("{\n  \"a\" : 1\n}\n"));

        v.grupo("JSON — entradas inválidas");
        for (String invalido : new String[] {"", "{", "{\"a\":1,}", "{a:1}", "{'a':1}", "[1,2", "\"sem fim",
                "01", "1.", "tru", "{\"a\":1} lixo", "\"tab\tdentro\"", "\"\\x\"", "\"\\u12G4\""}) {
            v.lanca("rejeita " + Json.escrever(invalido), JsonInvalidoException.class, () -> Json.ler(invalido));
        }
        v.lanca("rejeita aninhamento absurdo", JsonInvalidoException.class, () -> Json.ler("[".repeat(500)));

        v.grupo("JSON — escrita");
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("statusCode", 200);
        resposta.put("message", "Sucesso no Login");
        resposta.put("data", null);
        v.igual("ordem das chaves e data:null explícito",
                "{\"statusCode\":200,\"message\":\"Sucesso no Login\",\"data\":null}", Json.escrever(resposta));
        v.igual("escapa aspas, barra e quebra de linha", "\"a\\\"b\\\\c\\nd\"", Json.escrever("a\"b\\c\nd"));
        v.igual("acentos saem como estão", "\"João\"", Json.escrever("João"));
        v.igual("caractere de controle vira \\u", "\"\\u0001\"", Json.escrever("\u0001"));
        Object idaEVolta = Json.ler(Json.escrever(Map.of("x", List.of("é", 1L, true))));
        v.igual("ida e volta", Map.of("x", List.of("é", 1L, true)), idaEVolta);

        v.grupo("Validação — name");
        v.verdadeiro("Joao Silva", Validacao.nomeValido("Joao Silva"));
        v.verdadeiro("com acento (João)", Validacao.nomeValido("João"));
        v.verdadeiro("1 letra", Validacao.nomeValido("A"));
        v.verdadeiro("60 letras", Validacao.nomeValido("a".repeat(60)));
        v.falso("61 letras", Validacao.nomeValido("a".repeat(61)));
        v.falso("vazio", Validacao.nomeValido(""));
        v.falso("só espaços", Validacao.nomeValido("   "));
        v.falso("null", Validacao.nomeValido(null));
        v.falso("número", Validacao.nomeValido("Jo4o"));
        v.falso("símbolo", Validacao.nomeValido("Joao!"));
        v.falso("hífen", Validacao.nomeValido("Ana-Maria"));

        v.grupo("Validação — username");
        v.verdadeiro("joao.s", Validacao.usernameValido("joao.s"));
        v.verdadeiro("user_01", Validacao.usernameValido("user_01"));
        v.verdadeiro("3 caracteres", Validacao.usernameValido("abc"));
        v.verdadeiro("20 caracteres", Validacao.usernameValido("a".repeat(20)));
        v.falso("2 caracteres", Validacao.usernameValido("ab"));
        v.falso("21 caracteres", Validacao.usernameValido("a".repeat(21)));
        v.falso("maiúscula", Validacao.usernameValido("Joao"));
        v.falso("acento", Validacao.usernameValido("joão"));
        v.falso("espaço", Validacao.usernameValido("jo ao"));
        v.falso("hífen", Validacao.usernameValido("jo-ao"));
        v.falso("null", Validacao.usernameValido(null));

        v.grupo("Validação — password");
        v.verdadeiro("Senha123!", Validacao.senhaValida("Senha123!"));
        v.verdadeiro("todos os símbolos permitidos", Validacao.senhaValida("Aa1#.*&%$@!()-_=+"));
        v.verdadeiro("8 caracteres", Validacao.senhaValida("Aa1!aaaa"));
        v.verdadeiro("20 caracteres", Validacao.senhaValida("Aa1!" + "a".repeat(16)));
        v.falso("7 caracteres", Validacao.senhaValida("Aa1!aaa"));
        v.falso("21 caracteres", Validacao.senhaValida("Aa1!" + "a".repeat(17)));
        v.falso("sem maiúscula", Validacao.senhaValida("senha123!"));
        v.falso("sem minúscula", Validacao.senhaValida("SENHA123!"));
        v.falso("sem número", Validacao.senhaValida("SenhaSenha!"));
        v.falso("sem símbolo", Validacao.senhaValida("Senha1234"));
        v.falso("símbolo fora da lista (?)", Validacao.senhaValida("Senha123?"));
        v.falso("espaço", Validacao.senhaValida("Senha 123!"));
        v.falso("acento", Validacao.senhaValida("Senhá123!"));
        v.falso("null", Validacao.senhaValida(null));

        v.grupo("Cliente — leitura tolerante de respostas de outros servidores");
        v.igual("statusCode inteiro", 200, Resposta.deJson("{\"statusCode\":200,\"message\":\"ok\",\"data\":null}").statusCode());
        v.igual("statusCode 200.0", 200, Resposta.deJson("{\"statusCode\":200.0,\"message\":\"ok\"}").statusCode());
        v.igual("statusCode como texto", 401, Resposta.deJson("{\"statusCode\":\"401\",\"message\":\"x\"}").statusCode());
        v.igual("sem message usa o significado", "Conflito", Resposta.deJson("{\"statusCode\":409}").message());
        v.igual("chave com outra caixa (StatusCode)", 404, Resposta.deJson("{\"StatusCode\":404,\"Message\":\"x\"}").statusCode());
        Resposta chaveTorta = Resposta.deJson("{\"statusCode: \":400,\"message: \":\"Senha inválida!\"}");
        v.igual("chave com \": \" grudado (\"statusCode: \") — visto num servidor da turma", 400, chaveTorta.statusCode());
        v.igual("… e a message também é lida", "Senha inválida!", chaveTorta.message());
        v.igual("token em data", "abc", Resposta.deJson("{\"statusCode\":200,\"message\":\"ok\",\"data\":{\"token\":\"abc\"}}").textoEmData("token"));
        v.igual("data como texto com JSON dentro", "abc",
                Resposta.deJson("{\"statusCode\":200,\"message\":\"ok\",\"data\":\"{\\\"token\\\":\\\"abc\\\"}\"}").textoEmData("token"));
        v.lanca("sem statusCode é inválido", JsonInvalidoException.class, () -> Resposta.deJson("{\"message\":\"x\"}"));
        v.lanca("statusCode 200.5 é inválido", JsonInvalidoException.class, () -> Resposta.deJson("{\"statusCode\":200.5}"));
        v.lanca("resposta que não é objeto", JsonInvalidoException.class, () -> Resposta.deJson("[200]"));

        v.grupo("Log — senhas mascaradas");
        v.igual("password", "{\"username\":\"a\",\"password\":\"***\"}",
                LogSeguro.mascararSenhas("{\"username\":\"a\",\"password\":\"Senha123!\"}"));
        v.igual("old/newPassword com aspas escapadas", "{\"oldPassword\" : \"***\",\"newPassword\":\"***\"}",
                LogSeguro.mascararSenhas("{\"oldPassword\" : \"a\\\"b\",\"newPassword\":\"Nova1234!\"}"));
    }
}
