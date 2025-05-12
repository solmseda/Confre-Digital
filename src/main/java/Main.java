import dao.GrupoDAO;
import dao.UsuarioDAO;
import dao.MensagemDAO;
import dao.RegistroDAO;
import dao.ChaveiroDAO;
import service.CryptoService;
import service.BcryptService;
import service.LoggingService;
import service.TotpService;
import service.UserService;
import ui.LoginFrame;

import javax.crypto.SecretKey;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        try {
            // Inicialização das dependências
            CryptoService cryptoService    = new CryptoService();
            BcryptService bcryptService    = new BcryptService();
            MensagemDAO mensagemDAO        = new MensagemDAO();
            RegistroDAO registroDAO        = new RegistroDAO();
            LoggingService loggingService  = new LoggingService(mensagemDAO, registroDAO);
            UsuarioDAO usuarioDAO          = new UsuarioDAO();
            ChaveiroDAO chaveiroDAO        = new ChaveiroDAO();

            // Serviço de usuário para primeira partida (admin)
            UserService userService = new UserService(
                    usuarioDAO,
                    chaveiroDAO,
                    cryptoService,
                    bcryptService,
                    loggingService
            );

            // Executa primeira configuração do administrador
            userService.initializeAdmin();

            // Configura TotpService usando a chave AES gerada pelo admin e time-step de 30s
            SecretKey adminAesKey = userService.getAdminAesKey();
            TotpService totpService = new TotpService(
                    usuarioDAO,
                    cryptoService,
                    adminAesKey,
                    30L
            );

            // Inicia a interface de login multifator
            SwingUtilities.invokeLater(() -> {
                LoginFrame frame = new LoginFrame(
                        usuarioDAO,
                        bcryptService,
                        totpService,
                        loggingService
                );
                frame.setVisible(true);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
