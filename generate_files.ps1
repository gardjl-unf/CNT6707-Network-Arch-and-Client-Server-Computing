Get-Random -Minimum 0 -Maximum 255 | Out-File "file1MB.dat" -Encoding Byte -Append -Count 1048576
Get-Random -Minimum 0 -Maximum 255 | Out-File "file25MB.dat" -Encoding Byte -Append -Count 26214400
Get-Random -Minimum 0 -Maximum 255 | Out-File "file50MB.dat" -Encoding Byte -Append -Count 52428800
Get-Random -Minimum 0 -Maximum 255 | Out-File "file100MB.dat" -Encoding Byte -Append -Count 104857600