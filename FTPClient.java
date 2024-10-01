/* Name: Jason Gardner (n01480000), Samhitha Yenugu (n01603653), Deepak Yadama (n01601954), Sankeerthi Kilaru (n01598034)
 * Date: 21 August 2024
 * Project: Project 1/2
 * File: FTPClient.java
 * CNT6707 - Network Architecture and Client/Server Computing
 * Description: FTP server program that uses a blocking queue to handle multiple clients.
 *              Commands: GET, PUT, CD, LS, QUIT
 */

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
 
public class FTPClient {
    static final Logger LOGGER = Logger.getLogger("FTPClient");
    public static void main(String[] args) throws IOException {
        LogToFile.logToFile(LOGGER, "FTPClient.log");
        if (args.length < 1 || args.length > 2) {
            LOGGER.severe("Usage: java FTPClient <host name> <port number>");
            System.err.println("Usage: java FTPClient <host name> <port number>");
            System.exit(1);
        }
        if (args.length > 1) {
            // Check if the IP address is valid
            String[] ip = args[0].split("\\.");
            if (ip.length != 4) {
                LOGGER.severe(String.format("Invalid IP address : %s", args[0]));
                System.err.println(String.format("Invalid IP address : %s", args[0]));
                System.exit(1);
            }
            for (String segment : ip) {
                if (Integer.parseInt(segment) < 0 || Integer.parseInt(segment) > 255) {
                    LOGGER.severe(String.format("Invalid IP address : %s", args[0]));
                    System.err.println(String.format("Invalid IP address : %s", args[0]));
                    System.exit(1);
                }
            }
            // Check if the port number is valid
            if (args.length ==  2 && (Integer.parseInt(args[1]) < 0 || Integer.parseInt(args[1]) > 65535)) {
                LOGGER.severe("Invalid port number");
                System.err.println("Invalid port number");
                System.exit(1);
            }
        }
 
        String hostName = args[0];
        final int portNumber = args.length == 2 ? Integer.parseInt(args[1]) : 21;
        LOGGER.info(String.format("Connecting to %s:%s", hostName, portNumber));
 
        try (
            Socket ftpSocket = new Socket(hostName, portNumber);
            PrintWriter out =
                new PrintWriter(ftpSocket.getOutputStream(), true);
            BufferedReader in =
                new BufferedReader(
                    new InputStreamReader(ftpSocket.getInputStream()));
            BufferedReader stdIn =
                new BufferedReader(
                    new InputStreamReader(System.in))
        ) {
            String userInput;
            while ((userInput = stdIn.readLine().toUpperCase()).toCharArray()[0] !='q') {
                out.println(userInput);
                System.out.println(String.format("%s:%s: %s ",  hostName, portNumber, in.readLine()));
            }
        } catch (UnknownHostException e) {
            LOGGER.severe(String.format("Unknown Host: %s", hostName));
            LOGGER.severe(e.getMessage());
            System.err.println(String.format("Don't know about host: %s", hostName));
            System.exit(1);
        } catch (IOException e) {
            System.err.println(String.format("Couldn't get I/O for the connection to %s:%s", hostName, portNumber));
            System.exit(1);
        } 
    }
}