#!/bin/bash

# Create files with specified sizes using fallocate
echo "Generating files with non-random data..."

# Generate a 1 MB file
fallocate -l 1M file1MB.dat
echo "Generated file1MB.dat (1 MB)"

# Generate a 25 MB file
fallocate -l 25M file25MB.dat
echo "Generated file25MB.dat (25 MB)"

# Generate a 50 MB file
fallocate -l 50M file50MB.dat
echo "Generated file50MB.dat (50 MB)"

# Generate a 100 MB file
fallocate -l 100M file100MB.dat
echo "Generated file100MB.dat (100 MB)"

echo "File generation complete."
