# Function to generate a random binary file of a specified size (in bytes)
function Generate-RandomFile {
    param (
        [string]$fileName,
        [int]$fileSizeBytes
    )
    
    $random = New-Object Random
    $buffer = New-Object byte[] $fileSizeBytes
    $random.NextBytes($buffer)
    
    # Write the binary data to the file
    [System.IO.File]::WriteAllBytes($fileName, $buffer)
}

# Generate test files of various sizes
Generate-RandomFile -fileName "file1MB.dat" -fileSizeBytes 1048576  # 1MB
Generate-RandomFile -fileName "file25MB.dat" -fileSizeBytes 26214400 # 25MB
Generate-RandomFile -fileName "file50MB.dat" -fileSizeBytes 52428800 # 50MB
Generate-RandomFile -fileName "file100MB.dat" -fileSizeBytes 104857600 # 100MB
