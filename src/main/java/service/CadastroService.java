/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

package service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dao.ChaveiroDAO;
import dao.UsuarioDAO;
import model.Chaveiro;
import model.Usuario;
import util.Base32;
import util.CryptoUtils;
import util.TOTPUtil;
import util.Logger;

import javax.swing.*;
import javax.swing.BoxLayout;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CadastroService {

    public void executarCadastro() {
        JTextField      certField   = new JTextField(40);
        JTextField      keyField    = new JTextField(40);
        JPasswordField  phraseF     = new JPasswordField(40);
        JPasswordField  passF       = new JPasswordField(10);
        JPasswordField  confirmF    = new JPasswordField(10);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Certificado digital:"));        panel.add(certField);
        panel.add(new JLabel("Chave privada:"));              panel.add(keyField);
        panel.add(new JLabel("Frase secreta:"));              panel.add(phraseF);
        panel.add(new JLabel("Senha pessoal (8-10 dígitos):")); panel.add(passF);
        panel.add(new JLabel("Confirmação da senha:"));       panel.add(confirmF);

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

            // 1) Leitura e extração do certificado
            X509Certificate cert = CryptoUtils.lerCertificadoPEM(certPath);
            String email = CryptoUtils.extrairEmailDoCertificado(cert);
            String nome  = CryptoUtils.extrairNomeDoCertificado(cert);
            if (email == null || nome == null) {
                JOptionPane.showMessageDialog(null, "Certificado sem e-mail ou CN.");
                return;
            }

            // 2) Confirmação visual dos dados do certificado
            String info = ""
                    + "Versão:     " + cert.getVersion()       + "\n"
                    + "Série:      " + cert.getSerialNumber() + "\n"
                    + "Validade:   de " + cert.getNotBefore()
                    + " até "      + cert.getNotAfter()       + "\n"
                    + "Assinatura: " + cert.getSigAlgName()   + "\n"
                    + "Emissor:    " + cert.getIssuerX500Principal().getName()  + "\n"
                    + "Sujeito:    " + cert.getSubjectX500Principal().getName() + "\n"
                    + "E-mail:     " + email;
            if (JOptionPane.showConfirmDialog(null, info,
                    "Confirme os dados do certificado", JOptionPane.OK_CANCEL_OPTION)
                    != JOptionPane.OK_OPTION) {
                JOptionPane.showMessageDialog(null, "Cadastro cancelado.");
                return;
            }

            // 3) Decifra e valida a chave privada
            PrivateKey priv = CryptoUtils.decifrarPrivateKeyPKCS8(keyPath, phrase);
            if (!CryptoUtils.validarPrivateKeyComCertificado(cert, priv)) {
                JOptionPane.showMessageDialog(null, "Frase ou chave inválida.");
                return;
            }

            // 4) Gera e cifra o segredo TOTP
            byte[] totpSecret    = TOTPUtil.gerarChaveSecreta();
            byte[] totpSecretEnc = CryptoUtils.cifrarComAES256(totpSecret, senha.toCharArray());

            // 5) Persiste o usuário no banco
            Usuario u = new Usuario();
            u.setLoginEmail(email);
            u.setNome(nome);
            u.setSenhaBcrypt(CryptoUtils.gerarHashBcrypt(senha));
            u.setTotpSecretEnc(totpSecretEnc);
            u.setGid(1);  // administrador
            new UsuarioDAO().insert(u);

            // 6) Persiste o Chaveiro
            Chaveiro c = new Chaveiro();
            c.setUid(u.getUid());
            c.setCertPem(new String(Files.readAllBytes(new File(certPath).toPath()),
                    StandardCharsets.UTF_8));
            c.setPrivateKeyEnc(CryptoUtils.cifrarComAES256(priv.getEncoded(), phrase));
            new ChaveiroDAO().insert(c);

            // 7) Converte segredo em Base32
            String b32 = new Base32(Base32.Alphabet.BASE32, false, false)
                    .toString(totpSecret);

            // 8) Exibe o segredo Base32 e a URI TOTP
            String issuer = "Cofre Digital";
            String label  = URLEncoder.encode(issuer + ":" + email, StandardCharsets.UTF_8.name());
            String issuerQ= URLEncoder.encode(issuer, StandardCharsets.UTF_8.name());
            String otpUri = String.format(
                    "otpauth://totp/%s?secret=%s&issuer=%s",
                    label, b32, issuerQ
            );

            JOptionPane.showMessageDialog(null,
                    "Segredo TOTP (Base32):\n" + b32 + "\n\nURI:\n" + otpUri,
                    "TOTP Secret", JOptionPane.INFORMATION_MESSAGE
            );

            // 9) Gera o QR Code a partir da URI
            QRCodeWriter qrWriter = new QRCodeWriter();
            Map<EncodeHintType,Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = qrWriter.encode(otpUri,
                    BarcodeFormat.QR_CODE,
                    200, 200,
                    hints);
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(matrix);

            // 10) Exibe o QR Code em diálogo
            JLabel picLabel = new JLabel(new ImageIcon(qrImage));
            picLabel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            JPanel qrPanel = new JPanel(new BorderLayout());
            qrPanel.add(new JLabel("Escaneie este QR Code no Google Authenticator:"), BorderLayout.NORTH);
            qrPanel.add(picLabel, BorderLayout.CENTER);

            JOptionPane.showMessageDialog(null,
                    qrPanel,
                    "QR Code TOTP",
                    JOptionPane.PLAIN_MESSAGE
            );

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Erro no cadastro: " + ex.getClass().getSimpleName()
                            + ": " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        } finally {
            // Limpa frases e senhas da memória
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
