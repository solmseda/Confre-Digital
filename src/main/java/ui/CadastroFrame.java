/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

package ui;

import dao.GrupoDAO;
import dao.UsuarioDAO;
import dao.ChaveiroDAO;
import dao.RegistroDAO;
import model.Chaveiro;
import model.Registro;
import model.Usuario;
import util.CryptoUtils;
import util.TOTPUtil;
import util.Base32;
import util.QRCodeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * JFrame para o Cadastro de Usuário no Cofre Digital.
 * Permite ao administrador cadastrar novos usuários com
 * geração de TOTP e QR Code de configuração.
 */
public class CadastroFrame extends JFrame {
    private final Usuario currentUser;
    private final UsuarioDAO usuarioDao   = new UsuarioDAO();
    private final GrupoDAO   grupoDao     = new GrupoDAO();
    private final ChaveiroDAO chaveiroDao = new ChaveiroDAO();
    private final RegistroDAO registroDao = new RegistroDAO();

    private final JTextField certField    = new JTextField(40);
    private final JTextField keyField     = new JTextField(40);
    private final JPasswordField phraseF  = new JPasswordField(40);
    private final JComboBox<String> groupCombo;
    private final JPasswordField passF    = new JPasswordField(10);
    private final JPasswordField confirmF = new JPasswordField(10);

    public CadastroFrame(Usuario user) throws SQLException {
        super("Cofre Digital – Cadastro de Usuário");
        this.currentUser = user;
        groupCombo = new JComboBox<>(new String[]{"Administrador","Usuário Comum"});
        initComponents();
    }

    private void initComponents() throws SQLException {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        // Cabeçalho
        JPanel header = new JPanel(new GridLayout(3,1));
        header.setBorder(BorderFactory.createTitledBorder("Dados do Administrador"));
        header.add(new JLabel("Login:  " + currentUser.getLoginEmail()));
        header.add(new JLabel("Grupo:  " + grupoDao.findById(currentUser.getGid()).getNomeGrupo()));
        header.add(new JLabel("Nome:   " + currentUser.getNome()));
        add(header, BorderLayout.NORTH);

        // Corpo de formulário
        JPanel corpo = new JPanel(new GridBagLayout());
        corpo.setBorder(BorderFactory.createTitledBorder("Formulário de Cadastro"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx=0; c.gridy=0; corpo.add(new JLabel("Certificado digital:"), c);
        c.gridx=1; corpo.add(certField, c);
        c.gridx=0; c.gridy++; corpo.add(new JLabel("Chave privada:"), c);
        c.gridx=1; corpo.add(keyField, c);
        c.gridx=0; c.gridy++; corpo.add(new JLabel("Frase secreta:"), c);
        c.gridx=1; corpo.add(phraseF, c);
        c.gridx=0; c.gridy++; corpo.add(new JLabel("Grupo:"), c);
        c.gridx=1; corpo.add(groupCombo, c);
        c.gridx=0; c.gridy++; corpo.add(new JLabel("Senha pessoal:"), c);
        c.gridx=1; corpo.add(passF, c);
        c.gridx=0; c.gridy++; corpo.add(new JLabel("Confirmação senha:"), c);
        c.gridx=1; corpo.add(confirmF, c);

        // Botões
        JPanel buttons = new JPanel();
        JButton btnCadastrar = new JButton("Cadastrar");
        JButton btnVoltar    = new JButton("Voltar");
        buttons.add(btnCadastrar);
        buttons.add(btnVoltar);

        // Layout final
        add(corpo, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);

        // Ações
        btnVoltar.addActionListener(e -> {
            dispose();
            try {
                new TelaPrincipal(currentUser).setVisible(true);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCadastrar.addActionListener(e -> {
            try {
                String certPath = certField.getText().trim();
                String keyPath  = keyField.getText().trim();
                char[] phrase   = phraseF.getPassword();
                String senha    = new String(passF.getPassword());
                String confirm  = new String(confirmF.getPassword());
                int gidNovo     = groupCombo.getSelectedIndex() == 0 ? 1 : 2;

                // Valida senhas
                if (!senha.equals(confirm)) {
                    JOptionPane.showMessageDialog(this, "Senhas não coincidem.", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 1) Carrega e valida certificado
                X509Certificate cert = CryptoUtils.lerCertificadoPEM(certPath);
                String email = CryptoUtils.extrairEmailDoCertificado(cert);
                String nome  = CryptoUtils.extrairNomeDoCertificado(cert);
                String info = String.format(
                        "Versão: %d\nSérie: %s\nValidade: de %s até %s\nAssinatura: %s\nEmissor: %s\nSujeito: %s\nE-mail: %s",
                        cert.getVersion(), cert.getSerialNumber(), cert.getNotBefore(), cert.getNotAfter(),
                        cert.getSigAlgName(), cert.getIssuerX500Principal().getName(),
                        cert.getSubjectX500Principal().getName(), email
                );
                if (JOptionPane.showConfirmDialog(this, info, "Confirme os dados do certificado", JOptionPane.OK_CANCEL_OPTION)
                        != JOptionPane.OK_OPTION) {
                    JOptionPane.showMessageDialog(this, "Cadastro cancelado.");
                    return;
                }

                // 2) Unicidade de login
                if (usuarioDao.findByEmail(email) != null) {
                    JOptionPane.showMessageDialog(this, "E-mail já cadastrado.", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 3) Decifra e valida chave privada
                PrivateKey priv = CryptoUtils.decifrarPrivateKeyPKCS8(keyPath, phrase);
                if (!CryptoUtils.validarPrivateKeyComCertificado(cert, priv)) {
                    JOptionPane.showMessageDialog(this, "Chave ou frase secreta inválida.", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 4) Gera e cifra segredo TOTP
                byte[] totpSecret    = TOTPUtil.gerarChaveSecreta();
                byte[] totpSecretEnc = CryptoUtils.cifrarComAES256(totpSecret, senha.toCharArray());

                // 5) Persiste o usuário
                Usuario u = new Usuario();
                u.setLoginEmail(email);
                u.setNome(nome);
                u.setSenhaBcrypt(CryptoUtils.gerarHashBcrypt(senha));
                u.setTotpSecretEnc(totpSecretEnc);
                u.setGid(gidNovo);
                usuarioDao.insert(u);

                // 6) Persiste chaveiro
                Chaveiro chav = new Chaveiro();
                chav.setUid(u.getUid());
                chav.setCertPem(new String(Files.readAllBytes(new File(certPath).toPath()), StandardCharsets.UTF_8));
                chav.setPrivateKeyEnc(CryptoUtils.cifrarComAES256(priv.getEncoded(), phrase));
                chaveiroDao.insert(chav);

                // 7) Gera Base32 e URI otpauth
                String b32     = new Base32(Base32.Alphabet.BASE32, false, false).toString(totpSecret);
                String issuer  = "Cofre Digital";
                String label   = URLEncoder.encode(issuer + ":" + email, StandardCharsets.UTF_8.name());
                String issuerQ = URLEncoder.encode(issuer, StandardCharsets.UTF_8.name());
                String otpUri  = String.format("otpauth://totp/%s?secret=%s&issuer=%s", label, b32, issuerQ);

                // 8) Exibe segredo e URI
                JOptionPane.showMessageDialog(this,
                        "Segredo TOTP (Base32):\n" + b32 + "\n\nURI:\n" + otpUri,
                        "TOTP Secret", JOptionPane.INFORMATION_MESSAGE
                );

                // 9) Gera e exibe QR Code
                BufferedImage qrImage = QRCodeUtils.generateQRCodeImage(otpUri, 200, 200);
                JLabel picLabel = new JLabel(new ImageIcon(qrImage));
                picLabel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
                JPanel qrPanel = new JPanel(new BorderLayout());
                qrPanel.add(new JLabel("Escaneie este QR Code no Google Authenticator:"), BorderLayout.NORTH);
                qrPanel.add(picLabel, BorderLayout.CENTER);
                JOptionPane.showMessageDialog(this, qrPanel, "QR Code TOTP", JOptionPane.PLAIN_MESSAGE);

                // 10) Registro de auditoria
                Registro r = new Registro();
                r.setMid(6002);
                r.setUid(currentUser.getUid());
                r.setDetalhes("Cadastro de " + email);
                registroDao.insert(r);

                JOptionPane.showMessageDialog(this, "Usuário cadastrado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                dispose();
                new TelaPrincipal(currentUser).setVisible(true);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao cadastrar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(phraseF.getPassword(), ' ');
                Arrays.fill(passF.getPassword(),    ' ');
                Arrays.fill(confirmF.getPassword(), ' ');
            }
        });
    }
}
