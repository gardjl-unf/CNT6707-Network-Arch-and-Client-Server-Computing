import socket

def start_server():
    host = '127.0.0.1'  # Localhost
    port = 12345        # Arbitrary port

    # Create a socket object
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((host, port))
    server_socket.listen(1)
    print(f"Server listening on {host}:{port}")

    conn, addr = server_socket.accept()
    print(f"Connection established with {addr}")

    # Receive data
    data = conn.recv(1024).decode()
    print(f"Received from client: {data}")

    # Send a response
    response = "Message received!"
    conn.send(response.encode())

    conn.close()

if __name__ == "__main__":
    start_server()
