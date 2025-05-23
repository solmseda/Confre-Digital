package util;
import dao.RegistroDAO;
import dao.MensagemDAO;
import model.Mensagem;
import model.Registro;

import java.sql.SQLException;

public class Logger {
    public static void registra(String mid) throws SQLException {
        RegistroDAO registroDAO = new RegistroDAO();
        MensagemDAO mensagemDAO = new MensagemDAO();

        Registro regInit = new Registro();
        Mensagem m = mensagemDAO.findByCodigo(mid);
        regInit.setMid(m.getMid());
        regInit.setUid(null);
        regInit.setDetalhes(m.getTexto());
        registroDAO.insert(regInit);
    }
}
