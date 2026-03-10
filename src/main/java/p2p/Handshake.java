package p2p;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

// the initial handshake message exchanged between peers
// format: 18-byte header | 10 zero bytes | 4-byte peer ID  (32 bytes total)
public class Handshake {
    
    // Protocol header constant
    private static final String HEADER = "P2PFILESHARINGPROJ";
    private static final int HEADER_LENGTH = 18;
    private static final int ZERO_BITS_LENGTH = 10;
    private static final int PEER_ID_LENGTH = 4;
    private static final int HANDSHAKE_LENGTH = HEADER_LENGTH + ZERO_BITS_LENGTH + PEER_ID_LENGTH; // 32 bytes
    
    private final int peerId;
    
    public Handshake(int peerId) {
        this.peerId = peerId;
    }
    
    public int getPeerId() {
        return peerId;
    }
    
    // serialize to a 32-byte array
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HANDSHAKE_LENGTH);
        
        // Write header (18 bytes)
        byte[] headerBytes = HEADER.getBytes();
        buffer.put(headerBytes);
        
        // Write zero padding (10 bytes)
        byte[] zeroBits = new byte[ZERO_BITS_LENGTH];
        buffer.put(zeroBits);
        
        // Write peer ID (4 bytes)
        buffer.putInt(peerId);
        
        return buffer.array();
    }
    
    // write the handshake to the output stream
    public void send(DataOutputStream out) throws IOException {
        byte[] handshakeBytes = toBytes();
        out.write(handshakeBytes);
        out.flush();
    }
    
    // read a 32-byte handshake from the input stream
    public static Handshake receive(DataInputStream in) throws IOException {
        byte[] handshakeBytes = new byte[HANDSHAKE_LENGTH];
        in.readFully(handshakeBytes);
        
        return fromBytes(handshakeBytes);
    }
    
    // parse bytes into a Handshake object, validates header and length
    public static Handshake fromBytes(byte[] bytes) throws IOException {
        if (bytes.length != HANDSHAKE_LENGTH) {
            throw new IOException("Invalid handshake length: " + bytes.length + " (expected " + HANDSHAKE_LENGTH + ")");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        
        // Read and validate header (18 bytes)
        byte[] headerBytes = new byte[HEADER_LENGTH];
        buffer.get(headerBytes);
        String header = new String(headerBytes);
        
        if (!HEADER.equals(header)) {
            throw new IOException("Invalid handshake header: " + header + " (expected " + HEADER + ")");
        }
        
        // Skip zero padding (10 bytes)
        byte[] zeroBits = new byte[ZERO_BITS_LENGTH];
        buffer.get(zeroBits);
        
        // Read peer ID (4 bytes)
        int peerId = buffer.getInt();
        
        return new Handshake(peerId);
    }
    
    // basic sanity check on the peer ID
    public boolean isValid() {
        return peerId > 0;
    }
    
    public static int getHandshakeLength() {
        return HANDSHAKE_LENGTH;
    }
    
    @Override
    public String toString() {
        return "Handshake{" +
                "header='" + HEADER + '\'' +
                ", peerId=" + peerId +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Handshake handshake = (Handshake) o;
        return peerId == handshake.peerId;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(peerId);
    }
}
