package p2p;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileManager {

    private final String fileName;
    private final int fileSize;
    private final int pieceSize;
    private final int pieceCount;

    private final byte[][] pieces;
    private final boolean[] hasPiece;

    private final Path peerDirectory;
    private final Path peerFilePath;

    public FileManager(int peerId, String fileName, int fileSize, int pieceSize, int pieceCount, boolean startsWithFile)
            throws IOException {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.pieceCount = pieceCount;
        this.pieces = new byte[pieceCount][];
        this.hasPiece = new boolean[pieceCount];

        this.peerDirectory = Paths.get("peer_" + peerId);
        this.peerFilePath = peerDirectory.resolve(fileName);

        Files.createDirectories(peerDirectory);

        if (startsWithFile) {
            loadInitialFile();
            writeFullFile();
        }
    }

    private void loadInitialFile() throws IOException {
        byte[] fullFileData;

        if (Files.exists(peerFilePath)) {
            fullFileData = Files.readAllBytes(peerFilePath);
        } else {
            Path fallback = Paths.get(fileName);
            if (!Files.exists(fallback)) {
                throw new IOException("Seeder file not found at " + peerFilePath + " or " + fallback.toAbsolutePath());
            }
            fullFileData = Files.readAllBytes(fallback);
        }

        if (fullFileData.length != fileSize) {
            throw new IOException("File size mismatch for " + fileName + ": expected " + fileSize + " bytes but got " + fullFileData.length);
        }

        for (int i = 0; i < pieceCount; i++) {
            int start = i * pieceSize;
            int len = getPieceLength(i);
            byte[] pieceData = new byte[len];
            System.arraycopy(fullFileData, start, pieceData, 0, len);
            pieces[i] = pieceData;
            hasPiece[i] = true;
        }
    }

    public synchronized boolean hasPiece(int pieceIndex) {
        checkPieceIndex(pieceIndex);
        return hasPiece[pieceIndex];
    }

    public synchronized byte[] readPiece(int pieceIndex) {
        checkPieceIndex(pieceIndex);
        if (!hasPiece[pieceIndex]) {
            return null;
        }
        byte[] src = pieces[pieceIndex];
        byte[] copy = new byte[src.length];
        System.arraycopy(src, 0, copy, 0, src.length);
        return copy;
    }

    public synchronized boolean writePiece(int pieceIndex, byte[] data) throws IOException {
        checkPieceIndex(pieceIndex);

        if (hasPiece[pieceIndex]) {
            return false;
        }

        int expectedLength = getPieceLength(pieceIndex);
        if (data == null || data.length != expectedLength) {
            throw new IOException("Invalid piece length for index " + pieceIndex + ": expected " + expectedLength + ", got " + (data == null ? 0 : data.length));
        }

        byte[] copy = new byte[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);

        pieces[pieceIndex] = copy;
        hasPiece[pieceIndex] = true;
        return true;
    }

    public synchronized int getPieceLength(int pieceIndex) {
        checkPieceIndex(pieceIndex);

        if (pieceIndex == pieceCount - 1) {
            int remainder = fileSize % pieceSize;
            return remainder == 0 ? pieceSize : remainder;
        }
        return pieceSize;
    }

    public synchronized int getOwnedPieceCount() {
        int count = 0;
        for (boolean pieceOwned : hasPiece) {
            if (pieceOwned) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean isComplete() {
        return getOwnedPieceCount() == pieceCount;
    }

    public synchronized void writeFullFile() throws IOException {
        if (!isComplete()) {
            throw new IOException("Cannot assemble file yet. Missing pieces remain.");
        }

        byte[] fullData = new byte[fileSize];
        for (int i = 0; i < pieceCount; i++) {
            int start = i * pieceSize;
            byte[] pieceData = pieces[i];
            System.arraycopy(pieceData, 0, fullData, start, pieceData.length);
        }

        Files.write(peerFilePath, fullData);
    }

    private void checkPieceIndex(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieceCount) {
            throw new IndexOutOfBoundsException("Invalid piece index: " + pieceIndex);
        }
    }

    public Path getPeerFilePath() {
        return peerFilePath;
    }

    public Path getPeerDirectory() {
        return peerDirectory;
    }
}
