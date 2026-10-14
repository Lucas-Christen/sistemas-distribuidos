package br.edu.utfpr.sdcontas.protocolo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor e escritor de JSON feito à mão, sem bibliotecas externas.
 *
 * <p>Mapeamento usado:
 * <ul>
 *   <li>objeto → {@code LinkedHashMap<String, Object>} (mantém a ordem das chaves)</li>
 *   <li>array → {@code ArrayList<Object>}</li>
 *   <li>número inteiro → {@code Long}; número com ponto ou expoente → {@code Double}</li>
 *   <li>texto → {@code String}; true/false → {@code Boolean}; null → {@code null}</li>
 * </ul>
 * Para saber se uma chave veio com {@code null} ou não veio, use {@code Map.containsKey}.
 */
public final class Json {

    private static final int PROFUNDIDADE_MAXIMA = 64;

    private final String texto;
    private int pos;

    private Json(String texto) {
        this.texto = texto;
    }

    // ------------------------------------------------------------------ leitura

    /** Converte um texto JSON completo em objetos Java. Lança {@link JsonInvalidoException} se for inválido. */
    public static Object ler(String texto) {
        if (texto == null) {
            throw new JsonInvalidoException("texto nulo", 0);
        }
        Json leitor = new Json(texto);
        leitor.pularEspacos();
        Object valor = leitor.lerValor(0);
        leitor.pularEspacos();
        if (leitor.pos != texto.length()) {
            throw leitor.erro("conteúdo extra depois do JSON");
        }
        return valor;
    }

    private Object lerValor(int profundidade) {
        if (profundidade > PROFUNDIDADE_MAXIMA) {
            throw erro("JSON aninhado demais");
        }
        if (pos >= texto.length()) {
            throw erro("fim inesperado do texto");
        }
        char c = texto.charAt(pos);
        switch (c) {
            case '{':
                return lerObjeto(profundidade);
            case '[':
                return lerArray(profundidade);
            case '"':
                return lerTexto();
            case 't':
                esperarPalavra("true");
                return Boolean.TRUE;
            case 'f':
                esperarPalavra("false");
                return Boolean.FALSE;
            case 'n':
                esperarPalavra("null");
                return null;
            default:
                if (c == '-' || ehDigito(c)) {
                    return lerNumero();
                }
                throw erro("caractere inesperado '" + c + "'");
        }
    }

    private Map<String, Object> lerObjeto(int profundidade) {
        pos++; // '{'
        Map<String, Object> objeto = new LinkedHashMap<>();
        pularEspacos();
        if (olhar() == '}') {
            pos++;
            return objeto;
        }
        while (true) {
            pularEspacos();
            if (olhar() != '"') {
                throw erro("esperava o nome de um campo entre aspas");
            }
            String chave = lerTexto();
            pularEspacos();
            if (olhar() != ':') {
                throw erro("esperava ':' depois de \"" + chave + "\"");
            }
            pos++;
            pularEspacos();
            objeto.put(chave, lerValor(profundidade + 1));
            pularEspacos();
            char c = proximo();
            if (c == '}') {
                return objeto;
            }
            if (c != ',') {
                throw erro("esperava ',' ou '}' no objeto");
            }
        }
    }

    private List<Object> lerArray(int profundidade) {
        pos++; // '['
        List<Object> lista = new ArrayList<>();
        pularEspacos();
        if (olhar() == ']') {
            pos++;
            return lista;
        }
        while (true) {
            pularEspacos();
            lista.add(lerValor(profundidade + 1));
            pularEspacos();
            char c = proximo();
            if (c == ']') {
                return lista;
            }
            if (c != ',') {
                throw erro("esperava ',' ou ']' no array");
            }
        }
    }

    private String lerTexto() {
        pos++; // aspas de abertura
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= texto.length()) {
                throw erro("texto sem aspas de fechamento");
            }
            char c = texto.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                sb.append(lerEscape());
            } else if (c < 0x20) {
                throw erro("caractere de controle dentro de texto");
            } else {
                sb.append(c);
            }
        }
    }

    private char lerEscape() {
        if (pos >= texto.length()) {
            throw erro("escape incompleto");
        }
        char e = texto.charAt(pos++);
        switch (e) {
            case '"': return '"';
            case '\\': return '\\';
            case '/': return '/';
            case 'b': return '\b';
            case 'f': return '\f';
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'u':
                if (pos + 4 > texto.length()) {
                    throw erro("escape \\u incompleto");
                }
                int codigo = 0;
                for (int i = 0; i < 4; i++) {
                    int digito = Character.digit(texto.charAt(pos + i), 16);
                    if (digito < 0) {
                        throw erro("escape \\u com dígito hexadecimal inválido");
                    }
                    codigo = codigo * 16 + digito;
                }
                pos += 4;
                return (char) codigo; // pares substitutos (emoji) se juntam sozinhos no StringBuilder
            default:
                throw erro("escape inválido '\\" + e + "'");
        }
    }

    private Object lerNumero() {
        int inicio = pos;
        if (olhar() == '-') {
            pos++;
        }
        if (olhar() == '0') {
            pos++;
        } else if (ehDigito(olhar())) {
            while (ehDigito(olhar())) pos++;
        } else {
            throw erro("número inválido");
        }
        boolean decimal = false;
        if (olhar() == '.') {
            decimal = true;
            pos++;
            exigirDigitos();
        }
        if (olhar() == 'e' || olhar() == 'E') {
            decimal = true;
            pos++;
            if (olhar() == '+' || olhar() == '-') pos++;
            exigirDigitos();
        }
        String numero = texto.substring(inicio, pos);
        if (!decimal) {
            try {
                return Long.parseLong(numero);
            } catch (NumberFormatException grandeDemais) {
                return Double.parseDouble(numero);
            }
        }
        return Double.parseDouble(numero);
    }

    private void exigirDigitos() {
        if (!ehDigito(olhar())) {
            throw erro("número inválido");
        }
        while (ehDigito(olhar())) pos++;
    }

    private void esperarPalavra(String palavra) {
        if (!texto.startsWith(palavra, pos)) {
            throw erro("valor desconhecido");
        }
        pos += palavra.length();
    }

    private void pularEspacos() {
        while (pos < texto.length()) {
            char c = texto.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private char olhar() {
        return pos < texto.length() ? texto.charAt(pos) : '\0';
    }

    private char proximo() {
        if (pos >= texto.length()) {
            throw erro("fim inesperado do texto");
        }
        return texto.charAt(pos++);
    }

    private static boolean ehDigito(char c) {
        return c >= '0' && c <= '9';
    }

    private JsonInvalidoException erro(String motivo) {
        return new JsonInvalidoException(motivo, pos);
    }

    // ------------------------------------------------------------------ escrita

    /** Converte objetos Java (Map, Collection, String, Number, Boolean, null) em texto JSON compacto. */
    public static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escreverValor(sb, valor);
        return sb.toString();
    }

    private static void escreverValor(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String texto) {
            escreverTexto(sb, texto);
        } else if (valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Double || valor instanceof Float) {
            double d = ((Number) valor).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (valor instanceof Number) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escreverTexto(sb, String.valueOf(entrada.getKey()));
                sb.append(':');
                escreverValor(sb, entrada.getValue());
            }
            sb.append('}');
        } else if (valor instanceof Collection<?> lista) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : lista) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escreverValor(sb, item);
            }
            sb.append(']');
        } else {
            escreverTexto(sb, valor.toString());
        }
    }

    private static void escreverTexto(StringBuilder sb, String texto) {
        sb.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c); // acentos saem como estão (a mensagem é enviada em UTF-8)
                    }
            }
        }
        sb.append('"');
    }
}
