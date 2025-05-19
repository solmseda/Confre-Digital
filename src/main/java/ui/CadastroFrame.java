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

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * JFrame para o Cadastro de Usuário no Cofre Digital.
 * Permite ao administrador cadastrar novos usuários com
 * informações como certificado digital, chave privada,
 * frase secreta, grupo e senha pessoal.
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

        // Corpo 1
        JPanel corpo1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        corpo1.setBorder(BorderFactory.createTitledBorder("Informações"));
        corpo1.add(new JLabel("Total de usuários do sistema: " + usuarioDao.findAll().size()));
        add(corpo1, BorderLayout.CENTER);

        // Corpo 2
        JPanel corpo2 = new JPanel(new GridBagLayout());
        corpo2.setBorder(BorderFactory.createTitledBorder("Formulário de Cadastro"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4); c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx=0; c.gridy=0; corpo2.add(new JLabel("Certificado digital:"),c);
        c.gridx=1; corpo2.add(certField,c);
        c.gridx=0; c.gridy++; corpo2.add(new JLabel("Chave privada:"),c);
        c.gridx=1; corpo2.add(keyField,c);
        c.gridx=0; c.gridy++; corpo2.add(new JLabel("Frase secreta:"),c);
        c.gridx=1; corpo2.add(phraseF,c);
        c.gridx=0; c.gridy++; corpo2.add(new JLabel("Grupo:"),c);
        c.gridx=1; corpo2.add(groupCombo,c);
        c.gridx=0; c.gridy++; corpo2.add(new JLabel("Senha pessoal:"),c);
        c.gridx=1; corpo2.add(passF,c);
        c.gridx=0; c.gridy++; corpo2.add(new JLabel("Confirmação senha:"),c);
        c.gridx=1; corpo2.add(confirmF,c);

        JPanel buttons = new JPanel();
        JButton btnCadastrar = new JButton("Cadastrar");
        JButton btnVoltar    = new JButton("Voltar");
        buttons.add(btnCadastrar); buttons.add(btnVoltar);

        JPanel south = new JPanel(new BorderLayout());
        south.add(corpo2, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        pack(); setLocationRelativeTo(null);

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

                if (!senha.equals(confirm)) {
                    JOptionPane.showMessageDialog(this, "Senhas não coincidem.", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // extrai cert
                X509Certificate cert = CryptoUtils.lerCertificadoPEM(certPath);
                String email = CryptoUtils.extrairEmailDoCertificado(cert);
                String nome  = CryptoUtils.extrairNomeDoCertificado(cert);
                // confirma dados do cert
                String info = String.format(
                        "Versão: %d\nSérie: %s\nValidade: de %s até %s\n" +
                                "Assinatura: %s\nEmissor: %s\nSujeito: %s\nE-mail: %s",
                        cert.getVersion(), cert.getSerialNumber().toString(), cert.getNotBefore(), cert.getNotAfter(),
                        cert.getSigAlgName(), cert.getIssuerX500Principal().getName(),
                        cert.getSubjectX500Principal().getName(), email
                );
                if (JOptionPane.showConfirmDialog(this, info,
                        "Confirme os dados do certificado", JOptionPane.OK_CANCEL_OPTION)
                        != JOptionPane.OK_OPTION) {
                    JOptionPane.showMessageDialog(this, "Cadastro cancelado.");
                    return;
                }

                // verifica unicidade de login
                if (usuarioDao.findByEmail(email) != null) {
                    JOptionPane.showMessageDialog(this, "E-mail já cadastrado.", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // decifra chave e persiste
                PrivateKey priv = CryptoUtils.decifrarPrivateKeyPKCS8(keyPath, phrase);
                if (!CryptoUtils.validarPrivateKeyComCertificado(cert, priv)) {
                    JOptionPane.showMessageDialog(this, "Chave ou frase secreta inválida.", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Usuario u = new Usuario();
                u.setLoginEmail(email); u.setNome(nome);
                u.setSenhaBcrypt(CryptoUtils.gerarHashBcrypt(senha));
                u.setTotpSecretEnc(new byte[0]); u.setGid(gidNovo);
                usuarioDao.insert(u);

                Chaveiro chav = new Chaveiro();
                chav.setUid(u.getUid());
                chav.setCertPem(new String(Files.readAllBytes(new File(certPath).toPath()), StandardCharsets.UTF_8));
                chav.setPrivateKeyEnc(CryptoUtils.cifrarComAES256(priv.getEncoded(), phrase));
                chaveiroDao.insert(chav);

                Registro r = new Registro(); r.setMid(6002);
                r.setUid(currentUser.getUid()); r.setDetalhes("Cadastro de " + email);
                registroDao.insert(r);

                JOptionPane.showMessageDialog(this, "Usuário cadastrado com sucesso!",
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                dispose(); new TelaPrincipal(currentUser).setVisible(true);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao cadastrar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(phraseF.getPassword(), ' ');
                Arrays.fill(passF.getPassword(), ' ');
                Arrays.fill(confirmF.getPassword(), ' ');
            }
        });
    }
}
