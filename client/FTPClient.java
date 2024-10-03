/* Name: Jason Gardner (n01480000), Samhitha Yenugu (n01603653), Deepak Yadama (n01601954), Sankeerthi Kilaru (n01598034)
 * Date: 21 August 2024
 * Project: Project 1/2
 * File: FTPClient.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: FTP server program
 *              Commands: GET, PUT, CD, LS, QUIT
 */

import java.io.*;
import java.net.*;
import java.util.logging.*;

/**
 * FTPClient interacts with the FTPServer to execute commands like LS, CD, GET, and PUT.
* It maintains a persistent connection with the server and handles file transfers using proper stream management.
*/
public class FTPClient {
    static final Logger LOGGER = Logger.getLogger("FTPClient");

    public static void main(String[] args) throws IOException {
        System.out.println("Starting FTP Client...");
        LogToFile.logToFile(LOGGER, "FTPClient.log"); // Log to file
        printAndLog("Logging to FTPClient.log");

        if (args.length == 2) {
            printAndLog("Connecting to " + args[0] + " on port " + args[1]);
        } else if (args.length == 1) {
            printAndLog("Attempting to connect to " + args[0] + " on default port (2121)");
        } else {
            printAndLog("Usage: java FTPClient <hostname> <port number>");
            System.exit(1);
        }

        String hostName = args[0];
        final int portNumber = args.length == 2 ? Integer.parseInt(args[1]) : 2121;

        try (
            Socket ftpSocket = new Socket(hostName, portNumber);
            PrintWriter out = new PrintWriter(ftpSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(ftpSocket.getInputStream()));
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
        ) {
            printAndLog("Connection successful to " + hostName + ":" + portNumber);

            // Automatically send an LS command on login to list the initial directory contents
            out.println("LS");
            String responseLine;
            while (!(responseLine = in.readLine()).equals("EOF")) {
                printAndLog("Server Response (LS): " + responseLine);
            }

            // After the LS, allow user input
            String userInput;
            while (!(userInput = stdIn.readLine()).equalsIgnoreCase("QUIT")) {
                String[] command = userInput.split(" ", 2);
                String cmd = command[0].toUpperCase(); // Only the command part (e.g., GET, PUT)
                String argument = command.length > 1 ? command[1] : ""; // Filename stays unchanged

                switch (cmd) {
                    case "GET":
                        out.println(cmd + " " + argument);
                        long startTime = System.currentTimeMillis();
                        receiveFile(argument, ftpSocket, in);
                        long endTime = System.currentTimeMillis();
                        logTransferDetails("GET", argument, startTime, endTime);
                        break;

                    case "PUT":
                        out.println(cmd + " " + argument);
                        startTime = System.currentTimeMillis();
                        sendFile(argument, ftpSocket, out);
                        endTime = System.currentTimeMillis();
                        logTransferDetails("PUT", argument, startTime, endTime);
                        break;

                    case "CD":
                        out.println(cmd + " " + argument);
                        printAndLog("Server Response: " + in.readLine());

                        // Run LS after CD to list directory contents
                        out.println("LS");
                        while (!(responseLine = in.readLine()).equals("EOF")) {
                            printAndLog("Server Response (LS): " + responseLine);
                        }
                        break;

                    case "LS":
                        out.println(cmd);  // Send LS command to the server
                        while (!(responseLine = in.readLine()).equals("EOF")) {
                            printAndLog("Server Response (LS): " + responseLine);  // Print server response
                        }
                        break;

                    default:
                        out.println(userInput); // Send raw user input to the server
                        printAndLog("Server Response: " + in.readLine());
                        break;
                }
            }
            out.println("QUIT");
            printAndLog("Server Response: " + in.readLine());
        } catch (UnknownHostException e) {
            printAndLog("Unknown host: " + hostName);
            System.exit(1);
        } catch (IOException e) {
            printAndLog("Couldn't get I/O for the connection to " + hostName + ":" + portNumber);
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Handles the file receiving for the GET command.
    * @param fileName The name of the file to download.
    * @param ftpSocket The socket through which data is received.
    * @param in The input reader to communicate with the server.
    * @throws IOException If an I/O error occurs.
    */
    private static void receiveFile(String fileName, Socket ftpSocket, BufferedReader in) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(ftpSocket.getInputStream());
            FileOutputStream fos = new FileOutputStream(fileName, false)) {  // Overwrite mode
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
            printAndLog("File " + fileName + " downloaded.");
        }
    }

    /**
     * Handles the file sending for the PUT command.
    * @param fileName The name of the file to upload.
    * @param ftpSocket The socket through which data is sent.
    * @param out The output writer to communicate with the server.
    * @throws IOException If an I/O error occurs.
    */
    private static void sendFile(String fileName, Socket ftpSocket, PrintWriter out) throws IOException {
        File file = new File(fileName);
        if (file.exists() && !file.isDirectory()) {
            try (BufferedOutputStream bos = new BufferedOutputStream(ftpSocket.getOutputStream());
                FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);  // Send file data
                }
                bos.write("EOF".getBytes());  // End of file marker
                bos.flush();
                printAndLog("File " + fileName + " uploaded.");
            }
        } else {
            printAndLog("File not found: " + fileName);
        }
    }

    /**
     * Logs the details of a file transfer (GET/PUT), including file size and throughput.
    * @param operation The type of operation (GET/PUT).
    * @param fileName The name of the file being transferred.
    * @param startTime The start time of the transfer.
    * @param endTime The end time of the transfer.
    * @throws IOException If an I/O error occurs while logging file size.
    */
    private static void logTransferDetails(String operation, String fileName, long startTime, long endTime) throws IOException {
        File file = new File(fileName);
        long fileSize = file.length();
        long duration = endTime - startTime;
        double throughput = (fileSize / (duration / 1000.0)) / 1024.0; // Throughput in KB/s
        printAndLog(operation + " of " + fileName + " completed in " + duration + " ms");
        printAndLog("File size: " + fileSize + " bytes");
        printAndLog("Throughput: " + throughput + " KB/s");
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