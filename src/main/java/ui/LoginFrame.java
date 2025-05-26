package ui;

import dao.UsuarioDAO;
import dao.RegistroDAO;
import model.Registro;
import model.Usuario;
import util.CryptoUtils;
import util.Base32;
import util.TOTP;
import util.Logger;


import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * JFrame para autenticação multifator do Cofre Digital:
 * 1) Identificação por e-mail
 * 2) Senha pessoal via teclado virtual de pares de dígitos
 * 3) Código TOTP
 */
public class LoginFrame extends JFrame {
    private final UsuarioDAO usuarioDao = new UsuarioDAO();
    private final RegistroDAO registroDao = new RegistroDAO();

    private Usuario currentUser;
    private char[] currentPassword;

    private final Map<Integer, Integer> falhasSenha = new HashMap<>();
    private final Map<Integer, Long> bloqueios = new HashMap<>();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);

    // Etapa 1: Identificação
    private final JTextField txtEmail = new JTextField(20);
    private final JButton btnNext = new JButton("Próximo");

    // Etapa 2: Senha Pessoal
    private final JPasswordField pfSenha = new JPasswordField(10);
    private final List<JButton> senhaButtons = new ArrayList<>();
    private final JButton btnOk = new JButton("OK");
    private final JButton btnLimpar = new JButton("LIMPAR");
    private final List<String> clickedPairs = new ArrayList<>();

    // Etapa 3: TOTP
    private final JTextField txtTotp = new JTextField(6);
    private final JButton btnTotpValidar = new JButton("Validar TOTP");

    public LoginFrame() throws SQLException {
        super("Cofre Digital - Autenticação");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(500, 350);
        setLocationRelativeTo(null);
        initComponents();
        Logger.registra("2001");
        cardLayout.show(mainPanel, "identificacao");
    }

    private void initComponents() throws SQLException {
        // Etapa 1: Identificação
        JPanel idPanel = new JPanel(new BorderLayout(10,10));
        JPanel idFields = new JPanel(new FlowLayout());
        idFields.add(new JLabel("E-mail:"));
        idFields.add(txtEmail);
        idPanel.add(idFields, BorderLayout.CENTER);
        JPanel idNav = new JPanel();
        idNav.add(btnNext);
        idPanel.add(idNav, BorderLayout.SOUTH);
        btnNext.addActionListener(e -> {
            try { performIdentification(); }
            catch (SQLException ex) { throw new RuntimeException(ex); }
        });

        // Etapa 2: Senha Pessoal
        JPanel senhaPanel = new JPanel(new BorderLayout(10,10));
        pfSenha.setEditable(false);
        senhaPanel.add(pfSenha, BorderLayout.NORTH);

        JPanel teclasPanel = new JPanel(new GridLayout(2, 3, 5, 5));
        // Gera 5 botões de pares
        for (int i = 0; i < 5; i++) {
            JButton b = new JButton();
            senhaButtons.add(b);
            teclasPanel.add(b);
            b.addActionListener(evt -> {
                clickedPairs.add(b.getText());
                pfSenha.setText("*".repeat(clickedPairs.size()));
                embaralharTeclas();
            });
        }
        // Botão LIMPAR ao lado
        teclasPanel.add(btnLimpar);
        btnLimpar.addActionListener(e -> {
            clickedPairs.clear();
            pfSenha.setText("");
        });
        senhaPanel.add(teclasPanel, BorderLayout.CENTER);

        // Rodapé para OK e LIMPAR
        JPanel pwdActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pwdActions.add(btnLimpar);
        pwdActions.add(btnOk);
        senhaPanel.add(pwdActions, BorderLayout.SOUTH);
        btnOk.addActionListener(e -> {
            try {
                performPasswordValidation();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });

        // Etapa 3: TOTP
        JPanel totpPanel = new JPanel(new BorderLayout(10,10));
        JPanel totpFields = new JPanel(new FlowLayout());
        totpFields.add(new JLabel("Código TOTP:"));
        totpFields.add(txtTotp);
        totpPanel.add(totpFields, BorderLayout.CENTER);
        JPanel totpNav = new JPanel();
        totpNav.add(btnTotpValidar);
        totpPanel.add(totpNav, BorderLayout.SOUTH);
        btnTotpValidar.addActionListener(e -> performTotp());

        // Adiciona painéis ao mainPanel
        mainPanel.add(idPanel, "identificacao");
        mainPanel.add(senhaPanel, "senha");
        mainPanel.add(totpPanel, "totp");
        add(mainPanel);
    }

    private void performIdentification() throws SQLException {
        String email = txtEmail.getText().trim();
        Usuario user = usuarioDao.findByEmail(email);
        if (user == null) {
            JOptionPane.showMessageDialog(this, "E-mail inválido.", "Erro", JOptionPane.ERROR_MESSAGE);
            log(2005, null, "Identificação inválida: usuário não encontrado");
            return;
        }
        int uid = user.getUid();
        long now = System.currentTimeMillis();
        Long until = bloqueios.get(uid);
        if (until != null && now < until) {
            JOptionPane.showMessageDialog(this,
                    "Acesso bloqueado até " + new Date(until),
                    "Bloqueado", JOptionPane.WARNING_MESSAGE);
            log(2004, uid, "Usuário bloqueado na identificação");
            return;
        }
        currentUser = user;
        Logger.registra("2002");
        Logger.registra("3001");
        clickedPairs.clear();
        pfSenha.setText("");
        embaralharTeclas();
        cardLayout.show(mainPanel, "senha");
    }

    private void embaralharTeclas() {
        List<Integer> digs = IntStream.range(0, 10).boxed().collect(Collectors.toList());
        Collections.shuffle(digs);
        for (int i = 0; i < 5; i++) {
            senhaButtons.get(i).setText(digs.get(2*i) + "-" + digs.get(2*i+1));
        }
    }

    private void performPasswordValidation() throws SQLException {
        int uid = currentUser.getUid();
        String hash = currentUser.getSenhaBcrypt();
        boolean matched = tryCombinations(0, new StringBuilder(), hash);
        if (!matched) {
            int f = falhasSenha.getOrDefault(uid, 0) + 1;
            falhasSenha.put(uid, f);
            Logger.registra("3003");
            if (f >= 3) {
                Logger.registra("3006");
                long until = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
                bloqueios.put(uid, until);
                falhasSenha.remove(uid);
                Logger.registra("3007");
                JOptionPane.showMessageDialog(this,
                        "Você excedeu o número de tentativas. Tente novamente mais tarde.",
                        "Bloqueado", JOptionPane.WARNING_MESSAGE);
                cardLayout.show(mainPanel, "identificacao");
            } else {
                if(f == 1) Logger.registra("3004");
                if(f == 2) Logger.registra("3005");
                JOptionPane.showMessageDialog(this,
                        "Senha incorreta. Tentativa " + f + "/3",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
            clickedPairs.clear();
            pfSenha.setText("");
            return;
        }

        Logger.registra("3002");
        falhasSenha.remove(uid);
        Logger.registra("4001");
        cardLayout.show(mainPanel, "totp");
    }

    private boolean tryCombinations(int idx, StringBuilder sb, String hash) {
        if (idx == clickedPairs.size()) {
            String candidate = sb.toString();
            if (OpenBSDBCrypt.checkPassword(hash, candidate.toCharArray())) {
                currentPassword = candidate.toCharArray();
                return true;
            }
            return false;
        }
        String pair = clickedPairs.get(idx);
        char d1 = pair.charAt(0), d2 = pair.charAt(2);
        sb.append(d1);
        if (tryCombinations(idx+1, sb, hash)) return true;
        sb.setLength(sb.length()-1);
        sb.append(d2);
        if (tryCombinations(idx+1, sb, hash)) return true;
        sb.setLength(sb.length()-1);
        return false;
    }

    private void performTotp() {
        String code = txtTotp.getText().trim();
        int uid = currentUser.getUid();
        try {
            byte[] enc    = currentUser.getTotpSecretEnc();
            byte[] secret = CryptoUtils.decifrarComAES256(enc, currentPassword);
            Base32 b32    = new Base32(Base32.Alphabet.BASE32, false, false);
            String base32 = b32.toString(secret);
            TOTP totp     = new TOTP(base32, 30);
            if (!totp.validateCode(code)) {
                JOptionPane.showMessageDialog(this, "Código TOTP inválido.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                Logger.registra("4004");
                return;
            }
            Logger.registra("4003");
            JOptionPane.showMessageDialog(this, "Autenticado com sucesso!",
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            new TelaPrincipal(currentUser).setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro no TOTP: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void log(int mid, Integer uid, String detalhes) {
        try {
            Registro r = new Registro();
            r.setMid(mid);
            r.setUid(uid);
            r.setDetalhes(detalhes);
            registroDao.insert(r);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
