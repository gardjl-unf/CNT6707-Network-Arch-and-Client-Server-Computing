import java.net.*;
import java.io.*;

public class SimpleServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(2221);
        System.out.println("Server listening on port 2221...");
        Socket clientSocket = serverSocket.accept();
        System.out.println("Connection accepted: " + clientSocket.getInetAddress());
        
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        String line = in.readLine();
        System.out.println("Received: " + line);
        out.println("Hello from server!");

        clientSocket.close();
        serverSocket.close();
    }
}
