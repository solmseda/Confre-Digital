package service;

import dao.UsuarioDAO;
import dao.ChaveiroDAO;
import model.Usuario;
import model.Chaveiro;
import util.Base32;
import util.CryptoUtils;
import util.TOTPUtil;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class CadastroService {

    public void executarCadastro() {
        try {
            UsuarioDAO usuarioDAO = new UsuarioDAO();
            ChaveiroDAO chaveiroDAO = new ChaveiroDAO();

            // 1. Caminho do certificado digital
            String caminhoCertificado = JOptionPane.showInputDialog("Caminho do certificado digital (.pem):");
            if (caminhoCertificado == null || caminhoCertificado.isBlank()) return;

            // 2. Caminho da chave privada
            String caminhoChavePrivada = JOptionPane.showInputDialog("Caminho da chave privada (.enc):");
            if (caminhoChavePrivada == null || caminhoChavePrivada.isBlank()) return;

            // 3. Frase secreta para descriptografar a chave privada
            String fraseSecreta = JOptionPane.showInputDialog("Frase secreta da chave privada:");
            if (fraseSecreta == null || fraseSecreta.isBlank()) return;

            // 4. Grupo (Administrador por padrão)
            int gid = 1;

            // 5. Senha pessoal
            String senha = JOptionPane.showInputDialog("Senha pessoal (8 a 10 dígitos):");
            String senhaConfirmacao = JOptionPane.showInputDialog("Confirme a senha pessoal:");

            if (!senha.equals(senhaConfirmacao)) {
                JOptionPane.showMessageDialog(null, "Senhas não coincidem.");
                return;
            }

            // 6. Leitura do certificado e extração do email
            X509Certificate cert = CryptoUtils.lerCertificadoPEM(caminhoCertificado);
            String email = CryptoUtils.extrairEmailDoCertificado(cert);
            String nome = CryptoUtils.extrairNomeDoCertificado(cert);

            // 7. Geração da chave secreta TOTP
            byte[] segredoTotp = TOTPUtil.gerarChaveSecreta();
            byte[] segredoTotpCifrado = CryptoUtils.cifrarComAES256(segredoTotp, senha);

            // 8. Hash da senha
            String hashBcrypt = CryptoUtils.gerarHashBcrypt(senha);

            // 9. Criação do usuário
            Usuario usuario = new Usuario();
            usuario.setLoginEmail(email);
            usuario.setNome(nome);
            usuario.setSenhaBcrypt(hashBcrypt);
            usuario.setTotpSecretEnc(segredoTotpCifrado);
            usuario.setGid(gid);

            usuarioDAO.insert(usuario);

            // 10. Decriptar chave privada
            byte[] chavePrivadaCifrada = Files.readAllBytes(new File(caminhoChavePrivada).toPath());
            byte[] chavePrivadaDescriptografada = CryptoUtils.decifrarComAES256(chavePrivadaCifrada, fraseSecreta);
            byte[] chavePrivadaRevalidada = CryptoUtils.validarChaveComCertificado(cert, chavePrivadaDescriptografada);
            if (chavePrivadaRevalidada == null) {
                JOptionPane.showMessageDialog(null, "Validação da chave falhou.");
                return;
            }

            // 11. Salvar certificado + chave privada no banco (tabela Chaveiro)
            Chaveiro chaveiro = new Chaveiro();
            chaveiro.setUid(usuario.getUid());
            chaveiro.setCertPem(Files.readString(new File(caminhoCertificado).toPath()));
            chaveiro.setPrivateKeyEnc(chavePrivadaCifrada);
            chaveiroDAO.insert(chaveiro);

            // 12. Exibir segredo TOTP para registrar no Authenticator
            String segredoBase32 = new Base32(Base32.Alphabet.BASE32, false, false).toString(segredoTotp);
            JOptionPane.showMessageDialog(null, "Segredo TOTP (registre no Google Authenticator):\n" + segredoBase32);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Erro no cadastro: " + e.getMessage());
        }
    }
}
