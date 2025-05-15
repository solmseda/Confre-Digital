import service.CadastroService;

import dao.UsuarioDAO;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UsuarioDAO usuarioDAO = new UsuarioDAO();

            boolean primeiraExecucao = usuarioDAO.findAll().isEmpty();

            if (primeiraExecucao) {
                JOptionPane.showMessageDialog(null, "Primeira execução detectada. Vamos cadastrar o administrador.");
                CadastroService cadastroService = new CadastroService();
                cadastroService.executarCadastro();
            } else {
                JOptionPane.showMessageDialog(null, "Usuários já cadastrados. Pronto para iniciar autenticação.");
                // Você poderá chamar o AutenticacaoService aqui no futuro
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Erro ao iniciar o sistema: " + e.getMessage());
        }
    }
}
