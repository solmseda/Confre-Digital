/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

import dao.RegistroDAO;
import model.Registro;

import java.util.Comparator;
import java.util.List;


public class logViewer {
    
    public static void main(String[] args) {
        try{
            RegistroDAO registroDAO = new RegistroDAO();
            List<Registro> registros = registroDAO.findAll();

            registros.sort(Comparator.comparing(Registro::getTimestamp));

            registros.forEach(registro -> {
                System.out.println("Timestamp: " + registro.getTimestamp());
                System.out.println("Detalhes: " + registro.getDetalhes());
                System.out.println("------------------------------");
            });

            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}