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
                printAndLog(responseLine);
            }

            // After the LS, switch to the menu
            menu(out, in, stdIn);

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
     * Menu system for user interaction
    */
    private static void menu(PrintWriter out, BufferedReader in, BufferedReader stdIn) throws IOException {
        while (true) {
            System.out.println("Menu:\n1) GET\n2) PUT\n3) CD\n4) LS\n5) QUIT");
            System.out.print("Enter choice: ");
            String choice = stdIn.readLine();
            switch (choice) {
                case "1":
                    System.out.print("Enter file name to download: ");
                    String getFileName = stdIn.readLine();
                    long startTime = System.currentTimeMillis();
                    receiveFile(getFileName, out, in);
                    long endTime = System.currentTimeMillis();
                    logTransferDetails("GET", getFileName, startTime, endTime);
                    break;
                case "2":
                    System.out.print("Enter file name to upload: ");
                    String putFileName = stdIn.readLine();
                    startTime = System.currentTimeMillis();
                    sendFile(putFileName, out, in);
                    endTime = System.currentTimeMillis();
                    logTransferDetails("PUT", putFileName, startTime, endTime);
                    break;
                case "3":
                    System.out.print("Enter directory to change to: ");
                    String dirName = stdIn.readLine();
                    out.println("CD " + dirName);
                    printAndLog(in.readLine());

                    // Run LS after CD to list directory contents
                    out.println("LS");
                    String responseLine;
                    while (!(responseLine = in.readLine()).equals("EOF")) {
                        printAndLog(responseLine);
                    }
                    break;
                case "4":
                    out.println("LS");
                    while (!(responseLine = in.readLine()).equals("EOF")) {
                        printAndLog(responseLine);
                    }
                    break;
                case "5":
                    out.println("QUIT");
                    printAndLog(in.readLine());
                    return;
                default:
                    System.out.println("Invalid option.");
                    break;
            }
        }
    }

    /**
     * Handles the file receiving for the GET command.
    */
    private static void receiveFile(String fileName, PrintWriter out, BufferedReader in) throws IOException {
        out.println("GET " + fileName); // Send GET command
        String serverResponse = in.readLine();
        if (serverResponse.startsWith("READY")) {
            int port = Integer.parseInt(serverResponse.split(" ")[1]);
            try (Socket transferSocket = new Socket("localhost", port);
                BufferedInputStream bis = new BufferedInputStream(transferSocket.getInputStream());
                FileOutputStream fos = new FileOutputStream(fileName)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.flush();
                printAndLog("File " + fileName + " downloaded.");
            }
        } else {
            printAndLog("Server error: " + serverResponse);
        }
    }

    /**
     * Handles the file sending for the PUT command.
    */
    private static void sendFile(String fileName, PrintWriter out, BufferedReader in) throws IOException {
        out.println("PUT " + fileName); // Send PUT command
        String serverResponse = in.readLine();
        if (serverResponse.startsWith("READY")) {
            int port = Integer.parseInt(serverResponse.split(" ")[1]);
            try (Socket transferSocket = new Socket("localhost", port);
                BufferedOutputStream bos = new BufferedOutputStream(transferSocket.getOutputStream());
                FileInputStream fis = new FileInputStream(fileName)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                bos.flush();
                printAndLog("File " + fileName + " uploaded.");
            }
        } else {
            printAndLog("Server error: " + serverResponse);
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