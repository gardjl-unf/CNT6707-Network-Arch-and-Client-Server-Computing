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

public class FTPServer {
    final private int MAX_THREADS = 250;
    private static int listenPort = 21;
    static final Logger LOGGER = Logger.getLogger("FTPServer");

    public static void main(String[] args) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPServer.log"); // Log to file
        if (args.length > 0) {
            listenPort = Integer.parseInt(args[0]);
        } else if (args.length == 0) {
            printAndLog("Attempting to listen on port 21");
            listenPort = 21;
        } else {
            printAndLog("Usage: java FTPServer <port number>");
            System.exit(1);
        }

        final Scanner userInput = new Scanner(System.in);

        while (userInput.next().charAt(0) != 'q') {
            try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
                printAndLog("Listening on port: " + listenPort);
                Socket clientSocket = serverSocket.accept();
                printAndLog("Accepted connection from: " + clientSocket.getInetAddress());
                new Thread(new server(clientSocket)).start();
            } catch (IOException e) {
                printAndLog("Could not listen on port " + listenPort);
                LOGGER.severe(e.getMessage());
                System.exit(-1);
            }
        }
        userInput.close();
        System.exit(0);
    }

    private static class server implements Runnable {
        private final Socket clientSocket;
        private static final String ROOT_DIR = System.getProperty("user.dir");
        private String currentDir;

        server(Socket clientSocket) {
            this.clientSocket = clientSocket;
            this.currentDir = ROOT_DIR; // Start in the root directory
        }

        public void run() {
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
                            File dir = new File(currentDir);
                            File[] files = dir.listFiles();
                            for (File file : files) {
                                out.println(file.getName());
                            }
                            out.println("EOF"); // Mark the end of listing
                            break;

                        case "CD":
                            if (command.length > 1) {
                                File newDir = new File(currentDir + File.separator + command[1]);
                                if (newDir.isDirectory() && newDir.getCanonicalPath().startsWith(ROOT_DIR)) {
                                    currentDir = newDir.getCanonicalPath(); // Update current directory
                                    out.println("Changed directory to: " + currentDir);
                                    printAndLog("Changed directory to: " + currentDir);
                                } else {
                                    out.println("Directory not found or permission denied.");
                                }
                            } else {
                                out.println("ERROR: No directory specified.");
                            }
                            break;

                        case "GET":
                            if (command.length > 1) {
                                sendFile(command[1], out);
                            } else {
                                out.println("ERROR: No file specified.");
                            }
                            break;

                        case "PUT":
                            if (command.length > 1) {
                                receiveFile(command[1], in);
                            } else {
                                out.println("ERROR: No file specified.");
                            }
                            break;

                        case "QUIT":
                            out.println("Goodbye!");
                            clientSocket.close();
                            return;

                        default:
                            out.println("Unknown command");
                            break;
                    }
                }
            } catch (IOException e) {
                printAndLog("Exception caught: " + e.getMessage());
                LOGGER.severe(e.getMessage());
            }
        }

        private void sendFile(String fileName, PrintWriter out) {
            File file = new File(currentDir + File.separator + fileName);
            if (file.exists() && !file.isDirectory()) {
                try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = fileReader.readLine()) != null) {
                        out.println(line);
                    }
                    out.println("EOF"); // End of file marker
                    printAndLog("File " + fileName + " sent to client.");

                } catch (IOException e) {
                    out.println("Error reading file");
                    printAndLog("Error sending file: " + e.getMessage());
                }
            } else {
                out.println("File not found");
            }
        }

        private void receiveFile(String fileName, BufferedReader in) {
            File file = new File(currentDir + File.separator + fileName);
            try (PrintWriter fileWriter = new PrintWriter(new FileWriter(file))) {
                String line;
                while (!(line = in.readLine()).equals("EOF")) {
                    fileWriter.println(line);
                }
                printAndLog("File " + fileName + " received from client.");

            } catch (IOException e) {
                printAndLog("Error receiving file: " + e.getMessage());
            }
        }
    }

    private static void printAndLog(String message) {
        // Print to console and log it
        System.out.println(message);
        LOGGER.info(message);
    }
}
