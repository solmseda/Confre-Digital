/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

package ui;

import dao.GrupoDAO;
import dao.RegistroDAO;
import model.Usuario;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/**
 * JFrame para a Tela Principal do Cofre Digital.
 * Exibe cabeçalho, total de acessos e opções de Cadastro,
 * Consulta e Sair do sistema.
 */
public class TelaPrincipal extends JFrame {
    private final Usuario user;
    private final RegistroDAO registroDao = new RegistroDAO();
    private final GrupoDAO   grupoDao     = new GrupoDAO();

    public TelaPrincipal(Usuario user) throws SQLException {
        super("Cofre Digital – Tela Principal");
        this.user = user;
        initComponents();
    }

    private void initComponents() throws SQLException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        // Cabeçalho
        String groupLabel = user.getGid() == 1
                ? grupoDao.findById(user.getGid()).getNomeGrupo()
                : "Usuário";

        JPanel header = new JPanel(new GridLayout(3,1,5,5));
        header.setBorder(BorderFactory.createTitledBorder("Dados do Usuário"));
        header.add(new JLabel("Login:  " + user.getLoginEmail()));
        header.add(new JLabel("Grupo:  " + groupLabel));
        header.add(new JLabel("Nome:   " + user.getNome()));
        add(header, BorderLayout.NORTH);

        // Corpo 1
        int totalAcessos = registroDao.countByUid(user.getUid());
        JPanel corpo1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        corpo1.setBorder(BorderFactory.createTitledBorder("Informações"));
        corpo1.add(new JLabel("Total de acessos do usuário: " + totalAcessos));
        add(corpo1, BorderLayout.CENTER);

        // Corpo 2
        JPanel corpo2 = new JPanel(new GridLayout(0,1,5,5));
        corpo2.setBorder(BorderFactory.createTitledBorder("Menu Principal"));

        // Se for admin mostra opção de cadastro
        if (user.getGid() == 1) {
            JButton btnCadastrar = new JButton("1 – Cadastrar um novo usuário");
            btnCadastrar.addActionListener(e -> {
                dispose();
                try {
                    new CadastroFrame(user).setVisible(true);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Erro ao abrir cadastro: " + ex.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            });
            corpo2.add(btnCadastrar);
        }

        // Consultar pasta de arquivos secretos (todos os grupos)
        String consultLabel = user.getGid() == 1
                ? "2 – Consultar pasta de arquivos secretos"
                : "1 – Consultar pasta de arquivos secretos";
        JButton btnConsultar = new JButton(consultLabel);
        btnConsultar.addActionListener(e -> {
            dispose();              // fecha o menu principal
            try {
                new ConsultaFrame(user).setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Erro ao abrir consulta:\n" + ex.getMessage(),
                        "Erro",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
        corpo2.add(btnConsultar);

        // Sair do sistema / sessão
        String exitLabel = user.getGid() == 1
                ? "3 – Sair do Sistema"
                : "2 – Sair do Sistema";
        JButton btnSair = new JButton(exitLabel);
        btnSair.addActionListener(e -> {
            dispose();
            try {
                new SaidaFrame(user).setVisible(true);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
        corpo2.add(btnSair);

        add(corpo2, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }
}
