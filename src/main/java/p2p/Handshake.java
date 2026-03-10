package p2p;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Handshake represents the initial handshake message exchanged between peers.
 * Handshake format (32 bytes total):
 *   18 bytes: header string "P2PFILESHARINGPROJ"
 *   10 bytes: zero padding
 *   4 bytes:  peer ID (as integer)
 */
public class Handshake {
    
    // Protocol header constant
    private static final String HEADER = "P2PFILESHARINGPROJ";
    private static final int HEADER_LENGTH = 18;
    private static final int ZERO_BITS_LENGTH = 10;
    private static final int PEER_ID_LENGTH = 4;
    private static final int HANDSHAKE_LENGTH = HEADER_LENGTH + ZERO_BITS_LENGTH + PEER_ID_LENGTH; // 32 bytes
    
    private final int peerId;
    
    /**
     * Constructor for creating a handshake message.
     * @param peerId Peer ID to include in the handshake
     */
    public Handshake(int peerId) {
        this.peerId = peerId;
    }
    
    /**
     * Gets the peer ID from this handshake.
     * @return Peer ID
     */
    public int getPeerId() {
        return peerId;
    }
    
    /**
     * Serializes the handshake message to a byte array.
     * @return 32-byte handshake message
     */
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
    
    /**
     * Sends this handshake message to the specified output stream.
     * @param out DataOutputStream to send the handshake to
     * @throws IOException if an I/O error occurs
     */
    public void send(DataOutputStream out) throws IOException {
        byte[] handshakeBytes = toBytes();
        out.write(handshakeBytes);
        out.flush();
    }
    
    /**
     * Reads a handshake message from the specified input stream.
     * @param in DataInputStream to read from
     * @return Parsed Handshake object
     * @throws IOException if an I/O error occurs or handshake format is invalid
     */
    public static Handshake receive(DataInputStream in) throws IOException {
        byte[] handshakeBytes = new byte[HANDSHAKE_LENGTH];
        in.readFully(handshakeBytes);
        
        return fromBytes(handshakeBytes);
    }
    
    /**
     * Parses a handshake message from a byte array.
     * @param bytes Byte array containing the handshake (must be 32 bytes)
     * @return Parsed Handshake object
     * @throws IOException if handshake format is invalid
     */
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
    
    /**
     * Validates that the handshake header matches the expected protocol header.
     * @return True if header is valid, false otherwise
     */
    public boolean isValid() {
        return peerId > 0;
    }
    
    /**
     * Gets the expected handshake length in bytes.
     * @return Handshake length (32 bytes)
     */
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
