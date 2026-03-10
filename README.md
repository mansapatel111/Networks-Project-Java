# BitTorrent-Style P2P File Sharing System

A peer-to-peer file sharing system implemented in Java, similar to BitTorrent, for a networking course project.

## Project Overview

This system allows multiple peers to share a file efficiently using a distributed protocol. Each peer can act as both a client and a server, downloading pieces from other peers while simultaneously uploading pieces to others.

### Key Features

- **TCP Socket Communication**: Peers communicate using TCP sockets
- **Handshake Protocol**: Custom 32-byte handshake for peer identification
- **Bitfield Exchange**: Efficient tracking of which pieces each peer has
- **Message Protocol**: Eight message types for piece exchange and coordination
- **Choking Algorithm**: Implements preferred neighbors and optimistic unchoking
- **Logging**: Comprehensive logging of all peer activities

## Project Structure

```
Networks-Project-Java/
├── src/main/java/p2p/
│   ├── peerProcess.java          # Main entry point
│   ├── CommonConfig.java         # Parses Common.cfg
│   ├── PeerInfo.java             # Peer information data class
│   ├── PeerInfoConfig.java       # Parses PeerInfo.cfg
│   ├── MessageType.java          # Message type enumeration
│   ├── Message.java              # Message serialization/deserialization
│   ├── Handshake.java            # Handshake protocol implementation
│   └── Bitfield.java             # Bitfield tracking pieces
├── Common.cfg                    # Common configuration file
├── PeerInfo.cfg                  # Peer information file
├── compile.sh                    # Compilation script
└── run_peer.sh                   # Script to run a peer
```

## Configuration Files

### Common.cfg

Contains system-wide configuration parameters:

```
NumberOfPreferredNeighbors 2
UnchokingInterval 5
OptimisticUnchokingInterval 15
FileName TheFile.dat
FileSize 10000232
PieceSize 32768
```

- **NumberOfPreferredNeighbors**: Number of preferred neighbors to unchoke
- **UnchokingInterval**: Interval (seconds) for updating preferred neighbors
- **OptimisticUnchokingInterval**: Interval (seconds) for optimistic unchoking
- **FileName**: Name of the file to share
- **FileSize**: Total size of the file in bytes
- **PieceSize**: Size of each piece in bytes

### PeerInfo.cfg

Contains information about all peers in the network:

```
1001 localhost 6008 1
1002 localhost 6009 0
1003 localhost 6010 0
1004 localhost 6011 0
```

Format: `peerID hostName port hasFile`

- **peerID**: Unique identifier for the peer
- **hostName**: Hostname or IP address
- **port**: Port number the peer listens on
- **hasFile**: 1 if peer has the complete file initially, 0 otherwise

## Protocol Specification

### Handshake Protocol

- **Size**: 32 bytes
- **Format**:
  - 18 bytes: "P2PFILESHARINGPROJ"
  - 10 bytes: zero padding
  - 4 bytes: peer ID (integer)

### Message Protocol

Each message consists of:
- **4 bytes**: message length (type + payload)
- **1 byte**: message type
- **N bytes**: payload (optional)

#### Message Types

| Type | Value | Description | Payload |
|------|-------|-------------|---------|
| CHOKE | 0 | Sender is choking the receiver | None |
| UNCHOKE | 1 | Sender is unchoking the receiver | None |
| INTERESTED | 2 | Sender is interested | None |
| NOT_INTERESTED | 3 | Sender is not interested | None |
| HAVE | 4 | Sender has a piece | 4-byte piece index |
| BITFIELD | 5 | Sender's bitfield | Bitfield bytes |
| REQUEST | 6 | Request a piece | 4-byte piece index |
| PIECE | 7 | Sending a piece | 4-byte index + data |

## Connection Protocol

1. **Initialization**: Each peer reads configuration files
2. **Server Setup**: Each peer starts a ServerSocket on its port
3. **Client Connections**: Each peer connects to all peers listed before it in PeerInfo.cfg
4. **Handshake**: After TCP connection, peers exchange handshake messages
5. **Bitfield Exchange**: Peers exchange BITFIELD messages
6. **Interest Determination**: Peers send INTERESTED or NOT_INTERESTED based on available pieces
7. **File Transfer**: Transfer begins based on choking/unchoking decisions

## Building and Running

### Compilation

Compile all Java files:

```bash
./compile.sh
```

Or manually:

```bash
mkdir -p bin
javac -d bin src/main/java/p2p/*.java
```

### Running Peers

Start each peer in a separate terminal:

```bash
./run_peer.sh 1001
./run_peer.sh 1002
./run_peer.sh 1003
./run_peer.sh 1004
```

Or manually:

```bash
java -cp bin p2p.peerProcess 1001
```

### Running Order

1. Start all peers with `hasFile = 0` first
2. Then start the peer with `hasFile = 1`
3. Observe the file transfer through log files

## Logging

Each peer creates a log file: `log_peer_<peerID>.log`

Example log entries:
```
Peer 1002 makes a connection to Peer 1001
Peer 1002 is unchoked by Peer 1001
Peer 1002 received the piece 5 from Peer 1001
Peer 1002 has downloaded the complete file
```

## Implementation Details

### Core Classes

#### peerProcess.java
- Main entry point for each peer
- Handles TCP connections (both client and server)
- Coordinates handshake and message exchange
- Manages peer state and bitfields

#### CommonConfig.java
- Parses Common.cfg file
- Validates configuration parameters
- Calculates piece count: `ceil(FileSize / PieceSize)`

#### PeerInfoConfig.java
- Parses PeerInfo.cfg file
- Provides list of peers to connect to
- Peer connects only to peers appearing before it in the file

#### Handshake.java
- Creates and parses 32-byte handshake messages
- Validates protocol header
- Extracts peer ID from handshake

#### Message.java
- Serializes and deserializes protocol messages
- Factory methods for creating specific message types
- Helper methods for extracting payload data

#### Bitfield.java
- Tracks which pieces a peer has using Java BitSet
- Determines if peer is interested in another peer
- Selects random missing pieces for requests
- Tracks download completion percentage

#### MessageType.java
- Enumeration of all message types
- Maps byte values to message types
- Indicates which messages require payloads

#### PeerInfo.java
- Data class representing a peer
- Contains peer ID, hostname, port, and initial file status

## Next Steps

The current implementation includes:
- ✅ Configuration parsing
- ✅ TCP connection setup
- ✅ Handshake protocol
- ✅ Message serialization/deserialization
- ✅ Bitfield tracking
- ✅ Basic message handling
- ✅ Logging infrastructure

To complete the full system, implement:
- [ ] **FileManager.java**: Handle actual file I/O and piece storage
- [ ] **PeerState.java**: Track download rates, choked/unchoked status
- [ ] **NeighborState.java**: Track state of each neighbor
- [ ] **ConnectionHandler.java**: Manage individual peer connections
- [ ] **ServerListener.java**: Handle incoming connections
- [ ] **ChokingManager.java**: Implement choking algorithm with schedulers
- [ ] **LoggerUtil.java**: Enhanced logging utilities
- [ ] Piece request/download logic
- [ ] Piece upload logic when unchoked
- [ ] Preferred neighbor selection
- [ ] Optimistic unchoking
- [ ] File completion detection

## Testing

1. **Single Seeder Test**: Start one peer with the file and multiple peers without
2. **Multi-Seeder Test**: Start multiple peers with the file
3. **Large File Test**: Test with different file sizes and piece sizes
4. **Network Test**: Test across different machines (update PeerInfo.cfg with real hostnames)

## Requirements

- Java 8 or higher
- JDK with javac compiler
- Unix-like environment (for shell scripts) or Windows with Git Bash

## Troubleshooting

**Port Already in Use**: Ensure no other process is using the ports specified in PeerInfo.cfg

**Connection Refused**: Make sure the peer you're connecting to has started its server socket

**Handshake Failed**: Verify that all peers are using the same protocol version

**File Not Found**: Ensure Common.cfg and PeerInfo.cfg are in the working directory

## Author

Networking Course Project - 2026

## License

Academic project - for educational purposes only
