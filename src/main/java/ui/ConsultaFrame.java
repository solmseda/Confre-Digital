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
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
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

        // Cabeçalho
        JPanel header = new JPanel(new GridLayout(2,2));
        header.setBorder(BorderFactory.createTitledBorder("Usuário"));
        header.add(new JLabel("Login:"));
        header.add(new JLabel(currentUser.getLoginEmail()));
        header.add(new JLabel("Grupo:"));
        header.add(new JLabel(grupoDao.findById(currentUser.getGid()).getNomeGrupo()));
        add(header, BorderLayout.NORTH);

        // Formulário
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
        form.add(new JLabel("Frase secreta (usuário):"), gbc);
        gbc.gridx = 1;
        form.add(passField, gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        form.add(btnList, gbc);
        add(form, BorderLayout.WEST);

        // Tabela
        add(new JScrollPane(table), BorderLayout.CENTER);
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) onDecryptSelected();
            }
        });

        // Rodapé
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
            // 1) Verifica existência dos arquivos de índice
            File dir     = new File(dirField.getText());
            File envFile = new File(dir, "index.env");
            File encFile = new File(dir, "index.enc");
            File sigFile = new File(dir, "index.asd");
            if (!envFile.exists() || !encFile.exists() || !sigFile.exists()) {
                JOptionPane.showMessageDialog(this,
                        "Faltando index.env, index.enc ou index.asd.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 2) Obtém private key do ADMIN em memória
            PrivateKey adminPriv = CryptoUtils.getCurrentPrivateKey();
            if (adminPriv == null) {
                JOptionPane.showMessageDialog(this,
                        "Chave do administrador não carregada em memória!",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            byte[] seed   = CryptoUtils.decifrarEnvelope(envFile, adminPriv);
            byte[] aesKey = CryptoUtils.generateAESKey(seed);

            // 4) Decripta index.enc
            byte[] cipherIndex = Files.readAllBytes(encFile.toPath());
            byte[] plainIndex  = CryptoUtils.decryptAesEcbPkcs5(cipherIndex, aesKey);

            // 5) Carrega assinatura e certificado
            byte[] sigBytes      = Files.readAllBytes(sigFile.toPath());
            X509Certificate cert = loadAdminCert();

            // 6) Tenta verificar sobre o plaintext com vários algoritmos
            String[] algs = {"SHA256withRSA", "SHA1withRSA"};
            boolean ok = false;
            for (String alg : algs) {
                try {
                    Signature v = Signature.getInstance(alg);
                    v.initVerify(cert.getPublicKey());
                    v.update(plainIndex);
                    if (v.verify(sigBytes)) {
                        System.out.println("[LOG] Assinatura validada com " + alg);
                        ok = true;
                        break;
                    } else {
                        System.out.println("[LOG] Falhou com " + alg);
                    }
                } catch (NoSuchAlgorithmException e) {
                    System.out.println("[LOG] Algoritmo não suportado: " + alg);
                }
            }

            if (!ok) {
                throw new SecurityException("Assinatura digital inválida em todos os algoritmos tentados.");
            }

            // 7) Popula a tabela
            String content = new String(plainIndex, StandardCharsets.UTF_8);
            populateFileTable(content);

        } catch (SecurityException se) {
            showSignatureError(se);
        } catch (Exception e) {
            showGenericError(e);
        }
    }

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
                        "Faltando .env, .enc ou .asd para " + code,
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Decifrar envelope do arquivo usando a chave DO USUÁRIO
            byte[] seed = CryptoUtils.decifrarEnvelope(envFile, decryptPrivateKey(
                    chaveiroDao.findByUid(currentUser.getUid()).getPrivateKeyEnc(),
                    passField.getPassword()
            ));
            byte[] aesKey = CryptoUtils.generateAESKey(seed);

            // Verificar assinatura do arquivo
            byte[] cipherData = Files.readAllBytes(encFile.toPath());
            X509Certificate userCert = loadUserCert();
            verifyUserSignature(userCert, cipherData, sigFile);

            // Decriptar e gravar
            byte[] plain = CryptoUtils.decryptAesEcbPkcs5(cipherData, aesKey);
            Files.write(new File(dir, secretName).toPath(), plain);
            JOptionPane.showMessageDialog(this,
                    "Arquivo decriptado com sucesso: " + secretName,
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Erro ao decriptar: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void verifyAdminSignature(X509Certificate cert, byte[] data, File sigFile) throws Exception {
        byte[] sig = Files.readAllBytes(sigFile.toPath());
        Signature v = Signature.getInstance("SHA256withRSA");
        v.initVerify(cert.getPublicKey());
        v.update(data);
        if (!v.verify(sig)) throw new SecurityException("Assinatura digital inválida.");
    }

    private void populateFileTable(String content) throws SQLException {
        tableModel.setRowCount(0);
        String login = currentUser.getLoginEmail();
        String grp   = grupoDao.findById(currentUser.getGid()).getNomeGrupo();
        for (String line : content.split("\\r?\\n")) {
            String[] p = line.split("\\s+");
            if (p.length>=4 && (p[2].equals(login)||p[3].equals(grp))) {
                tableModel.addRow(Arrays.copyOf(p,4));
            }
        }
    }

    private void showSignatureError(SecurityException e) {
        JOptionPane.showMessageDialog(this,
                "Erro de segurança: " + e.getMessage(),
                "Falha na Verificação", JOptionPane.ERROR_MESSAGE);
    }

    private void showGenericError(Exception e) {
        JOptionPane.showMessageDialog(this,
                "Erro técnico: " + e.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
    }

    private PrivateKey decryptPrivateKey(byte[] encKey, char[] pass) throws Exception {
        File tmp = File.createTempFile("priv_enc", ".key");
        Files.write(tmp.toPath(), encKey);
        PrivateKey pk = CryptoUtils.decifrarPrivateKeyPKCS8(tmp.getAbsolutePath(), pass);
        tmp.delete();
        return pk;
    }

    private X509Certificate loadAdminCert() throws Exception {
        Chaveiro adm = chaveiroDao.findByUid(1);
        byte[] pem = adm.getCertPem().getBytes(StandardCharsets.UTF_8);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(pem));
    }

    private X509Certificate loadUserCert() throws Exception {
        Chaveiro ch = chaveiroDao.findByUid(currentUser.getUid());
        byte[] pem = ch.getCertPem().getBytes(StandardCharsets.UTF_8);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(pem));
    }

    private void verifyUserSignature(X509Certificate cert, byte[] data, File sigFile) throws Exception {
        byte[] sig = Files.readAllBytes(sigFile.toPath());
        Signature v = Signature.getInstance("SHA256withRSA");
        v.initVerify(cert.getPublicKey());
        v.update(data);
        if (!v.verify(sig)) throw new SecurityException("Assinatura de arquivo inválida.");
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x: b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
