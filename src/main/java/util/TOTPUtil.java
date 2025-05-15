package util;

import java.security.SecureRandom;

public class TOTPUtil {

    /**
     * Gera uma chave secreta TOTP com 20 bytes aleatórios (160 bits).
     * Essa chave será usada no algoritmo HMAC-SHA1 para gerar códigos TOTP.
     */
    public static byte[] gerarChaveSecreta() {
        SecureRandom random = new SecureRandom();
        byte[] chave = new byte[20]; // 160 bits
        random.nextBytes(chave);
        return chave;
    }
}
