package br.edu.utfpr.sdcontas.protocolo;

import java.util.Map;

/**
 * Leitura tolerante de campos de um objeto JSON já convertido em {@code Map}.
 *
 * <p>Ser tolerante no que recebe é o que permite conversar com implementações
 * de outras pessoas: o campo é procurado pelo nome exato e, se não existir,
 * ignorando maiúsculas/minúsculas ({@code "oldpassword"} encontra {@code "oldPassword"}).
 */
public final class Dados {

    private Dados() {
    }

    public static boolean temCampo(Map<String, ?> objeto, String nome) {
        return chaveReal(objeto, nome) != null;
    }

    public static Object campo(Map<String, ?> objeto, String nome) {
        String chave = chaveReal(objeto, nome);
        return chave == null ? null : objeto.get(chave);
    }

    /** Valor do campo se for texto; {@code null} se ausente, nulo ou de outro tipo. */
    public static String texto(Map<String, ?> objeto, String nome) {
        Object valor = campo(objeto, nome);
        return valor instanceof String s ? s : null;
    }

    /**
     * Interpreta o valor como objeto JSON. Aceita também um texto que contenha um
     * objeto JSON (há implementações que mandam {@code "data": "{\"token\":...}"}).
     * Retorna {@code null} se não for possível.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> comoObjeto(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            return (Map<String, Object>) mapa;
        }
        if (valor instanceof String s && s.strip().startsWith("{")) {
            try {
                Object interno = Json.ler(s.strip());
                if (interno instanceof Map<?, ?> mapa) {
                    return (Map<String, Object>) mapa;
                }
            } catch (JsonInvalidoException ignorada) {
                // não era JSON; segue como "não é objeto"
            }
        }
        return null;
    }

    private static String chaveReal(Map<String, ?> objeto, String nome) {
        if (objeto == null) {
            return null;
        }
        if (objeto.containsKey(nome)) {
            return nome;
        }
        for (String chave : objeto.keySet()) {
            if (chave != null && chave.equalsIgnoreCase(nome)) {
                return chave;
            }
        }
        // Último recurso: ignora espaços e pontuação. Já vimos servidor mandando "statusCode: " como chave.
        String procurado = soLetrasEDigitos(nome);
        for (String chave : objeto.keySet()) {
            if (chave != null && soLetrasEDigitos(chave).equals(procurado)) {
                return chave;
            }
        }
        return null;
    }

    private static String soLetrasEDigitos(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
