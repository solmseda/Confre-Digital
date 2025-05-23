package ui;

import dao.ChaveiroDAO;
import dao.GrupoDAO;
import model.Chaveiro;
import model.Usuario;
import util.CryptoUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;

public class ConsultaFrame extends JFrame {
    private final Usuario currentUser;
    private final GrupoDAO grupoDao = new GrupoDAO();
    private final ChaveiroDAO chaveiroDao = new ChaveiroDAO();

    private final JTextField dirField = new JTextField(30);
    private final JPasswordField passField = new JPasswordField(30);
    private final JButton btnList = new JButton("Listar");
    private final JButton btnBack = new JButton("Voltar");
    private final DefaultTableModel tableModel;
    private final JTable table;

    public ConsultaFrame(Usuario user) throws Exception {
        super("Consulta de Arquivos Secretos");
        this.currentUser = user;
        tableModel = new DefaultTableModel(new String[]{"Código","Nome","Dono","Grupo"}, 0);
        table = new JTable(tableModel);
        initComponents();  // configura UI e listeners
    }

    private void initComponents() throws Exception {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        // Cabeçalho
        JPanel header = new JPanel(new GridLayout(2,2));
        header.setBorder(BorderFactory.createTitledBorder("Usuário"));
        header.add(new JLabel("Login:"));
        header.add(new JLabel(currentUser.getLoginEmail()));
        header.add(new JLabel("Grupo:"));
        header.add(new JLabel(grupoDao.findById(currentUser.getGid()).getNomeGrupo()));
        add(header, BorderLayout.NORTH);

        // Formulário de parâmetros
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Parâmetros da Consulta"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Pasta segura:"), gbc);
        gbc.gridx = 1;
        form.add(dirField, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Frase secreta:"), gbc);
        gbc.gridx = 1;
        form.add(passField, gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        form.add(btnList, gbc);
        add(form, BorderLayout.WEST);

        // Tabela de resultados
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Duplo clique para decriptar arquivo
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onDecryptSelected();
                }
            }
        });

        // Botão Voltar
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(btnBack);
        add(footer, BorderLayout.SOUTH);

        // Listeners
        btnList.addActionListener(e -> onList());
        btnBack.addActionListener(e -> {
            dispose();
            try {
                new TelaPrincipal(currentUser).setVisible(true);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    // 1) Listar índice: decifra, verifica e popula tabela
    private void onList() {
        try {
            File dir = new File(dirField.getText());
            File envFile = new File(dir, "index.env");
            File encFile = new File(dir, "index.enc");
            File sigFile = new File(dir, "index.asd");
            if (!envFile.exists() || !encFile.exists() || !sigFile.exists()) {
                JOptionPane.showMessageDialog(this,
                        "Faltando index.env, index.enc ou index.asd.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Decifra chave privada do usuário
            byte[] encKey = chaveiroDao.findByUid(currentUser.getUid()).getPrivateKeyEnc();
            PrivateKey priv = decryptPrivateKey(encKey, passField.getPassword());

            // Decifra envelope -> seed e gera AES
            byte[] seed = CryptoUtils.decifrarEnvelope(envFile, priv);
            byte[] aesKeyBytes = CryptoUtils.generateAESKey(seed);

            // Verifica assinatura do índice
            byte[] cipherData = Files.readAllBytes(encFile.toPath());
            X509Certificate adminCert = loadAdminCert();
            System.out.println("Admin cert serial: " + adminCert.getSerialNumber());
            System.out.println("Algoritmo de assinatura do cert: " + adminCert.getSigAlgName());
            verifyAdminSignature(adminCert, cipherData, sigFile);

            // Decripta e popula tabela
            byte[] plainData = CryptoUtils.decryptAesEcbPkcs5(cipherData, aesKeyBytes);
            String decryptedContent = new String(plainData, StandardCharsets.UTF_8);
            populateFileTable(decryptedContent);

        } catch (SecurityException e) {
            showSignatureError(e);
        } catch (Exception ex) {
            showGenericError(ex);
        }
    }

    // 2) Duplo clique: decripta arquivo secreto selecionado
    private void onDecryptSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        String code       = (String) tableModel.getValueAt(row, 0);
        String secretName = (String) tableModel.getValueAt(row, 1);
        String owner      = (String) tableModel.getValueAt(row, 2);

        if (!owner.equals(currentUser.getLoginEmail())) {
            JOptionPane.showMessageDialog(this,
                    "Você não tem permissão para acessar este arquivo.",
                    "Acesso Negado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            File dir     = new File(dirField.getText());
            File envFile = new File(dir, code + ".env");
            File encFile = new File(dir, code + ".enc");
            File sigFile = new File(dir, code + ".asd");
            if (!envFile.exists() || !encFile.exists() || !sigFile.exists()) {
                JOptionPane.showMessageDialog(this,
                        "Faltando arquivos .env, .enc ou .asd para " + code,
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Redecifra chave privada e envelope
            byte[] encKey = chaveiroDao.findByUid(currentUser.getUid()).getPrivateKeyEnc();
            PrivateKey priv = decryptPrivateKey(encKey, passField.getPassword());
            byte[] seed = CryptoUtils.decifrarEnvelope(envFile, priv);
            byte[] aesKeyBytes = CryptoUtils.generateAESKey(seed);

            // Lê e verifica assinatura do arquivo
            byte[] cipherData = Files.readAllBytes(encFile.toPath());
            X509Certificate userCert = loadUserCert();
            verifyUserSignature(userCert, cipherData, sigFile);

            // Decripta e grava no nome secreto
            byte[] plainData = CryptoUtils.decryptAesEcbPkcs5(cipherData, aesKeyBytes);
            Files.write(new File(dir, secretName).toPath(), plainData);

            JOptionPane.showMessageDialog(this,
                    "Arquivo decriptado com sucesso: " + secretName,
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);

        } catch (SecurityException se) {
            JOptionPane.showMessageDialog(this,
                    "Erro de segurança: " + se.getMessage(),
                    "Falha na Verificação", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Erro ao decriptar arquivo: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Verifica assinatura do índice com certificado do admin :contentReference[oaicite:1]{index=1}
    /**
     * Verifica assinatura do índice com certificado do admin.
     */
    private void verifyAdminSignature(X509Certificate adminCert,
                                      byte[] cipherData,
                                      File sigFile) throws Exception {
        byte[] sigBytes = Files.readAllBytes(sigFile.toPath());

        // 1) Inicializa verificador COM O MESMO algoritmo que foi usado para assinar:
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(adminCert.getPublicKey());

        // 2) Atualiza com o blob criptografado (index.enc), pois foi isso que foi assinado:
        verifier.update(cipherData);

        // 3) Faz a checagem final
        if (!verifier.verify(sigBytes)) {
            throw new SecurityException(
                    "Assinatura digital inválida. " +
                            "Os dados podem ter sido alterados ou a assinatura foi feita com outra chave."
            );
        }
    }


    // Popula tabela apenas com entradas do usuário ou do grupo :contentReference[oaicite:2]{index=2}
    private void populateFileTable(String decryptedContent) throws SQLException {
        tableModel.setRowCount(0);
        String login = currentUser.getLoginEmail();
        String grp   = grupoDao.findById(currentUser.getGid()).getNomeGrupo();
        for (String line : decryptedContent.split("\r?\n")) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 4 && (parts[2].equals(login) || parts[3].equals(grp))) {
                tableModel.addRow(Arrays.copyOf(parts, 4));
            }
        }
    }

    // Mensagens de erro de assinatura e genérico :contentReference[oaicite:3]{index=3}
    private void showSignatureError(SecurityException e) {
        JOptionPane.showMessageDialog(this,
                "Erro de segurança: " + e.getMessage() + "\n\n" +
                        "Possíveis causas:\n" +
                        "1. Arquivo modificado após assinatura\n" +
                        "2. Certificado do administrador incorreto\n" +
                        "3. Assinatura feita com chave diferente",
                "Falha na Verificação", JOptionPane.ERROR_MESSAGE);
    }

    private void showGenericError(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this,
                "Erro técnico: " + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
    }

    // Decifra PKCS#8 da chave privada com CryptoUtils :contentReference[oaicite:4]{index=4}
    private PrivateKey decryptPrivateKey(byte[] encKeyBytes, char[] password) throws Exception {
        File tmp = File.createTempFile("privkey_enc", ".key");
        Files.write(tmp.toPath(), encKeyBytes);
        PrivateKey pk = CryptoUtils.decifrarPrivateKeyPKCS8(tmp.getAbsolutePath(), password);
        tmp.delete();
        return pk;
    }

    // Carrega certificado do admin (UID=1) :contentReference[oaicite:5]{index=5}
    private X509Certificate loadAdminCert() throws Exception {
        Chaveiro adm = chaveiroDao.findByUid(1);
        byte[] pem = adm.getCertPem().getBytes(StandardCharsets.UTF_8);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem)
        );
    }

    // Carrega certificado do usuário logado para verificação de arquivos
    private X509Certificate loadUserCert() throws Exception {
        Chaveiro ch = chaveiroDao.findByUid(currentUser.getUid());
        byte[] pem = ch.getCertPem().getBytes(StandardCharsets.UTF_8);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem)
        );
    }

    // Verifica assinatura de arquivos secretos com certificado do usuário
    private void verifyUserSignature(X509Certificate cert, byte[] plainData, File sigFile)
            throws Exception {
        byte[] sigBytes = Files.readAllBytes(sigFile.toPath());
        cert.checkValidity();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        if (!cert.getSigAlgName().equals(verifier.getAlgorithm()))
            throw new SecurityException("Incompatibilidade de algoritmo de assinatura");
        verifier.initVerify(cert.getPublicKey());
        verifier.update(plainData);
        if (!verifier.verify(sigBytes))
            throw new SecurityException("Assinatura do arquivo inválida");
    }

    // Auxiliar para debug em hexa :contentReference[oaicite:6]{index=6}
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
