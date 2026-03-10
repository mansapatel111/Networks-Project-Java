package p2p;

import java.io.*;
import java.util.*;

// reads and stores the values from Common.cfg
public class CommonConfig {
    
    // Configuration parameters
    private int numberOfPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;
    private int pieceCount;
    
    public CommonConfig(String configFilePath) throws IOException {
        loadConfig(configFilePath);
        calculatePieceCount();
    }
    
    // parse Common.cfg line by line
    // expected format: NumberOfPreferredNeighbors <value>, UnchokingInterval <value>, etc.
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
    
    // pieceCount = ceil(FileSize / PieceSize)
    private void calculatePieceCount() {
        pieceCount = (int) Math.ceil((double) fileSize / pieceSize);
    }
    
    // make sure all required fields were actually set
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
