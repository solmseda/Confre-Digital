package util;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CryptoUtils {

    // === Certificado Digital ===

    public static X509Certificate lerCertificadoPEM(String caminho) throws Exception {
        try (FileInputStream fis = new FileInputStream(caminho)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(fis);
        }
    }

    public static String extrairEmailDoCertificado(X509Certificate cert) {
        if (cert == null) return null;

        X500Principal subject = cert.getSubjectX500Principal();
        String subjectDN = subject.getName();

        for (String parte : subjectDN.split(",")) {
            parte = parte.trim();
            if (parte.startsWith("E=") || parte.startsWith("EMAIL=") ||
                    parte.startsWith("1.2.840.113549.1.9.1=")) {
                return parte.substring(parte.indexOf('=') + 1).trim();
            }
        }

        // Fallback para CN se parecer email
        for (String parte : subjectDN.split(",")) {
            parte = parte.trim();
            if (parte.startsWith("CN=")) {
                String cnValue = parte.substring(3).trim();
                if (cnValue.contains("@") && cnValue.contains(".")) {
                    return cnValue;
                }
            }
        }

        return null;
    }

    public static String extrairNomeDoCertificado(X509Certificate cert) {
        String subject = cert.getSubjectX500Principal().getName();
        for (String part : subject.split(",")) {
            part = part.trim();
            if (part.startsWith("CN=")) {
                return part.substring(part.indexOf('=') + 1).trim();
            }
        }
        return null;
    }

    // === Hash Bcrypt ===

    public static String gerarHashBcrypt(String senha) {
        return OpenBSDBCrypt.generate(senha.toCharArray(), gerarSalt(16), 8);
    }

    private static byte[] gerarSalt(int tamanho) {
        byte[] salt = new byte[tamanho];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    // === AES 256 (ECB como especificado) ===

    public static byte[] cifrarComAES256(byte[] dados, String senha) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, gerarChaveAES(senha));
        return cipher.doFinal(dados);
    }

    public static byte[] decifrarComAES256(byte[] dadosCifrados, String senha) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, gerarChaveAES(senha));
        return cipher.doFinal(dadosCifrados);
    }

    private static SecretKey gerarChaveAES(String senha) throws Exception {
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(senha.getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[32]; // 256 bits
        sr.nextBytes(key);
        return new SecretKeySpec(key, "AES");
    }

    // === Validação chave privada ===

    public static byte[] validarChaveComCertificado(X509Certificate cert, byte[] chavePrivadaBytes) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(chavePrivadaBytes));

        // Mensagem teste de 8192 bytes como especificado
        byte[] mensagem = new byte[8192];
        new SecureRandom().nextBytes(mensagem);

        // Assina
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(mensagem);
        byte[] assinatura = signer.sign();

        // Verifica com a chave pública do certificado
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(cert.getPublicKey());
        verifier.update(mensagem);

        return verifier.verify(assinatura) ? chavePrivadaBytes : null;
    }
}