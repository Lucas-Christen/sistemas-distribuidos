package br.edu.utfpr.sdcontas.protocolo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Esconde senhas antes de mostrar um JSON em log, mantendo o resto da mensagem intacto. */
public final class LogSeguro {

    private LogSeguro() {
    }

    private static final Pattern CAMPO_SENHA = Pattern.compile(
            "(\"(?i:password|oldPassword|newPassword)\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"");

    public static String mascararSenhas(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = CAMPO_SENHA.matcher(json);
        return m.replaceAll(resultado -> Matcher.quoteReplacement(resultado.group(1)) + "\"***\"");
    }
}
