import socket

def start_client():
    host = '139.62.210.102'  # Server's IP address
    port = 12345        # Server's port

    # Create a socket object
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((host, port))
    print "Connected to server at {}:{}".format(host, port)

    # Send a message
    message = "Hello, Server!"
    client_socket.send(message)
    print "Sent to server: {}".format(message)

    # Receive a response
    response = client_socket.recv(1024)
    print "Received from server: {}".format(response)

    client_socket.close()

if __name__ == "__main__":
    start_client()
