package util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

public class TOTP {
    private byte[] key = null;
    private long timeStepInSeconds = 30;

    // Construtor da classe. Recebe a chave secreta em BASE32 e o intervalo
    // de tempo a ser adotado (default = 30 segundos). Decodifica a chave
    // secreta usando sua classe Base32.
    public TOTP(String base32EncodedSecret, long timeStepInSeconds) throws Exception {
        this.timeStepInSeconds = timeStepInSeconds;
        Base32 b32 = new Base32(Base32.Alphabet.BASE32, false, false);
        this.key = b32.fromString(base32EncodedSecret);
        if (this.key == null) {
            throw new IllegalArgumentException("Chave Base32 inválida");
        }
    }

    // Recebe o HASH HMAC-SHA1 e determina o código TOTP de 6 algarismos
    // decimais, prefixado com zeros quando necessário.
    private String getTOTPCodeFromHash(byte[] hash) {
        // dynamic truncation
        int offset = hash[hash.length - 1] & 0x0F;
        int binary =
                ((hash[offset]     & 0x7F) << 24) |
                        ((hash[offset + 1] & 0xFF) << 16) |
                        ((hash[offset + 2] & 0xFF) <<  8) |
                        ( hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        // formata em 6 dígitos com zeros à esquerda
        return String.format("%06d", otp);
    }

    // Recebe o contador e a chave secreta para produzir o hash HMAC-SHA1.
    private byte[] HMAC_SHA1(byte[] counter, byte[] keyByteArray) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(keyByteArray, "HmacSHA1");
            mac.init(keySpec);
            return mac.doFinal(counter);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar HMAC-SHA1", e);
        }
    }

    // Recebe o intervalo de tempo e executa o algoritmo TOTP para produzir
    // o código TOTP. Usa os métodos auxiliares getTOTPCodeFromHash e HMAC_SHA1.
    private String TOTPCode(long timeInterval) {
        // contador de 8 bytes (big-endian)
        byte[] counter = ByteBuffer.allocate(8).putLong(timeInterval).array();
        byte[] hash = HMAC_SHA1(counter, key);
        return getTOTPCodeFromHash(hash);
    }

    // Método que é utilizado para solicitar a geração do código TOTP.
    @SuppressWarnings("unused")
    public String generateCode() {
        long currentTimeSeconds = System.currentTimeMillis() / 1000L;
        long t = currentTimeSeconds / timeStepInSeconds;
        return TOTPCode(t);
    }

    // Método que é utilizado para validar um código TOTP (inputTOTP).
    // Considera um atraso ou adiantamento de 1 intervalo (± timeStepInSeconds).
    @SuppressWarnings("unused")
    public boolean validateCode(String inputTOTP) {
        long currentTimeSeconds = System.currentTimeMillis() / 1000L;
        long t = currentTimeSeconds / timeStepInSeconds;

        // janela de -1, 0 e +1 passos
        for (int i = -1; i <= 1; i++) {
            if (TOTPCode(t + i).equals(inputTOTP)) {
                return true;
            }
        }
        return false;
    }
}