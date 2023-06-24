package icu.megakite.chatswing;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class TCPClient {
    private final Socket socket;

    public TCPClient(InetAddress address, int port) throws IOException {
        this.socket = new Socket(address, port);
    }

    public Socket getSocket() {
        return socket;
    }
}
