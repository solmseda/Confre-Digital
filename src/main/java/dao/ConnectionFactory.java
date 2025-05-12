package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {
    private static final String URL  = "jdbc:mysql://localhost:3306/trabalho3?useSSL=false&serverTimezone=UTC";
    private static final String USER = "appuser";
    private static final String PASS = "s3nh4F0rt3";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
