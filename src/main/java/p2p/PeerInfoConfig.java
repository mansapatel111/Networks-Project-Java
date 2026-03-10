package p2p;

import java.io.*;
import java.util.*;

// reads and stores peer info from PeerInfo.cfg
public class PeerInfoConfig {
    
    private List<PeerInfo> peers;
    
    public PeerInfoConfig(String configFilePath) throws IOException {
        peers = new ArrayList<>();
        loadConfig(configFilePath);
    }
    
    // parse PeerInfo.cfg — each line is: peerID hostName port hasFile (e.g. "1001 localhost 6008 1")
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
    
    public List<PeerInfo> getPeers() {
        return new ArrayList<>(peers);
    }
    
    public PeerInfo getPeerById(int peerId) {
        for (PeerInfo peer : peers) {
            if (peer.getPeerId() == peerId) {
                return peer;
            }
        }
        return null;
    }
    
    // according to the protocol, a peer connects only to peers listed before it in the config
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
