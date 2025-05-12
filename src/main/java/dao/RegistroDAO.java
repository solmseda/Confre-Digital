package dao;

import model.Registro;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RegistroDAO {

    public void insert(Registro r) throws SQLException {
        String sql = "INSERT INTO Registros(MID, UID, detalhes) VALUES(?, ?, ?)";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getMid());
            if (r.getUid() != null) {
                ps.setInt(2, r.getUid());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, r.getDetalhes());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    r.setRid(rs.getLong(1));
                }
            }
        }
    }

    public Registro findById(long rid) throws SQLException {
        String sql = "SELECT * FROM Registros WHERE RID = ?";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, rid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Registro> findAll() throws SQLException {
        List<Registro> list = new ArrayList<>();
        String sql = "SELECT * FROM Registros ORDER BY timestamp";
        try (Connection c = ConnectionFactory.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void update(Registro r) throws SQLException {
        String sql = "UPDATE Registros SET MID = ?, UID = ?, detalhes = ? WHERE RID = ?";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, r.getMid());
            if (r.getUid() != null) {
                ps.setInt(2, r.getUid());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, r.getDetalhes());
            ps.setLong(4, r.getRid());
            ps.executeUpdate();
        }
    }

    public void delete(long rid) throws SQLException {
        String sql = "DELETE FROM Registros WHERE RID = ?";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, rid);
            ps.executeUpdate();
        }
    }

    private Registro mapRow(ResultSet rs) throws SQLException {
        Registro r = new Registro();
        r.setRid(rs.getLong("RID"));
        Timestamp ts = rs.getTimestamp("timestamp");
        r.setTimestamp(ts != null ? ts.toLocalDateTime() : null);
        r.setMid(rs.getInt("MID"));
        int uidVal = rs.getInt("UID");
        r.setUid(rs.wasNull() ? null : uidVal);
        r.setDetalhes(rs.getString("detalhes"));
        return r;
    }
}
