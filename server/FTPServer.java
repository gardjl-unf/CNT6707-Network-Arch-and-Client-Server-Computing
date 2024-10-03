/* Name: Jason Gardner (n01480000), Samhitha Yenugu (n01603653), Deepak Yadama (n01601954), Sankeerthi Kilaru (n01598034)
 * Date: 21 August 2024
 * Project: Project 1/2
 * File: FTPServer.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: Mutlithreaded FTP server program that uses threads to handle multiple clients
 *              Commands: GET, PUT, CD, LS, QUIT
 */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.*;
import java.util.Arrays;

/**
 * FTPServer handles incoming FTP client connections, executing commands such as LS, CD, GET, and PUT.
* It uses a single persistent connection and ensures clean file transfers with proper stream management.
*/
public class FTPServer {
    private static int listenPort = 2121;
    private static boolean running = true; // Server running flag
    private static ServerSocket serverSocket; // Class-level ServerSocket for handling shutdown
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
            serverSocket = new ServerSocket(listenPort);  // Initialize class-level ServerSocket
            printAndLog("Listening on port: " + listenPort);

            // Main loop to accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    printAndLog("Accepted connection from: " + clientSocket.getInetAddress());

                    // Handle client connection in a new thread
                    Thread clientThread = new Thread(new ClientHandler(clientSocket));
                    clientThread.start();
                    printAndLog("Started thread for client: " + clientSocket.getInetAddress());

                } catch (IOException e) {
                    if (running) { // Only log if still running
                        printAndLog("Error accepting connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            if (running) {
                printAndLog("Could not listen on port " + listenPort + ": " + e.getMessage());
            }
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
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
                running = false;
                printAndLog("Shutting down the server...");
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();  // Close the server socket to unblock accept()
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
     * ClientHandler class to handle individual client connections in separate threads.
     * Each client connection is handled by a separate instance of this class.
     * The class implements the Runnable interface to run in a separate thread.
     * The class handles the LS, CD, GET, PUT, and QUIT commands.
     * The class maintains the current directory for each client.
     * The class uses a static ROOT_DIR for the server root directory.
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String clientAddress; // Store client address for logging
        private static final String ROOT_DIR = System.getProperty("user.dir");
        private String currentDir;
    
        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            this.clientAddress = clientSocket.getInetAddress().toString(); // Capture client address
            this.currentDir = ROOT_DIR; // Start in the root directory
            printAndLog("ClientHandler initialized for: " + clientAddress);
        }
    
        /**
         * Run method to handle client connections and execute commands.
        */
        @Override
        public void run() {
            printAndLog("Handling client connection from: " + clientAddress);
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    printAndLog("Received command from " + clientAddress + ": " + inputLine); // Log with client info
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
                            break;
                    }
                }
            // Handle exceptions and close the client connection
            } catch (IOException e) {
                printAndLog("Exception in client handling for " + clientAddress + ": " + e.getMessage());
            }
        }
    
        /**
         * Handles the LS command to list files in the current directory in the desired format.
         * @param out The output writer to communicate with the client.
         */
        private void handleLS(PrintWriter out) {
            File dir = new File(currentDir);
            File[] files = dir.listFiles();
    
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
    
                out.println("Directory:\t" + currentDir);
                out.println("\t.");
                out.println("\t..");
    
                for (File file : files) {
                    if (file.isDirectory()) {
                        out.println("\t/" + file.getName() + "/");
                    }
                }
    
                for (File file : files) {
                    if (!file.isDirectory()) {
                        out.println("\t/" + file.getName());
                    }
                }
            }
            out.println("EOF"); // Mark the end of listing
            out.flush();
            printAndLog("LS command executed by " + clientAddress);
        }
    
        /**
         * Handles the CD command to change the current directory.
         * @param command The command array containing the directory to change to.
         * @param out The output writer to communicate with the client.
         */
        private void handleCD(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File newDir = new File(currentDir + File.separator + command[1]);
                try {
                    if (newDir.isDirectory() && newDir.getCanonicalPath().startsWith(ROOT_DIR)) {
                        currentDir = newDir.getCanonicalPath(); // Update current directory
                        out.println("Changed directory to: " + currentDir);
                        printAndLog("Changed directory to: " + currentDir + " for client: " + clientAddress);
                    } else {
                        out.println("Directory not found or permission denied.");
                    }
                } catch (IOException e) {
                    printAndLog("Error changing directory for " + clientAddress + ": " + e.getMessage());
                }
            } else {
                out.println("ERROR: No directory specified.");
            }
            out.flush();
        }
    
        /**
         * Handles the GET command for file download.
         * @param command The command array containing the file to download.
         * @param out The output writer to communicate with the client.
         * @throws IOException If an I/O error occurs while sending the file.
         */
        private void handleGET(String[] command, PrintWriter out) throws IOException {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                if (file.exists() && !file.isDirectory()) {
                    try (ServerSocket transferSocket = new ServerSocket(0)) {
                        out.println("READY " + transferSocket.getLocalPort()); // Inform the client of the new port
                        try (Socket fileTransferSocket = transferSocket.accept();
                             FileInputStream fis = new FileInputStream(file);
                             BufferedOutputStream bos = new BufferedOutputStream(fileTransferSocket.getOutputStream())) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                bos.write(buffer, 0, bytesRead);
                            }
                            bos.flush();
                            printAndLog("File " + command[1] + " sent to client: " + clientAddress);
                        }
                    }
                } else {
                    out.println("ERROR: File not found");
                }
            } else {
                out.println("ERROR: No file specified for GET command.");
            }
            out.flush();
        }
    
        /**
         * Handles the PUT command for file upload.
         * @param command The command array containing the file to upload.
         * @param out The output writer to communicate with the client.
         * @throws IOException If an I/O error occurs while receiving the file.
         */
        private void handlePUT(String[] command, PrintWriter out) throws IOException {
            if (command.length > 1) {
                try (ServerSocket transferSocket = new ServerSocket(0)) {
                    out.println("READY " + transferSocket.getLocalPort()); // Inform the client of the new port
                    try (Socket fileTransferSocket = transferSocket.accept();
                         BufferedInputStream bis = new BufferedInputStream(fileTransferSocket.getInputStream());
                         FileOutputStream fos = new FileOutputStream(new File(currentDir, command[1]))) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                        printAndLog("File " + command[1] + " received from client: " + clientAddress);
                    }
                }
            } else {
                out.println("ERROR: No file specified.");
            }
            out.flush();
        }
    
        /**
         * Handles the QUIT command, closing the client connection.
         * @param out The output writer to communicate with the client.
         * @throws IOException If an I/O error occurs.
         */
        private void handleQUIT(PrintWriter out) throws IOException {
            out.println("Goodbye!"); // Inform the client the server is closing the connection
            printAndLog("Client issued QUIT. Closing connection for: " + clientAddress);
    
            // Close the client socket
            clientSocket.close();
            printAndLog("Client connection closed for: " + clientAddress);
        }
    }

    /**
     * Utility method to print messages to the console and log them.
    * @param message The message to log.
    */
    private static void printAndLog(String message) {
        System.out.println(message);
        LOGGER.info(message);
    }
} 