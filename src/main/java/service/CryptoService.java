package service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * Serviço de criptografia para:
 * - derivar chave AES via SHA1PRNG
 * - decriptar chave privada (AES/ECB/PKCS5Padding)
 * - carregar certificado X.509 a partir de PEM
 * - assinar dados com chave privada
 * - verificar assinaturas com chave pública do certificado
 * - encriptar/decriptar dados genéricos com AES/ECB/PKCS5Padding
 */
public class CryptoService {

    /**
     * Deriva uma chave AES-128 a partir de uma frase secreta
     * usando SHA1PRNG como gerador de entropia.
     */
    public SecretKey deriveAesKey(char[] passphrase) throws Exception {
        byte[] seed = new String(passphrase).getBytes(StandardCharsets.UTF_8);
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128, sr);
        return kg.generateKey();
    }

    /**
     * Decripta bytes de chave privada (formato PKCS#8) com AES/ECB/PKCS5Padding.
     */
    public PrivateKey decryptPrivateKey(byte[] encryptedKey, SecretKey aesKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        byte[] pkcs8Bytes = cipher.doFinal(encryptedKey);

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Bytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    /**
     * Carrega um certificado X.509 a partir de um arquivo PEM.
     */
    public X509Certificate loadCertificate(Path pemPath) throws Exception {
        try (FileInputStream fis = new FileInputStream(pemPath.toFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(fis);
        }
    }

    /**
     * Assina dados usando SHA256withRSA e a chave privada.
     */
    public byte[] signData(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(data);
        return signer.sign();
    }

    /**
     * Verifica uma assinatura SHA256withRSA usando a chave pública do certificado.
     */
    public boolean verifySignature(byte[] data, byte[] signatureBytes, X509Certificate cert) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(cert.getPublicKey());
        verifier.update(data);
        return verifier.verify(signatureBytes);
    }

    /**
     * Encripta dados com AES/ECB/PKCS5Padding.
     */
    public byte[] encrypt(SecretKey aesKey, byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        return cipher.doFinal(plain);
    }

    /**
     * Decripta dados com AES/ECB/PKCS5Padding.
     */
    public byte[] decrypt(SecretKey aesKey, byte[] cipherText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        return cipher.doFinal(cipherText);
    }
}