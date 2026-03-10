package p2p;

import java.io.*;
import java.util.*;

/**
 * CommonConfig reads and parses the Common.cfg file containing
 * system-wide configuration parameters for the P2P file sharing system.
 */
public class CommonConfig {
    
    // Configuration parameters
    private int numberOfPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;
    private int pieceCount;
    
    /**
     * Constructor that loads configuration from the specified file.
     * @param configFilePath Path to the Common.cfg file
     * @throws IOException if file cannot be read or parsed
     */
    public CommonConfig(String configFilePath) throws IOException {
        loadConfig(configFilePath);
        calculatePieceCount();
    }
    
    /**
     * Parses the Common.cfg file and loads configuration parameters.
     * Expected format:
     *   NumberOfPreferredNeighbors <value>
     *   UnchokingInterval <value>
     *   OptimisticUnchokingInterval <value>
     *   FileName <value>
     *   FileSize <value>
     *   PieceSize <value>
     */
    private void loadConfig(String configFilePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(configFilePath));
        String line;
        
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            
            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // Parse key-value pairs
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            
            String key = parts[0];
            String value = parts[1];
            
            switch (key) {
                case "NumberOfPreferredNeighbors":
                    numberOfPreferredNeighbors = Integer.parseInt(value);
                    break;
                case "UnchokingInterval":
                    unchokingInterval = Integer.parseInt(value);
                    break;
                case "OptimisticUnchokingInterval":
                    optimisticUnchokingInterval = Integer.parseInt(value);
                    break;
                case "FileName":
                    fileName = value;
                    break;
                case "FileSize":
                    fileSize = Integer.parseInt(value);
                    break;
                case "PieceSize":
                    pieceSize = Integer.parseInt(value);
                    break;
            }
        }
        
        reader.close();
        validateConfig();
    }
    
    /**
     * Calculates the total number of pieces based on file size and piece size.
     * Formula: pieceCount = ceil(FileSize / PieceSize)
     */
    private void calculatePieceCount() {
        pieceCount = (int) Math.ceil((double) fileSize / pieceSize);
    }
    
    /**
     * Validates that all required configuration parameters were loaded.
     * @throws IllegalStateException if any required parameter is missing or invalid
     */
    private void validateConfig() {
        if (numberOfPreferredNeighbors <= 0) {
            throw new IllegalStateException("NumberOfPreferredNeighbors must be positive");
        }
        if (unchokingInterval <= 0) {
            throw new IllegalStateException("UnchokingInterval must be positive");
        }
        if (optimisticUnchokingInterval <= 0) {
            throw new IllegalStateException("OptimisticUnchokingInterval must be positive");
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalStateException("FileName must be specified");
        }
        if (fileSize <= 0) {
            throw new IllegalStateException("FileSize must be positive");
        }
        if (pieceSize <= 0) {
            throw new IllegalStateException("PieceSize must be positive");
        }
    }
    
    // Getters
    public int getNumberOfPreferredNeighbors() {
        return numberOfPreferredNeighbors;
    }
    
    public int getUnchokingInterval() {
        return unchokingInterval;
    }
    
    public int getOptimisticUnchokingInterval() {
        return optimisticUnchokingInterval;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public int getFileSize() {
        return fileSize;
    }
    
    public int getPieceSize() {
        return pieceSize;
    }
    
    public int getPieceCount() {
        return pieceCount;
    }
    
    @Override
    public String toString() {
        return "CommonConfig{" +
                "numberOfPreferredNeighbors=" + numberOfPreferredNeighbors +
                ", unchokingInterval=" + unchokingInterval +
                ", optimisticUnchokingInterval=" + optimisticUnchokingInterval +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", pieceSize=" + pieceSize +
                ", pieceCount=" + pieceCount +
                '}';
    }
}
