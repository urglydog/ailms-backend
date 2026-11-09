package com.lms;

import java.sql.*;

public class TestDb {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/lms_db", "root", "");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM quiz_attempt");
        while(rs.next()) {
            System.out.println("ID: " + rs.getLong("id") + ", QuizId: " + rs.getLong("quiz_id") + ", Status: " + rs.getString("status"));
        }
    }
}
