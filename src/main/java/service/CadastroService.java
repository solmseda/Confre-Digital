package service;

import dao.UsuarioDAO;
import dao.ChaveiroDAO;
import model.Usuario;
import model.Chaveiro;
import util.CryptoUtils;
import util.TOTPUtil;
import util.Base32;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public class CadastroService {

    public void executarCadastro() {
        JTextField campoCert = new JTextField(40);
        JTextField campoChave = new JTextField(40);
        JPasswordField campoFraseSecreta = new JPasswordField(40);
        JPasswordField campoSenha = new JPasswordField(10);
        JPasswordField campoConfirmacao = new JPasswordField(10);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Caminho do certificado digital (.pem):"));
        panel.add(campoCert);
        panel.add(new JLabel("Caminho da chave privada (.enc):"));
        panel.add(campoChave);
        panel.add(new JLabel("Frase secreta da chave privada:"));
        panel.add(campoFraseSecreta);
        panel.add(new JLabel("Senha pessoal (8 a 10 dígitos):"));
        panel.add(campoSenha);
        panel.add(new JLabel("Confirme a senha pessoal:"));
        panel.add(campoConfirmacao);

        int result = JOptionPane.showConfirmDialog(null, panel,
                "Cadastro do Administrador", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String caminhoCertificado = campoCert.getText().trim().replace("\"", "");
            String caminhoChavePrivada = campoChave.getText().trim().replace("\"", "");
            String fraseSecreta = new String(campoFraseSecreta.getPassword());
            String senha = new String(campoSenha.getPassword());
            String senhaConfirmacao = new String(campoConfirmacao.getPassword());

            if (!validarDados(senha, senhaConfirmacao, caminhoCertificado, caminhoChavePrivada)) {
                return;
            }

            X509Certificate cert = CryptoUtils.lerCertificadoPEM(caminhoCertificado);
            String email = CryptoUtils.extrairEmailDoCertificado(cert);
            String nome = CryptoUtils.extrairNomeDoCertificado(cert);

            if (email == null || nome == null) {
                JOptionPane.showMessageDialog(null, "Certificado inválido ou sem e-mail/nome.");
                return;
            }

            byte[] chavePrivadaCifrada = Files.readAllBytes(new File(caminhoChavePrivada).toPath());
            byte[] chavePrivadaDescriptografada = CryptoUtils.decifrarComAES256(chavePrivadaCifrada, fraseSecreta);
            byte[] chavePrivadaValidada = CryptoUtils.validarChaveComCertificado(cert, chavePrivadaDescriptografada);

            if (chavePrivadaValidada == null) {
                JOptionPane.showMessageDialog(null, "Validação da chave falhou.");
                return;
            }

            UsuarioDAO usuarioDAO = new UsuarioDAO();
            ChaveiroDAO chaveiroDAO = new ChaveiroDAO();

            byte[] segredoTotp = TOTPUtil.gerarChaveSecreta();
            byte[] segredoTotpCifrado = CryptoUtils.cifrarComAES256(segredoTotp, senha);

            Usuario usuario = new Usuario();
            usuario.setLoginEmail(email);
            usuario.setNome(nome);
            usuario.setSenhaBcrypt(CryptoUtils.gerarHashBcrypt(senha));
            usuario.setTotpSecretEnc(segredoTotpCifrado);
            usuario.setGid(1); // Grupo administrador

            usuarioDAO.insert(usuario);

            Chaveiro chaveiro = new Chaveiro();
            chaveiro.setUid(usuario.getUid());
            chaveiro.setCertPem(Files.readString(new File(caminhoCertificado).toPath()));
            chaveiro.setPrivateKeyEnc(CryptoUtils.cifrarComAES256(chavePrivadaValidada, senha));
            chaveiroDAO.insert(chaveiro);

            String segredoBase32 = new Base32(Base32.Alphabet.BASE32, false, false).toString(segredoTotp);
            JOptionPane.showMessageDialog(null,
                    "Segredo TOTP (registre no Google Authenticator):\n" + segredoBase32 +
                            "\n\nURI para QR Code:\notpauth://totp/Cofre%20Digital:" + email +
                            "?secret=" + segredoBase32);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erro no cadastro: " + e.getMessage());
        } finally {
            Arrays.fill(campoFraseSecreta.getPassword(), ' ');
            Arrays.fill(campoSenha.getPassword(), ' ');
            Arrays.fill(campoConfirmacao.getPassword(), ' ');
        }
    }

    private boolean validarDados(String senha, String confirmacao, String caminhoCert, String caminhoChave) {
        // Verifica se as senhas coincidem
        if (!senha.equals(confirmacao)) {
            JOptionPane.showMessageDialog(null, "As senhas não coincidem.");
            return false;
        }

        // Verifica o comprimento da senha
        if (senha.length() < 8 || senha.length() > 10) {
            JOptionPane.showMessageDialog(null,
                    "A senha deve ter entre 8 e 10 caracteres.");
            return false;
        }

        // Verifica se contém apenas dígitos numéricos
        if (!senha.matches("[0-9]+")) {
            JOptionPane.showMessageDialog(null,
                    "A senha deve conter apenas dígitos numéricos (0-9).");
            return false;
        }

        // Verifica sequências repetidas (opcional, conforme enunciado)
        if (temSequenciasRepetidas(senha)) {
            JOptionPane.showMessageDialog(null,
                    "A senha não pode conter sequências de números repetidos.");
            return false;
        }

        // Verifica arquivos
        if (!new File(caminhoCert).exists()) {
            JOptionPane.showMessageDialog(null, "Certificado não encontrado.");
            return false;
        }

        if (!new File(caminhoChave).exists()) {
            JOptionPane.showMessageDialog(null, "Chave privada não encontrada.");
            return false;
        }

        return true;
    }

    // Método auxiliar para verificar sequências repetidas
    private boolean temSequenciasRepetidas(String senha) {
        for (int i = 0; i < senha.length() - 1; i++) {
            if (senha.charAt(i) == senha.charAt(i + 1)) {
                return true;
            }
        }
        return false;
    }
}