package dao;

import model.Mensagem;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MensagemDAO {

    public void insert(Mensagem m) throws SQLException {
        String sql = "INSERT INTO Mensagens(codigo, texto) VALUES(?, ?)";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getCodigo());
            ps.setString(2, m.getTexto());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    m.setMid(rs.getInt(1));
                }
            }
        }
    }

    public Mensagem findById(int mid) throws SQLException {
        String sql = "SELECT * FROM Mensagens WHERE MID = ?";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, mid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Mensagem findByCodigo(String codigo) throws SQLException {
        String sql = "SELECT * FROM Mensagens WHERE codigo = ?";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Mensagem> findAll() throws SQLException {
        List<Mensagem> list = new ArrayList<>();
        String sql = "SELECT * FROM Mensagens";
        try (Connection c = ConnectionFactory.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void update(Mensagem m) throws SQLException {
        String sql = "UPDATE Mensagens SET codigo = ?, texto = ? WHERE MID = ?";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, m.getCodigo());
            ps.setString(2, m.getTexto());
            ps.setInt(3, m.getMid());
            ps.executeUpdate();
        }
    }

    public void delete(int mid) throws SQLException {
        String sql = "DELETE FROM Mensagens WHERE MID = ?";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, mid);
            ps.executeUpdate();
        }
    }

    private Mensagem mapRow(ResultSet rs) throws SQLException {
        Mensagem m = new Mensagem();
        m.setMid(rs.getInt("MID"));
        m.setCodigo(rs.getString("codigo"));
        m.setTexto(rs.getString("texto"));
        return m;
    }
}
