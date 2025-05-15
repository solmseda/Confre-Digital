import dao.UsuarioDAO;
import model.Usuario;
import service.CadastroService;

public class Sistema {

    private final UsuarioDAO usuarioDao = new UsuarioDAO();

    /**
     * Verifica se é a primeira execução do sistema
     * (ou seja, não há nenhum usuário cadastrado ainda).
     */
    public boolean primeiraExecucao() {
        try {
            return usuarioDao.findAll().isEmpty();
        } catch (Exception e) {
            System.err.println("Erro ao verificar primeira execução: " + e.getMessage());
            return true; // por segurança
        }
    }

    /**
     * Chama o processo de cadastro do administrador (interativo).
     */
    public void cadastrarAdministrador() {
        System.out.println("=== Primeira execução detectada ===");
        System.out.println("Cadastre o administrador:");

        CadastroService cadastro = new CadastroService();
        cadastro.executarCadastro();
    }

    /**
     * Inicia o processo de autenticação normal.
     */
//    public void autenticarUsuario() {
//        System.out.println("=== Autenticação de usuário ===");
//
//        AutenticacaoService auth = new AutenticacaoService();
//        auth.executarAutenticacao();
//    }
}
