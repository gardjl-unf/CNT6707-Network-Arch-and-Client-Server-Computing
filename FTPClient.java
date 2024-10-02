/* Name: Jason Gardner (n01480000), Samhitha Yenugu (n01603653), Deepak Yadama (n01601954), Sankeerthi Kilaru (n01598034)
 * Date: 21 August 2024
 * Project: Project 1/2
 * File: FTPClient.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: FTP server program
 *              Commands: GET, PUT, CD, LS, QUIT
 */

import java.net.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.logging.*;

public class FTPServer {
    private static int listenPort = 2121;
    static final Logger LOGGER = Logger.getLogger("FTPServer");

    public static void main(String[] args) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPServer.log"); // Log to file

        if (args.length > 0) {
            listenPort = Integer.parseInt(args[0]);
        } else {
            printAndLog("Attempting to listen on default port (2121)");
        }

        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            printAndLog("Listening on port: " + listenPort);

            // Main loop to accept client connections
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    printAndLog("Accepted connection from: " + clientSocket.getInetAddress());

                    // Handle client connection in a new thread
                    Thread clientThread = new Thread(new ClientHandler(clientSocket));
                    clientThread.start();
                    printAndLog("Started thread for client: " + clientSocket.getInetAddress());

                } catch (IOException e) {
                    printAndLog("Error accepting connection: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            printAndLog("Could not listen on port " + listenPort + ": " + e.getMessage());
        }
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
                            handleGET(command, out);
                            break;
                        case "PUT":
                            handlePUT(command, in);
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

        private void handleLS(PrintWriter out) {
            File dir = new File(currentDir);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    out.println(file.getName());
                }
            }
            out.println("EOF"); // Mark the end of listing
            printAndLog("LS command executed.");
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
                        out.println("Directory not found or permission denied.");
                    }
                } catch (IOException e) {
                    printAndLog("Error changing directory: " + e.getMessage());
                }
            } else {
                out.println("ERROR: No directory specified.");
            }
        }

        private void handleGET(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                if (file.exists() && !file.isDirectory()) {
                    try (
                        FileInputStream fis = new FileInputStream(file);
                        FileChannel fileChannel = fis.getChannel();
                        FileLock lock = fileChannel.lock(0, Long.MAX_VALUE, true) // Shared lock for reading
                    ) {
                        BufferedReader fileReader = new BufferedReader(new InputStreamReader(fis));
                        String line;
                        while ((line = fileReader.readLine()) != null) {
                            out.println(line);
                        }
                        out.println("EOF"); // End of file marker
                        printAndLog("File " + command[1] + " sent to client.");
                    } catch (IOException e) {
                        out.println("Error reading file");
                        printAndLog("Error sending file: " + e.getMessage());
                    }
                } else {
                    out.println("File not found");
                }
            } else {
                out.println("ERROR: No file specified.");
            }
        }

        private void handlePUT(String[] command, BufferedReader in) {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                try (
                    FileOutputStream fos = new FileOutputStream(file, false); // Overwrite mode
                    FileChannel fileChannel = fos.getChannel();
                    FileLock lock = fileChannel.lock() // Exclusive lock for writing
                ) {
                    PrintWriter fileWriter = new PrintWriter(fos);
                    String line;
                    while (!(line = in.readLine()).equals("EOF")) {
                        fileWriter.println(line);
                    }
                    fileWriter.flush();
                    printAndLog("File " + command[1] + " received from client.");
                } catch (IOException e) {
                    printAndLog("Error receiving file: " + e.getMessage());
                }
            } else {
                printAndLog("ERROR: No file specified.");
            }
        }

        private void handleQUIT(PrintWriter out) throws IOException {
            out.println("Goodbye!"); // Inform the client the server is closing the connection
            printAndLog("Client issued QUIT. Closing connection.");

            // Close the client socket
            clientSocket.close();
            printAndLog("Client connection closed.");
        }
    }

    private static void printAndLog(String message) {
        System.out.println(message);
        LOGGER.info(message);
    }
}