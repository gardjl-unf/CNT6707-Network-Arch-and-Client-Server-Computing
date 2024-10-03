import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public abstract class LogToFile {
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