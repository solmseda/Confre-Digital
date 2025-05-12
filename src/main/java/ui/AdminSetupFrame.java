package ui;

import dao.GrupoDAO;
import model.Grupo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * JFrame modal para configuração do administrador na primeira execução.
 */
public class AdminSetupFrame extends JDialog {
    private boolean submitted = false;
    private Path certificatePath;
    private Path privateKeyPath;
    private char[] passphrase;
    private char[] password;
    private String email;
    private String name;
    private int selectedGroupId;

    private final JTextField txtEmail = new JTextField(20);
    private final JTextField txtName = new JTextField(20);
    private final JComboBox<Grupo> comboGroup = new JComboBox<>();
    private final JPasswordField pfPassphrase = new JPasswordField(20);
    private final JPasswordField pfPassword = new JPasswordField(20);
    private final JPasswordField pfConfirm = new JPasswordField(20);
    private final JLabel lblCert = new JLabel("Nenhum arquivo selecionado");
    private final JLabel lblKey  = new JLabel("Nenhum arquivo selecionado");

    public AdminSetupFrame() throws Exception {
        super((Frame) null, "Configurar Administrador", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initComponents();
        loadGroups();
        pack();
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Email:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        panel.add(txtEmail, c);

        c.gridy++; c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Nome:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        panel.add(txtName, c);

        c.gridy++; c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Grupo:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        panel.add(comboGroup, c);

        c.gridy++; c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Certificado PEM:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        JButton btnCert = new JButton("Selecionar...");
        btnCert.addActionListener(this::onSelectCert);
        panel.add(btnCert, c);
        c.gridx = 2;
        panel.add(lblCert, c);

        c.gridy++; c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Chave Privada:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        JButton btnKey = new JButton("Selecionar...");
        btnKey.addActionListener(this::onSelectKey);
        panel.add(btnKey, c);
        c.gridx = 2;
        panel.add(lblKey, c);

        c.gridy++; c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Frase Secreta:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        panel.add(pfPassphrase, c);

        c.gridy++; c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Senha Admin:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        panel.add(pfPassword, c);

        c.gridy++; c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Confirmar Senha:"), c);
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        panel.add(pfConfirm, c);

        c.gridy++; c.gridx = 0; c.gridwidth = 3; c.anchor = GridBagConstraints.CENTER;
        JButton btnSubmit = new JButton("Criar Admin");
        btnSubmit.addActionListener(e -> onSubmit());
        JButton btnCancel = new JButton("Cancelar");
        btnCancel.addActionListener(e -> onCancel());
        JPanel buttons = new JPanel();
        buttons.add(btnSubmit);
        buttons.add(btnCancel);
        panel.add(buttons, c);

        getContentPane().add(panel);
    }

    private void loadGroups() throws Exception {
        List<Grupo> grupos = new GrupoDAO().findAll();
        DefaultComboBoxModel<Grupo> model = new DefaultComboBoxModel<>();
        for (Grupo g : grupos) model.addElement(g);
        comboGroup.setModel(model);
    }

    private void onSelectCert(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            certificatePath = f.toPath();
            lblCert.setText(f.getName());
        }
    }

    private void onSelectKey(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            privateKeyPath = f.toPath();
            lblKey.setText(f.getName());
        }
    }

    private void onSubmit() {
        String emailText = txtEmail.getText().trim();
        String nameText  = txtName.getText().trim();
        char[] pass      = pfPassphrase.getPassword();
        char[] pwd       = pfPassword.getPassword();
        char[] confirm   = pfConfirm.getPassword();

        // 1) Email não vazio e contendo '@'
        if (!emailText.contains("@")) {
            JOptionPane.showMessageDialog(
                    this,
                    "Informe um e-mail válido",
                    "Erro de validação",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // 2) Nome não vazio
        if (nameText.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Informe o nome do administrador",
                    "Erro de validação",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // 3) Grupo selecionado
        if (comboGroup.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Selecione um grupo",
                    "Erro de validação",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // 4) Certificado e chave privada informados
        if (certificatePath == null || privateKeyPath == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Selecione o certificado PEM e a chave privada",
                    "Erro de validação",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // 5) Senhas conferem e não vazias
        if (pwd.length == 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Informe a senha do administrador",
                    "Erro de validação",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        if (!String.valueOf(pwd).equals(String.valueOf(confirm))) {
            JOptionPane.showMessageDialog(
                    this,
                    "As senhas não conferem",
                    "Erro de validação",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Tudo OK: popula os campos e fecha
        this.email           = emailText;
        this.name            = nameText;
        this.passphrase      = pass;
        this.password        = pwd;
        this.selectedGroupId = ((Grupo)comboGroup.getSelectedItem()).getGid();
        this.submitted       = true;
        dispose();
    }

    private void onCancel() {
        submitted = false;
        dispose();
    }

    /**
     * Aguarda até que o usuário feche o diálogo (setVisible já bloqueia por ser modal).
     */
    public void waitForSubmit() {
        // Modal dialog faz blocking em setVisible()
    }

    public boolean isSubmitted() { return submitted; }
    public Path getCertificatePath() { return certificatePath; }
    public Path getPrivateKeyPath() { return privateKeyPath; }
    public char[] getPassphrase() { return passphrase; }
    public char[] getPassword() { return password; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public int getSelectedGroupId() { return selectedGroupId; }
}
