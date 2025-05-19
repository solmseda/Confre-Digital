/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

package ui;

import dao.RegistroDAO;
import model.Registro;
import model.Usuario;
import util.CryptoUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;

/**
 * ConsultaFrame: atende à etapa de consulta de arquivos secretos,
 * com cabeçalho, total de consultas, formulário de pasta e frase,
 * listagem de arquivos e controles de acesso/descriptografia.
 */
public class ConsultaFrame extends JFrame {
    private final Usuario currentUser;
    private final RegistroDAO registroDao = new RegistroDAO();

    private final JTextField folderField = new JTextField(40);
    private final JPasswordField phraseField = new JPasswordField(40);
    private final JButton btnList = new JButton("Listar");
    private final JButton btnBack = new JButton("Voltar para o Menu Principal");
    private final JTable table;
    private final DefaultTableModel tableModel;

    public ConsultaFrame(Usuario user) throws SQLException {
        super("Cofre Digital – Consulta de Arquivos");
        this.currentUser = user;
        // Colunas: Código, Nome, Dono, Grupo
        String[] cols = {"Código","Nome","Dono","Grupo"};
        tableModel = new DefaultTableModel(cols, 0);
        table = new JTable(tableModel);
        initComponents();
    }

    private void initComponents() throws SQLException {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        // Cabeçalho
        JPanel header = new JPanel(new GridLayout(3,1));
        header.setBorder(BorderFactory.createTitledBorder("Dados do Usuário"));
        header.add(new JLabel("Login: " + currentUser.getLoginEmail()));
        header.add(new JLabel("Grupo: " + currentUser.getGid()));
        header.add(new JLabel("Nome: " + currentUser.getNome()));
        add(header, BorderLayout.NORTH);

        // Corpo 1
        int count = registroDao.countByUid(currentUser.getUid());
        JPanel corpo1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        corpo1.setBorder(BorderFactory.createTitledBorder("Informações"));
        corpo1.add(new JLabel("Total de consultas do usuário: " + count));
        add(corpo1, BorderLayout.CENTER);

        // Corpo 2
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Formulário de Consulta"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx=0; c.gridy=0;
        form.add(new JLabel("Caminho da pasta:"), c);
        c.gridx=1;
        form.add(folderField, c);

        c.gridx=0; c.gridy=1;
        form.add(new JLabel("Frase secreta:"), c);
        c.gridx=1;
        form.add(phraseField, c);

        c.gridx=1; c.gridy=2; c.anchor = GridBagConstraints.EAST;
        form.add(btnList, c);

        add(form, BorderLayout.WEST);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnBack);
        add(south, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        // Listar
        btnList.addActionListener(e -> onList());
        // Voltar
        btnBack.addActionListener(e -> {
            dispose();
            try {
                new TelaPrincipal(currentUser).setVisible(true);
            } catch(SQLException ex){
                JOptionPane.showMessageDialog(this,
                        "Erro: "+ex.getMessage(),"Erro",JOptionPane.ERROR_MESSAGE);
            }
        });

        // Duplo clique na tabela para download
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount()==2) onDownload();
            }
        });
    }

    private void onList() {
        String dir = folderField.getText().trim();
        char[] phrase = phraseField.getPassword();

        //Pega a chave privada do usuário
        PrivateKey priv;
        try {
            priv = CryptoUtils.getUserPrivateKey(currentUser.getUid(), phrase);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            // falha ao decifrar privateKeyEnc => frase incorreta
            JOptionPane.showMessageDialog(this,
                    "Frase secreta inválida.",
                    "Erro de autenticação",
                    JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao obter chave do usuário:\n" + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //Verifica existência dos arquivos
        File encIdx = new File(dir, "index.enc");
        File env    = new File(dir, "index.env");
        File sig    = new File(dir, "index.asd");
        if (!encIdx.exists() || !env.exists() || !sig.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Arquivos de índice faltando na pasta selecionada.",
                    "Erro de arquivo", JOptionPane.ERROR_MESSAGE);
            return;
        }

        byte[] aesKey;
        try {
            byte[] aesSeed = CryptoUtils.decifrarEnvelope(env, priv);
            aesKey = CryptoUtils.generateAESKey(aesSeed);
        } catch (BadPaddingException|IllegalBlockSizeException e) {
            JOptionPane.showMessageDialog(this,
                    "Frase secreta inválida.",
                    "Erro de autenticação", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao processar envelope:\n" + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //Decifra o index
        byte[] decrypted;
        try {
            byte[] encrypted = Files.readAllBytes(encIdx.toPath());
            decrypted = CryptoUtils.decryptAesEcbPkcs5(encrypted, aesKey);
        } catch (BadPaddingException|IllegalBlockSizeException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro de integridade no índice.\n" +
                            "O arquivo index.enc não corresponde ao index.env fornecido.",
                    "Erro de integridade", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao decifrar índice:\n" + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }


        //Verifica assinatura
        try {
            X509Certificate adminCert = CryptoUtils.loadAdminCert();
            byte[] sigBytes = Files.readAllBytes(sig.toPath());
            Signature signature = Signature.getInstance(adminCert.getSigAlgName());
            signature.initVerify(adminCert.getPublicKey());
            signature.update(decrypted);
            if (!signature.verify(sigBytes)) {
                throw new SecurityException("Assinatura inválida no índice.");
            }
        } catch (SignatureException | InvalidKeyException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro na verificação de assinatura:\n" + e.getMessage(),
                    "Erro de integridade", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao verificar assinatura:\n" + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        tableModel.setRowCount(0);
        String text = new String(decrypted, StandardCharsets.UTF_8);
        for (String ln : text.split("\\R")) {
            if (ln.isBlank()) continue;
            String[] parts = ln.split(" ");
            if (parts.length < 4) continue;

            String code  = parts[0];
            String name  = parts[1];
            String owner = parts[2];
            String grp   = parts[3];

            tableModel.addRow(new Object[]{ code, name, owner, grp });
        }
    }


    private void onDownload() {
        int idx = table.getSelectedRow();
        if (idx<0) return;
        String code = tableModel.getValueAt(idx,0).toString();
        String owner=tableModel.getValueAt(idx,2).toString();
        if (!owner.equals(currentUser.getLoginEmail())){
            JOptionPane.showMessageDialog(this, "Você não tem permissão.","Erro",JOptionPane.ERROR_MESSAGE);
            return;
        }
        String dir = folderField.getText().trim();
        char[] phrase = phraseField.getPassword();
        try {
            PrivateKey priv = CryptoUtils.getUserPrivateKey(currentUser.getUid(), phrase);
            File encFile = new File(dir, code+".enc");
            File envFile = new File(dir, code+".env");
            File sigFile = new File(dir, code+".asd");

            byte[] aesSeed = CryptoUtils.decifrarEnvelope(envFile, priv);
            byte[] aesKey  = CryptoUtils.generateAESKey(aesSeed);
            byte[] encData = Files.readAllBytes(encFile.toPath());
            byte[] decData = CryptoUtils.decryptAesEcbPkcs5(encData, aesKey);

            X509Certificate adminCert = CryptoUtils.loadAdminCert();
            byte[] sigBytes = Files.readAllBytes(sigFile.toPath());
            Signature sign = Signature.getInstance(adminCert.getSigAlgName());
            sign.initVerify(adminCert.getPublicKey());
            sign.update(decData);
            if (!sign.verify(sigBytes)) throw new SecurityException("Assinatura inválida do arquivo");

            String secretName = tableModel.getValueAt(idx,1).toString();
            Files.write(new File(dir, secretName).toPath(), decData);
            JOptionPane.showMessageDialog(this, "Arquivo decriptado: " + secretName);

            registroDao.insert(new Registro(){ { setMid(7005); setUid(currentUser.getUid()); setDetalhes("Decriptar arquivo " + code); } });
        } catch(Exception ex){
            JOptionPane.showMessageDialog(this, "Erro ao decriptar: "+ex.getMessage(),"Erro",JOptionPane.ERROR_MESSAGE);
        }
    }
}
