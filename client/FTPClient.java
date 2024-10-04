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
import java.nio.ByteBuffer;
import java.util.logging.*;
import java.util.zip.CRC32;

/**
 * FTP client program that connects to an FTP server and allows the user to interact with the server using the following commands:
* 1) GET <file> - Download a file from the server
* 2) PUT <file> - Upload a file to the server
* 3) CD <directory> - Change the current directory on the server
* 4) LS - List the contents of the current directory on the server
* 5) Switch transfer mode (TCP/UDP)
* 6) Enable testing mode (GET/PUT performed NUM_TESTS times and average time/throughput is calculated)
* 5) QUIT - Disconnect from the server and exit the client
*/
public class FTPClient {
    static final Logger LOGGER = Logger.getLogger("FTPClient");
    static final int NUM_TESTS = 10;
    static boolean testingMode = false;
    static boolean udpMode = false;

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

            menu(out, in, stdIn);

        } catch (UnknownHostException e) {
            // If the host is not found, log the error and exit
            printAndLog("Unknown host: " + hostName);
            System.exit(1);
        } catch (IOException e) {
            // If an I/O error occurs, log the error and exit
            printAndLog("Couldn't get I/O for the connection to " + hostName + ":" + portNumber);
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Menu system for user interaction
    * @param out The PrintWriter for sending commands to the server
    * @param in The BufferedReader for reading responses from the server
    * @param stdIn The BufferedReader for reading user input
    * @throws IOException If an I/O error occurs while reading user input
    */
    private static void menu(PrintWriter out, BufferedReader in, BufferedReader stdIn) throws IOException {
        while (true) {
            String transferModeMenu = "Toggle Transfer Mode ("+ (!udpMode ? "[" : "") + "TCP" + (!udpMode ? "]" : "") + "/" + (udpMode ? "[" : "") + "UDP" + (udpMode ? "]" : "") + ")";
            String testingModeMenu = "Toggle Testing Mode (" + (testingMode ? "[" : "") + "ON" + (testingMode ? "]" : "") + "/" + (!testingMode ? "[" : "") + "OFF" + (!testingMode ? "]" : "") + ")";
            System.out.printf("\nFTP Client Menu:\n1) GET\n2) PUT\n3) CD\n4) LS\n5) %s\n6) %s\n7) QUIT\n", transferModeMenu, testingModeMenu);
            System.out.print("Enter choice: ");
            String choice = stdIn.readLine();
            switch (choice) {
                case "1":
                    System.out.print("Enter file name to download: ");
                    String getFileName = stdIn.readLine();
                    receiveFile(getFileName, out, in);
                    break;
                case "2":
                    System.out.print("Enter file name to upload: ");
                    String putFileName = stdIn.readLine();
                    sendFile(putFileName, out, in);
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
                    udpMode = !udpMode;
                    out.println("MODE");
                    printAndLog("Transfer mode switched to " + (udpMode ? "UDP" : "TCP"));
                    break;
                case "6":
                    testingMode = !testingMode;
                    printAndLog("Testing mode " + (testingMode ? "enabled" : "disabled"));
                    break;
                case "7":
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
    * @param fileName The name of the file to download.
    * @param out The PrintWriter for sending commands to the server.
    * @param in The BufferedReader for reading responses from the server.
    * @throws IOException If an I/O error occurs while receiving the file.
    */
    private static void receiveFile(String fileName, PrintWriter out, BufferedReader in) throws IOException {
        long totalDuration = 0;  // Accumulate transfer times
        long totalBytesTransferred = 0;  // To accumulate bytes transferred
        int numRuns = testingMode ? NUM_TESTS : 1;

        for (int i = 0; i < numRuns; i++) {
            long startTime = System.currentTimeMillis();  // Start time for each run
            out.println("GET " + fileName);  // Send GET command to the server
            String serverResponse = in.readLine();
            if (serverResponse != null && serverResponse.startsWith("READY")) {
                String[] readyResponse = serverResponse.split(" ");
                int port = Integer.parseInt(readyResponse[1]); // Server's transfer port
                int totalBytes = Integer.parseInt(readyResponse[2]);  // File size received
    
                if (!udpMode) {
                    // TCP mode
                    try (Socket transferSocket = new Socket("localhost", port);
                        BufferedInputStream bis = new BufferedInputStream(transferSocket.getInputStream());
                        FileOutputStream fos = new FileOutputStream(fileName)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        int currentBytes = 0;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            currentBytes += bytesRead;
                            totalBytesTransferred = currentBytes;
    
                            // Display transfer progress
                            transferDisplay(totalBytes, currentBytes, 0);  // No CRC for TCP
                        }
                        fos.flush();
                    }
                } else {
                    // UDP mode
                    DatagramSocket datagramSocket = new DatagramSocket();
                    datagramSocket.setSoTimeout(5000); // Timeout to avoid indefinite waiting
    
                    InetAddress serverAddress = InetAddress.getByName("localhost"); // Server's address
                    out.println("CLIENT_READY " + datagramSocket.getLocalPort());  // Send client's port to the server
                    
                    FileOutputStream fos = new FileOutputStream(fileName);
                    CRC32 crc = new CRC32();
                    byte[] buffer = new byte[4096 + Long.BYTES]; // Buffer to hold data and CRC32
                    int currentBytes = 0;
    
                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        try {
                            datagramSocket.receive(packet); // Receive packet from server
                        } catch (SocketTimeoutException e) {
                            printAndLog("Timeout waiting for packets. No more packets received.");
                            break;
                        }
    
                        int dataLength = packet.getLength() - Long.BYTES;
                        
                        // Check if it's the end-of-file signal (empty packet)
                        if (dataLength <= 0) {
                            break;  // End of file signal received, exit loop
                        }
    
                        // Only process data packets
                        if (dataLength > 0) {
                            // Extract CRC32 from the packet
                            ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
                            byte[] data = new byte[dataLength];
                            byteBuffer.get(data);
                            long receivedChecksum = byteBuffer.getLong();
    
                            // Validate CRC32
                            crc.update(data, 0, dataLength);
                            long calculatedChecksum = crc.getValue();
                            if (calculatedChecksum != receivedChecksum) {
                                printAndLog("CRC32 mismatch detected. Transfer failed.");
                                fos.close();
                                datagramSocket.close();
                                return;
                            }
    
                            fos.write(data, 0, dataLength);  // Write to file
                            currentBytes += dataLength;
                            totalBytesTransferred = currentBytes;
    
                            // Display transfer progress
                            transferDisplay(totalBytes, currentBytes, (int) calculatedChecksum);
    
                            crc.reset(); // Reset CRC32 for next packet
                        }
                    }
    
                    fos.flush();  // Ensure all data is written to disk
                    fos.close();  // Close file
                    datagramSocket.close();  // Close socket
                }
            } else {
                printAndLog("Server error: " + serverResponse);
            }

            long endTime = System.currentTimeMillis();
            long runDuration = endTime - startTime;  // Time for this run
            totalDuration += runDuration;  // Accumulate total time
        }
        // Use the helper method to log the transfer details (average for test mode, single for non-test mode)
        logTransferDetails(numRuns, totalDuration, totalBytesTransferred, fileName, "PUT");
    }

    /**
     * Handles the file sending for the PUT command.
    * @param fileName The name of the file to upload.
    * @param out The PrintWriter for sending commands to the server.
    * @param in The BufferedReader for reading responses from the server.
    * @throws IOException If an I/O error occurs while sending the file.
    */
    private static void sendFile(String fileName, PrintWriter out, BufferedReader in) throws IOException {
        long totalDuration = 0;  // Accumulate transfer times
        long totalBytesTransferred = 0;  // To accumulate bytes transferred
        int numRuns = testingMode ? NUM_TESTS : 1;
        for (int i = 0; i < numRuns; i++) {
            long startTime = System.currentTimeMillis();  // Start time for each run
            File file = new File(fileName);
            long fileSize = file.length();  // Get the actual file size
    
            out.println("PUT " + fileName + " " + fileSize);  // Send PUT command with file size
    
            String serverResponse = in.readLine();
            if (serverResponse != null && serverResponse.startsWith("READY")) {
                String[] readyResponse = serverResponse.split(" ");
                int port = Integer.parseInt(readyResponse[1]); // Server's transfer port
                int totalBytes = Integer.parseInt(readyResponse[2]);  // File size received
    
                if (!udpMode) {
                    // TCP mode
                    try (Socket transferSocket = new Socket("localhost", port);
                        BufferedOutputStream bos = new BufferedOutputStream(transferSocket.getOutputStream());
                        FileInputStream fis = new FileInputStream(fileName)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        int currentBytes = 0;
    
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                            currentBytes += bytesRead;
                            totalBytesTransferred = currentBytes;
    
                            // Display transfer progress
                            transferDisplay((int) fileSize, currentBytes, 0);  // No CRC for TCP
                        }
                        bos.flush();
                    }
                } else {
                    // UDP mode
                    DatagramSocket datagramSocket = new DatagramSocket();
                    datagramSocket.setSoTimeout(5000);
                    InetAddress serverAddress = InetAddress.getByName("localhost");
                    CRC32 crc = new CRC32();
    
                    FileInputStream fis = new FileInputStream(fileName);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    int currentBytes = 0;
    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        crc.update(buffer, 0, bytesRead);
                        long checksum = crc.getValue();
    
                        ByteBuffer byteBuffer = ByteBuffer.allocate(bytesRead + Long.BYTES);
                        byteBuffer.put(buffer, 0, bytesRead);
                        byteBuffer.putLong(checksum);
    
                        DatagramPacket packet = new DatagramPacket(byteBuffer.array(), byteBuffer.capacity(), serverAddress, port);
                        datagramSocket.send(packet);
    
                        currentBytes += bytesRead;
                        totalBytesTransferred = currentBytes;
    
                        // Display transfer progress
                        transferDisplay((int) fileSize, currentBytes, (int) checksum);
    
                        crc.reset();
                    }
    
                    // End-of-transfer signal (send empty packet)
                    byte[] endOfFileSignal = new byte[0];
                    DatagramPacket endPacket = new DatagramPacket(endOfFileSignal, endOfFileSignal.length, serverAddress, port);
                    datagramSocket.send(endPacket);  // Send the EOF signal
    
                    fis.close();
                    datagramSocket.close();
                }
            } else {
                printAndLog("Server error: " + serverResponse);
            }
            long endTime = System.currentTimeMillis();
            long runDuration = endTime - startTime;  // Time for this run
            totalDuration += runDuration;  // Accumulate total time
        }
        logTransferDetails(numRuns, totalDuration, totalBytesTransferred, fileName, "GET");
    }
    

    /**
     * Logs and prints the details of a file transfer, handling both single run and test mode.
    * @param numRuns The number of runs (1 for a single run, NUM_TESTS for test mode).
    * @param totalDuration The total duration of all runs in milliseconds.
    * @param totalBytesTransferred The total number of bytes transferred.
    * @param fileName The name of the file being transferred.
    * @param operation The operation type ("GET" or "PUT").
    */
    private static void logTransferDetails(int numRuns, long totalDuration, long totalBytesTransferred, String fileName, String operation) {
        if (numRuns > 1) {
            // Test mode: display average statistics
            long averageDuration = totalDuration / numRuns;
            double averageThroughput = totalBytesTransferred / (averageDuration / 1000.0);  // Throughput in b/s
            printAndLog("\nAverage transfer time for " + numRuns + " runs: " + averageDuration + " ms");
            printAndLog("File size: " + totalBytesTransferred + " bytes");
            printAndLog("Average throughput: " + (long) averageThroughput + " b/s");
        } else {
            // Single run: display detailed stats
            long duration = totalDuration;  // Total duration is for the single run
            double throughput = totalBytesTransferred / (duration / 1000.0);  // Throughput in b/s
            printAndLog("\n" + operation + " of " + fileName + " completed in " + duration + " ms");
            printAndLog("File size: " + totalBytesTransferred + " bytes");
            printAndLog("Throughput: " + (long) throughput + " b/s");
        }
    }

    /**
     * Displays a progress bar for the file transfer.
    * @param totalBytes The total number of bytes to transfer.
    * @param currentBytes The number of bytes transferred so far.
    * @param crc The CRC32 checksum of the transferred data.
    */
    private static void transferDisplay(int totalBytes, int currentBytes, int crc) {
        int transferPercentage = (int) ((currentBytes / (double) totalBytes) * 100);
    
        // Calculate how much of the bar to fill. Minimum is 0, and maximum is 50.
        int arrowPosition = Math.max(0, Math.min(50, transferPercentage / 2));
    
        // Build the progress bar display
        String progressBar = "|"
                + "=".repeat(arrowPosition)  // Filled portion
                + ">".repeat(arrowPosition < 50 ? 1 : 0)  // Arrow
                + " ".repeat(50 - arrowPosition)  // Empty portion
                + "| ";
    
        // Print transfer percentage and byte count
        System.out.print("\r" + progressBar
                + transferPercentage + "% ("
                + currentBytes + "/" + totalBytes + " bytes)");
    
        // Only print CRC if it is non-zero
        if (crc != 0) {
            System.out.print(" CRC32: 0x" + Integer.toHexString(crc));
        }
    
        // Flush the output to ensure it updates in real-time
        System.out.flush();
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