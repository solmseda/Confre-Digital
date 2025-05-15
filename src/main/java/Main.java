import service.CadastroService;
import dao.UsuarioDAO;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UsuarioDAO usuarioDAO = new UsuarioDAO();

            if (usuarioDAO.findAll().isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "Primeira execução detectada. Vamos cadastrar o administrador.",
                        "Cofre Digital - Configuração Inicial",
                        JOptionPane.INFORMATION_MESSAGE);

                new CadastroService().executarCadastro();
            } else {
                JOptionPane.showMessageDialog(null,
                        "Sistema já possui usuários cadastrados. Inicie o processo de autenticação.",
                        "Cofre Digital",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Erro fatal ao iniciar o sistema: " + e.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}