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
 * FTPServer handles incoming FTP client connections, executing commands such as LS, CD, GET, and PUT.
* It uses a single persistent connection and ensures clean file transfers with proper stream management.
*/
public class FTPServer {
    private static int listenPort = 2121;
    private static boolean running = true; // Server running flag
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

        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
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
            printAndLog("Could not listen on port " + listenPort + ": " + e.getMessage());
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
                break;
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
            this.currentDir = ROOT_DIR; // Start in the root directory
            printAndLog("ClientHandler initialized for: " + clientSocket.getInetAddress());
        }

        @Override
        public void run() {
            printAndLog("Handling client connection...");
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    printAndLog("Received command: " + inputLine); // Log received command
                    String[] command = inputLine.split(" ");
                    switch (command[0].toUpperCase()) {
                        case "LS":
                            handleLS(out);
                            break;
                        case "CD":
                            handleCD(command, out);
                            break;
                        case "GET":
                            handleGET(command, clientSocket, out);
                            break;
                        case "PUT":
                            handlePUT(command, clientSocket, out);
                            break;
                        case "QUIT":
                            handleQUIT(out);
                            return;  // Close this client handler after QUIT
                        default:
                            out.println("Unknown command");
                            break;
                    }
                }
            } catch (IOException e) {
                printAndLog("Exception in client handling: " + e.getMessage());
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
            out.println("EOF"); // Mark the end of listing
            out.flush();
            printAndLog("LS command executed.");
        }

        /**
         * Handles the CD command to change the current directory.
        */
        private void handleCD(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File newDir = new File(currentDir + File.separator + command[1]);
                try {
                    if (newDir.isDirectory() && newDir.getCanonicalPath().startsWith(ROOT_DIR)) {
                        currentDir = newDir.getCanonicalPath(); // Update current directory
                        out.println("Changed directory to: " + currentDir);
                        printAndLog("Changed directory to: " + currentDir);
                    } else {
                        out.println("Directory not found or permission denied.");
                    }
                } catch (IOException e) {
                    printAndLog("Error changing directory: " + e.getMessage());
                }
            } else {
                out.println("ERROR: No directory specified.");
            }
            out.flush();
        }

        /**
         * Handles the GET command for file download.
        * @param command The command array containing the GET command and file name.
        * @param clientSocket The client socket to send the file data through.
        * @param out The output writer to communicate with the client.
        */
        private void handleGET(String[] command, Socket clientSocket, PrintWriter out) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                if (file.exists() && !file.isDirectory()) {
                    try (
                        FileInputStream fis = new FileInputStream(file);
                        BufferedOutputStream bos = new BufferedOutputStream(clientSocket.getOutputStream());
                        FileChannel fileChannel = fis.getChannel();
                        FileLock lock = fileChannel.lock(0, Long.MAX_VALUE, true) // Shared lock for reading
                    ) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);  // Write file data
                        }
                        bos.flush();  // Ensure everything is sent to the client
                        bos.write("EOF".getBytes()); // Send EOF marker explicitly after sending file
                        bos.flush();
                        printAndLog("File " + command[1] + " sent to client.");

                    } catch (IOException e) {
                        printAndLog("Error sending file: " + e.getMessage());
                        out.println("ERROR: Could not transfer file");
                    }
                } else {
                    out.println("ERROR: File not found");
                }
            } else {
                out.println("ERROR: No file specified for GET command.");
            }
            out.flush(); // Ensure all communication is completed
        }

        /**
         * Handles the PUT command for file upload.
        * @param command The command array containing the PUT command and file name.
        * @param clientSocket The client socket to receive the file data through.
        * @param out The output writer to communicate with the client.
        */
        private void handlePUT(String[] command, Socket clientSocket, PrintWriter out) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                try (
                    BufferedInputStream bis = new BufferedInputStream(clientSocket.getInputStream());
                    FileOutputStream fos = new FileOutputStream(file, false); // Overwrite mode
                    FileChannel fileChannel = fos.getChannel();
                    FileLock lock = fileChannel.lock() // Exclusive lock for writing
                ) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        String chunk = new String(buffer, 0, bytesRead);
                        if (chunk.contains("EOF")) {
                            int eofIndex = chunk.indexOf("EOF");
                            fos.write(buffer, 0, eofIndex);  // Write everything up to EOF
                            break;
                        }
                        fos.write(buffer, 0, bytesRead);  // Write file data
                    }
                    fos.flush();
                    printAndLog("File " + command[1] + " received from client.");
                } catch (IOException e) {
                    printAndLog("Error receiving file: " + e.getMessage());
                    out.println("ERROR: Could not receive file");
                }
            } else {
                out.println("ERROR: No file specified.");
            }
            out.flush(); // Ensure all communication is completed
        }

        /**
         * Handles the QUIT command, closing the client connection.
        * @param out The output writer to communicate with the client.
        * @throws IOException If an I/O error occurs.
        */
        private void handleQUIT(PrintWriter out) throws IOException {
            out.println("Goodbye!"); // Inform the client the server is closing the connection
            printAndLog("Client issued QUIT. Closing connection.");

            // Close the client socket
            clientSocket.close();
            printAndLog("Client connection closed.");
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