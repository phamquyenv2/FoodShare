import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class FixFlyway {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/foodsharedb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", "root", "");
            Statement stmt = conn.createStatement();
            stmt.execute("DELETE FROM flyway_schema_history WHERE version='9'");
            stmt.execute("DROP TABLE IF EXISTS system_configs");
            System.out.println("Cleaned up DB!");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
