package p2p;

// message types used in the protocol, each maps to a byte value (0–7)
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
    
    MessageType(int value) {
        this.value = (byte) value;
    }
    
    public byte getValue() {
        return value;
    }
    
    // look up a MessageType from its byte value
    public static MessageType fromByte(byte value) {
        for (MessageType type : MessageType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid message type: " + value);
    }
    
    // true for message types that include a payload (HAVE, BITFIELD, REQUEST, PIECE)
    public boolean hasPayload() {
        return this == HAVE || this == BITFIELD || this == REQUEST || this == PIECE;
    }
}
