import java.net.*;
import java.io.*;

public class SimpleClient {
    public static void main(String[] args) throws IOException {
        String serverIP = "10.11.12.64"; // Replace with the server's IP
        int port = 2221;

        Socket socket = new Socket(serverIP, port);
        System.out.println("Connected to server: " + serverIP);

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println("Hello from client!");
        String response = in.readLine();
        System.out.println("Server response: " + response);

        socket.close();
    }
}
