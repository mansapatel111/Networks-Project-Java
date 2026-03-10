package p2p;

// holds info about one peer: ID, hostname, port, and whether they start with the file
public class PeerInfo {
    
    private final int peerId;
    private final String hostName;
    private final int port;
    private final boolean hasFile;
    
    public PeerInfo(int peerId, String hostName, int port, boolean hasFile) {
        this.peerId = peerId;
        this.hostName = hostName;
        this.port = port;
        this.hasFile = hasFile;
    }
    
    public int getPeerId() {
        return peerId;
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public int getPort() {
        return port;
    }
    
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
