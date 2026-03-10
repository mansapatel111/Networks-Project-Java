package p2p;

import java.util.BitSet;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

/**
 * Bitfield tracks which pieces a peer has.
 * Uses Java's BitSet for efficient storage and manipulation.
 */
public class Bitfield {
    
    private final BitSet bitset;
    private final int pieceCount;
    private int piecesOwned;
    
    /**
     * Constructor for creating an empty bitfield.
     * @param pieceCount Total number of pieces in the file
     */
    public Bitfield(int pieceCount) {
        this.pieceCount = pieceCount;
        this.bitset = new BitSet(pieceCount);
        this.piecesOwned = 0;
    }
    
    /**
     * Constructor for creating a bitfield with all pieces.
     * @param pieceCount Total number of pieces in the file
     * @param hasAllPieces If true, all pieces are marked as owned initially
     */
    public Bitfield(int pieceCount, boolean hasAllPieces) {
        this.pieceCount = pieceCount;
        this.bitset = new BitSet(pieceCount);
        this.piecesOwned = 0;
        
        if (hasAllPieces) {
            for (int i = 0; i < pieceCount; i++) {
                setPiece(i);
            }
        }
    }
    
    /**
     * Constructor for creating a bitfield from a byte array.
     * Used when receiving a BITFIELD message from a peer.
     * @param pieceCount Total number of pieces in the file
     * @param bytes Byte array representing the bitfield
     */
    public Bitfield(int pieceCount, byte[] bytes) {
        this.pieceCount = pieceCount;
        this.bitset = BitSet.valueOf(bytes);
        this.piecesOwned = bitset.cardinality();
    }
    
    /**
     * Marks a piece as owned.
     * @param pieceIndex Index of the piece to set
     * @return True if the piece was newly set, false if already owned
     */
    public synchronized boolean setPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieceCount) {
            throw new IndexOutOfBoundsException("Invalid piece index: " + pieceIndex);
        }
        
        boolean wasSet = bitset.get(pieceIndex);
        if (!wasSet) {
            bitset.set(pieceIndex);
            piecesOwned++;
            return true;
        }
        return false;
    }
    
    /**
     * Checks if a specific piece is owned.
     * @param pieceIndex Index of the piece to check
     * @return True if the piece is owned, false otherwise
     */
    public synchronized boolean hasPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieceCount) {
            return false;
        }
        return bitset.get(pieceIndex);
    }
    
    /**
     * Checks if all pieces are owned.
     * @return True if all pieces are owned, false otherwise
     */
    public synchronized boolean hasAllPieces() {
        return piecesOwned == pieceCount;
    }
    
    /**
     * Gets the number of pieces owned.
     * @return Number of pieces owned
     */
    public synchronized int getPiecesOwned() {
        return piecesOwned;
    }
    
    /**
     * Gets the total number of pieces.
     * @return Total piece count
     */
    public int getPieceCount() {
        return pieceCount;
    }
    
    /**
     * Checks if this peer has any pieces that another peer doesn't have.
     * @param other Another peer's bitfield
     * @return True if this peer has at least one piece that the other peer doesn't have
     */
    public synchronized boolean hasInterestingPiecesFor(Bitfield other) {
        for (int i = 0; i < pieceCount; i++) {
            if (this.hasPiece(i) && !other.hasPiece(i)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets a list of piece indices that this peer doesn't have but the other peer has.
     * @param other Another peer's bitfield
     * @return List of interesting piece indices
     */
    public synchronized List<Integer> getMissingPiecesFrom(Bitfield other) {
        List<Integer> missingPieces = new ArrayList<>();
        for (int i = 0; i < pieceCount; i++) {
            if (!this.hasPiece(i) && other.hasPiece(i)) {
                missingPieces.add(i);
            }
        }
        return missingPieces;
    }
    
    /**
     * Gets a random missing piece index from the available pieces in another peer's bitfield.
     * @param other Another peer's bitfield
     * @return Random missing piece index, or -1 if no interesting pieces available
     */
    public synchronized int getRandomMissingPieceFrom(Bitfield other) {
        List<Integer> missingPieces = getMissingPiecesFrom(other);
        
        if (missingPieces.isEmpty()) {
            return -1;
        }
        
        Random random = new Random();
        return missingPieces.get(random.nextInt(missingPieces.size()));
    }
    
    /**
     * Converts the bitfield to a byte array for transmission.
     * @return Byte array representation of the bitfield
     */
    public synchronized byte[] toBytes() {
        // Calculate the number of bytes needed
        int byteCount = (pieceCount + 7) / 8; // Ceiling division
        byte[] bytes = new byte[byteCount];
        
        byte[] bitsetBytes = bitset.toByteArray();
        System.arraycopy(bitsetBytes, 0, bytes, 0, Math.min(bitsetBytes.length, byteCount));
        
        return bytes;
    }
    
    /**
     * Creates a copy of this bitfield.
     * @return New Bitfield object with the same state
     */
    public synchronized Bitfield copy() {
        Bitfield copy = new Bitfield(pieceCount);
        for (int i = 0; i < pieceCount; i++) {
            if (this.hasPiece(i)) {
                copy.setPiece(i);
            }
        }
        return copy;
    }
    
    /**
     * Gets the completion percentage.
     * @return Percentage of pieces owned (0.0 to 100.0)
     */
    public synchronized double getCompletionPercentage() {
        if (pieceCount == 0) {
            return 100.0;
        }
        return (double) piecesOwned / pieceCount * 100.0;
    }
    
    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bitfield{pieces=");
        for (int i = 0; i < pieceCount; i++) {
            sb.append(hasPiece(i) ? "1" : "0");
        }
        sb.append(", owned=").append(piecesOwned);
        sb.append("/").append(pieceCount);
        sb.append(", completion=").append(String.format("%.2f", getCompletionPercentage())).append("%");
        sb.append("}");
        return sb.toString();
    }
}
