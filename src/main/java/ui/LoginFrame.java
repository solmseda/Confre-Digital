package ui;

import dao.UsuarioDAO;
import dao.RegistroDAO;
import model.Registro;
import model.Usuario;
import util.CryptoUtils;
import util.Base32;
import util.TOTP;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * JFrame para autenticação multifator do Cofre Digital:
 * 1) Identificação por e-mail
 * 2) Senha pessoal via teclado virtual de pares de dígitos (sem escolha interna),
 *    validada por enumeração de possíveis sequências
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

    public LoginFrame() {
        super("Cofre Digital - Autenticação");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(500, 350);
        setLocationRelativeTo(null);
        initComponents();
        log(2001, null, "Início da etapa 1: identificação do usuário");
        cardLayout.show(mainPanel, "identificacao");
    }

    private void initComponents() {
        // Etapa 1
        JPanel idPanel = new JPanel(new BorderLayout(10,10));
        JPanel idFields = new JPanel(new FlowLayout());
        idFields.add(new JLabel("E-mail:")); idFields.add(txtEmail);
        idPanel.add(idFields, BorderLayout.CENTER);
        JPanel idNav = new JPanel();
        idNav.add(btnNext); idPanel.add(idNav, BorderLayout.SOUTH);
        btnNext.addActionListener(e -> {
            try {
                performIdentification();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });

        // Etapa 2
        JPanel senhaPanel = new JPanel(new BorderLayout(10,10));
        pfSenha.setEditable(false); senhaPanel.add(pfSenha, BorderLayout.NORTH);
        JPanel teclasPanel = new JPanel(new GridLayout(1,5,5,5));
        for (int i = 0; i < 5; i++) {
            JButton b = new JButton();
            senhaButtons.add(b);
            teclasPanel.add(b);
            b.addActionListener(evt -> {
                String pair = b.getText();
                clickedPairs.add(pair);
                pfSenha.setText("*".repeat(clickedPairs.size()));
                embaralharTeclas();
            });
        }
        senhaPanel.add(teclasPanel, BorderLayout.CENTER);
        JPanel pwdActions = new JPanel();
        pwdActions.add(btnOk); pwdActions.add(btnLimpar);
        senhaPanel.add(pwdActions, BorderLayout.SOUTH);
        btnLimpar.addActionListener(e -> { clickedPairs.clear(); pfSenha.setText(""); });
        btnOk.addActionListener(e -> performPasswordValidation());

        // Etapa 3
        JPanel totpPanel = new JPanel(new BorderLayout(10,10));
        JPanel totpFields = new JPanel(new FlowLayout());
        totpFields.add(new JLabel("Código TOTP:")); totpFields.add(txtTotp);
        totpPanel.add(totpFields, BorderLayout.CENTER);
        JPanel totpNav = new JPanel(); totpNav.add(btnTotpValidar);
        totpPanel.add(totpNav, BorderLayout.SOUTH);
        btnTotpValidar.addActionListener(e -> performTotp());

        // adiciona ao mainPanel
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
            JOptionPane.showMessageDialog(this, "Acesso bloqueado até " + new Date(until),
                    "Bloqueado", JOptionPane.WARNING_MESSAGE);
            log(2004, uid, "Usuário bloqueado na identificação");
            return;
        }
        currentUser = user;
        log(2002, uid, "Identificação bem-sucedida");
        log(3001, uid, "Início da etapa 2: senha pessoal");
        clickedPairs.clear(); pfSenha.setText("");
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

    private void performPasswordValidation() {
        int uid = currentUser.getUid();
        String hash = currentUser.getSenhaBcrypt();

        boolean matched = tryCombinations(0, new StringBuilder(), hash);
        if (!matched) {
            int f = falhasSenha.getOrDefault(uid, 0) + 1;
            falhasSenha.put(uid, f);
            log(3003 + f, uid, "Erro " + f + " na senha pessoal");
            if (f >= 3) {
                long until = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
                bloqueios.put(uid, until);
                falhasSenha.remove(uid);
                log(3007, uid, "Usuário bloqueado após 3 falhas na senha");
                cardLayout.show(mainPanel, "identificacao");
            } else {
                JOptionPane.showMessageDialog(this,
                        "Senha incorreta. Tentativa " + f + "/3",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
            clickedPairs.clear(); pfSenha.setText("");
            return;
        }

        log(3002, uid, "Senha pessoal válida");
        falhasSenha.remove(uid);
        log(4001, uid, "Início da etapa 3: TOTP");
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
        char d1 = pair.charAt(0);
        char d2 = pair.charAt(2);
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
            byte[] enc = currentUser.getTotpSecretEnc();
            byte[] secret = CryptoUtils.decifrarComAES256(enc, currentPassword);
            Base32 b32 = new Base32(Base32.Alphabet.BASE32, false, false);
            String base32Secret = b32.toString(secret);
            TOTP totp = new TOTP(base32Secret, 30);
            if (!totp.validateCode(code)) {
                JOptionPane.showMessageDialog(this, "Código TOTP inválido.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                log(4004, uid, "Falha no TOTP");
                return;
            }
            log(4003, uid, "TOTP válido");
            JOptionPane.showMessageDialog(this, "Autenticado com sucesso!",
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro no TOTP: " + ex.getMessage(),
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
