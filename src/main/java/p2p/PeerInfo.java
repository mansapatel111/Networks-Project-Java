package p2p;

/**
 * PeerInfo represents information about a single peer in the P2P network.
 * Contains peer ID, hostname, port, and whether the peer has the complete file.
 */
public class PeerInfo {
    
    private final int peerId;
    private final String hostName;
    private final int port;
    private final boolean hasFile;
    
    /**
     * Constructor for PeerInfo.
     * @param peerId Unique identifier for the peer
     * @param hostName Hostname or IP address where the peer is running
     * @param port Port number the peer is listening on
     * @param hasFile True if the peer has the complete file initially, false otherwise
     */
    public PeerInfo(int peerId, String hostName, int port, boolean hasFile) {
        this.peerId = peerId;
        this.hostName = hostName;
        this.port = port;
        this.hasFile = hasFile;
    }
    
    /**
     * Gets the peer ID.
     * @return Peer ID
     */
    public int getPeerId() {
        return peerId;
    }
    
    /**
     * Gets the hostname.
     * @return Hostname or IP address
     */
    public String getHostName() {
        return hostName;
    }
    
    /**
     * Gets the port number.
     * @return Port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Checks if the peer initially has the complete file.
     * @return True if peer has the file, false otherwise
     */
    public boolean hasFile() {
        return hasFile;
    }
    
    @Override
    public String toString() {
        return "PeerInfo{" +
                "peerId=" + peerId +
                ", hostName='" + hostName + '\'' +
                ", port=" + port +
                ", hasFile=" + hasFile +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return peerId == peerInfo.peerId;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(peerId);
    }
}
