package ui;

import dao.UsuarioDAO;
import model.Usuario;
import service.BcryptService;
import service.TotpService;
import service.LoggingService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JFrame para login multifator:
 * Etapa 1: email + senha (teclado virtual de 5 botões com 2 dígitos cada)
 * Etapa 2: código TOTP
 */
public class LoginFrame extends JFrame {
    // Dependências injetadas
    private final UsuarioDAO usuarioDao;
    private final BcryptService bcryptService;
    private final TotpService totpService;
    private final LoggingService loggingService;

    // UI etapa 1
    private final JTextField txtEmail     = new JTextField(20);
    private final JPasswordField pfPassword = new JPasswordField(20);
    private final List<JButton> virtualButtons = new ArrayList<>();

    // UI etapa 2
    private final JTextField txtTotp = new JTextField(6);

    // Controle de fluxo
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private Usuario currentUser;
    private int loginAttempts = 0;
    private int totpAttempts  = 0;
    private boolean locked    = false;
    private final Timer unlockTimer;

    public LoginFrame(UsuarioDAO usuarioDao,
                      BcryptService bcryptService,
                      TotpService totpService,
                      LoggingService loggingService) {
        super("Login Multifator");
        this.usuarioDao     = usuarioDao;
        this.bcryptService  = bcryptService;
        this.totpService    = totpService;
        this.loggingService = loggingService;

        // Timer para destravar após 2 minutos (120_000 ms)
        this.unlockTimer = new Timer(120_000, e -> {
            locked = false;
            loginAttempts = 0;
            totpAttempts  = 0;
            txtEmail.setEnabled(true);
            pfPassword.setEnabled(true);
            txtTotp.setEnabled(true);
            shuffleVirtualKeys();
        });
        this.unlockTimer.setRepeats(false);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initComponents();
        pack();
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        cards.add(buildStep1(), "STEP1");
        cards.add(buildStep2(), "STEP2");
        getContentPane().add(cards);
    }

    private JPanel buildStep1() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);

        // Email
        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Email:"), c);
        c.gridx = 1;            p.add(txtEmail, c);

        // Senha
        c.gridy++; c.gridx = 0; p.add(new JLabel("Senha:"), c);
        c.gridx = 1;            p.add(pfPassword, c);

        // Teclado virtual
        JPanel vk = new JPanel(new GridLayout(1, 5, 5, 5));
        for (int i = 0; i < 5; i++) {
            JButton btn = new JButton();
            btn.addActionListener(this::onVirtualKey);
            virtualButtons.add(btn);
            vk.add(btn);
        }
        shuffleVirtualKeys();
        c.gridy++; c.gridx = 0; c.gridwidth = 2; p.add(vk, c);
        c.gridwidth = 1;

        // Botão Próximo
        JButton btnNext = new JButton("Próximo");
        btnNext.addActionListener(e -> onPasswordSubmit());
        c.gridy++; c.gridx = 0; c.gridwidth = 2; p.add(btnNext, c);

        return p;
    }

    private JPanel buildStep2() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);

        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Código TOTP:"), c);
        c.gridx = 1;            p.add(txtTotp, c);

        JButton btnConfirm = new JButton("Confirmar");
        btnConfirm.addActionListener(e -> onTotpSubmit());
        c.gridy++; c.gridx = 0; c.gridwidth = 2; p.add(btnConfirm, c);

        return p;
    }

    private void shuffleVirtualKeys() {
        List<String> pairs = new ArrayList<>();
        SecureRandom rnd = new SecureRandom();
        while (pairs.size() < 5) {
            String p = String.format("%02d", rnd.nextInt(100));
            if (!pairs.contains(p)) pairs.add(p);
        }
        Collections.shuffle(pairs);
        for (int i = 0; i < 5; i++) {
            virtualButtons.get(i).setText(pairs.get(i));
        }
    }

    private void onVirtualKey(ActionEvent e) {
        JButton btn = (JButton) e.getSource();
        String current = pfPassword.getText();
        String pair    = btn.getText();
        // pega sempre o primeiro dígito do par
        pfPassword.setText(current + pair.charAt(0));
        shuffleVirtualKeys();
    }

    private void onPasswordSubmit() {
        if (locked) return;

        String email = txtEmail.getText().trim();
        if (email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe seu email", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String pass = new String(pfPassword.getPassword());
        if (pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe sua senha", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Usuario u = usuarioDao.findByEmail(email);
            boolean ok = u != null &&
                    bcryptService.verifyPassword(pass.toCharArray(), u.getSenhaBcrypt());
            if (!ok) {
                loginAttempts++;
                loggingService.log("1001", null, "Tentativa nº " + loginAttempts);
                JOptionPane.showMessageDialog(this, "Email ou senha inválidos.", "Erro", JOptionPane.ERROR_MESSAGE);
                if (loginAttempts >= 3) {
                    locked = true;
                    txtEmail.setEnabled(false);
                    pfPassword.setEnabled(false);
                    unlockTimer.start();
                    JOptionPane.showMessageDialog(this, "Conta bloqueada por 2 minutos.", "Bloqueio", JOptionPane.WARNING_MESSAGE);
                }
                pfPassword.setText("");
                shuffleVirtualKeys();
                return;
            }
            // sucesso na etapa 1
            currentUser   = u;
            loginAttempts = 0;
            cardLayout.show(cards, "STEP2");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void onTotpSubmit() {
        if (locked) return;

        String code = txtTotp.getText().trim();
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe o código TOTP", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            boolean valid = totpService.validateCode(currentUser.getUid(), code);
            if (!valid) {
                totpAttempts++;
                loggingService.log("1002", currentUser.getUid(), "TOTP falhou");
                JOptionPane.showMessageDialog(this, "Código TOTP inválido.", "Erro", JOptionPane.ERROR_MESSAGE);
                if (totpAttempts >= 3) {
                    locked = true;
                    txtTotp.setEnabled(false);
                    cardLayout.show(cards, "STEP1");
                    unlockTimer.start();
                    JOptionPane.showMessageDialog(this, "Conta bloqueada por 2 minutos.", "Bloqueio", JOptionPane.WARNING_MESSAGE);
                }
                return;
            }
            // sucesso no login multifator
            loggingService.log("1003", currentUser.getUid(), "Login bem-sucedido");
            JOptionPane.showMessageDialog(this, "Login bem-sucedido!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            // aqui você abre a próxima tela do sistema
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}