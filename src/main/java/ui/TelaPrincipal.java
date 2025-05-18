
package ui;

import dao.GrupoDAO;
import dao.RegistroDAO;
import model.Usuario;
import service.CadastroService;
//import service.ConsultaService;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class TelaPrincipal extends JFrame {
    private final Usuario user;
    private final RegistroDAO registroDao = new RegistroDAO();
    private final GrupoDAO grupoDao     = new GrupoDAO();

    public TelaPrincipal(Usuario user) throws SQLException {
        super("Cofre Digital – Tela Principal");
        this.user = user;
        initComponents();
    }

    private void initComponents() throws SQLException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        JPanel header = new JPanel(new GridLayout(3,1,5,5));
        header.setBorder(BorderFactory.createTitledBorder("Dados do Usuário"));
        header.add(new JLabel("Login:  " + user.getLoginEmail()));
        header.add(new JLabel("Grupo:  " + grupoDao.findById(user.getGid()).getNomeGrupo()));
        header.add(new JLabel("Nome:   " + user.getNome()));
        add(header, BorderLayout.NORTH);

        int totalAcessos = registroDao.countByUid(user.getUid());
        JPanel corpo1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        corpo1.setBorder(BorderFactory.createTitledBorder("Informações"));
        corpo1.add(new JLabel("Total de acessos do usuário: " + totalAcessos));
        add(corpo1, BorderLayout.CENTER);

        JPanel corpo2 = new JPanel(new GridLayout(3,1,5,5));
        corpo2.setBorder(BorderFactory.createTitledBorder("Menu Principal"));

        JButton btnCadastrar = new JButton("1 – Cadastrar um novo usuário");
        btnCadastrar.addActionListener(e -> new CadastroService().executarCadastro());

        JButton btnConsultar = new JButton("2 – Consultar pasta de arquivos secretos");
        //btnConsultar.addActionListener(e -> new ConsultaService().executarConsulta(user));

        JButton btnSair = new JButton("3 – Sair do Sistema");
        btnSair.addActionListener(e -> System.exit(0));

        corpo2.add(btnCadastrar);
        corpo2.add(btnConsultar);
        corpo2.add(btnSair);

        add(corpo2, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }
}
