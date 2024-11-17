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
private static final int MTU = 1500;  // Maximum Transmission Unit (MTU) for Ethernet
private static final int IP_OVERHEAD = 20; // 20 bytes for IP header
private static final int TCP_OVERHEAD = 20; // 20 bytes for TCP header
private static final int TCP_IP_OVERHEAD = IP_OVERHEAD + TCP_OVERHEAD; // Total TCP/IP overhead
private static final int TCP_BUFFER_SIZE = MTU - TCP_IP_OVERHEAD; // Final payload size
private static final int UDP_OVERHEAD = 8; // 8 bytes for UDP header
private static final int APPLICATION_OVERHEAD = Long.BYTES + Integer.BYTES; // 8 bytes for sequence + 4 bytes for CRC
private static final int UDP_IP_OVERHEAD = IP_OVERHEAD + UDP_OVERHEAD; // Total UDP/IP overhead
private static final int UDP_IP_APPLICATION_OVERHEAD = UDP_IP_OVERHEAD + APPLICATION_OVERHEAD; // 8 bytes for sequence + 4 bytes for CRC
private static final int UDP_BUFFER_SIZE = MTU - UDP_IP_APPLICATION_OVERHEAD; // Maximum UDP payload size
private static final int MAX_RETRIES = 5;  // Maximum number of retries for UDP
private static final int TIMEOUT = 2000;  // Timeout in milliseconds
private static final int PORT = 21;  // Default port number
private static String serverIP;  // Server IP address
private static int serverPort;  // Server port number

public static void main(String[] args) throws IOException {
    LogToFile.logToFile(LOGGER, "FTPClient.log"); // Log to file
    printAndLog("Logging to FTPClient.log", true);

    System.out.println("Starting FTP Client...");

    String javaVersion = System.getProperty("java.version");
    printAndLog("Java version: " + javaVersion, true);

    if (args.length == 2) {
        printAndLog("Connecting to " + args[0] + " on port " + args[1], true);
    } else if (args.length == 1) {
        printAndLog("Attempting to connect to " + args[0] + " on default port (" + PORT + ")", true);
    } else {
        printAndLog("Usage: java FTPClient <hostname> <port number>", true);
        System.exit(1);
    }

    serverIP = args[0];
    serverPort = args.length == 2 ? Integer.parseInt(args[1]) : PORT;

    try (
        Socket ftpSocket = new Socket(serverIP, serverPort);
        PrintWriter out = new PrintWriter(ftpSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(ftpSocket.getInputStream()));
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
    ) {
        printAndLog("Connection successful to " + serverIP + ":" + serverPort, true);

        menu(out, in, stdIn);

    } catch (UnknownHostException e) {
        // If the host is not found, log the error and exit
        printAndLog("Unknown host: " + serverIP, false);
        System.exit(1);
    } catch (IOException e) {
        // If an I/O error occurs, log the error and exit
        printAndLog("Couldn't get I/O for the connection to " + serverIP + ":" + serverPort, true);
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
    long totalBytesTransferred = 0;  // Accumulate bytes transferred
    int numRuns = testingMode ? NUM_TESTS : 1;
    long fileSize = 0;
    long bytesPerFile = 0;

    for (int i = 0; i < numRuns; i++) {
        if (i > 0) {
            // If we're in testing mode, display a separator between runs
            System.out.println("\n--------------------------------------------------");
        }
        if (testingMode) {
            System.out.println("Starting run " + (i + 1) + " of " + numRuns + " for " + fileName + " transfer.");
        }

        long startTime = System.currentTimeMillis();  // Start time for each run
        out.println("GET " + fileName);  // Send GET command to the server
        out.flush();
        String serverResponse = in.readLine();

        if (serverResponse != null && serverResponse.startsWith("READY")) {
            String[] readyResponse = serverResponse.split(" ");
            int port = Integer.parseInt(readyResponse[1]); // Server's transfer port
            fileSize = Long.parseLong(readyResponse[2]);  // File size from server

            if (!udpMode) {
                // TCP mode
                try (Socket transferSocket = new Socket(serverIP, port);
                    BufferedInputStream bis = new BufferedInputStream(transferSocket.getInputStream());
                    FileOutputStream fos = new FileOutputStream(fileName)) {
                    byte[] buffer = new byte[TCP_BUFFER_SIZE];
                    int bytesRead;
                    long currentBytes = 0;
                    bytesPerFile = fileSize + TCP_IP_OVERHEAD * (int)Math.ceil((double) fileSize/TCP_BUFFER_SIZE);  // Total bytes to transfer

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        currentBytes += bytesRead;
                        totalBytesTransferred += (bytesRead + 40); // bytesRead + TCP Header + IP Header

                        // Display progress for the current run
                        transferDisplay((int) bytesPerFile, totalBytesTransferred - i * bytesPerFile, 0, 0);
                    }

                    fos.flush();
                    
                }
            } else {
                // UDP mode
                try (DatagramSocket datagramSocket = new DatagramSocket();
                     FileOutputStream fos = new FileOutputStream(fileName)) {
                    datagramSocket.setSoTimeout(TIMEOUT); // Set timeout for receiving packets
                    InetAddress serverAddress = InetAddress.getByName(serverIP);

                    out.println("CLIENT_READY " + datagramSocket.getLocalPort());
                    out.flush();

                    byte[] buffer = new byte[Long.BYTES + UDP_BUFFER_SIZE + Integer.BYTES];
                    //long expectedSequence = 0;
                    long currentBytes = 0;
                    bytesPerFile = fileSize + UDP_IP_APPLICATION_OVERHEAD * (int)Math.ceil((double) fileSize/UDP_BUFFER_SIZE);  // Total bytes to transfer

                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        try {
                            datagramSocket.receive(packet);
                        } catch (SocketTimeoutException e) {
                            printAndLog(" Timeout waiting for packets. No more packets received.", false);
                            break;
                        }

                        ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                        byteBuffer.order(ByteOrder.BIG_ENDIAN);
                        long receivedSeq = byteBuffer.getLong();

                        if (receivedSeq == -1L) { // End-of-file signal
                            break;
                        }

                        int bytesRead = packet.getLength() - Long.BYTES - Integer.BYTES;
                        if (bytesRead <= 0) {
                            printAndLog("Invalid packet size. Aborting transfer.", true);
                            break;
                        }

                        byte[] data = new byte[bytesRead];
                        byteBuffer.get(data);
                        int receivedChecksum = byteBuffer.getInt();

                        CRC32 crc = new CRC32();
                        crc.update(data, 0, bytesRead);
                        long calculatedChecksum = crc.getValue() & 0xFFFFFFFFL;

                        if (calculatedChecksum != (receivedChecksum & 0xFFFFFFFFL)) {
                            printAndLog("CRC32 mismatch detected. Ignoring packet.", false);
                            continue;
                        }

                        fos.write(data, 0, bytesRead);
                        currentBytes += bytesRead;
                        totalBytesTransferred += (bytesRead + UDP_IP_APPLICATION_OVERHEAD); // Total UDP packet size

                        // Display progress for the current run
                        transferDisplay((int) bytesPerFile, totalBytesTransferred - i * bytesPerFile, (int) calculatedChecksum, receivedSeq);

                        ByteBuffer ackBuffer = ByteBuffer.allocate(Long.BYTES);
                        ackBuffer.order(ByteOrder.BIG_ENDIAN);
                        ackBuffer.putLong(receivedSeq);
                        DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), ackBuffer.capacity(), serverAddress, port);
                        datagramSocket.send(ackPacket);
                    }
                }
            }
        } else {
            printAndLog("Server error: " + serverResponse, true);
            return;
        }

        long endTime = System.currentTimeMillis();
        totalDuration += (endTime - startTime);  // Accumulate total time for all runs
    }

    // Log details
    logTransferDetails(numRuns, fileSize, totalDuration, totalBytesTransferred, fileName, "GET");
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
    long totalBytesTransferred = 0;  // Accumulate bytes transferred
    int numRuns = testingMode ? NUM_TESTS : 1;
    long fileSize = 0;
    long bytesPerFile = 0;

    for (int i = 0; i < numRuns; i++) {
        if (i > 0) {
            // If we're in testing mode, display a separator between runs
            System.out.println("\n--------------------------------------------------");
        }
        if (testingMode) {
            System.out.println("Starting run " + (i + 1) + " of " + numRuns + " for " + fileName + " transfer.");
        }
        long startTime = System.currentTimeMillis();  // Start time for each run
        File file = new File(fileName);
        fileSize = file.length();  // Get the actual file size

        out.println("PUT " + fileName + " " + fileSize);  // Send PUT command with file size
        out.flush();

        String serverResponse = in.readLine();
        if (serverResponse != null && serverResponse.startsWith("READY")) {
            String[] readyResponse = serverResponse.split(" ");
            int port = Integer.parseInt(readyResponse[1]); // Server's transfer port

            if (!udpMode) {
                // TCP mode
                try (Socket transferSocket = new Socket(serverIP, port);
                     BufferedOutputStream bos = new BufferedOutputStream(transferSocket.getOutputStream());
                     FileInputStream fis = new FileInputStream(fileName)) {
                    byte[] buffer = new byte[TCP_BUFFER_SIZE];
                    int bytesRead;
                    long currentBytes = 0;
                    bytesPerFile = fileSize + TCP_IP_OVERHEAD * (int)Math.ceil((double) fileSize/TCP_BUFFER_SIZE);  // Total bytes to transfer

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        currentBytes += bytesRead;

                        totalBytesTransferred += (bytesRead + 40); // bytesRead + TCP Header + IP Header

                        // Display progress for the current run
                        transferDisplay((int) bytesPerFile, totalBytesTransferred - i * bytesPerFile, 0, 0);
                    }

                    bos.flush();
                    
                }
            } else {
                // UDP mode
                try (DatagramSocket datagramSocket = new DatagramSocket();
                     FileInputStream fis = new FileInputStream(fileName)) {
                    datagramSocket.setSoTimeout(TIMEOUT);
                    InetAddress serverAddress = InetAddress.getByName(serverIP);

                    out.println("CLIENT_READY " + datagramSocket.getLocalPort());
                    out.flush();

                    byte[] buffer = new byte[UDP_BUFFER_SIZE];
                    long sequenceNumber = 0;
                    long currentBytes = 0;
                    bytesPerFile = fileSize + UDP_IP_APPLICATION_OVERHEAD * (int)Math.ceil((double) fileSize/UDP_BUFFER_SIZE);  // Total bytes to transfer

                    while (true) {
                        int bytesRead = fis.read(buffer);
                        if (bytesRead == -1) break;

                        CRC32 crc = new CRC32();
                        crc.update(buffer, 0, bytesRead);
                        int checksum = (int) crc.getValue();

                        ByteBuffer packetBuffer = ByteBuffer.allocate(Long.BYTES + bytesRead + Integer.BYTES);
                        packetBuffer.order(ByteOrder.BIG_ENDIAN);
                        packetBuffer.putLong(sequenceNumber);
                        packetBuffer.put(buffer, 0, bytesRead);
                        packetBuffer.putInt(checksum);

                        DatagramPacket packet = new DatagramPacket(packetBuffer.array(), packetBuffer.position(), serverAddress, port);
                        datagramSocket.send(packet);

                        boolean ackReceived = false;
                        int retries = 0;

                        while (!ackReceived && retries < MAX_RETRIES) {
                            try {
                                byte[] ackBuffer = new byte[Long.BYTES];
                                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                                datagramSocket.receive(ackPacket);

                                ByteBuffer ackByteBuffer = ByteBuffer.wrap(ackPacket.getData());
                                ackByteBuffer.order(ByteOrder.BIG_ENDIAN);
                                long ackSequence = ackByteBuffer.getLong();

                                if (ackSequence == sequenceNumber) {
                                    ackReceived = true;
                                }
                            } catch (SocketTimeoutException e) {
                                retries++;
                                if (retries == MAX_RETRIES) {
                                    printAndLog("Max retries reached for sequence " + sequenceNumber + ". Aborting.", true);
                                    return;
                                }
                            }
                        }

                        sequenceNumber++;
                        currentBytes += bytesRead;
                        totalBytesTransferred += (bytesRead + UDP_IP_APPLICATION_OVERHEAD); // Total UDP packet size

                        // Display progress for the current run
                        transferDisplay((int) bytesPerFile, totalBytesTransferred - i * bytesPerFile, (int) checksum, sequenceNumber);
                    }

                    // End-of-file signal
                    ByteBuffer endBuffer = ByteBuffer.allocate(Long.BYTES);
                    endBuffer.order(ByteOrder.BIG_ENDIAN);
                    endBuffer.putLong(-1L);
                    DatagramPacket endPacket = new DatagramPacket(endBuffer.array(), endBuffer.capacity(), serverAddress, port);
                    datagramSocket.send(endPacket);
                }
            }
        } else {
            printAndLog("Server error: " + serverResponse, true);
            return;
        }

        long endTime = System.currentTimeMillis();
        totalDuration += (endTime - startTime);  // Accumulate total time for all runs
    }

    // Log details
    logTransferDetails(numRuns, fileSize, totalDuration, totalBytesTransferred, fileName, "PUT");
}

/* ACK
private static void sendACK(DatagramSocket socket, InetAddress address, int port, long sequenceNumber) throws IOException {
    ByteBuffer ackBuffer = ByteBuffer.allocate(Long.BYTES);
    ackBuffer.order(ByteOrder.BIG_ENDIAN);
    ackBuffer.putLong(sequenceNumber);
    DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), ackBuffer.capacity(), address, port);
    socket.send(ackPacket);
}
*/

/**
 * Logs and prints the details of a file transfer, handling both single run and test mode.
 * @param numRuns The number of runs (1 for a single run, NUM_TESTS for test mode).
 * @param totalDuration The total duration of all runs in milliseconds.
 * @param totalBytesTransferred The total number of bytes transferred.
 * @param fileName The name of the file being transferred.
 * @param operation The operation type ("GET" or "PUT").
 */
private static void logTransferDetails(int numRuns, long filesize, long totalDuration, long totalBytesTransferred, String fileName, String operation) {
    printAndLog(" Transfer complete.", true);
    printAndLog("Operation: " + operation, true);
    if (numRuns > 1) {
        // Test mode: display average statistics
        long averageDuration = totalDuration / numRuns;
        double averageThroughput = totalBytesTransferred / (averageDuration / 1000.0);  // Throughput in b/s
        System.out.println("");  // New line for clarity
        printAndLog("Average transfer time for " + numRuns + " runs: " + averageDuration + " ms", true);
        printAndLog("File size: " + filesize + " bytes", true);
        printAndLog("Total bytes transferred: " + totalBytesTransferred + " bytes", true);
        printAndLog("Average throughput: " + (long) averageThroughput + " b/s", true);
    } else {
        // Single run: display detailed stats
        long duration = totalDuration;  // Total duration is for the single run
        double throughput = totalBytesTransferred / (duration / 1000.0);  // Throughput in b/s
        System.out.println("");  // New line for clarity
        printAndLog(operation + " of " + fileName + " completed in " + duration + " ms", true);
        printAndLog("File size: " + filesize + " bytes", true);
        printAndLog("Total bytes transferred: " + totalBytesTransferred + " bytes", true);
        printAndLog("Throughput: " + (long) throughput + " b/s", true);
    }
}

/**
 * Displays a progress bar for the file transfer.
 * @param totalBytes The total number of bytes to transfer.
 * @param currentBytes The number of bytes transferred so far.
 * @param crc The CRC32 checksum of the transferred data.
 */
private static void transferDisplay(int totalBytes, long currentBytes, int crc, long segment) {
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

    if (segment != 0) {
        System.out.print(" Segment: " + segment);
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
 