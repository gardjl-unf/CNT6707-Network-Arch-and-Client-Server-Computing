/* Author:  Jason Gardner (n01480000),
 * Date: 23 October 2024
 * Project: Project 2
 * File: FTPServer.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: Mutlithreaded FTP server program that uses threads to handle multiple clients
 *              Commands: GET, PUT, CD, LS, QUIT
 *              Transfer modes: TCP, UDP
 *              Testing mode: GET/PUT performed NUM_TESTS times and average time/throughput is calculated
 */

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * FTPServer handles incoming FTP client connections, executing commands such as LS, CD, GET, and PUT.
* It uses a single persistent connection and ensures clean file transfers with proper stream management.
*/
public class FTPServer {
    private static int listenPort = 21;
    private static boolean running = true; // Server running flag
    private static ServerSocket serverSocket; // Class-level ServerSocket for handling shutdown
    static final Logger LOGGER = Logger.getLogger("FTPServer");
    private static final int MTU = 1500;  // Maximum Transmission Unit (MTU) for Ethernet
    private static final int IP_OVERHEAD = 20; // 20 bytes for IP header
    private static final int TCP_OVERHEAD = 20; // 20 bytes for TCP header
    private static final int TCP_IP_OVERHEAD = IP_OVERHEAD + TCP_OVERHEAD; // Total TCP/IP overhead
    private static final int TCP_BUFFER_SIZE = MTU - TCP_IP_OVERHEAD; // Final payload size
    private static final int UDP_OVERHEAD = 8; // 8 bytes for UDP header
    private static final int APPLICATION_OVERHEAD = Long.BYTES + Integer.BYTES; // 8 bytes for sequence + 4 bytes for CRC
    private static final int UDP_IP_OVERHEAD = IP_OVERHEAD + UDP_OVERHEAD; // Total UDP/IP overhead
    private static final int UDP_IP_APPLICATION_OVERHEAD = UDP_IP_OVERHEAD + APPLICATION_OVERHEAD; // 8 bytes for sequence + 4 bytes for CRC
    private static final int UDP_BUFFER_SIZE = MTU - UDP_IP_APPLICATION_OVERHEAD; // Final payload size
    private static final int TIMEOUT = 2000; // Timeout in milliseconds
    private static final int UDP_RECV_BUFFER = 100000000; // 100MB buffer size for UDP
    private static final int UDP_DELAY = 0; // Delay in milliseconds for UDP mode

    private static class PacketHandler extends Thread {
        private final DatagramSocket socket;
        private final FileOutputStream fos;
        private final long expectedFileSize;
        private final int timeout;
        private final long startTime;
        private long totalBytesTransferred = 0;
        private volatile boolean transferActive = true;
        long bytesPerFile = 0;

        private PacketHandler(DatagramSocket socket, FileOutputStream fos, long expectedFileSize, int timeout) {
            this.socket = socket;
            this.fos = fos;
            this.expectedFileSize = expectedFileSize;
            this.timeout = timeout;
            this.startTime = System.currentTimeMillis();
            this.bytesPerFile = expectedFileSize + UDP_IP_APPLICATION_OVERHEAD * (int)Math.ceil((double) expectedFileSize/UDP_BUFFER_SIZE);
        }

        @Override
        public void run() {
            try {
                long expectedSequence = 0;
                Map<Long, byte[]> packetBuffer = new TreeMap<>();

                byte[] buffer = new byte[Long.BYTES + UDP_BUFFER_SIZE + Integer.BYTES];

                while (transferActive && totalBytesTransferred < bytesPerFile) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketTimeoutException e) {
                        if (totalBytesTransferred >= bytesPerFile) {
                            break;
                        }
                        printAndLog("Timeout waiting for next packet from client. Aborting transfer.");
                        transferActive = false;
                        return;
                    }

                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                    byteBuffer.order(ByteOrder.BIG_ENDIAN);
                    long sequenceNumber = byteBuffer.getLong();

                    if (sequenceNumber == -1L) {
                        transferActive = false;
                        break;
                    }

                    int dataLength = packet.getLength() - Long.BYTES - Integer.BYTES;
                    if (dataLength <= 0) {
                        printAndLog("Invalid packet received from client. Skipping.");
                        continue;
                    }

                    byte[] data = new byte[dataLength];
                    byteBuffer.get(data);
                    int receivedChecksum = byteBuffer.getInt();

                    CRC32 crc = new CRC32();
                    crc.update(data, 0, dataLength);
                    long calculatedChecksum = crc.getValue() & 0xFFFFFFFFL;
                    if (calculatedChecksum != (receivedChecksum & 0xFFFFFFFFL)) {
                        printAndLog("CRC mismatch for sequence " + sequenceNumber + " from client. Ignoring packet.");
                        continue;
                    }

                    // Add packet to buffer for reassembly
                    packetBuffer.put(sequenceNumber, data);

                    while (packetBuffer.containsKey(expectedSequence)) {
                        byte[] nextData = packetBuffer.remove(expectedSequence);
                        fos.write(nextData);
                        totalBytesTransferred += (nextData.length + UDP_IP_APPLICATION_OVERHEAD); // data size + TCP Header + IP Header
                        expectedSequence++;
                    }
                }

                fos.flush();
            } catch (IOException e) {
                printAndLog("Error in packet handler: " + e.getMessage());
            } finally {
                try {
                    fos.close();
                } catch (IOException e) {
                    printAndLog("Error closing file: " + e.getMessage());
                }
                socket.close();
                long duration = System.currentTimeMillis() - startTime;
                printAndLog("File upload completed in " + duration + " ms. Total bytes transferred: " + totalBytesTransferred);
            }
        }

        public long getTotalBytesTransferred() {
            return totalBytesTransferred;
        }
    }

    public static void main(String[] args) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPServer.log"); // Log to file
        printAndLog("Logging to FTPServer.log");
        printAndLog("Starting FTP server...");
        String javaVersion = System.getProperty("java.version");
        printAndLog("Java version: " + javaVersion);
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        printAndLog("System Memory: " + freeMemory / 1000.0 + "Mb/" + totalMemory / 1000.0 + "Mb (" + (usedMemory / 1000.0) + " used)");

        if (args.length > 0) {
            listenPort = Integer.parseInt(args[0]);
        } else {
            printAndLog("Attempting to listen on default port (" + listenPort + ")");
        }

        // Start the server shutdown listener (listens for "q" to quit)
        new Thread(FTPServer::shutdownListener).start();

        try {
            serverSocket = new ServerSocket(listenPort, 50, InetAddress.getByName("0.0.0.0")); // Bind to all interfaces
            printAndLog("Server listening on " + serverSocket.getInetAddress() + ":" + serverSocket.getLocalPort());
            try (final DatagramSocket datagramSocket = new DatagramSocket()) {
                datagramSocket.connect(InetAddress.getByName("8.8.8.8"), 12345);
                printAndLog("Server WAN IP: " + datagramSocket.getLocalAddress().getHostAddress());
                // Display server interface IP address
                printAndLog("Server LAN IP: " + InetAddress.getLocalHost());
            } catch (Exception e) {
                printAndLog("Server WAN IP: Unable to Reach Google DNS: " + e.getMessage());
            }
            
            printAndLog("Root directory: " + System.getProperty("user.dir"));
            printAndLog("Maximum Transmission Unit (MTU): " + MTU + " bytes");
            printAndLog("TCP buffer size: " + TCP_BUFFER_SIZE + " bytes");
            printAndLog("UDP buffer size: " + UDP_BUFFER_SIZE + " bytes");
            printAndLog("Server ready to accept client connections.");
            printAndLog("Waiting for client connections...");


            // Main loop to accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    printAndLog("Accepted connection from: " + clientSocket.getInetAddress());

                    // Handle client connection in a new thread
                    Thread clientThread = new Thread(new ClientHandler(clientSocket));
                    clientThread.start();
                    printAndLog("Started thread for client: " + clientSocket.getInetAddress());

                } catch (IOException e) {
                    if (running) { // Only log if still running
                        printAndLog("Error accepting connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            if (running) {
                printAndLog("Could not listen on port " + listenPort + ": " + e.getMessage());
            }
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        }
    }

    /**
     * Listens for "q" input to shut down the server.
    */
    private static void shutdownListener() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            if (scanner.nextLine().equalsIgnoreCase("q")) {
                running = false;
                printAndLog("Shutting down the server...");
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();  // Close the server socket to unblock accept()
                    }
                } catch (IOException e) {
                    printAndLog("Error closing the server socket: " + e.getMessage());
                }
                break;
            }
        }
        scanner.close();
    }

    /**
     * ClientHandler class to handle individual client connections in separate threads.
    * Each client connection is handled by a separate instance of this class.
    * The class implements the Runnable interface to run in a separate thread.
    * The class handles the LS, CD, GET, PUT, and QUIT commands.
    * The class maintains the current directory for each client.
    * The class uses a static ROOT_DIR for the server root directory.
    */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String clientAddress; // Store client address for logging
        private static final String ROOT_DIR = System.getProperty("user.dir");
        private String currentDir;
        private boolean udpMode = false; // UDP mode flag
    
        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            this.clientAddress = clientSocket.getInetAddress().toString(); // Capture client address
            this.currentDir = ROOT_DIR; // Start in the root directory
            printAndLog("ClientHandler initialized for: " + clientAddress);
        }
    
        /**
         * Run method to handle client connections and execute commands.
        */
        @Override
        public void run() {
            printAndLog("Handling client connection from: " + clientAddress);
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    printAndLog("Received command from " + clientAddress + ": " + inputLine); // Log with client info
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
                            handlePUT(command, out, in);  // Pass 'in' to handlePUT
                            break;
                        case "MODE":
                            udpMode = !udpMode; // Toggle UDP mode
                            break;
                        case "QUIT":
                            handleQUIT(out);
                            return;  // Close this client handler after QUIT
                        default:
                            out.println("Unknown command");
                            break;
                    }
                }
            // Handle exceptions and close the client connection
            } catch (IOException e) {
                printAndLog("Exception in client handling for " + clientAddress + ": " + e.getMessage());
            }
        }
    
        /**
         * Handles the LS command to list files in the current directory in the desired format.
        * @param out The output writer to communicate with the client.
        */
        /**
         * Handles the LS command to list files in the current directory in the desired format.
        * @param out The output writer to communicate with the client.
        */
        private void handleLS(PrintWriter out) {
            File dir = new File(currentDir);
            File[] files = dir.listFiles();
        
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                String OUTPUT_FORMAT = "  %-50s %-30s";
        
                out.println("Directory: " + currentDir);
                out.println(OUTPUT_FORMAT.formatted("Name", "Size"));
                out.println(OUTPUT_FORMAT.formatted(".", "<DIR>"));
                out.println(OUTPUT_FORMAT.formatted("..", "<DIR>"));
        
                for (File file : files) {
                    if (file.isDirectory()) {
                        out.println(OUTPUT_FORMAT.formatted("/" + file.getName() + "/", "<DIR>"));
                    }
                }
        
                for (File file : files) {
                    if (!file.isDirectory()) {
                        out.println(OUTPUT_FORMAT.formatted(file.getName(), file.length() + " bytes"));  
                    }
                }
            }
            out.println("EOF"); // Mark the end of listing
            out.flush();
            printAndLog("LS command executed by " + clientAddress);
        }
    
        /**
         * Handles the CD command to change the current directory.
        * @param command The command array containing the directory to change to.
        * @param out The output writer to communicate with the client.
        */
        private void handleCD(String[] command, PrintWriter out) {
            if (command.length > 1) {
                File newDir = new File(currentDir + File.separator + command[1]);
                try {
                    if (newDir.isDirectory() && newDir.getCanonicalPath().startsWith(ROOT_DIR)) {
                        currentDir = newDir.getCanonicalPath(); // Update current directory
                        out.println("Changed directory to: " + currentDir);
                        printAndLog("Changed directory to: " + currentDir + " for client: " + clientAddress);
                    } else {
                        out.println("Directory not found or permission denied.");
                    }
                } catch (IOException e) {
                    printAndLog("Error changing directory for " + clientAddress + ": " + e.getMessage());
                }
            } else {
                out.println("ERROR: No directory specified.");
            }
            out.flush();
        }

        /**
         * Handles the QUIT command, closing the client connection.
        * @param out The output writer to communicate with the client.
        * @throws IOException If an I/O error occurs.
        */
        private void handleQUIT(PrintWriter out) throws IOException {
            out.println("Goodbye!"); // Inform the client the server is closing the connection
            printAndLog("Client issued QUIT. Closing connection for: " + clientAddress);
    
            // Close the client socket
            clientSocket.close();
            printAndLog("Client connection closed for: " + clientAddress);
        }
    
        /**
         * Handles the GET command for file download.
        * @param command The command array containing the file to download.
        * @param out The output writer to communicate with the client.
        * @throws IOException If an I/O error occurs while sending the file.
        */
        private void handleGET(String[] command, PrintWriter out) throws IOException {
            if (command.length > 1) {
                File file = new File(currentDir + File.separator + command[1]);
                if (file.exists() && !file.isDirectory()) {
                    long fileSize = file.length();  // Get file size
                    if (!udpMode) {
                        try (ServerSocket transferSocket = new ServerSocket(0)) {
                            out.println("READY " + transferSocket.getLocalPort() + " " + fileSize);  // Send file size
                            try (Socket fileTransferSocket = transferSocket.accept();
                                FileInputStream fis = new FileInputStream(file);
                                BufferedOutputStream bos = new BufferedOutputStream(fileTransferSocket.getOutputStream())) {
                                byte[] buffer = new byte[TCP_BUFFER_SIZE];
                                int bytesRead;
                                while ((bytesRead = fis.read(buffer)) != -1) {
                                    bos.write(buffer, 0, bytesRead);
                                }
                                bos.flush();
                            }
                        }
                    } else {
                        // UDP mode
                        DatagramSocket datagramSocket = new DatagramSocket(); // Create DatagramSocket for sending data
                        datagramSocket.setSoTimeout(5000); // Timeout for receiving packets
                        InetAddress clientAddress = clientSocket.getInetAddress(); // Client IP
                        out.println("READY " + datagramSocket.getLocalPort() + " " + fileSize);  // Server tells client it's ready
        
                        // Wait for the client to send its local port
                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String clientResponse = in.readLine();
                        if (clientResponse != null && clientResponse.startsWith("CLIENT_READY")) {
                            int clientPort = Integer.parseInt(clientResponse.split(" ")[1]);  // Get client's port
        
                            // Start sending file data
                            FileInputStream fileInputStream = new FileInputStream(file);
                            CRC32 crc = new CRC32();
                            byte[] buffer = new byte[UDP_BUFFER_SIZE];
                            int bytesRead;
        
                            // Initialize sequence number
                            long sequenceNumber = 0;

                            // Read and send data packets
                            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                                // Calculate CRC32 for the data
                                crc.update(buffer, 0, bytesRead);
                                int checksum = (int) crc.getValue(); // Use int for CRC32

                                // Create a packet with sequence number, data, and CRC32
                                // Format: [sequence number (8 bytes)][data (MTU)][CRC32 checksum (4 bytes)]
                                ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + bytesRead + Integer.BYTES);
                                byteBuffer.order(ByteOrder.BIG_ENDIAN); // Ensure consistent byte order
                                byteBuffer.putLong(sequenceNumber);     // Sequence number
                                byteBuffer.put(buffer, 0, bytesRead);   // Data
                                byteBuffer.putInt(checksum);            // CRC32 checksum

                                byte[] packetData = byteBuffer.array();
                                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientAddress, clientPort);
                                datagramSocket.send(packet); // Send the packet

                                sequenceNumber++;
                                crc.reset(); // Reset CRC for next packet

                                // Introduce a small delay
                                try {
                                    Thread.sleep(UDP_DELAY);
                                }
                                    catch(InterruptedException e) {
                                    printAndLog("Thread interrupted: " + e.getMessage());
                                }
                            }

                            // Send end-of-file signal with sequence number -1
                            ByteBuffer endBuffer = ByteBuffer.allocate(Long.BYTES);
                            endBuffer.order(ByteOrder.BIG_ENDIAN);
                            endBuffer.putLong(-1L); // Special sequence number for EOF
                            DatagramPacket endPacket = new DatagramPacket(endBuffer.array(), endBuffer.capacity(), clientAddress, clientPort);
                            datagramSocket.send(endPacket);

                            fileInputStream.close();
                            datagramSocket.close();
                            printAndLog("File transfer completed successfully to: " + clientAddress);

                        }
                    }
                } else {
                    out.println("ERROR: File not found.");
                }
            } else {
                out.println("ERROR: No file specified for GET command.");
            }
            out.flush();
        }
    
        /**
    * Handles the PUT command for file upload.
    * @param command The command array containing the file to upload.
    * @param out The output writer to communicate with the client.
    * @param in The existing BufferedReader to read client messages.
    * @throws IOException If an I/O error occurs while receiving the file.
    */
    private void handlePUT(String[] command, PrintWriter out, BufferedReader in) throws IOException {
        if (command.length > 2) {
            long fileSize;
            try {
                fileSize = Long.parseLong(command[2]);  // Get file size from the client
            } catch (NumberFormatException e) {
                out.println("ERROR: Invalid file size.");
                out.flush();
                return;
            }

            File file = new File(currentDir, command[1]);

            // Attempt to lock the file
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                FileChannel channel = raf.getChannel()) {
                java.nio.channels.FileLock fileLock = channel.tryLock();
                if (fileLock == null) {
                    out.println("ERROR: File is currently in use.");
                    out.flush();
                    return;
                }

                if (!udpMode) {
                    // TCP mode
                    try (ServerSocket transferSocket = new ServerSocket(0);
                        FileOutputStream fos = new FileOutputStream(raf.getFD())) {
                        out.println("READY " + transferSocket.getLocalPort() + " " + fileSize);  // Send file size
                        out.flush();

                        try (Socket fileTransferSocket = transferSocket.accept();
                            BufferedInputStream bis = new BufferedInputStream(fileTransferSocket.getInputStream())) {
                            byte[] buffer = new byte[TCP_BUFFER_SIZE];
                            int bytesRead;
                            long currentBytes = 0;
                            while ((bytesRead = bis.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                currentBytes += bytesRead;
                            }
                            fos.flush();
                        }
                    }
                } else {
                    // UDP mode
                    DatagramSocket datagramSocket = new DatagramSocket();
                    datagramSocket.setSoTimeout(TIMEOUT);
                    datagramSocket.setReceiveBufferSize(UDP_RECV_BUFFER);
    
                    out.println("READY " + datagramSocket.getLocalPort() + " " + fileSize);
                    out.flush();
    
                    // Start the PacketHandler thread
                    FileOutputStream fos = new FileOutputStream(raf.getFD());
                    PacketHandler handler = new PacketHandler(datagramSocket, fos, fileSize, TIMEOUT);
                    handler.start();
    
                    // Wait for the handler to finish
                    try {
                        handler.join();
                    } catch (InterruptedException e) {
                        printAndLog("File transfer was interrupted: " + e.getMessage());
                        Thread.currentThread().interrupt();
                        return;
                    }
    
                    printAndLog("File upload completed successfully from: " + clientAddress);
                }
            } catch (IOException e) {
                out.println("ERROR: Could not lock file for writing: " + e.getMessage());
                out.flush();
            }
        } else {
            out.println("ERROR: No file specified for PUT command.");
            out.flush();
        }
    }
}

    /**
     * Utility method to print messages to the console and log them.
    * @param message The message to log.
    */
    private static void printAndLog(String message) {
        System.out.println(message);
        LOGGER.info(message);
    }

    /**
     * Utility class to set up logging to a file.
     */
    private static class LogToFile {
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
        /**
         * Custom log formatter to format log messages with a timestamp and log level.
         */
        private static class CustomLogFormatter extends Formatter {
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
    }
}
