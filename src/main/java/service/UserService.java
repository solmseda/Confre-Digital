package service;

import dao.GrupoDAO;
import dao.UsuarioDAO;
import dao.ChaveiroDAO;
import model.Grupo;
import model.Usuario;
import model.Chaveiro;
import ui.AdminSetupFrame;
import util.Base32;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * Serviço de gerenciamento de usuários, incluindo inicialização do admin.
 */
public class UserService {
    private final UsuarioDAO usuarioDao;
    private final ChaveiroDAO chaveiroDao;
    private final CryptoService cryptoService;
    private final BcryptService bcryptService;
    private final LoggingService loggingService;
    private char[] adminPassphrase;
    private SecretKey adminAesKey;

    public UserService(UsuarioDAO usuarioDao,
                       ChaveiroDAO chaveiroDao,
                       CryptoService cryptoService,
                       BcryptService bcryptService,
                       LoggingService loggingService) {
        this.usuarioDao     = usuarioDao;
        this.chaveiroDao    = chaveiroDao;
        this.cryptoService  = cryptoService;
        this.bcryptService  = bcryptService;
        this.loggingService = loggingService;
    }

    /**
     * Executa o fluxo de "primeira partida" para cadastro do administrador.
     * Abre um formulário Swing para:
     *  1. Selecionar certificado PEM
     *  2. Selecionar chave privada criptografada
     *  3. Digitar frase secreta para derivar chave AES
     *  4. Definir senha de login e confirmação
     * Se não existirem usuários, cria o admin e registra logs de cada etapa.
     */
    public void initializeAdmin() throws Exception {
        // 1. Verifica se já existe usuário cadastrado
        if (!usuarioDao.findAll().isEmpty()) {
            return;
        }

        // inicializa o banco
        GrupoDAO grupoDao = new GrupoDAO();
        if (grupoDao.findAll().isEmpty()) {
            Grupo admin = new Grupo(); admin.setNomeGrupo("Administrador"); grupoDao.insert(admin);
            Grupo user = new Grupo(); user.setNomeGrupo("Usuário"); grupoDao.insert(user);
        }

        // 2. Exibe formulário e aguarda submissão
        AdminSetupFrame frame = new AdminSetupFrame();
        frame.setVisible(true);
        frame.waitForSubmit();
        if (!frame.isSubmitted()) {
            return;
        }

        Path certPath = frame.getCertificatePath();
        Path keyPath  = frame.getPrivateKeyPath();
        char[] passphrase = frame.getPassphrase();
        char[] password   = frame.getPassword();

        // 3.1 Deriva AES key e carrega certificado
        SecretKey aesKey = cryptoService.deriveAesKey(passphrase);
        loggingService.log("CERT_VALIDATION_STARTED", null, "Iniciando validação de certificado");
        X509Certificate cert = cryptoService.loadCertificate(certPath);

        // 3.2 Decripta chave privada
        byte[] encryptedKeyBytes = Files.readAllBytes(keyPath);
        PrivateKey privateKey = cryptoService.decryptPrivateKey(encryptedKeyBytes, aesKey);

        // 3.3 Valida frase secreta por assinatura de 8192 bytes
        byte[] challenge = new byte[8192];
        new SecureRandom().nextBytes(challenge);
        byte[] signature = cryptoService.signData(challenge, privateKey);
        if (!cryptoService.verifySignature(challenge, signature, cert)) {
            throw new SecurityException("Frase secreta inválida: assinatura não confere");
        }
        loggingService.log("CERT_VALIDATED", null, "Frase secreta validada");

        // 3.4 Gera hash bcrypt da senha e limpa o array de senha
        String hash = bcryptService.hashPassword(password);
        Arrays.fill(password, '\0');
        loggingService.log("PASSWORD_HASHED", null, "Senha do admin hasheada");

        // 3.5 Gera e armazena segredo TOTP
        byte[] rawTotp = new byte[20];
        new SecureRandom().nextBytes(rawTotp);
        String base32Secret = new Base32(Base32.Alphabet.BASE32, false, false)
                .toString(rawTotp)
                .trim()
                .replace("=", "");
        byte[] encTotp = cryptoService.encrypt(aesKey, base32Secret.getBytes(StandardCharsets.UTF_8));
        loggingService.log("TOTP_GENERATED", null, "Segredo TOTP gerado");

        // 3.6 Persiste Usuário
        Usuario u = new Usuario();
        u.setLoginEmail(frame.getEmail());
        u.setNome(frame.getName());
        u.setSenhaBcrypt(hash);
        u.setTotpSecretEnc(encTotp);
        u.setGid(frame.getSelectedGroupId());
        usuarioDao.insert(u);
        loggingService.log("USER_CREATED", u.getUid(), "Admin criado");

        // 3.7 Persiste Chaveiro usando os bytes originais da chave privada cifrada
        Chaveiro c = new Chaveiro();
        c.setUid(u.getUid());
        c.setCertPem(Files.readString(certPath, StandardCharsets.UTF_8));
        c.setPrivateKeyEnc(encryptedKeyBytes);
        chaveiroDao.insert(c);
        loggingService.log("CHAVEIRO_CREATED", u.getUid(), "Par certificado+chave persistido");

        // 4. Fecha o formulário
        frame.dispose();
    }

    /**
     * @return a SecretKey AES do admin, derivada na primeira partida
     */
    public SecretKey getAdminAesKey() {
        return adminAesKey;
    }

    /**
     * Zera o array da passphrase (para chamar no shutdown)
     */
    public void clearPassphrase() {
        if (adminPassphrase != null) {
            Arrays.fill(adminPassphrase, '\0');
            adminPassphrase = null;
        }
    }
}
