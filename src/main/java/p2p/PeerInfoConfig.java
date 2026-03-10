package p2p;

import java.io.*;
import java.util.*;

/**
 * PeerInfoConfig reads and parses the PeerInfo.cfg file containing
 * information about all peers in the P2P network.
 */
public class PeerInfoConfig {
    
    private List<PeerInfo> peers;
    
    /**
     * Constructor that loads peer information from the specified file.
     * @param configFilePath Path to the PeerInfo.cfg file
     * @throws IOException if file cannot be read or parsed
     */
    public PeerInfoConfig(String configFilePath) throws IOException {
        peers = new ArrayList<>();
        loadConfig(configFilePath);
    }
    
    /**
     * Parses the PeerInfo.cfg file and loads peer information.
     * Expected format (one peer per line):
     *   peerID hostName port hasFile
     * Example:
     *   1001 localhost 6008 1
     *   1002 localhost 6009 0
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
            
            // Parse peer information
            String[] parts = line.split("\\s+");
            if (parts.length < 4) {
                continue;
            }
            
            try {
                int peerId = Integer.parseInt(parts[0]);
                String hostName = parts[1];
                int port = Integer.parseInt(parts[2]);
                boolean hasFile = parts[3].equals("1");
                
                PeerInfo peerInfo = new PeerInfo(peerId, hostName, port, hasFile);
                peers.add(peerInfo);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing line: " + line);
            }
        }
        
        reader.close();
    }
    
    /**
     * Gets the list of all peers.
     * @return List of PeerInfo objects
     */
    public List<PeerInfo> getPeers() {
        return new ArrayList<>(peers);
    }
    
    /**
     * Finds a peer by its ID.
     * @param peerId Peer ID to search for
     * @return PeerInfo object if found, null otherwise
     */
    public PeerInfo getPeerById(int peerId) {
        for (PeerInfo peer : peers) {
            if (peer.getPeerId() == peerId) {
                return peer;
            }
        }
        return null;
    }
    
    /**
     * Gets all peers that should be connected to by the specified peer.
     * According to the protocol, a peer connects only to peers that appear
     * before it in the configuration file.
     * @param peerId The peer ID that will initiate connections
     * @return List of PeerInfo objects representing peers to connect to
     */
    public List<PeerInfo> getPeersToConnectTo(int peerId) {
        List<PeerInfo> result = new ArrayList<>();
        
        for (PeerInfo peer : peers) {
            if (peer.getPeerId() == peerId) {
                break; // Stop when we reach the current peer
            }
            result.add(peer);
        }
        
        return result;
    }
    
    /**
     * Gets the total number of peers in the configuration.
     * @return Number of peers
     */
    public int getPeerCount() {
        return peers.size();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PeerInfoConfig{\n");
        for (PeerInfo peer : peers) {
            sb.append("  ").append(peer).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
