/* Name: Jason Gardner (n01480000), Samhitha Yenugu (n01603653), Deepak Yadama (n01601954), Sankeerthi Kilaru (n01598034)
 * Date: 21 August 2024
 * Project: Project 1/2
 * File: FTPServer.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: Mutlithreaded FTP server program that uses threads to handle multiple clients
 *              Commands: GET, PUT, CD, LS, QUIT
 */

import java.net.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.logging.*;
import java.util.Scanner;

/**
 * FTPServer handles client connections and commands like LS, CD, GET, and PUT.
* It uses a single socket for all file transfers and ensures proper EOF handling.
*/
public class FTPServer {
    private static int listenPort = 2121;
    private static boolean running = true;
    static final Logger LOGGER = Logger.getLogger("FTPServer");

    public static void main(String[] args) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPServer.log");

        if (args.length > 0) {
            listenPort = Integer.parseInt(args[0]);
        } else {
            printAndLog("Listening on default port 2121.");
        }

        new Thread(FTPServer::shutdownListener).start();

        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            printAndLog("Listening on port: " + listenPort);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    printAndLog("Accepted connection from: " + clientSocket.getInetAddress());
                    new Thread(new ClientHandler(clientSocket)).start();
                } catch (IOException e) {
                    if (running) {
                        printAndLog("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        }
    }

    private static void shutdownListener() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            if (scanner.nextLine().equalsIgnoreCase("q")) {
                running = false;
                printAndLog("Shutting down the server...");
            }
        }
        scanner.close();
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private static final String ROOT_DIR = System.getProperty("user.dir");
        private String currentDir;

        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            this.currentDir = ROOT_DIR;
            printAndLog("ClientHandler initialized for: " + clientSocket.getInetAddress());
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    printAndLog("Received command: " + inputLine);
                    String[] command = inputLine.split(" ");
                    switch (command[0].toUpperCase()) {
                        case "LS":
                            handleLS(out);
                            break;
                        case "CD":
                            handleCD(command, out);
                            break;
                        case "GET":
                            handleGET(command, out);
                            break;
                        case "PUT":
                            handlePUT(command, out);
                            break;
                        case "QUIT":
                            handleQUIT(out);
                            return;
                        default:
                            out.println("Unknown command");
                    }
                }
            } catch (IOException e) {
                printAndLog("Exception in client handling: " + e.getMessage());
            }
        }

        private void handleLS(PrintWriter out) {
            File dir = new File(currentDir);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    out.println(file.getName());
                }
            }
            out.println("EOF");
            out.flush();
        }

        private void handleCD(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File newDir = new File(currentDir + File.separator + command[1]);
                try {
                    if (newDir.isDirectory() && newDir.getCanonicalPath().startsWith(ROOT_DIR)) {
                        currentDir = newDir.getCanonicalPath(); // Update current directory
                        out.println("Changed directory to: " + currentDir);
                        printAndLog("Changed directory to: " + currentDir);
                    } else {
                        out.println("ERROR: Directory not found or permission denied."); // Respond with error
                        printAndLog("ERROR: Directory not found or permission denied for directory: " + command[1]);
                    }
                } catch (IOException e) {
                    printAndLog("Error changing directory: " + e.getMessage());
                    out.println("ERROR: Failed to change directory.");
                }
            } else {
                out.println("ERROR: No directory specified.");
            }
            out.flush();  // Ensure response is sent to the client
        }

        private void handleGET(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                if (file.exists() && !file.isDirectory()) {
                    try (FileInputStream fis = new FileInputStream(file);
                        BufferedOutputStream bos = new BufferedOutputStream(clientSocket.getOutputStream())) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                        bos.flush();
                        bos.write("EOF".getBytes());
                        bos.flush();
                    } catch (IOException e) {
                        printAndLog("Error sending file: " + e.getMessage());
                    }
                } else {
                    out.println("File not found.");
                }
                out.flush();
            }
        }

        private void handlePUT(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                try (BufferedInputStream bis = new BufferedInputStream(clientSocket.getInputStream());
                    FileOutputStream fos = new FileOutputStream(file)) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        if (new String(buffer, 0, bytesRead).contains("EOF")) break;
                    }
                    fos.flush();
                    out.println("File received.");
                } catch (IOException e) {
                    out.println("Error receiving file.");
                }
                out.flush();
            }
        }

        private void handleQUIT(PrintWriter out) throws IOException {
            out.println("Goodbye!");
            out.flush();
            clientSocket.close();
        }
    }

    private static void printAndLog(String message) {
        System.out.println(message);
        LOGGER.info(message);
    }
}  