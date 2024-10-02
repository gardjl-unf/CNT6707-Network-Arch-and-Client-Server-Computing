import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public abstract class LogToFile {
    public static void logToFile(Logger logger, String logFile) {
        try {
            FileHandler fh = new FileHandler(logFile, true);  // Append mode to prevent overwriting
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
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