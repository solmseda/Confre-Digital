import dao.ChaveiroDAO;
import dao.UsuarioDAO;
import model.Chaveiro;
import service.CadastroService;
import util.CryptoUtils;
import util.Logger;
import ui.LoginFrame;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UsuarioDAO usuarioDAO = new UsuarioDAO();
                //Logger.registra("1001");

                if (usuarioDAO.findAll().isEmpty()) {
                    // --- PRIMEIRA EXECUÇÃO ---
                    JOptionPane.showMessageDialog(null,
                            "Primeira execução detectada. Vamos cadastrar o administrador.",
                            "Cofre Digital – Configuração Inicial",
                            JOptionPane.INFORMATION_MESSAGE);
                    //Logger.registra("1005");

                    new CadastroService().executarCadastro();

                } else {
                    // --- A PARTIR DA SEGUNDA EXECUÇÃO ---
                    JOptionPane.showMessageDialog(null,
                            "Informe a frase secreta do administrador para iniciar o sistema.",
                            "Cofre Digital – Autenticação do Admin",
                            JOptionPane.INFORMATION_MESSAGE);

                    // 1) pede a frase secreta do admin
                    JPasswordField pf = new JPasswordField();
                    int ok = JOptionPane.showConfirmDialog(
                            null,
                            pf,
                            "Frase secreta do administrador:",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.PLAIN_MESSAGE
                    );
                    if (ok != JOptionPane.OK_OPTION) {
                        System.exit(0);
                    }
                    char[] adminPass = pf.getPassword();

                    // 2) carrega do banco o registro cifrado do admin (UID = 1)
                    Chaveiro adminCh = new ChaveiroDAO().findByUid(1);
                    byte[] encKey = adminCh.getPrivateKeyEnc();

                    // 3) decifra o .key em memória (usa PKCS#8 + AES)
                    File tmp = File.createTempFile("adm_priv", ".key");
                    Files.write(tmp.toPath(), encKey);
                    // isso já vai setar CryptoUtils.currentPrivateKey e currentPassphrase
                    CryptoUtils.decifrarPrivateKeyPKCS8(tmp.getAbsolutePath(), adminPass);
                    tmp.delete();

                    // 4) extrai e valida contra o certificado PEM do admin
                    byte[] pemBytes = adminCh.getCertPem()
                            .getBytes(StandardCharsets.UTF_8);
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate adminCert = (X509Certificate)
                            cf.generateCertificate(new ByteArrayInputStream(pemBytes));

                    boolean okKey = CryptoUtils.validarPrivateKeyComCertificado(
                            adminCert,
                            CryptoUtils.getCurrentPrivateKey()
                    );
                    if (!okKey) {
                        JOptionPane.showMessageDialog(null,
                                "Frase secreta inválida. O sistema será encerrado.",
                                "Erro",
                                JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }

                    //Logger.registra("1007");  // código de evento: admin autenticado
                }

                // --- se passou, inicia a autenticação de usuários ---
                LoginFrame login = new LoginFrame();
                login.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                login.setVisible(true);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Erro fatal ao iniciar o sistema:\n" + e.getMessage(),
                        "Erro",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
