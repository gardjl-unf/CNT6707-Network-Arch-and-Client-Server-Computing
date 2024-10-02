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
                printAndLog("Server Response (LS): " + responseLine);
            }

            // After the LS, allow user input
            String userInput;
            while (!(userInput = stdIn.readLine().toUpperCase()).equals("QUIT")) {
                String[] command = userInput.split(" ");
                switch (command[0]) {
                    case "GET":
                        out.println(userInput);
                        long startTime = System.currentTimeMillis();
                        receiveFile(command[1], in);
                        long endTime = System.currentTimeMillis();
                        long responseTime = endTime - startTime;
                        long fileSize = new File(command[1]).length();
                        printAndLog("GET Response Time: " + responseTime + " ms");
                        printAndLog("GET Throughput: " + (fileSize / (responseTime / 1000.0)) + " bytes/second");
                        break;

                    case "PUT":
                        out.println(userInput);
                        startTime = System.currentTimeMillis();
                        sendFile(command[1], out);
                        endTime = System.currentTimeMillis();
                        responseTime = endTime - startTime;
                        fileSize = new File(command[1]).length();
                        printAndLog("PUT Response Time: " + responseTime + " ms");
                        break;

                    case "CD":
                        out.println(userInput);
                        printAndLog("Server Response: " + in.readLine());

                        // Run LS after CD to list directory contents
                        out.println("LS");
                        while (!(responseLine = in.readLine()).equals("EOF")) {
                            printAndLog("Server Response: " + responseLine);
                        }
                        break;

                    case "LS":
                        out.println(userInput);  // Send LS command to the server
                        while (!(responseLine = in.readLine()).equals("EOF")) {
                            printAndLog("Server Response: " + responseLine);  // Print server response
                        }
                        break;

                    default:
                        out.println(userInput);
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

    private static void receiveFile(String fileName, BufferedReader in) throws IOException {
        try (PrintWriter fileWriter = new PrintWriter(new FileWriter(fileName))) {
            String line;
            while (!(line = in.readLine()).equals("EOF")) {
                fileWriter.println(line);
            }
            printAndLog("File " + fileName + " downloaded.");
        }
    }

    private static void sendFile(String fileName, PrintWriter out) throws IOException {
        File file = new File(fileName);
        if (file.exists() && !file.isDirectory()) {
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    out.println(line);
                }
                out.println("EOF"); // End of file marker
            }
            printAndLog("File " + fileName + " uploaded.");
        } else {
            printAndLog("File not found: " + fileName);
        }
    }

    private static void printAndLog(String message) {
        // Print to console and log it
        System.out.println(message);
        LOGGER.info(message);
    }
} 