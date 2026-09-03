import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestNotification {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // 1. Login
        String loginBody = "{\"identifier\":\"admin@foodshare.com\",\"password\":\"Admin@123456\"}";
        HttpRequest loginReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/api/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(loginBody))
            .build();
            
        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        if(loginRes.statusCode() != 200) {
            System.out.println("Login failed: " + loginRes.body());
            return;
        }
        
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(loginRes.body());
        if(!m.find()) return;
        String token = m.group(1);
        System.out.println("Login OK");
        
        // 2. Get notifications
        HttpRequest getReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/api/notifications?page=0"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        
        // Find an unread one
        m = Pattern.compile("\"id\":(\\d+),\"title\":\"[^\"]+\",\"content\":\"[^\"]+\",\"isRead\":false").matcher(getRes.body());
        if(!m.find()) {
            System.out.println("No unread notifications found!");
            return;
        }
        String id = m.group(1);
        System.out.println("Marking " + id + " as read...");
        
        // 3. Mark as read
        HttpRequest patchReq = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/api/notifications/" + id + "/read"))
            .header("Authorization", "Bearer " + token)
            .method("PATCH", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> patchRes = client.send(patchReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Patch Status: " + patchRes.statusCode());
        
        // 4. Get again
        getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        
        m = Pattern.compile("\"id\":" + id + ".*?\"isRead\":(true|false)").matcher(getRes.body());
        if(m.find()) {
            System.out.println("isRead is now: " + m.group(1));
        } else {
            System.out.println("Could not find the notification in the response!");
        }
    }
}
