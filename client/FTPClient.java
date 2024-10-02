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
            while (!(userInput = stdIn.readLine()).equalsIgnoreCase("QUIT")) {
                String[] command = userInput.split(" ", 2);
                String cmd = command[0].toUpperCase(); // Only the command part (e.g., GET, PUT)
                String argument = command.length > 1 ? command[1] : ""; // Filename stays unchanged

                switch (cmd) {
                    case "GET":
                        out.println(cmd + " " + argument);
                        long startTime = System.currentTimeMillis();
                        receiveFile(argument, ftpSocket); // Adding diagnostics here
                        long endTime = System.currentTimeMillis();
                        long responseTime = endTime - startTime;
                        long fileSize = new File(argument).length();
                        printAndLog("GET Response Time: " + responseTime + " ms");
                        printAndLog("GET Throughput: " + (fileSize / (responseTime / 1000.0)) + " bytes/second");
                        break;

                    case "PUT":
                        out.println(cmd + " " + argument);
                        startTime = System.currentTimeMillis();
                        sendFile(argument, ftpSocket); // Adding diagnostics here
                        endTime = System.currentTimeMillis();
                        responseTime = endTime - startTime;
                        fileSize = new File(argument).length();
                        printAndLog("PUT Response Time: " + responseTime + " ms");
                        printAndLog("PUT Throughput: " + (fileSize / (responseTime / 1000.0)) + " bytes/second");
                        break;

                    case "CD":
                        out.println(cmd + " " + argument);
                        printAndLog("Server Response: " + in.readLine());

                        // Run LS after CD to list directory contents
                        out.println("LS");
                        while (!(responseLine = in.readLine()).equals("EOF")) {
                            printAndLog("Server Response: " + responseLine);
                        }
                        break;

                    case "LS":
                        out.println(cmd);  // Send LS command to the server
                        while (!(responseLine = in.readLine()).equals("EOF")) {
                            printAndLog("Server Response: " + responseLine);  // Print server response
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

    private static void receiveFile(String fileName, Socket ftpSocket) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(ftpSocket.getInputStream());
            FileOutputStream fos = new FileOutputStream(fileName, false)) {  // Overwrite mode
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;

            while ((bytesRead = bis.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, bytesRead);
                if (chunk.contains("EOF")) {
                    int eofIndex = chunk.indexOf("EOF");
                    fos.write(buffer, 0, eofIndex);  // Write everything up to EOF
                    totalBytesRead += eofIndex;
                    break;
                }
                fos.write(buffer, 0, bytesRead);  // Write file data
                totalBytesRead += bytesRead;
            }
            fos.flush();
            printAndLog("File " + fileName + " downloaded, Total Bytes: " + totalBytesRead);
        }
    }

    private static void sendFile(String fileName, Socket ftpSocket) throws IOException {
        File file = new File(fileName);
        if (file.exists() && !file.isDirectory()) {
            try (BufferedOutputStream bos = new BufferedOutputStream(ftpSocket.getOutputStream());
                FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesSent = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);  // Send file data
                    totalBytesSent += bytesRead;
                }
                bos.write("EOF".getBytes());  // End of file marker
                bos.flush();
                printAndLog("File " + fileName + " uploaded, Total Bytes: " + totalBytesSent);
            }
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