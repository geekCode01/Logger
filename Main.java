package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    // Enum to define log levels
    enum LogLevel {
        DEBUG, INFO, WARN, ERROR;
    }

    // Interface for log formatting strategies
    interface LogFormatter {

        // Method to format log messages based on log level and message content
        String format(LogLevel level, String message);
    }

    // Simple log formatter implementation
    static class SimpleLogFormatter implements LogFormatter {

        // Implementation of the format method for simple log messages
        @Override
        public String format(LogLevel level, String message) {
            // Format the log message to include the log level in brackets
            return String.format("[%s] %s", level, message);
        }
    }

    // Timestamped log formatter implementation
    static class TimestampedLogFormatter implements LogFormatter {

        // ThreadLocal instance to ensure thread safety and performance
        // Each thread will have its own SimpleDateFormat instance
        private static final ThreadLocal<SimpleDateFormat> dateFormat =
                ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

        // Implementation of the format method for timestamped log messages
        @Override
        public String format(LogLevel level, String message) {
            // Get the current timestamp from the ThreadLocal instance
            String timestamp = dateFormat.get().format(new Date());
            // Format the log message to include the timestamp, log level, and message
            return String.format("%s [%s] %s", timestamp, level, message);
        }
    }

    // Interface for log appenders
    interface Appender {
        // Method to append log messages
        void append(String logMessage);
    }

    // Factory for creating appenders
    static class AppenderFactory {
        // Static method to create an Appender based on the specified type
        public static Appender createAppender(String type, String filePath) {
            // Switch statement to determine which type of appender to create
            switch (type.toLowerCase()) {
                // Create a new ConsoleAppender if the type is "console"
                case "console":
                    return new ConsoleAppender();
                case "file":
                    return new FileAppender(filePath);
                default:
                    throw new IllegalArgumentException("Unknown appender type: " + type);
            }
        }
    }

    // Console appender implementation
    static class ConsoleAppender implements Appender {
        @Override
        public void append(String logMessage) {
            System.out.println(logMessage);
        }
    }

    // File appender implementation
    static class FileAppender implements Appender {
        // PrintWriter to handle writing log messages to a file
        private PrintWriter writer;

        // Constructor that takes a file path and initializes the PrintWriter
        public FileAppender(String filePath) {
            try {
                // Create a PrintWriter with FileWriter to append to the specified file
                writer = new PrintWriter(new FileWriter(filePath, true));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Implementation of the append method to write log messages to the file
        @Override
        public void append(String logMessage) {
            if (writer != null) {
                writer.println(logMessage); // Write the log message to the file
                writer.flush(); // Flush the writer to ensure all data is written to the file
            }
        }

        // Method to close the PrintWriter and release any system resources
        public void close() {
            if (writer != null) {
                writer.close(); // Close the writer to free up resources
            }
        }
    }

    // Singleton Logger class
    public static class Logger {
        // Thread-safe singleton instance of Logger
        private static volatile Logger instance;
        // Lock for ensuring thread safety when modifying shared resources
        private final Lock lock = new ReentrantLock();
        private LogLevel currentLogLevel; // Current log level for filtering log messages
        private final List<Appender> appenders; // List of appenders for outputting log messages
        private final LogFormatter formatter; // Formatter for log messages

        // Private constructor to initialize logger with log level and formatter
        private Logger(LogLevel logLevel, LogFormatter formatter) {
            this.currentLogLevel = logLevel;
            this.formatter = formatter;
            this.appenders = new ArrayList<>();
        }

        // Method to get the single instance of Logger
        public static Logger getInstance(LogLevel logLevel, LogFormatter formatter) {
            if (instance == null) {
                synchronized (Logger.class) { // Double-checked locking to ensure thread safety
                    if (instance == null) {
                        instance = new Logger(logLevel, formatter); // Create a new instance if it doesn't exist
                    }
                }
            }
            return instance; // Return the singleton instance
        }

        // Method to add a new appender to the logger
        public void addAppender(Appender appender) {
            // Locking only around the critical section to reduce contention
            lock.lock();
            try {
                appenders.add(appender); // Add the appender to the list
            } finally {
                lock.unlock(); // Ensure the lock is released
            }
        }

        // Method to log messages at a specific log level
        public void log(LogLevel level, String message) {
            if (level.ordinal() >= currentLogLevel.ordinal()) { // Check if the log level is enabled for logging
                String logMessage = formatter.format(level, message); // Format the log message
                lock.lock(); // Acquire the lock for thread safety
                try {
                    // Append the log message to all registered appenders
                    for (Appender appender : appenders) {
                        appender.append(logMessage); // Call append method on each appender
                    }
                } finally {
                    lock.unlock(); // Ensure the lock is released
                }
            }
        }

        // Convenience method for logging info messages
        public void info(String message) {
            log(LogLevel.INFO, message); // Delegate to the log method
        }

        // Convenience method for logging debug messages
        public void debug(String message) {
            log(LogLevel.DEBUG, message); // Delegate to the log method
        }

        // Convenience method for logging error messages
        public void error(String message) {
            log(LogLevel.ERROR, message); // Delegate to the log method
        }

        // Method to change the current log level at runtime
        public void setLogLevel(LogLevel logLevel) {
            lock.lock(); // Acquire the lock for thread safety
            try {
                this.currentLogLevel = logLevel; // Update the current log level
            } finally {
                lock.unlock(); // Ensure the lock is released
            }
        }

        // Method to close all appenders and release resources
        public void close() {
            lock.lock(); // Acquire the lock for thread safety
            try {
                // Iterate through all appenders and close any FileAppender instances
                for (Appender appender : appenders) {
                    if (appender instanceof FileAppender) {
                        ((FileAppender) appender).close(); // Close the FileAppender
                    }
                }
            } finally {
                lock.unlock(); // Ensure the lock is released
            }
        }
    }

    public static void main(String[] args) {
        Logger logger = Logger.getInstance(LogLevel.INFO, new TimestampedLogFormatter());

        // Add appenders using the factory
        logger.addAppender(AppenderFactory.createAppender("console", null)); // Output to console
        logger.addAppender(AppenderFactory.createAppender("file", "application.log")); // Output to file

        // Log messages at different levels using convenience methods

        // This message is logged because the current log level is INFO,
        // which means that all messages of this level and above will be recorded.
        logger.info("This is an info message.");

        // This will not be logged
        // Debug messages are considered lower priority and will not be captured
        logger.debug("This is a debug message.");

        // This will be logged
        // ERROR level is higher than INFO, so it is recorded regardless of the current log level
        logger.error("This is an error message.");

        // Change log level to DEBUG
        logger.setLogLevel(LogLevel.DEBUG);
        logger.debug("Debug level is now enabled."); // This will be logged

        // Close the logger to release resources
        logger.close(); // Clean up resources before exiting
    }
}