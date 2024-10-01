/* Name: Jason Gardner (n01480000), Samhitha Yenugu (n01603653), Deepak Yadama (n01601954), Sankeerthi Kilaru (n01598034)
 * Date: 21 August 2024
 * Project: Project 1/2
 * File: FTPServer.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: Mutlithreaded FTP server program that uses threads to handle multiple clients
 *              Commands: GET, PUT, CD, LS, QUIT
 */

import java.net.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel; 
import java.nio.channels.FileLock; 
import java.util.Scanner;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.lang.SecurityException;


 public class FTPServer {
    final private int MAX_THREADS = 250;
    static final Logger LOGGER = Logger.getLogger("FTPServer");
    private static int listenPort = 21;
    
    /**
     * <p>Main method for the server program
     * Takes an optional command line argument for the port number
     * If no argument is provided, the default port number is 21
     * If more than one argument is provided, or the argument is -h, the program will exit
     * If the port number is not between 0 and 65535, the program will exit
     * The program will listen on the specified port number for incoming connections
     * When a connection is accepted, a new thread is created to handle the connection
     * The program will continue to listen for connections until the user enters 'q'
     * The program will exit when the user enters 'q'
     * </p>
     * @param args command line argument: port number
     */
    public static void main (String args[]) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPServer.log");
        if (args.length == 0) {
            LOGGER.info("Usage: java server <port number>, using default port FTP(21)");
            System.out.println("Usage: java server <port number>, using default port FTP(21)");

        }
        else if (args.length > 1 || args[0].equals("-h")) {
            LOGGER.severe("Usage: java server <port number>");
            System.err.println("Usage: java server <port number>");
            System.exit(1);
        }
        else {
            listenPort = Integer.parseInt(args[0]);
            if (0 < listenPort && listenPort < 65535) {
                LOGGER.info("Using port number: " + args[0]);
                System.out.println("Using port number: " + args[0]);
            }
            else {
                LOGGER.severe("Invalid port number: " + args[0]);
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
            LOGGER.info("Using port number: " + args[0]);
            System.out.println("Using port number: " + args[0]);
        }

        final Scanner userInput = new Scanner(System.in);

        while (userInput.next().charAt(0) != 'q') {
            try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new server(clientSocket)).start();
            } catch (IOException e) {
                System.err.println("Could not listen on port " + listenPort);
                userInput.close();
                System.exit(-1);
            }
        }
        userInput.close();
        System.exit(0);
    }

    private static class server implements Runnable {
        private final Socket clientSocket;
        private static final String ROOT_DIR = System.getProperty("user.dir");
        private static final String FILE_SEP = System.getProperty("file.separator");
        private static String currentDir = ROOT_DIR;
        private static String inputLine = "";


        server(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            while(!inputLine.equals("QUIT")) {
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    String inputLine;
                    while ((inputLine = in.readLine().toUpperCase()) != null) {
                        switch (inputLine) {
                            case "LS":
                                File dir = new File(ROOT_DIR);
                                File[] files = dir.listFiles();
                                for (File file : files) {
                                    out.println(file.getName());
                                }
                                break;
                            case "CD":
                                out.println("Change directory");
                                try {
                                    currentDir = in.readLine();
                                    File newDir = new File(currentDir);
                                    if (newDir.exists() && newDir.isDirectory()) {
                                        out.println("Directory changed to " + currentDir);
                                    } else {
                                        out.println("Directory does not exist");
                                    }
                                } catch (IOException e) {
                                    out.println("Error changing directory");
                                }
                                break;
                            default:
                                break;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Exception caught when trying to listen on port "
                        + listenPort + " or listening for a connection");
                    System.out.println(e.getMessage());
                }
            }
        }

        
}

    
/** 
     try (FileOutputStream fileOutputStream = new FileOutputStream(filePath); 
             FileChannel fileChannel = fileOutputStream.getChannel(); 
             FileLock fileLock = fileChannel.lock()) { 
  
            // Perform operations within the locked region 
            System.out.println("File locked successfully!"); 
  
            // Simulating a process that holds the lock for some time 
            Thread.sleep(500); 
  
        } catch (IOException | InterruptedException e) { 
            e.printStackTrace(); 
        } 
  
        // File lock released automatically when 
        // the try-with-resources block is exited 
        System.out.println("File lock released!"); 
    } */


    /**
     *     Logger logger = Logger.getLogger("MyLog");  
    FileHandler fh;  

    try {  

        // This block configure the logger with handler and formatter  
        fh = new FileHandler("C:/temp/test/MyLogFile.log");  
        logger.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();  
        fh.setFormatter(formatter);  

        // the following statement is used to log any messages  
        logger.info("My first log");  

    } catch (SecurityException e) {  
        e.printStackTrace();  
    } catch (IOException e) {  
        e.printStackTrace();  
    }  
     */

    
}
