package br.edu.utfpr.sdcontas.servidor;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de senha com PBKDF2-HMAC-SHA256 e sal aleatório (tudo da própria JDK).
 * Formato guardado: {@code pbkdf2-sha256$iteracoes$salBase64$hashBase64}.
 *
 * <p>Se o arquivo de usuários vazar, ninguém lê as senhas. O protocolo não muda:
 * o cliente continua mandando a senha e o servidor compara com o hash.
 */
final class Senhas {

    private static final String PREFIXO = "pbkdf2-sha256";
    private static final int ITERACOES = 65_536;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Hash de uma senha qualquer, usado para gastar o mesmo tempo quando o usuário não existe. */
    static final String HASH_FICTICIO = gerarHash("Senha-ficticia#1");

    private Senhas() {
    }

    static String gerarHash(String senha) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = pbkdf2(senha, sal, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIXO + "$" + ITERACOES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    static boolean confere(String senha, String armazenado) {
        if (senha == null || armazenado == null) {
            return false;
        }
        String[] partes = armazenado.split("\\$");
        if (partes.length != 4 || !PREFIXO.equals(partes[0])) {
            return false;
        }
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = pbkdf2(senha, sal, iteracoes);
            return MessageDigest.isEqual(esperado, calculado); // comparação em tempo constante
        } catch (IllegalArgumentException formatoInvalido) {
            return false;
        }
    }

    private static byte[] pbkdf2(String senha, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível nesta JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
