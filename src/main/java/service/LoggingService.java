package service;

import dao.MensagemDAO;
import dao.RegistroDAO;
import model.Mensagem;
import model.Registro;
import java.time.LocalDateTime;

public class LoggingService {
    private final MensagemDAO mensagemDao;
    private final RegistroDAO registroDao;

    public LoggingService(MensagemDAO mensagemDao, RegistroDAO registroDao) {
        this.mensagemDao = mensagemDao;
        this.registroDao = registroDao;
    }

    public void log(String codigoEvento, Integer uid, String detalhes) throws Exception {
        Mensagem msg = mensagemDao.findByCodigo(codigoEvento);
        if (msg == null) {
            msg = new Mensagem();
            msg.setCodigo(codigoEvento);
            msg.setTexto(codigoEvento);
            mensagemDao.insert(msg);
        }
        Registro r = new Registro();
        r.setMid(msg.getMid());
        r.setUid(uid);
        r.setDetalhes(detalhes);
        registroDao.insert(r);
    }
}