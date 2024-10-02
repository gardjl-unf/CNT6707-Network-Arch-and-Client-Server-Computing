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
* It creates a new socket for file transfers (GET/PUT) to avoid blocking the main connection.
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
            readServerResponse(in);

            // After the LS, allow user input
            String userInput;
            while (!(userInput = stdIn.readLine()).equalsIgnoreCase("QUIT")) {
                String[] command = userInput.split(" ", 2);
                String cmd = command[0].toUpperCase(); // Only the command part (e.g., GET, PUT)
                String argument = command.length > 1 ? command[1] : ""; // Filename stays unchanged

                switch (cmd) {
                    case "GET":
                        out.println(cmd + " " + argument);
                        String portResponse = readTransferPort(in);
                        if (portResponse.equals("ERROR: File not found")) {
                            printAndLog("File not found. Aborting transfer.");
                        } else {
                            int transferPort = Integer.parseInt(portResponse);
                            long startTime = System.currentTimeMillis();
                            transferGET(argument, hostName, transferPort);
                            long endTime = System.currentTimeMillis();
                            logTransferDetails("GET", argument, startTime, endTime);
                        }
                        break;

                    case "PUT":
                        out.println(cmd + " " + argument);
                        int transferPort = Integer.parseInt(readTransferPort(in));
                        long startTime = System.currentTimeMillis();
                        transferPUT(argument, hostName, transferPort);
                        long endTime = System.currentTimeMillis();
                        logTransferDetails("PUT", argument, startTime, endTime);
                        break;

                    case "CD":
                        out.println(cmd + " " + argument);
                        printAndLog("Server Response: " + in.readLine());

                        // Run LS after CD to list directory contents
                        out.println("LS");
                        readServerResponse(in);
                        break;

                    case "LS":
                        out.println(cmd);  // Send LS command to the server
                        readServerResponse(in);
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
     * Reads the server's response for a command.
    * 
    * @param in The input stream from the server.
    * @throws IOException If an I/O error occurs.
    */
    private static void readServerResponse(BufferedReader in) throws IOException {
        String responseLine;
        while (!(responseLine = in.readLine()).equals("EOF")) {
            printAndLog("Server Response: " + responseLine);
        }
    }

    /**
     * Reads the transfer port information from the server.
     * @param in The input stream from the server.
     * @return The transfer port number.
     * @throws IOException If an I/O error occurs.
     */
    private static String readTransferPort(BufferedReader in) throws IOException {
        String transferInfo = in.readLine();
        printAndLog("Received transfer info: " + transferInfo);  // Log for debugging
        if (transferInfo.startsWith("TRANSFER ")) {
            return transferInfo.split(" ")[1]; // Extract and return the port number
        } else if (transferInfo.startsWith("ERROR")) {
            printAndLog("Error response from server: " + transferInfo);
            return "ERROR: File not found";
        }
        throw new IOException("Transfer port information not received correctly.");
    }

    /**
     * Handles the GET operation for downloading a file from the server.
     * @param fileName The name of the file to download.
     * @param hostName The host name of the server.
     * @param port The port number for the transfer socket.
     * @throws IOException If an I/O error occurs.
     */
    private static void transferGET(String fileName, String hostName, int port) throws IOException {
        printAndLog("Attempting to connect to transfer port " + port + " for GET operation.");
        try (Socket transferSocket = new Socket(hostName, port);
            BufferedInputStream bis = new BufferedInputStream(transferSocket.getInputStream());
            FileOutputStream fos = new FileOutputStream(fileName, false)) { // Overwrite mode

            printAndLog("Connected to transfer port " + port + " for GET operation.");
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead); // Write file data
            }
            fos.flush();
            printAndLog("File " + fileName + " successfully downloaded.");
        } catch (IOException e) {
            printAndLog("Error during GET file transfer: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Handles the PUT operation for uploading a file to the server.
     * @param fileName The name of the file to upload.
     * @param hostName The host name of the server.
     * @param port The port number for the transfer socket.
     * @throws IOException If an I/O error occurs.
     */
    private static void transferPUT(String fileName, String hostName, int port) throws IOException {
        File file = new File(fileName);
        if (file.exists() && !file.isDirectory()) {
            printAndLog("Attempting to connect to transfer port " + port + " for PUT operation.");
            try (Socket transferSocket = new Socket(hostName, port);
                BufferedOutputStream bos = new BufferedOutputStream(transferSocket.getOutputStream());
                FileInputStream fis = new FileInputStream(file)) {

                printAndLog("Connected to transfer port " + port + " for PUT operation.");
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);  // Send file data
                }
                bos.flush(); // Ensure everything is flushed
                printAndLog("File " + fileName + " successfully uploaded.");
            } catch (IOException e) {
                printAndLog("Error during PUT file transfer: " + e.getMessage());
                throw e;
            }
        } else {
            printAndLog("File not found: " + fileName);
        }
    }



    /**
     * Logs the details of a file transfer (GET/PUT), including file size and throughput.
    * 
    * @param operation The type of operation (GET/PUT).
    * @param fileName  The name of the file being transferred.
    * @param startTime The start time of the transfer.
    * @param endTime   The end time of the transfer.
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
    * 
    * @param message The message to log.
    */
    private static void printAndLog(String message) {
        System.out.println(message);
        LOGGER.info(message);
    }
}   