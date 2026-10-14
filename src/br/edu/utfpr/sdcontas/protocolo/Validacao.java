package br.edu.utfpr.sdcontas.protocolo;

/**
 * Regras de validação de campos do documento de requisitos.
 * Escritas como laços explícitos (sem regex) para ficar fácil de explicar regra por regra.
 */
public final class Validacao {

    private Validacao() {
    }

    public static final int NOME_MIN = 1;
    public static final int NOME_MAX = 60;
    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 20;
    public static final int SENHA_MIN = 8;
    public static final int SENHA_MAX = 20;
    public static final String ESPECIAIS_SENHA = "#.*&%$@!()-_=+";

    public static final String REGRA_NOME =
            "Nome: só letras e espaços, de 1 a 60 caracteres.";
    public static final String REGRA_USERNAME =
            "Username: 3 a 20 caracteres, só letras minúsculas, números, '.' e '_' (sem acento e sem espaço).";
    public static final String REGRA_SENHA =
            "Senha: 8 a 20 caracteres, com pelo menos 1 maiúscula, 1 minúscula, 1 número e 1 símbolo entre "
                    + "# . * & % $ @ ! ( ) - _ = +";

    /**
     * name: letras maiúsculas e minúsculas, pode ter espaços, sem símbolos, 1 a 60 caracteres.
     * Letras acentuadas são aceitas (a especificação só proíbe acento no username).
     */
    public static boolean nomeValido(String nome) {
        if (nome == null) {
            return false;
        }
        int tamanho = nome.codePointCount(0, nome.length());
        if (tamanho < NOME_MIN || tamanho > NOME_MAX) {
            return false;
        }
        boolean temLetra = false;
        for (int i = 0; i < nome.length(); ) {
            int caractere = nome.codePointAt(i);
            if (Character.isLetter(caractere)) {
                temLetra = true;
            } else if (caractere != ' ') {
                return false;
            }
            i += Character.charCount(caractere);
        }
        return temLetra; // "   " não é um nome
    }

    /** username: letras minúsculas, números, '.' e '_', sem acento e sem espaço, 3 a 20 caracteres. */
    public static boolean usernameValido(String username) {
        if (username == null || username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            return false;
        }
        for (char c : username.toCharArray()) {
            boolean permitido = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_';
            if (!permitido) {
                return false;
            }
        }
        return true;
    }

    /**
     * password: 8 a 20 caracteres, só letras, números e os símbolos permitidos,
     * com no mínimo 1 maiúscula, 1 minúscula, 1 número e 1 símbolo.
     */
    public static boolean senhaValida(String senha) {
        if (senha == null || senha.length() < SENHA_MIN || senha.length() > SENHA_MAX) {
            return false;
        }
        boolean maiuscula = false;
        boolean minuscula = false;
        boolean numero = false;
        boolean simbolo = false;
        for (char c : senha.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                maiuscula = true;
            } else if (c >= 'a' && c <= 'z') {
                minuscula = true;
            } else if (c >= '0' && c <= '9') {
                numero = true;
            } else if (ESPECIAIS_SENHA.indexOf(c) >= 0) {
                simbolo = true;
            } else {
                return false; // caractere fora da lista (espaço, '?', acento...)
            }
        }
        return maiuscula && minuscula && numero && simbolo;
    }
}
