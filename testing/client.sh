#!/bin/bash

import socket

host = '139.62.210.102'  # Replace with the server's IP address
port = 12345          # Port number

try:
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((host, port))
    print(f"Connected to server at {host}:{port}")
except Exception as e:
    print(f"Error: {e}")
finally:
    client_socket.close()