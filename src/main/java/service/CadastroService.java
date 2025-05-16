// service/CadastroService.java
package service;

import dao.ChaveiroDAO;
import dao.UsuarioDAO;
import model.Chaveiro;
import model.Usuario;
import util.Base32;
import util.CryptoUtils;
import util.TOTPUtil;

import javax.swing.*;
import javax.swing.BoxLayout;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public class CadastroService {

    public void executarCadastro() {
        JTextField  certField    = new JTextField(40);
        JTextField  keyField     = new JTextField(40);
        JPasswordField phraseF   = new JPasswordField(40);
        JPasswordField passF     = new JPasswordField(10);
        JPasswordField confirmF  = new JPasswordField(10);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Certificado digital:"));   panel.add(certField);
        panel.add(new JLabel("Chave privada:"));         panel.add(keyField);
        panel.add(new JLabel("Frase secreta:"));                panel.add(phraseF);
        panel.add(new JLabel("Senha pessoal (8-10 dígitos):")); panel.add(passF);
        panel.add(new JLabel("Confirmação da senha:"));         panel.add(confirmF);

        if (JOptionPane.showConfirmDialog(null, panel, "Cadastro do Administrador",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }

        String certPath = certField.getText().trim();
        String keyPath  = keyField.getText().trim();
        char[] phrase   = phraseF.getPassword();
        String senha    = new String(passF.getPassword());
        String confirm  = new String(confirmF.getPassword());

        try {
            if (!validarDados(senha, confirm, certPath, keyPath)) return;

            //Carrega e extrai do certificado
            X509Certificate cert = CryptoUtils.lerCertificadoPEM(certPath);
            String email = CryptoUtils.extrairEmailDoCertificado(cert);
            String nome  = CryptoUtils.extrairNomeDoCertificado(cert);
            if (email == null || nome == null) {
                JOptionPane.showMessageDialog(null, "Certificado sem e-mail ou CN.");
                return;
            }

            //Mostra confirmação completa do certificado
            String info = ""
                    + "Versão:     " + cert.getVersion()           + "\n"
                    + "Série:      " + cert.getSerialNumber()     + "\n"
                    + "Validade:   de " + cert.getNotBefore()
                    + " até "     + cert.getNotAfter()         + "\n"
                    + "Assinatura: " + cert.getSigAlgName()        + "\n"
                    + "Emissor:    " + cert.getIssuerX500Principal().getName()  + "\n"
                    + "Sujeito:    " + cert.getSubjectX500Principal().getName() + "\n"
                    + "E-mail:     " + email;
            if (JOptionPane.showConfirmDialog(null, info,
                    "Confirme os dados do certificado", JOptionPane.OK_CANCEL_OPTION)
                    != JOptionPane.OK_OPTION) {
                JOptionPane.showMessageDialog(null, "Cadastro cancelado.");
                return;
            }

            //Decifra a chave privada PKCS#8 e valida com o certificado
            PrivateKey priv = CryptoUtils.decifrarPrivateKeyPKCS8(keyPath, phrase);
            if (!CryptoUtils.validarPrivateKeyComCertificado(cert, priv)) {
                JOptionPane.showMessageDialog(null, "Frase ou chave inválida.");
                return;
            }

            //Gera e cifra o segredo TOTP com AES da senha pessoal
            byte[] totpSecret    = TOTPUtil.gerarChaveSecreta();
            byte[] totpSecretEnc = CryptoUtils.cifrarComAES256(totpSecret, senha.toCharArray());

            //Persiste o usuário
            Usuario u = new Usuario();
            u.setLoginEmail(email);
            u.setNome(nome);
            u.setSenhaBcrypt(CryptoUtils.gerarHashBcrypt(senha));
            u.setTotpSecretEnc(totpSecretEnc);
            u.setGid(1);  // administrador
            new UsuarioDAO().insert(u);

            //Persiste o Chaveiro
            Chaveiro c = new Chaveiro();
            c.setUid(u.getUid());
            c.setCertPem(new String(Files.readAllBytes(new File(certPath).toPath()),
                    StandardCharsets.UTF_8));
            c.setPrivateKeyEnc(CryptoUtils.cifrarComAES256(priv.getEncoded(), phrase));
            new ChaveiroDAO().insert(c);

            //Exibe Base32 e URI para QR Code
            String b32 = new Base32(Base32.Alphabet.BASE32, false, false)
                    .toString(totpSecret);
            JOptionPane.showMessageDialog(null,
                    "Segredo TOTP:\n" + b32 +
                            "\n\nURI:\n" +
                            "otpauth://totp/Cofre%20Digital:" + email +
                            "?secret=" + b32
            );

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Erro no cadastro: " + ex.getClass().getSimpleName()
                            + ": " + ex.getMessage()
            );
        } finally {
            //Limpando senhas/arrays da UI
            Arrays.fill(phrase, ' ');
            Arrays.fill(passF.getPassword(), ' ');
            Arrays.fill(confirmF.getPassword(), ' ');
        }
    }

    private boolean validarDados(String senha, String confirm,
                                 String certPath, String keyPath) {
        if (!senha.equals(confirm)) {
            JOptionPane.showMessageDialog(null, "As senhas não coincidem.");
            return false;
        }
        if (!senha.matches("\\d{8,10}")) {
            JOptionPane.showMessageDialog(null, "Senha deve ter 8–10 dígitos.");
            return false;
        }
        for (int i = 0; i + 1 < senha.length(); i++) {
            if (senha.charAt(i) == senha.charAt(i + 1)) {
                JOptionPane.showMessageDialog(null,
                        "Senha não pode ter dígitos repetidos consecutivos."
                );
                return false;
            }
        }
        if (!new File(certPath).exists()) {
            JOptionPane.showMessageDialog(null, "Certificado não encontrado.");
            return false;
        }
        if (!new File(keyPath).exists()) {
            JOptionPane.showMessageDialog(null, "Chave privada não encontrada.");
            return false;
        }
        return true;
    }
}
