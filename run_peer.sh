#!/bin/bash

# Usage: ./run_peer.sh <peerID>

if [ $# -lt 1 ]; then
    echo "Usage: ./run_peer.sh <peerID>"
    exit 1
fi

PEER_ID=$1

echo "Starting peer $PEER_ID..."
java -cp bin p2p.peerProcess $PEER_ID
