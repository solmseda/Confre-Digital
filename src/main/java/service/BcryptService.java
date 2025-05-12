package service;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import java.security.SecureRandom;

/**
 * Serviço de hashing e verificação de senhas usando Bcrypt (OpenBSDBCrypt).
 */
public class BcryptService {
    // Fator de trabalho: log2(rounds) = 8 -> 2^8 = 256 iterações
    private static final int COST = 8;
    private static final int SALT_LEN = 16;  // 128-bit salt, padrão Bcrypt

    /**
     * Gera um hash Bcrypt a partir da senha em cleartext.
     * @param password senha em char[]
     * @return hash completo no formato $2a$...
     */
    public String hashPassword(char[] password) {
        // Gera salt aleatório
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        // Gera hash Bcrypt
        return OpenBSDBCrypt.generate(password, salt, COST);
    }

    /**
     * Verifica se a senha em cleartext corresponde ao hash Bcrypt armazenado.
     * @param password senha em char[]
     * @param bcryptHash hash retornado por hashPassword
     * @return true se válido, false caso contrário
     */
    public boolean verifyPassword(char[] password, String bcryptHash) {
        try {
            return OpenBSDBCrypt.checkPassword(bcryptHash, password);
        } catch (IllegalArgumentException ex) {
            // hash malformado
            return false;
        }
    }
}