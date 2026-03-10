#!/bin/bash

# Compile all Java files
echo "Compiling Java files..."
javac -d bin src/main/java/p2p/*.java

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Class files are in the bin/ directory"
else
    echo "Compilation failed!"
    exit 1
fi
