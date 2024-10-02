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
import java.util.Scanner;
import java.util.logging.*;

/**
 * FTPServer handles client connections and commands like LS, CD, GET, and PUT.
* It creates a new socket for file transfers (GET/PUT) to avoid blocking the main connection.
*/
public class FTPServer {
    private static ServerSocket serverSocket; // Global ServerSocket for main communication
    private static int listenPort = 2121;     // Default port for client connections
    private static boolean running = true;    // Server running flag
    static final Logger LOGGER = Logger.getLogger("FTPServer");

    public static void main(String[] args) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPServer.log"); // Log to file

        if (args.length > 0) {
            listenPort = Integer.parseInt(args[0]);
        } else {
            printAndLog("Attempting to listen on default port (2121)");
        }

        // Start the server shutdown listener (listens for "q" to quit)
        new Thread(FTPServer::shutdownListener).start();

        try {
            serverSocket = new ServerSocket(listenPort);
            printAndLog("Listening on port: " + listenPort);

            // Main loop to accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIp = clientSocket.getInetAddress().toString();
                    printAndLog("Accepted connection from IP_ADDR: " + clientIp);

                    // Handle client connection in a new thread
                    Thread clientThread = new Thread(new ClientHandler(clientSocket));
                    clientThread.start();
                    printAndLog("Started thread for client IP_ADDR: " + clientIp);

                } catch (IOException e) {
                    if (running) { // Only log if still running
                        printAndLog("Error accepting connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            printAndLog("Could not listen on port " + listenPort + ": " + e.getMessage());
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                printAndLog("Server socket closed.");
            }
        }
    }

    /**
     * Listens for "q" input to shut down the server.
    */
    private static void shutdownListener() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            if (scanner.nextLine().equalsIgnoreCase("q")) {
                printAndLog("Shutting down the server...");
                running = false;
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close(); // Close the server socket to stop accept()
                    }
                } catch (IOException e) {
                    printAndLog("Error closing the server socket: " + e.getMessage());
                }
                break;
            }
        }
        scanner.close();
    }

    /**
     * Handles individual client requests on a separate thread.
    */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String clientIp;
        private static final String ROOT_DIR = System.getProperty("user.dir");
        private String currentDir;

        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            this.clientIp = clientSocket.getInetAddress().toString();
            this.currentDir = ROOT_DIR; // Start in the root directory
            printAndLog("ClientHandler initialized for IP_ADDR: " + clientIp);
        }

        @Override
        public void run() {
            printAndLog("Handling client connection from IP_ADDR: " + clientIp);
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    printAndLog("Received command from IP_ADDR: " + clientIp + ": " + inputLine);
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
                            return;  // Close this client handler after QUIT
                        default:
                            out.println("Unknown command");
                            out.flush();
                            printAndLog("Unknown command received from IP_ADDR: " + clientIp);
                            break;
                    }
                }
            } catch (IOException e) {
                printAndLog("Exception in client handling for IP_ADDR: " + clientIp + ": " + e.getMessage());
            }
        }

        /**
         * Handles the LS command to list files in the current directory.
        */
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
            printAndLog("LS command executed for IP_ADDR: " + clientIp);
        }

        /**
         * Handles the CD command to change the current directory.
        */
        private void handleCD(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File newDir = new File(currentDir + File.separator + command[1]);
                try {
                    if (newDir.isDirectory() && newDir.getCanonicalPath().startsWith(ROOT_DIR)) {
                        currentDir = newDir.getCanonicalPath();
                        out.println("Changed directory to: " + currentDir);
                        printAndLog("Changed directory to: " + currentDir + " for IP_ADDR: " + clientIp);
                    } else {
                        out.println("Directory not found or permission denied.");
                    }
                } catch (IOException e) {
                    printAndLog("Error changing directory for IP_ADDR: " + clientIp + ": " + e.getMessage());
                }
            } else {
                out.println("ERROR: No directory specified.");
            }
            out.flush();
        }

        /**
         * Handles the GET command for file downloads.
        */
        private void handleGET(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                if (file.exists() && !file.isDirectory()) {
                    try (ServerSocket transferSocket = new ServerSocket(0)) {
                        int transferPort = transferSocket.getLocalPort();
                        out.println("TRANSFER " + transferPort); // Tell client the transfer port
                        out.flush();

                        Socket transferClient = transferSocket.accept();
                        printAndLog("GET: Transfer connection accepted for IP_ADDR: " + clientIp);

                        try (FileInputStream fis = new FileInputStream(file);
                            BufferedOutputStream bos = new BufferedOutputStream(transferClient.getOutputStream());
                            FileChannel fileChannel = fis.getChannel();
                            FileLock lock = fileChannel.lock(0, Long.MAX_VALUE, true)) {

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                bos.write(buffer, 0, bytesRead);
                            }
                            bos.flush();
                            printAndLog("File " + command[1] + " sent to IP_ADDR: " + clientIp);
                        }
                    } catch (IOException e) {
                        printAndLog("Error in GET file transfer for IP_ADDR: " + clientIp + ": " + e.getMessage());
                    }
                } else {
                    out.println("ERROR: File not found");
                    out.flush();
                    printAndLog("File not found: " + command[1] + " for IP_ADDR: " + clientIp);
                }
            } else {
                out.println("ERROR: No file specified for GET command.");
                out.flush();
            }
        }

        /**
         * Handles the PUT command for file uploads.
        */
        private void handlePUT(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                try (ServerSocket transferSocket = new ServerSocket(0)) {
                    int transferPort = transferSocket.getLocalPort();
                    out.println("TRANSFER " + transferPort); // Tell client the transfer port
                    out.flush();

                    Socket transferClient = transferSocket.accept();
                    printAndLog("PUT: Transfer connection accepted for IP_ADDR: " + clientIp);

                    try (BufferedInputStream bis = new BufferedInputStream(transferClient.getInputStream());
                        FileOutputStream fos = new FileOutputStream(file, false);
                        FileChannel fileChannel = fos.getChannel();
                        FileLock lock = fileChannel.lock()) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                        printAndLog("File " + command[1] + " received from IP_ADDR: " + clientIp);
                    }
                } catch (IOException e) {
                    printAndLog("Error in PUT file transfer for IP_ADDR: " + clientIp + ": " + e.getMessage());
                }
            } else {
                out.println("ERROR: No file specified for PUT command.");
                out.flush();
            }
        }

        /**
         * Handles the QUIT command to close the client connection.
        */
        private void handleQUIT(PrintWriter out) throws IOException {
            out.println("Goodbye!"); // Inform the client the server is closing the connection
            out.flush();
            printAndLog("Client issued QUIT. Closing connection for IP_ADDR: " + clientIp);
            clientSocket.close(); // Close the client socket
            printAndLog("Client connection closed for IP_ADDR: " + clientIp);
        }
    }

    /**
     * Utility method to log messages both to the console and log file.
    */
    private static void printAndLog(String message) {
        System.out.println(message);
        LOGGER.info(message);
    }
} 