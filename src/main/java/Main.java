import dao.RegistroDAO;
import dao.UsuarioDAO;
import model.Registro;
import service.CadastroService;
import util.CryptoUtils;
import ui.LoginFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UsuarioDAO usuarioDAO   = new UsuarioDAO();
                RegistroDAO registroDAO = new RegistroDAO();

                if (usuarioDAO.findAll().isEmpty()) {
                    // Primeira execução
                    JOptionPane.showMessageDialog(null,
                            "Primeira execução detectada. Vamos cadastrar o administrador.",
                            "Cofre Digital – Configuração Inicial",
                            JOptionPane.INFORMATION_MESSAGE);

                    Registro regInit = new Registro();
                    regInit.setMid(1005);
                    regInit.setUid(null);
                    regInit.setDetalhes("Partida do sistema iniciada para cadastro do administrador");
                    registroDAO.insert(regInit);

                    new CadastroService().executarCadastro();
                    char[] adminPassphrase = CryptoUtils.getCurrentPassphrase();

                } else {
                    JOptionPane.showMessageDialog(null,
                            "Sistema já possui usuários cadastrados. Inicie o processo de autenticação.",
                            "Cofre Digital",
                            JOptionPane.INFORMATION_MESSAGE);

                    Registro regNorm = new Registro();
                    regNorm.setMid(1006);
                    regNorm.setUid(null);
                    regNorm.setDetalhes("Partida do sistema iniciada para operação normal pelos usuários");
                    registroDAO.insert(regNorm);
                }

                //Inicia a tela de login multifator (3 etapas)
                LoginFrame login = new LoginFrame();
                login.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                login.setVisible(true);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Erro fatal ao iniciar o sistema:\n" + e.getMessage(),
                        "Erro",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
