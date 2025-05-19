// util/CryptoUtils.java
package util;

import dao.ChaveiroDAO;
import model.Chaveiro;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CryptoUtils {

    // Mantém em memória a frase e a chave privada decifrada
    private static char[] currentPassphrase;
    private static PrivateKey currentPrivateKey;

    public static X509Certificate lerCertificadoPEM(String caminho) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(caminho)), StandardCharsets.UTF_8);

        // Filtra apenas o bloco Base64 entre os delimitadores
        String b64 = pem.replaceAll("(?s).*-----BEGIN CERTIFICATE-----", "")  // Remove tudo antes do BEGIN
                .replaceAll("-----END CERTIFICATE-----(?s).*", "")    // Remove tudo depois do END
                .replaceAll("\\s", "");                               // Remove espaços/quebras de linha

        byte[] der = Base64.getDecoder().decode(b64);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
    }

    /** Extrai e-mail do Subject DN (campo E=, EMAIL= ou OID). */
    public static String extrairEmailDoCertificado(X509Certificate cert) {
        String dn = cert.getSubjectX500Principal().getName();
        for (String parte : dn.split(",")) {
            parte = parte.trim();
            int eq = parte.indexOf('=');
            if (eq < 0) continue;
            String chave = parte.substring(0, eq).trim();
            String valor = parte.substring(eq + 1).trim();

            boolean isEmailOid = chave.equalsIgnoreCase("E")
                    || chave.equalsIgnoreCase("EMAIL")
                    || chave.equals("1.2.840.113549.1.9.1");

            if (!isEmailOid) continue;

            // Se vier como hex (padrão RFC2253), decodifica
            if (valor.startsWith("#")) {
                // retira o '#' e converte cada par de hex em byte
                byte[] der = hexStringToByteArray(valor.substring(1));
                // DER-IA5String tem tag 0x16, seguido de comprimento
                if (der.length > 2 && (der[0] & 0x1F) == 0x16) {
                    int len = der[1] & 0xFF;
                    return new String(der, 2, len, java.nio.charset.StandardCharsets.UTF_8);
                }
                // fallback: tenta decodificar tudo como UTF-8
                return new String(der, java.nio.charset.StandardCharsets.UTF_8);
            }

            // Se não for hex, retorna o valor cru
            return valor;
        }
        return null;
    }


    /** Extrai CN (Common Name) do Subject. */
    public static String extrairNomeDoCertificado(X509Certificate cert) {
        String dn = cert.getSubjectX500Principal().getName();
        for (String parte : dn.split(",")) {
            parte = parte.trim();
            if (parte.startsWith("CN=")) {
                return parte.substring(3).trim();
            }
        }
        return null;
    }

    /** Deriva SecretKey AES-256 via SHA1PRNG usando a frase como seed. */
    private static SecretKey gerarChaveAES(char[] passphrase) throws Exception {
        byte[] seed = new String(passphrase).getBytes(StandardCharsets.UTF_8);
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, sr);
        return kg.generateKey();
    }

    /** Cifra dados com AES/ECB/PKCS5Padding. */
    public static byte[] cifrarComAES256(byte[] dados, char[] passphrase) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, gerarChaveAES(passphrase));
        return c.doFinal(dados);
    }

    /** Decifra dados com AES/ECB/PKCS5Padding. */
    public static byte[] decifrarComAES256(byte[] dadosEnc, char[] passphrase) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, gerarChaveAES(passphrase));
        return c.doFinal(dadosEnc);
    }

    // Gera hash Bcrypt (custo 8). Você pode manter sua implementação.`,
    public static String gerarHashBcrypt(String senha) {
        // Parâmetros conforme especificação do trabalho:
        // - Versão: 2y
        // - Custo (iterações): 8 (2^8 = 256 iterações)
        // - SALT aleatório de 16 bytes (gerado internamente pelo OpenBSDBCrypt)
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt); // SALT aleatório

        // Gera o hash bcrypt (versão 2y, custo 8)
        String hash = OpenBSDBCrypt.generate(
                senha.toCharArray(), // Senha em char[]
                salt,               // SALT aleatório
                8                   // Custo (2^8 iterações)
        );

        return hash;
    }

    /**
     * 1) Lê bytes .enc (AES),
     * 2) decifra → texto PEM PKCS#8 completo,
     * 3) filtra linha a linha removendo cabeçalhos, metadados e delimitadores,
     * 4) Base64→DER,
     * 5) DER→PrivateKey via PKCS8EncodedKeySpec.
     */
    public static PrivateKey decifrarPrivateKeyPKCS8(String caminhoEnc, char[] passphrase) throws Exception {
        // 1) lê bytes criptografados
        byte[] enc     = Files.readAllBytes(Paths.get(caminhoEnc));
        // 2) decifra AES → resultado “decrypted”
        byte[] decrypted = decifrarComAES256(enc, passphrase);

        // 3) Se “decrypted” começar com 0x30 0x82 (DER), é DER puro
        if (decrypted.length > 2 && (decrypted[0] & 0xFF) == 0x30 && (decrypted[1] & 0xFF) == 0x82) {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decrypted);
            PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(spec);
            currentPassphrase = passphrase.clone();
            currentPrivateKey  = pk;
            return pk;
        }

        // 4) Caso contrário, trata como PEM Base64:
        String pem = new String(decrypted, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (String line : pem.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty())        continue;
            if (line.startsWith("-----")) continue; // retira BEGIN/END
            if (line.contains(":"))    continue; // retira Proc-Type, DEK-Info etc.
            sb.append(line);
        }
        byte[] der = Base64.getDecoder().decode(sb.toString());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(spec);

        currentPassphrase = passphrase.clone();
        currentPrivateKey  = pk;
        return pk;
    }

    /**
     * Valida se PrivateKey corresponde ao certificado, assinando/verificando
     * 8192 bytes com SHA256withRSA.
     */
    public static boolean validarPrivateKeyComCertificado(
            X509Certificate cert, PrivateKey priv) throws Exception {
        byte[] msg = new byte[8192];
        new SecureRandom().nextBytes(msg);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(priv);
        signer.update(msg);
        byte[] sig = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(cert.getPublicKey());
        verifier.update(msg);

        return verifier.verify(sig);
    }

    public static char[]     getCurrentPassphrase()    { return currentPassphrase; }
    public static PrivateKey getCurrentPrivateKey()    { return currentPrivateKey; }

    /**
     * Decifra o “envelope” RSA (arquivo .env) usando a private key.
     * Retorna o seed puro (byte[]).
     */
    public static byte[] decifrarEnvelope(File envFile, PrivateKey priv) throws Exception {
        byte[] env = Files.readAllBytes(envFile.toPath());
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE, priv);
        return rsa.doFinal(env);
    }

    /**
     * Gera a chave AES (128 bits) a partir do seed, usando SHA1PRNG conforme o enunciado.
     */
    public static byte[] generateAESKey(byte[] seed) throws Exception {
        SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
        prng.setSeed(seed);
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128, prng);
        SecretKey key = kg.generateKey();
        return key.getEncoded();
    }

    /**
     * Decripta dados com AES/ECB/PKCS5Padding.
     */
    public static byte[] decryptAesEcbPkcs5(byte[] data, byte[] aesKey) throws Exception {
        Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec spec = new SecretKeySpec(aesKey, "AES");
        aes.init(Cipher.DECRYPT_MODE, spec);
        return aes.doFinal(data);
    }

    /**
     * Lê do banco a entidade Chaveiro do usuário e decifra sua private key PKCS#8,
     * usando o mesmo método que você já tinha para arquivos.
     */
    public static PrivateKey getUserPrivateKey(int uid, char[] frase) throws Exception {
        Chaveiro c = new ChaveiroDAO().findByUid(uid);
        byte[] privBytes = decifrarComAES256(c.getPrivateKeyEnc(), frase); // seu método existente
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
    }

    /**
     * Carrega o certificado do administrador (usado para verificar as assinaturas).
     * Pode ler de um arquivo fixo, ou da tabela Chaveiro/Gerenciador de certificados.
     */
    public static X509Certificate loadAdminCert() throws Exception {
        // por exemplo, se você mantiver o PEM em resources/admin_cert.pem:
        try (InputStream in = CryptoUtils.class.getResourceAsStream("/admin_cert.pem")) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    /** Converte uma string hex (pares de dígitos) em um array de bytes */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Hex inválido em " + s);
            }
            data[i / 2] = (byte) ((hi << 4) + lo);
        }
        return data;
    }
}
