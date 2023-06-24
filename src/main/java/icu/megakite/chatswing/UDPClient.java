package icu.megakite.chatswing;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UDPClient {
    private final InetAddress inetAddress;
    private final int port;

    public UDPClient(String host, int port) throws UnknownHostException {
        this.inetAddress = InetAddress.getByName(host);
        this.port = port;
    }

    public void send(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, inetAddress, port);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(packet);
        }
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }
}
