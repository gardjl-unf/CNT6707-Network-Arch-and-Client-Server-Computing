#!/bin/bash

# Create files with specified sizes using fallocate
echo "Generating files with random data..."

# 1MB file
dd if=/dev/urandom of=file1MB.dat bs=1M count=1

# 25MB file
dd if=/dev/urandom of=file25MB.dat bs=1M count=25

# 50MB file
dd if=/dev/urandom of=file50MB.dat bs=1M count=50

# 100MB file
dd if=/dev/urandom of=file100MB.dat bs=1M count=100

