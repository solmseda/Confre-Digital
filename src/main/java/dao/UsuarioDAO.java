package dao;

import model.Usuario;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsuarioDAO {

    /**
     * Insere um novo usuário. O KID fica como NULL inicialmente
     * (será preenchido posteriormente pelo ChaveiroDAO).
     */
    public void insert(Usuario u) throws SQLException {
        String sql = "INSERT INTO Usuarios("
                + "login_email, nome, senha_bcrypt, totp_secret_enc, GID"
                + ") VALUES(?,?,?,?,?)";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, u.getLoginEmail());
            ps.setString(2, u.getNome());
            ps.setString(3, u.getSenhaBcrypt());
            ps.setBytes(4, u.getTotpSecretEnc());
            ps.setInt(5, u.getGid());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    u.setUid(rs.getInt(1));
                }
            }
        }
    }

    /**
     * Busca um usuário pelo UID.
     */
    public Usuario findById(int uid) throws SQLException {
        String sql = "SELECT * FROM Usuarios WHERE UID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Busca um usuário pelo login_email.
     */
    public Usuario findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM Usuarios WHERE login_email = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retorna todos os usuários.
     */
    public List<Usuario> findAll() throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        String sql = "SELECT * FROM Usuarios";
        try (Connection conn = ConnectionFactory.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                lista.add(mapRow(rs));
            }
        }
        return lista;
    }

    /**
     * Atualiza todos os campos do usuário, incluindo KID.
     */
    public void update(Usuario u) throws SQLException {
        String sql = "UPDATE Usuarios SET "
                + "login_email = ?, "
                + "nome = ?, "
                + "senha_bcrypt = ?, "
                + "totp_secret_enc = ?, "
                + "GID = ?, "
                + "KID = ? "
                + "WHERE UID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, u.getLoginEmail());
            ps.setString(2, u.getNome());
            ps.setString(3, u.getSenhaBcrypt());
            ps.setBytes(4, u.getTotpSecretEnc());
            ps.setInt(5, u.getGid());

            // KID pode ser null
            if (u.getKid() != null) {
                ps.setInt(6, u.getKid());
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            ps.setInt(7, u.getUid());
            ps.executeUpdate();
        }
    }

    /**
     * Deleta um usuário pelo UID.
     */
    public void delete(int uid) throws SQLException {
        String sql = "DELETE FROM Usuarios WHERE UID = ?";
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, uid);
            ps.executeUpdate();
        }
    }

    /**
     * Mapeia uma linha do ResultSet para o objeto Usuario,
     * incluindo UID, login_email, nome, senha_bcrypt, totp_secret_enc,
     * GID, KID e criado_em.
     */
    private Usuario mapRow(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setUid(rs.getInt("UID"));
        u.setLoginEmail(rs.getString("login_email"));
        u.setNome(rs.getString("nome"));
        u.setSenhaBcrypt(rs.getString("senha_bcrypt"));
        u.setTotpSecretEnc(rs.getBytes("totp_secret_enc"));
        u.setGid(rs.getInt("GID"));

        // Mapeamento de KID (pode ser NULL)
        int kidVal = rs.getInt("KID");
        u.setKid(rs.wasNull() ? null : kidVal);

        // Data de criação
        Timestamp ts = rs.getTimestamp("criado_em");
        u.setCriadoEm(ts != null ? ts.toLocalDateTime() : null);

        return u;
    }
}