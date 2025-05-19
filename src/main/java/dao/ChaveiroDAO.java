package dao;

import model.Chaveiro;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChaveiroDAO {

    /**
     * Insere um novo par (certificado PEM + chave privada cifrada) vinculado a um usuário
     * e atualiza a tabela Usuarios para armazenar o KID gerado.
     */
    public void insert(Chaveiro c) throws SQLException {
        String sqlInsert =
                "INSERT INTO Chaveiro (UID, cert_pem, private_key_enc) VALUES (?, ?, ?)";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, c.getUid());
            ps.setString(2, c.getCertPem());
            ps.setBytes(3, c.getPrivateKeyEnc());
            ps.executeUpdate();

            // Recupera o KID gerado pelo banco
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedKid = rs.getInt(1);
                    c.setKid(generatedKid);

                    // Atualiza a tabela Usuarios para linkar este KID ao usuário
                    String sqlUpdateUser = "UPDATE Usuarios SET KID = ? WHERE UID = ?";
                    try (PreparedStatement ps2 = conn.prepareStatement(sqlUpdateUser)) {
                        ps2.setInt(1, generatedKid);
                        ps2.setInt(2, c.getUid());
                        ps2.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Busca um registro de Chaveiro pelo seu KID.
     */
    public Chaveiro findById(int kid) throws SQLException {
        String sql = "SELECT * FROM Chaveiro WHERE KID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, kid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retorna todos os registros de Chaveiro.
     */
    public List<Chaveiro> findAll() throws SQLException {
        List<Chaveiro> list = new ArrayList<>();
        String sql = "SELECT * FROM Chaveiro";
        try (Connection conn = ConnectionFactory.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Atualiza um registro existente de Chaveiro.
     */
    public void update(Chaveiro c) throws SQLException {
        String sql =
                "UPDATE Chaveiro SET UID = ?, cert_pem = ?, private_key_enc = ? WHERE KID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, c.getUid());
            ps.setString(2, c.getCertPem());
            ps.setBytes(3, c.getPrivateKeyEnc());
            ps.setInt(4, c.getKid());
            ps.executeUpdate();
        }
    }

    /**
     * Remove um registro de Chaveiro pelo seu KID.
     */
    public void delete(int kid) throws SQLException {
        String sql = "DELETE FROM Chaveiro WHERE KID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, kid);
            ps.executeUpdate();
        }
    }

    /**
     * Retorna o último registro de chaveiro (privateKeyEnc + certPem) para o usuário.
     */
    public Chaveiro findByUid(int uid) throws SQLException {
        String sql = "SELECT * FROM Chaveiro WHERE UID = ? ORDER BY criado_em DESC LIMIT 1";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Chaveiro ch = new Chaveiro();
                    ch.setKid(rs.getInt("KID"));
                    ch.setUid(rs.getInt("UID"));
                    ch.setCertPem(rs.getString("cert_pem"));
                    ch.setPrivateKeyEnc(rs.getBytes("private_key_enc"));
                    return ch;
                }
                return null;
            }
        }
    }

    /**
     * Constrói um objeto Chaveiro a partir de um ResultSet.
     * OBS: removida a leitura de 'criado_em' para evitar SQLException
     * caso essa coluna não exista no schema.
     */
    private Chaveiro mapRow(ResultSet rs) throws SQLException {
        Chaveiro c = new Chaveiro();
        c.setKid(rs.getInt("KID"));
        c.setUid(rs.getInt("UID"));
        c.setCertPem(rs.getString("cert_pem"));
        c.setPrivateKeyEnc(rs.getBytes("private_key_enc"));
        return c;
    }
}