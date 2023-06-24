package icu.megakite.chatswing;

import java.io.IOException;
import java.net.ServerSocket;

public class TCPServer {
    private final ServerSocket socket;

    public TCPServer(int port) throws IOException {
        this.socket = new ServerSocket(port);
    }

    public ServerSocket getSocket() {
        return socket;
    }
}
