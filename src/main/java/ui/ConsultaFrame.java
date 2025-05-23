package ui;

import dao.ChaveiroDAO;
import dao.GrupoDAO;
import model.Chaveiro;
import model.Usuario;
import util.CryptoUtils;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
        initComponents();
    }

    private void initComponents() throws Exception {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        // Cabeçalho usuário
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

        // Botão voltar
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

    private void onList() {
        try {
            // Verifica existência dos arquivos
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

            // 1) Decifra chave privada do usuário
            byte[] encKey = chaveiroDao.findByUid(currentUser.getUid()).getPrivateKeyEnc();
            PrivateKey priv = decryptPrivateKey(encKey, passField.getPassword());

            // 2) Decifra envelope (.env) -> seed
            byte[] seed = CryptoUtils.decifrarEnvelope(envFile, priv);
            System.out.println("[DEBUG] Seed obtido: " + bytesToHex(seed));

            // 3) Gera chave AES de 256 bits
            byte[] aesKeyBytes = CryptoUtils.generateAESKey(seed);
            System.out.println("[DEBUG] Chave AES gerada: " + bytesToHex(aesKeyBytes));

            // 4) Lê os bytes cifrados
            byte[] cipherData = Files.readAllBytes(encFile.toPath());

            // 5) Verifica assinatura sobre o arquivo de índice
            X509Certificate adminCert = loadAdminCert();
            verifyAdminSignature(adminCert, cipherData, sigFile);

            // 6) Só então decripta e preenche a tabela
            byte[] plainData = CryptoUtils.decryptAesEcbPkcs5(cipherData, aesKeyBytes);
            String decryptedContent = new String(plainData, StandardCharsets.UTF_8);
            populateFileTable(decryptedContent);


        } catch (SecurityException e) {
            showSignatureError(e);
        } catch (Exception ex) {
            showGenericError(ex);
        }
    }

    private void verifyAdminSignature(X509Certificate adminCert, byte[] plainData, File sigFile)
            throws Exception {
        // Carrega assinatura
        byte[] sigBytes = Files.readAllBytes(sigFile.toPath());

        // Valida certificado
        try {
            adminCert.checkValidity();
            System.out.println("[DEBUG] Certificado válido até: " + adminCert.getNotAfter());
        } catch (Exception e) {
            throw new SecurityException("Certificado do administrador expirado ou inválido");
        }

        // Verifica algoritmo de assinatura
        Signature verifier = Signature.getInstance("SHA256withRSA");
        if (!adminCert.getSigAlgName().equals(verifier.getAlgorithm())) {
            throw new SecurityException("Incompatibilidade de algoritmo de assinatura");
        }

        // Executa verificação
        verifier.initVerify(adminCert.getPublicKey());
        verifier.update(plainData);

        if (!verifier.verify(sigBytes)) {
            // Diagnóstico detalhado
            System.out.println("[DEBUG] === DIAGNÓSTICO DE ASSINATURA ===");
            System.out.println("[DEBUG] Algoritmo: " + verifier.getAlgorithm());
            System.out.println("[DEBUG] Tamanho assinatura: " + sigBytes.length + " bytes");
            System.out.println("[DEBUG] Chave pública (início): " +
                    bytesToHex(adminCert.getPublicKey().getEncoded()).substring(0, 64));

            throw new SecurityException("Assinatura digital inválida. " +
                    "Os dados podem ter sido alterados ou a assinatura foi feita com outra chave.");
        }
    }

    private void populateFileTable(String decryptedContent) throws SQLException {
        tableModel.setRowCount(0);
        String login = currentUser.getLoginEmail();
        String grp = grupoDao.findById(currentUser.getGid()).getNomeGrupo();

        for (String line : decryptedContent.split("\r?\n")) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 4 && (parts[2].equals(login) || parts[3].equals(grp))) {
                tableModel.addRow(Arrays.copyOf(parts, 4));
            }
        }
    }

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

    private PrivateKey decryptPrivateKey(byte[] encKeyBytes, char[] password) throws Exception {
        File tmp = File.createTempFile("privkey_enc", ".key");
        Files.write(tmp.toPath(), encKeyBytes);
        PrivateKey pk = CryptoUtils.decifrarPrivateKeyPKCS8(tmp.getAbsolutePath(), password);
        tmp.delete();
        return pk;
    }

    private X509Certificate loadAdminCert() throws Exception {
        Chaveiro adm = chaveiroDao.findByUid(1); // UID 1 é o admin
        byte[] pem = adm.getCertPem().getBytes(StandardCharsets.UTF_8);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem)
        );
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}