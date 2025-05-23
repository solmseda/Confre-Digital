import dao.RegistroDAO;
import dao.UsuarioDAO;
import dao.MensagemDAO;
import model.Mensagem;
import model.Registro;
import service.CadastroService;
import util.Logger;

/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */
import ui.LoginFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UsuarioDAO usuarioDAO   = new UsuarioDAO();
                Logger.registra("1001");


                if (usuarioDAO.findAll().isEmpty()) {
                    // Primeira execução
                    JOptionPane.showMessageDialog(null,
                            "Primeira execução detectada. Vamos cadastrar o administrador.",
                            "Cofre Digital – Configuração Inicial",
                            JOptionPane.INFORMATION_MESSAGE);


                    Logger.registra("1005");

                    new CadastroService().executarCadastro();

                } else {
                    JOptionPane.showMessageDialog(null,
                            "Sistema já possui usuários cadastrados. Inicie o processo de autenticação.",
                            "Cofre Digital",
                            JOptionPane.INFORMATION_MESSAGE);

                    Logger.registra("1006");
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
