package service;

import dao.UsuarioDAO;
import model.Usuario;
import util.TOTP;                 // ajustar para o pacote onde ficou sua classe TOTP
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Serviço para geração e validação de códigos TOTP.
 */
public class TotpService {
    private final UsuarioDAO usuarioDao;
    private final CryptoService cryptoService;
    private final SecretKey aesKey;
    private final long timeStepInSeconds;

    public TotpService(UsuarioDAO usuarioDao,
                       CryptoService cryptoService,
                       SecretKey aesKey,
                       long timeStepInSeconds) {
        this.usuarioDao = usuarioDao;
        this.cryptoService = cryptoService;
        this.aesKey = aesKey;
        this.timeStepInSeconds = timeStepInSeconds;
    }

    public String generateCurrentCode(int uid) throws Exception {
        Usuario u = usuarioDao.findById(uid);
        if (u == null) {
            throw new IllegalArgumentException("Usuário não encontrado: " + uid);
        }
        // obtém o segredo cifrado
        byte[] encrypted = u.getTotpSecretEnc();
        // usa o método genérico decrypt (AES) para recuperar o segredo Base32
        byte[] decrypted = cryptoService.decrypt(aesKey, encrypted);
        String base32Secret = new String(decrypted, StandardCharsets.UTF_8);

        // gera o código TOTP
        TOTP totp = new TOTP(base32Secret, timeStepInSeconds);
        return totp.generateCode();
    }

    public boolean validateCode(int uid, String input) throws Exception {
        Usuario u = usuarioDao.findById(uid);
        if (u == null) {
            throw new IllegalArgumentException("Usuário não encontrado: " + uid);
        }
        byte[] encrypted = u.getTotpSecretEnc();
        byte[] decrypted = cryptoService.decrypt(aesKey, encrypted);
        String base32Secret = new String(decrypted, StandardCharsets.UTF_8);

        TOTP totp = new TOTP(base32Secret, timeStepInSeconds);
        return totp.validateCode(input);
    }
}
