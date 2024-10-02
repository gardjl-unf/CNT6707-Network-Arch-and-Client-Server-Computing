import java.net.*;
import java.io.*;
import java.util.Scanner;
import java.util.logging.*;

public class FTPTest {
    private static int listenPort = 2121;
    static final Logger LOGGER = Logger.getLogger("FTPServer");

    public static void main(String[] args) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPServer.log"); // Log to file

        if (args.length > 0) {
            listenPort = Integer.parseInt(args[0]);
        } else {
            printAndLog("Attempting to listen on default port (2121)");
        }

        final Scanner userInput = new Scanner(System.in);

        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            printAndLog("Listening on port: " + listenPort);

            // Main loop - waiting for 'q' to quit
            while (userInput.next().charAt(0) != 'q') {
                try {
                    printAndLog("Waiting for client connection...");

                    // This call will block until a client connects
                    Socket clientSocket = serverSocket.accept();
                    printAndLog("Accepted connection from: " + clientSocket.getInetAddress());

                    // For debugging purposes, handle the client in the same thread (no new Thread)
                    handleClient(clientSocket);

                } catch (IOException e) {
                    printAndLog("Exception during client connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            printAndLog("Could not listen on port " + listenPort + ": " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }

        userInput.close();
        System.exit(0);
    }

    private static void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String inputLine;
            printAndLog("Handling client commands...");
            while ((inputLine = in.readLine()) != null) {
                printAndLog("Received command: " + inputLine); // Log received command
                out.println("Echo: " + inputLine); // Just echo back the command for now
            }
            printAndLog("Client disconnected.");

        } catch (IOException e) {
            printAndLog("Exception in client handling: " + e.getMessage());
        }
    }

    private static void printAndLog(String message) {
        System.out.println(message);
        LOGGER.info(message);
    }
}
