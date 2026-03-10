package p2p;

/**
 * MessageType defines the different types of messages exchanged between peers.
 * Each message type has a corresponding byte value used in the message protocol.
 */
public enum MessageType {
    
    CHOKE(0),           // Sender is choking the receiver
    UNCHOKE(1),         // Sender is unchoking the receiver
    INTERESTED(2),      // Sender is interested in receiver's pieces
    NOT_INTERESTED(3),  // Sender is not interested in receiver's pieces
    HAVE(4),            // Sender has a specific piece (includes piece index)
    BITFIELD(5),        // Sender's bitfield of available pieces
    REQUEST(6),         // Request a specific piece (includes piece index)
    PIECE(7);           // Sending a piece (includes piece index and data)
    
    private final byte value;
    
    /**
     * Constructor for MessageType.
     * @param value Byte value representing the message type
     */
    MessageType(int value) {
        this.value = (byte) value;
    }
    
    /**
     * Gets the byte value of the message type.
     * @return Byte value
     */
    public byte getValue() {
        return value;
    }
    
    /**
     * Converts a byte value to a MessageType.
     * @param value Byte value to convert
     * @return Corresponding MessageType
     * @throws IllegalArgumentException if value is not a valid message type
     */
    public static MessageType fromByte(byte value) {
        for (MessageType type : MessageType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid message type: " + value);
    }
    
    /**
     * Checks if this message type requires a payload.
     * @return True if payload is required, false otherwise
     */
    public boolean hasPayload() {
        return this == HAVE || this == BITFIELD || this == REQUEST || this == PIECE;
    }
}
