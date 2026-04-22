package p2p;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class peerProcess {
    private static final String COMMON_CONFIG_FILE = "Common.cfg";
    private static final String PEER_INFO_CONFIG_FILE = "PeerInfo.cfg";

    private final int myPeerId;
    private CommonConfig commonConfig;
    private PeerInfoConfig peerInfoConfig;
    private PeerInfo myPeerInfo;
    private FileManager fileManager;
    private ServerSocket serverSocket;

    private final Map<Integer, Socket> peerConnections = new ConcurrentHashMap<>();
    private final Map<Integer, DataOutputStream> outputStreams = new ConcurrentHashMap<>();
    private final Map<Integer, DataInputStream> inputStreams = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> peerChokingMe = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> iAmChokingPeer = new ConcurrentHashMap<>();
    private final Set<Integer> interestedPeers = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> bytesDownloadedByPeer = new ConcurrentHashMap<>();
    private final Set<Integer> requestedPieces = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> requestedPieceOwners = new ConcurrentHashMap<>();
    private Bitfield myBitfield;
    private final Map<Integer, Bitfield> peerBitfields = new ConcurrentHashMap<>();

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private ScheduledExecutorService scheduler;
    private final Set<Integer> preferredNeighbors = ConcurrentHashMap.newKeySet();
    private volatile Integer optimisticNeighbor = null;
    private volatile boolean completionLogged = false;
    private final Set<Integer> completedPeers = ConcurrentHashMap.newKeySet();

    private static Logger logger;

    public peerProcess(int peerId) {
        this.myPeerId = peerId;
        setupLogger();
    }

    private void setupLogger() {
        try {
            logger = Logger.getLogger("Peer_" + myPeerId);
            logger.setUseParentHandlers(false);
            FileHandler fh = new FileHandler("log_peer_" + myPeerId + ".log");
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);

            // Also log to console for demo visibility
            ConsoleHandler ch = new ConsoleHandler();
            ch.setFormatter(new SimpleFormatter());
            logger.addHandler(ch);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Logger setup failed: " + e.getMessage());
        }
    }

    private void loadConfigurations() throws IOException {
        commonConfig = new CommonConfig(COMMON_CONFIG_FILE);
        peerInfoConfig = new PeerInfoConfig(PEER_INFO_CONFIG_FILE);
        myPeerInfo = peerInfoConfig.getPeerById(myPeerId);
        if (myPeerInfo == null) throw new IllegalArgumentException("Peer " + myPeerId + " not in PeerInfo.cfg");
        logger.info("Peer " + myPeerId + " Start, set variables to " + commonConfig +
                    ", bitfield to " + (myPeerInfo.hasFile() ? "all 1s" : "all 0s"));
    }

    private void initializeBitfield() throws IOException {
        int pieceCount = commonConfig.getPieceCount();
        boolean hasFile = myPeerInfo.hasFile();
        fileManager = new FileManager(myPeerId, commonConfig.getFileName(),
                commonConfig.getFileSize(), commonConfig.getPieceSize(), pieceCount, hasFile);
        myBitfield = new Bitfield(pieceCount, hasFile);
        if (hasFile) completionLogged = true;
    }

    private void startServer() throws IOException {
        serverSocket = new ServerSocket(myPeerInfo.getPort());
        threadPool.execute(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleIncomingConnection(clientSocket));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) logger.warning("Accept error: " + e.getMessage());
                }
            }
        });
    }

    private void handleIncomingConnection(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            Handshake received = Handshake.receive(in);
            int remotePeerId = received.getPeerId();

            new Handshake(myPeerId).send(out);

            logger.info("Peer " + myPeerId + " is connected from Peer " + remotePeerId);

            registerPeer(remotePeerId, socket, in, out);
            exchangeBitfields(remotePeerId, in, out);
            handlePeerMessages(remotePeerId, in, out);
        } catch (IOException e) {
            logger.warning("Incoming connection error: " + e.getMessage());
        }
    }

    private void connectToPeers() {
        for (PeerInfo peerInfo : peerInfoConfig.getPeersToConnectTo(myPeerId)) {
            threadPool.execute(() -> connectToPeer(peerInfo));
        }
    }

    private void connectToPeer(PeerInfo peerInfo) {
        // Retry a few times in case the peer isn't ready yet
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Socket socket = new Socket(peerInfo.getHostName(), peerInfo.getPort());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                new Handshake(myPeerId).send(out);
                Handshake received = Handshake.receive(in);

                if (received.getPeerId() != peerInfo.getPeerId()) {
                    logger.warning("Handshake ID mismatch from " + peerInfo.getPeerId());
                    socket.close();
                    return;
                }

                logger.info("Peer " + myPeerId + " makes a connection to Peer " + peerInfo.getPeerId());

                registerPeer(peerInfo.getPeerId(), socket, in, out);
                exchangeBitfields(peerInfo.getPeerId(), in, out);
                handlePeerMessages(peerInfo.getPeerId(), in, out);
                return;

            } catch (IOException e) {
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
        logger.warning("Failed to connect to Peer " + peerInfo.getPeerId() + " after retries");
    }

    private void registerPeer(int peerId, Socket socket, DataInputStream in, DataOutputStream out) throws IOException {
        peerConnections.put(peerId, socket);
        inputStreams.put(peerId, in);
        outputStreams.put(peerId, out);
        peerChokingMe.put(peerId, true);  // assume choked until told otherwise
        iAmChokingPeer.put(peerId, true); // we start by choking everyone
    }

    private void exchangeBitfields(int peerId, DataInputStream in, DataOutputStream out) throws IOException {

        sendMessage(out, Message.createBitfieldMessage(myBitfield.toBytes()));

        Message msg = Message.receive(in);
        if (msg.getType() == MessageType.BITFIELD) {
            Bitfield peerBf = new Bitfield(commonConfig.getPieceCount(), msg.getPayload());
            peerBitfields.put(peerId, peerBf);
            sendInterestDecision(peerId, out);
        } else {
            // They had no pieces and skipped BITFIELD; process this message normally
            peerBitfields.put(peerId, new Bitfield(commonConfig.getPieceCount()));
            sendInterestDecision(peerId, out);
            processMessage(peerId, msg, out);
        }
    }

    private void sendInterestDecision(int peerId, DataOutputStream out) throws IOException {
        Bitfield peerBf = peerBitfields.get(peerId);
        if (peerBf != null && peerBf.hasInterestingPiecesFor(myBitfield)) {
            sendMessage(out, new Message(MessageType.INTERESTED));
            logger.info("Peer " + myPeerId + " sent INTERESTED to Peer " + peerId);
        } else {
            sendMessage(out, new Message(MessageType.NOT_INTERESTED));
            logger.info("Peer " + myPeerId + " sent NOT_INTERESTED to Peer " + peerId);
        }
    }

    private void handlePeerMessages(int peerId, DataInputStream in, DataOutputStream out) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = Message.receive(in);
                processMessage(peerId, msg, out);
            }
        } catch (IOException e) {
            logger.info("Connection closed with Peer " + peerId);
        } finally {
            cleanupPeer(peerId);
        }
    }

    private void processMessage(int peerId, Message msg, DataOutputStream out) throws IOException {
        switch (msg.getType()) {
            case CHOKE:
                peerChokingMe.put(peerId, true);
                logger.info("Peer " + myPeerId + " is choked by " + peerId);
                // Release any pieces we requested from this peer so others can serve them
                List<Integer> stuckPieces = new ArrayList<>();
                for (Map.Entry<Integer, Integer> e : requestedPieceOwners.entrySet()) {
                    if (e.getValue().equals(peerId)) stuckPieces.add(e.getKey());
                }
                for (int p : stuckPieces) {
                    requestedPieceOwners.remove(p);
                    requestedPieces.remove(p);
                }
                break;
            case UNCHOKE:
                peerChokingMe.put(peerId, false);
                logger.info("Peer " + myPeerId + " is unchoked by " + peerId);
                maybeRequestNextPiece(peerId, out);
                break;
            case INTERESTED:
                logger.info("Peer " + myPeerId + " received the 'interested' message from " + peerId);
                interestedPeers.add(peerId);
                break;
            case NOT_INTERESTED:
                logger.info("Peer " + myPeerId + " received the 'not interested' message from " + peerId);
                interestedPeers.remove(peerId);
                break;
            case HAVE:
                int piece = msg.getPieceIndex();
                logger.info("Peer " + myPeerId + " received the 'have' message from " + peerId + " for the piece " + piece);
                Bitfield peerBf = peerBitfields.get(peerId);
                if (peerBf != null) {
                    peerBf.setPiece(piece);
                    if (peerBf.hasAllPieces()) completedPeers.add(peerId);
                }
                sendInterestDecision(peerId, out);
                maybeRequestNextPiece(peerId, out);
                break;
            case REQUEST:
                handleRequest(peerId, msg, out);
                break;
            case PIECE:
                handlePiece(peerId, msg, out);
                break;
            case BITFIELD:
                Bitfield updatedBf = new Bitfield(commonConfig.getPieceCount(), msg.getPayload());
                peerBitfields.put(peerId, updatedBf);
                sendInterestDecision(peerId, out);
                maybeRequestNextPiece(peerId, out);
                break;
        }
    }

    private void handleRequest(int peerId, Message msg, DataOutputStream out) throws IOException {
        if (iAmChokingPeer.getOrDefault(peerId, true)) return;
        int idx = msg.getPieceIndex();
        byte[] data = fileManager.readPiece(idx);
        if (data == null) { logger.warning("Requested piece " + idx + " not available"); return; }
        sendMessage(out, Message.createPieceMessage(idx, data));
    }

    private void handlePiece(int peerId, Message msg, DataOutputStream out) throws IOException {
        int idx = msg.getPieceIndex();
        byte[] data = msg.getPieceData();
        bytesDownloadedByPeer.merge(peerId, data.length, Integer::sum);
        requestedPieces.remove(idx);
        requestedPieceOwners.remove(idx);

        boolean newPiece = fileManager.writePiece(idx, data);
        if (!newPiece) { maybeRequestNextPiece(peerId, out); return; }

        myBitfield.setPiece(idx);
        logger.info("Peer " + myPeerId + " has downloaded the piece " + idx + " from " + peerId +
                    ". Now the number of pieces it has is " + myBitfield.getPiecesOwned() + ".");
        broadcastHave(idx);

        // Mark ourselves as complete when done
        if (myBitfield.hasAllPieces()) completedPeers.add(myPeerId);

        if (myBitfield.hasAllPieces() && !completionLogged) {
            fileManager.writeFullFile();
            logger.info("Peer " + myPeerId + " has downloaded the complete file.");
            completionLogged = true;
            broadcastNotInterested();
            return;
        }
        maybeRequestNextPiece(peerId, out);
    }

    private void maybeRequestNextPiece(int peerId, DataOutputStream out) throws IOException {
        if (peerChokingMe.getOrDefault(peerId, true)) return;
        Bitfield peerBf = peerBitfields.get(peerId);
        if (peerBf == null || myBitfield.hasAllPieces()) return;

        int pieceToRequest = pickPiece(peerBf);
        if (pieceToRequest == -1) {
            sendMessage(out, new Message(MessageType.NOT_INTERESTED));
            return;
        }
        requestedPieces.add(pieceToRequest);
        requestedPieceOwners.put(pieceToRequest, peerId);
        sendMessage(out, Message.createRequestMessage(pieceToRequest));
    }

    private int pickPiece(Bitfield peerBf) {
        List<Integer> candidates = myBitfield.getMissingPiecesFrom(peerBf);
        Collections.shuffle(candidates);
        // First: prefer pieces not requested from anyone
        for (int p : candidates) {
            if (!requestedPieces.contains(p)) return p;
        }
        // Second: if all candidates are "requested" but the owner disconnected, re-request them
        for (int p : candidates) {
            Integer owner = requestedPieceOwners.get(p);
            if (owner == null || !outputStreams.containsKey(owner)) {
                requestedPieces.remove(p);
                requestedPieceOwners.remove(p);
                return p;
            }
        }
        return -1;
    }

    private void startChokingSchedulers() {
        scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(this::updatePreferredNeighbors,
                commonConfig.getUnchokingInterval(), commonConfig.getUnchokingInterval(), TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::updateOptimisticNeighbor,
                commonConfig.getOptimisticUnchokingInterval(), commonConfig.getOptimisticUnchokingInterval(), TimeUnit.SECONDS);
    }

    private void updatePreferredNeighbors() {
        try {
            List<Integer> candidates = new ArrayList<>(interestedPeers);
            if (candidates.isEmpty()) { preferredNeighbors.clear(); applyChokingDecisions(); return; }

            int k = Math.min(commonConfig.getNumberOfPreferredNeighbors(), candidates.size());
            if (myBitfield.hasAllPieces()) {
                Collections.shuffle(candidates);
            } else {
                candidates.sort((a, b) -> Integer.compare(
                        bytesDownloadedByPeer.getOrDefault(b, 0),
                        bytesDownloadedByPeer.getOrDefault(a, 0)));
            }

            preferredNeighbors.clear();
            for (int i = 0; i < k; i++) preferredNeighbors.add(candidates.get(i));
            bytesDownloadedByPeer.clear();
            applyChokingDecisions();

            List<Integer> sorted = new ArrayList<>(preferredNeighbors);
            Collections.sort(sorted);
            logger.info("Peer " + myPeerId + " has the preferred neighbors " + sorted.toString().replaceAll("[\\[\\]]", ""));
        } catch (Exception e) {
            logger.warning("updatePreferredNeighbors error: " + e.getMessage());
        }
    }

    private void updateOptimisticNeighbor() {
        try {
            List<Integer> candidates = new ArrayList<>();
            for (int p : interestedPeers) {
                if (!preferredNeighbors.contains(p) && iAmChokingPeer.getOrDefault(p, true)) candidates.add(p);
            }
            Collections.shuffle(candidates);
            optimisticNeighbor = candidates.isEmpty() ? null : candidates.get(0);
            applyChokingDecisions();
            if (optimisticNeighbor != null)
                logger.info("Peer " + myPeerId + " has the optimistically unchoked neighbor " + optimisticNeighbor);
        } catch (Exception e) {
            logger.warning("updateOptimisticNeighbor error: " + e.getMessage());
        }
    }

    private void applyChokingDecisions() {
        for (Map.Entry<Integer, DataOutputStream> entry : outputStreams.entrySet()) {
            int peerId = entry.getKey();
            DataOutputStream out = entry.getValue();
            boolean shouldUnchoke = preferredNeighbors.contains(peerId)
                    || (optimisticNeighbor != null && optimisticNeighbor.equals(peerId));
            boolean currentlyChoking = iAmChokingPeer.getOrDefault(peerId, true);
            try {
                if (shouldUnchoke && currentlyChoking) {
                    sendMessage(out, new Message(MessageType.UNCHOKE));
                    iAmChokingPeer.put(peerId, false);
                } else if (!shouldUnchoke && !currentlyChoking) {
                    sendMessage(out, new Message(MessageType.CHOKE));
                    iAmChokingPeer.put(peerId, true);
                }
            } catch (IOException e) {
                logger.warning("Choke/unchoke failed for Peer " + peerId + ": " + e.getMessage());
            }
        }
    }

    private void broadcastHave(int pieceIndex) {
        Message haveMsg = Message.createHaveMessage(pieceIndex);
        for (Map.Entry<Integer, DataOutputStream> e : outputStreams.entrySet()) {
            try { sendMessage(e.getValue(), haveMsg); }
            catch (IOException ex) { logger.warning("HAVE broadcast failed to Peer " + e.getKey()); }
        }
    }

    private void broadcastNotInterested() {
        Message ni = new Message(MessageType.NOT_INTERESTED);
        for (Map.Entry<Integer, DataOutputStream> e : outputStreams.entrySet()) {
            try { sendMessage(e.getValue(), ni); }
            catch (IOException ex) { /* ignore */ }
        }
    }

    private synchronized void sendMessage(DataOutputStream out, Message msg) throws IOException {
        synchronized (out) { msg.send(out); }
    }

    private void cleanupPeer(int peerId) {
        try {
            Socket s = peerConnections.remove(peerId);
            if (s != null && !s.isClosed()) s.close();
        } catch (IOException ignored) {}
        outputStreams.remove(peerId);
        inputStreams.remove(peerId);
        peerChokingMe.remove(peerId);
        iAmChokingPeer.remove(peerId);
        interestedPeers.remove(peerId);
        preferredNeighbors.remove(peerId);
        if (Integer.valueOf(peerId).equals(optimisticNeighbor)) optimisticNeighbor = null;
        List<Integer> stale = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : requestedPieceOwners.entrySet())
            if (e.getValue() == peerId) stale.add(e.getKey());
        stale.forEach(p -> { requestedPieceOwners.remove(p); requestedPieces.remove(p); });
    }

    private boolean allPeersHaveCompleteFile() {
        if (!myBitfield.hasAllPieces()) return false;
        // Check all peers in config are accounted for as complete
        for (PeerInfo pi : peerInfoConfig.getPeers()) {
            if (pi.getPeerId() == myPeerId) continue;
            int pid = pi.getPeerId();
            // Already confirmed complete via HAVE messages or disconnect-after-complete
            if (completedPeers.contains(pid)) continue;
            // Still connected - check live bitfield
            Bitfield bf = peerBitfields.get(pid);
            if (bf == null || !bf.hasAllPieces()) return false;
        }
        return true;
    }

    public void start() {
        try {
            loadConfigurations();
            initializeBitfield();
            startServer();
            Thread.sleep(500);
            connectToPeers();
            startChokingSchedulers();

            while (true) {
                Thread.sleep(1000);
                if (allPeersHaveCompleteFile()) {
                    logger.info("All peers have the complete file. Shutting down Peer " + myPeerId);
                    shutdown();
                    break;
                }
                if (myBitfield.hasAllPieces() && !completionLogged) {
                    fileManager.writeFullFile();
                    logger.info("Peer " + myPeerId + " has downloaded the complete file.");
                    completionLogged = true;
                }
            }
        } catch (Exception e) {
            logger.severe("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void shutdown() {
        try {
            for (Socket s : peerConnections.values()) if (!s.isClosed()) s.close();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            threadPool.shutdownNow();
            if (scheduler != null) scheduler.shutdownNow();
        } catch (IOException e) { logger.warning("Shutdown error: " + e.getMessage()); }
    }

    public static void main(String[] args) {
        if (args.length < 1) { System.err.println("Usage: java peerProcess <peerID>"); System.exit(1); }
        try {
            new peerProcess(Integer.parseInt(args[0])).start();
        } catch (NumberFormatException e) {
            System.err.println("Invalid peer ID: " + args[0]); System.exit(1);
        }
    }
}