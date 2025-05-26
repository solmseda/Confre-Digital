/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

package ui;

import dao.GrupoDAO;
import dao.RegistroDAO;
import model.Registro;
import model.Usuario;
import util.Logger;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/**
 * JFrame para a Tela de Saída do sistema.
 * Exibe cabeçalho, total de acessos e opções de Encerrar Sessão,
 * Encerrar Sistema ou Voltar ao Menu Principal.
 */
public class SaidaFrame extends JFrame {
    private final Usuario user;
    private final RegistroDAO registroDao = new RegistroDAO();
    private final GrupoDAO   grupoDao     = new GrupoDAO();

    public SaidaFrame(Usuario user) throws SQLException {
        super("Cofre Digital - Saída");
        this.user = user;
        initComponents();
    }

    private void initComponents() throws SQLException {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        // Cabeçalho
        JPanel header = new JPanel(new GridLayout(3,1,5,5));
        header.setBorder(BorderFactory.createTitledBorder("Dados do Usuário"));
        header.add(new JLabel("Login:  " + user.getLoginEmail()));
        header.add(new JLabel("Grupo:  " + grupoDao.findById(user.getGid()).getNomeGrupo()));
        header.add(new JLabel("Nome:   " + user.getNome()));
        add(header, BorderLayout.NORTH);

        // Corpo 1
        int totalAcessos = registroDao.countByUid(user.getUid());
        JPanel corpo1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        corpo1.setBorder(BorderFactory.createTitledBorder("Informações"));
        corpo1.add(new JLabel("Total de acessos do usuário: " + totalAcessos));
        add(corpo1, BorderLayout.CENTER);

        // Corpo 2
        JPanel corpo2 = new JPanel();
        corpo2.setBorder(BorderFactory.createTitledBorder("Saída do sistema:"));
        corpo2.setLayout(new BoxLayout(corpo2, BoxLayout.Y_AXIS));
        corpo2.add(new JLabel("Pressione o botão Encerrar Sessão ou o botão Encerrar Sistema para confirmar."));
        corpo2.add(Box.createVerticalStrut(10));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton btnSessao = new JButton("Encerrar Sessão");
        JButton btnSystem = new JButton("Encerrar Sistema");
        JButton btnVoltar = new JButton("Voltar ao Menu Principal");
        btnPanel.add(btnSessao);
        btnPanel.add(btnSystem);
        btnPanel.add(btnVoltar);

        corpo2.add(btnPanel);
        add(corpo2, BorderLayout.SOUTH);

        // Listeners
        btnSessao.addActionListener(e -> {
            // log e volta ao login
            try {
                Logger.registra("8002");
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            dispose();
            try {
                new LoginFrame().setVisible(true);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });

        btnSystem.addActionListener(e -> {
            try {
                Logger.registra("8003");
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            System.exit(0);
        });

        btnVoltar.addActionListener(e -> {
            try {
                registroDao.insert(new RegistroBuilder(8004, user.getUid(), "Botão Voltar de Sair para o Menu Principal pressionado").build());
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            dispose();
            try {
                new TelaPrincipal(user).setVisible(true);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Erro ao retornar ao menu: " + ex.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private static class RegistroBuilder {
        private final Registro r = new Registro();
        public RegistroBuilder(int mid, Integer uid, String detalhes) {
            r.setMid(mid); r.setUid(uid); r.setDetalhes(detalhes);
        }
        public Registro build() {
            return r;
        }
    }
}
