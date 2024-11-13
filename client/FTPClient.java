/* Author:  Jason Gardner (n01480000),
 * Date: 23 October 2024
 * Project: Project 2
 * File: FTPClient.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: FTP server program
 *              Commands: GET, PUT, CD, LS, QUIT
 *              Transfer modes: TCP, UDP
 *              Testing mode: GET/PUT performed NUM_TESTS times and average time/throughput is calculated
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * FTP client program that connects to an FTP server and allows the user to interact with the server using the following commands:
 * 1) GET <file> - Download a file from the server
 * 2) PUT <file> - Upload a file to the server
 * 3) CD <directory> - Change the current directory on the server
 * 4) LS - List the contents of the current directory on the server
 * 5) Switch transfer mode (TCP/UDP)
 * 6) Enable testing mode (GET/PUT performed NUM_TESTS times and average time/throughput is calculated)
 * 7) QUIT - Disconnect from the server and exit the client
 */
public class FTPClient {
static final Logger LOGGER = Logger.getLogger("FTPClient"); // Logger for logging to file
static final int NUM_TESTS = 10;  // Number of tests for testing mode
static boolean testingMode = false;  // Default to testing mode off
static boolean udpMode = false;  // Default to TCP mode
private static final int TCP_BUFFER_SIZE = 4096;  // TCP buffer size
private static final int UDP_BUFFER_SIZE = 1024;  // UDP buffer size
private static final int MAX_RETRIES = 5;  // Maximum number of retries for UDP
private static final int TIMEOUT = 2000;  // Timeout in milliseconds
private static final int PORT = 21;  // Default port number

public static void main(String[] args) throws IOException {
    System.out.println("Starting FTP Client...");
    LogToFile.logToFile(LOGGER, "FTPClient.log"); // Log to file
    printAndLog("Logging to FTPClient.log", true);

    if (args.length == 2) {
        printAndLog("Connecting to " + args[0] + " on port " + args[1], true);
    } else if (args.length == 1) {
        printAndLog("Attempting to connect to " + args[0] + " on default port (" + PORT + ")", true);
    } else {
        printAndLog("Usage: java FTPClient <hostname> <port number>", true);
        System.exit(1);
    }

    String hostName = args[0];
    final int portNumber = args.length == 2 ? Integer.parseInt(args[1]) : PORT;

    try (
        Socket ftpSocket = new Socket(hostName, portNumber);
        PrintWriter out = new PrintWriter(ftpSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(ftpSocket.getInputStream()));
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
    ) {
        printAndLog("Connection successful to " + hostName + ":" + portNumber, true);

        menu(out, in, stdIn);

    } catch (UnknownHostException e) {
        // If the host is not found, log the error and exit
        printAndLog("Unknown host: " + hostName, false);
        System.exit(1);
    } catch (IOException e) {
        // If an I/O error occurs, log the error and exit
        printAndLog("Couldn't get I/O for the connection to " + hostName + ":" + portNumber, true);
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
                String cdResponse = in.readLine();
                printAndLog(cdResponse, true);
                if (!cdResponse.startsWith("Error")) {
                    // Run LS after CD to list directory contents if directory change is successful
                    out.println("LS");
                    String responseLine;
                    while (!(responseLine = in.readLine()).equals("EOF")) {
                        printAndLog(responseLine, true);
                    }
                }
                break;
            case "4":
                out.println("LS");
                String responseLine;
                while (!(responseLine = in.readLine()).equals("EOF")) {
                    printAndLog(responseLine, true);
                }
                break;
            case "5":
                udpMode = !udpMode;
                out.println("MODE");
                printAndLog("Transfer mode switched to " + (udpMode ? "UDP" : "TCP"), true);
                break;
            case "6":
                testingMode = !testingMode;
                printAndLog("Testing mode " + (testingMode ? "enabled" : "disabled"), true);
                break;
            case "7":
                out.println("QUIT");
                printAndLog(in.readLine(), false);
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
        if (numRuns > 0 && testingMode) {
            if (i > 0) {
                System.out.println("");
            }
            System.out.println("Starting run " + (i + 1) + " of " + numRuns + " for " + fileName + " transfer.");
        }
        long startTime = System.currentTimeMillis();  // Start time for each run
        out.println("GET " + fileName);  // Send GET command to the server
        out.flush();
        String serverResponse = in.readLine();
        if (serverResponse != null && serverResponse.startsWith("READY")) {
            String[] readyResponse = serverResponse.split(" ");
            int port = Integer.parseInt(readyResponse[1]); // Server's transfer port
            long totalBytes = Long.parseLong(readyResponse[2]);  // File size received

            if (!udpMode) {
                // TCP mode
                try (Socket transferSocket = new Socket("localhost", port);
                    BufferedInputStream bis = new BufferedInputStream(transferSocket.getInputStream());
                    FileOutputStream fos = new FileOutputStream(fileName)) {
                    byte[] buffer = new byte[TCP_BUFFER_SIZE];
                    int bytesRead;  // Declare bytesRead here
                    int currentBytes = 0;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        currentBytes += bytesRead;
                        totalBytesTransferred = currentBytes;

                        // Display transfer progress
                        transferDisplay((int) totalBytes, currentBytes, 0);  // No CRC for TCP
                    }
                    fos.flush();
                }
            } else {
                // UDP mode with Sequence Numbers and ACKs
                DatagramSocket datagramSocket = new DatagramSocket();
                datagramSocket.setSoTimeout(TIMEOUT); // Set timeout for receiving ACKs
                InetAddress serverAddress = InetAddress.getByName("localhost"); // Server's address
                CRC32 crc = new CRC32();

                FileOutputStream fos = new FileOutputStream(fileName);
                byte[] buffer = new byte[Long.BYTES + UDP_BUFFER_SIZE + Integer.BYTES]; // sequence + data + CRC
                long expectedSequence = 0;
                long currentBytes = 0;
                long totalBytesTransferredRun = 0;  // Initialize totalBytesTransferred for this run
                int bytesRead;  // Declare bytesRead here

                // **Send CLIENT_READY message with client's UDP port**
                // Format: CLIENT_READY <port>
                out.println("CLIENT_READY " + datagramSocket.getLocalPort());
                out.flush();

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        datagramSocket.receive(packet); // Receive packet from server
                    } catch (SocketTimeoutException e) {
                        printAndLog("Timeout waiting for packets. No more packets received.", false);
                        break;
                    }

                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                    byteBuffer.order(ByteOrder.BIG_ENDIAN);
                    long receivedSeq = byteBuffer.getLong();

                    if (receivedSeq == -1L) {
                        // End-of-file signal received
                        System.out.print("\tReceived end-of-file signal.");
                        break;
                    }

                    int dataLength = packet.getLength() - Long.BYTES - Integer.BYTES;
                    if (dataLength <= 0) {
                        printAndLog("\tInvalid packet size detected. Aborting.", true);
                        break;
                    }

                    byte[] data = new byte[dataLength];
                    byteBuffer.get(data); // Extract data
                    int receivedChecksum = byteBuffer.getInt(); // Extract CRC32 checksum

                    // Validate CRC32
                    crc.update(data, 0, dataLength);
                    long calculatedChecksum = crc.getValue() & 0xFFFFFFFFL; // Mask to 32 bits
                    if (calculatedChecksum != (receivedChecksum & 0xFFFFFFFFL)) {
                        printAndLog("CRC32 mismatch detected. Transfer failed.", true);
                        fos.close();
                        datagramSocket.close();
                        return;
                    }

                    fos.write(data, 0, dataLength); // Write to file
                    currentBytes += dataLength;
                    totalBytesTransferred = currentBytes;

                    // Send ACK back to server
                    // Format: [sequence number (8 bytes)]
                    ByteBuffer ackBuffer = ByteBuffer.allocate(Long.BYTES);
                    ackBuffer.order(ByteOrder.BIG_ENDIAN);
                    ackBuffer.putLong(receivedSeq);
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), ackBuffer.capacity(), serverAddress, port);
                    datagramSocket.send(ackPacket);
                    System.out.print("\tSequence number: " + receivedSeq);

                    // Display transfer progress
                    transferDisplay((int) totalBytes, (int) currentBytes, (int) calculatedChecksum);

                    crc.reset(); // Reset CRC32 for next packet
                }

                fos.flush(); // Ensure all data is written to disk
                fos.close(); // Close file
                datagramSocket.close(); // Close socket
            }
        } else {
            printAndLog("Server error: " + serverResponse, true);
            return;
        }

        long endTime = System.currentTimeMillis();
        long runDuration = endTime - startTime;  // Time for this run
        totalDuration += runDuration;  // Accumulate total time
    }
    // Use the helper method to log the transfer details (average for test mode, single for non-test mode)
    logTransferDetails(numRuns, totalDuration, totalBytesTransferred, fileName, "GET");
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
        if (numRuns > 0 && testingMode) {
            if (i > 0) {
                System.out.println("");
            }
            System.out.println("Starting run " + (i + 1) + " of " + numRuns + " for " + fileName + " transfer.");
        }
        long startTime = System.currentTimeMillis();  // Start time for each run
        File file = new File(fileName);
        long fileSize = file.length();  // Get the actual file size

        out.println("PUT " + fileName + " " + fileSize);  // Send PUT command with file size
        out.flush();

        String serverResponse = in.readLine();
        if (serverResponse != null && serverResponse.startsWith("READY")) {
            String[] readyResponse = serverResponse.split(" ");
            int port = Integer.parseInt(readyResponse[1]); // Server's transfer port

            if (!udpMode) {
                // TCP mode
                try (Socket transferSocket = new Socket("localhost", port);
                    BufferedOutputStream bos = new BufferedOutputStream(transferSocket.getOutputStream());
                    FileInputStream fis = new FileInputStream(fileName)) {
                    byte[] buffer = new byte[TCP_BUFFER_SIZE];
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
                // UDP mode with Sequence Numbers and ACKs
                DatagramSocket datagramSocket = new DatagramSocket(); // Bind to any available port
                datagramSocket.setSoTimeout(TIMEOUT); // Set timeout for ACKs
                InetAddress serverAddress = InetAddress.getByName("localhost"); // Server's address
                CRC32 crc = new CRC32();

                FileInputStream fis = new FileInputStream(fileName);
                byte[] buffer = new byte[UDP_BUFFER_SIZE];
                long sequenceNumber = 0;
                long currentBytes = 0;  // Initialize currentBytes
                long totalBytesTransferredRun = 0;  // Initialize totalBytesTransferred for this run
                int bytesRead;  // Declare bytesRead here

                // **Send CLIENT_READY message with client's UDP port**
                // Format: CLIENT_READY <port>
                out.println("CLIENT_READY " + datagramSocket.getLocalPort());
                out.flush();

                while ((bytesRead = fis.read(buffer)) != -1) {
                    crc.update(buffer, 0, bytesRead);
                    int checksum = (int) crc.getValue(); // Cast to int

                    // Create packet with sequence number, data, and CRC32
                    // Format: [sequence number (8 bytes)][data (MTU size)][checksum (4 bytes)]
                    ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + bytesRead + Integer.BYTES);
                    byteBuffer.order(ByteOrder.BIG_ENDIAN); // Ensure consistent byte order
                    byteBuffer.putLong(sequenceNumber);     // Sequence number
                    byteBuffer.put(buffer, 0, bytesRead);   // Data
                    byteBuffer.putInt(checksum);            // CRC32 checksum

                    byte[] packetData = byteBuffer.array();
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, port);

                    boolean ackReceived = false;
                    int retries = 0;

                    while (!ackReceived && retries < MAX_RETRIES) {
                        datagramSocket.send(packet);
                        try {
                            // Prepare to receive ACK
                            byte[] ackBuffer = new byte[Long.BYTES];
                            DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                            datagramSocket.receive(ackPacket);

                            ByteBuffer ackByteBuffer = ByteBuffer.wrap(ackPacket.getData());
                            ackByteBuffer.order(ByteOrder.BIG_ENDIAN);
                            long ackSequence = ackByteBuffer.getLong();

                            if (ackSequence == sequenceNumber) {
                                ackReceived = true; // Correct ACK received
                                System.out.print("\tSequence number: " + ackSequence);
                            }
                        } catch (SocketTimeoutException e) {
                            retries++;
                            if (retries == 1) {
                                System.out.print("\n"); // Move to new line for retries
                            }
                            printAndLog("Timeout waiting for ACK for sequence number: " + sequenceNumber + ". Retrying (" + retries + "/" + MAX_RETRIES + ").]", false);
                            // Log resends only
                            if (retries == MAX_RETRIES) {
                                printAndLog("Max retries reached for sequence number: " + sequenceNumber + ". Aborting transfer.", false);
                                fis.close();
                                datagramSocket.close();
                                return;
                            }
                        }
                    }

                    sequenceNumber++;
                    currentBytes += bytesRead;
                    totalBytesTransferredRun += bytesRead;

                    // Display transfer progress
                    transferDisplay((int) fileSize, (int) currentBytes, checksum);

                    crc.reset(); // Reset CRC for next packet
                }

                // Send end-of-transfer signal with sequence number -1
                ByteBuffer endBuffer = ByteBuffer.allocate(Long.BYTES);
                endBuffer.order(ByteOrder.BIG_ENDIAN);
                endBuffer.putLong(-1L); // Special sequence number for EOF
                DatagramPacket endPacket = new DatagramPacket(endBuffer.array(), endBuffer.capacity(), serverAddress, port);
                datagramSocket.send(endPacket);
                System.out.print(" Sent end-of-transfer signal.");

                fis.close();
                datagramSocket.close();

                totalBytesTransferred += totalBytesTransferredRun; // Accumulate total bytes transferred
            }
        } else {
            printAndLog("Server error: " + serverResponse, true);
        }
        long endTime = System.currentTimeMillis();
        long runDuration = endTime - startTime;  // Time for this run
        totalDuration += runDuration;  // Accumulate total time
    }
    // Use the helper method to log the transfer details (average for test mode, single for non-test mode)
    logTransferDetails(numRuns, totalDuration, totalBytesTransferred, fileName, "PUT");
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
        System.out.println("");  // New line for clarity
        printAndLog("Average transfer time for " + numRuns + " runs: " + averageDuration + " ms", true);
        printAndLog("File size: " + totalBytesTransferred + " bytes", true);
        printAndLog("Average throughput: " + (long) averageThroughput + " b/s", true);
    } else {
        // Single run: display detailed stats
        long duration = totalDuration;  // Total duration is for the single run
        double throughput = totalBytesTransferred / (duration / 1000.0);  // Throughput in b/s
        System.out.println("");  // New line for clarity
        printAndLog(operation + " of " + fileName + " completed in " + duration + " ms", true);
        printAndLog("File size: " + totalBytesTransferred + " bytes", true);
        printAndLog("Throughput: " + (long) throughput + " b/s", true);
    }
}

/**
 * Displays a progress bar for the file transfer.
 * @param totalBytes The total number of bytes to transfer.
 * @param currentBytes The number of bytes transferred so far.
 * @param crc The CRC32 checksum of the transferred data.
 */
private static void transferDisplay(int totalBytes, int currentBytes, int crc) {
    // Clear the line if we're about to hit 100% to ensure no residual characters are present
    if (currentBytes >= totalBytes) {
        System.out.print("\r" + " ".repeat(150) + "");
    }
    int transferPercentage = (int) ((currentBytes / (double) totalBytes) * 100);

    // Calculate how much of the bar to fill. Minimum is 0, and maximum is 50.
    int arrowPosition = Math.max(0, Math.min(50, transferPercentage / 2));

    // Build the progress bar display
    String progressBar = "|"
            + "=".repeat(arrowPosition)  // Filled portion
            + (arrowPosition < 50 ? ">" : "") // Arrow
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
 * @param newline Whether to include a newline character at the end of the console output.
 */
private static void printAndLog(String message, boolean newline) {
    if (newline) {
        System.out.println(message);
    } else {
        System.out.print(message);
    }
    LOGGER.info(message);
}
}

/**
 * Utility class to set up logging to a file.
 */
class LogToFile {
public static void logToFile(Logger logger, String logFile) {
    try {
        FileHandler fh = new FileHandler(logFile, true);  // Append mode
        logger.addHandler(fh);

        // Use the custom formatter for the log format
        CustomLogFormatter formatter = new CustomLogFormatter();
        fh.setFormatter(formatter);

        // Disable console output for the logger (remove default handlers)
        logger.setUseParentHandlers(false);
    } catch (SecurityException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    }
}
}

/**
 * Custom log formatter to format log messages with a timestamp and log level.
 */
class CustomLogFormatter extends Formatter {
// Define the date format
private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy@HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        // Get the current date and time
        String timeStamp = dateFormat.format(new Date(record.getMillis()));

        // Get the log level (severity)
        String logLevel = record.getLevel().getName();

        // Format the log message
        return String.format("%s:%s:\t%s%n",
                timeStamp,              // Short date and time
                logLevel,               // Log level (severity)
                record.getMessage()     // Actual log message
        );
    }
}
 