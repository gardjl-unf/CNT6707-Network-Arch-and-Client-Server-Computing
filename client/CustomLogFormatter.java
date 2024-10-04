import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class CustomLogFormatter extends Formatter {
    // Define the date format
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy@HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        // Get the current date and time
        String timeStamp = dateFormat.format(new Date(record.getMillis()));

        // Get the class and method name
        String sourceClass = record.getSourceClassName();

        // Get the log level (severity)
        String logLevel = record.getLevel().getName();

        // Format the log message according to your specifications
        return String.format("%s:%s:%s:%s:\t%s%n",
                timeStamp,              // Short date and time
                sourceClass,            // Class name
                logLevel,               // Log level (severity)
                record.getMessage()     // Actual log message
        );
    }
}