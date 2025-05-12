package dao;

import model.Grupo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GrupoDAO {
    public void insert(Grupo g) throws SQLException {
        String sql = "INSERT INTO Grupos(nome_grupo) VALUES(?)";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, g.getNomeGrupo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    g.setGid(rs.getInt(1));
                }
            }
        }
    }

    public Grupo findById(int gid) throws SQLException {
        String sql = "SELECT * FROM Grupos WHERE GID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Grupo g = new Grupo();
                    g.setGid(rs.getInt("GID"));
                    g.setNomeGrupo(rs.getString("nome_grupo"));
                    return g;
                }
            }
        }
        return null;
    }

    public List<Grupo> findAll() throws SQLException {
        List<Grupo> list = new ArrayList<>();
        String sql = "SELECT * FROM Grupos";
        try (Connection conn = ConnectionFactory.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Grupo g = new Grupo();
                g.setGid(rs.getInt("GID"));
                g.setNomeGrupo(rs.getString("nome_grupo"));
                list.add(g);
            }
        }
        return list;
    }

    public void update(Grupo g) throws SQLException {
        String sql = "UPDATE Grupos SET nome_grupo = ? WHERE GID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.getNomeGrupo());
            ps.setInt(2, g.getGid());
            ps.executeUpdate();
        }
    }

    public void delete(int gid) throws SQLException {
        String sql = "DELETE FROM Grupos WHERE GID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gid);
            ps.executeUpdate();
        }
    }
}