package icu.megakite.chatswing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class Frame extends JFrame {
    // The maximum safe UDP packet size is 508 bytes:
    // 508 = 576 (maximum packet size that is guaranteed not to be fragmented)
    //     - 60 (maximum IP header)
    //     - 8 (UDP header)
    static final int TEXT_MAX = 508;

    // Containers
    JPanel panelMeta;
    JScrollPane scrollPaneChat;
    JPanel panelMessage;

    // Styling
    JPanel gapConnect;

    // Components
    JTextField textFieldHost;
    JTextField textFieldPort;
    JButton buttonConnect;
    JTextArea textAreaChat;
    JTextField textFieldMessage;
    JButton buttonSendText;
    JButton buttonSendFile;

    // Application logic
    UDPClient udpClient;
    Thread threadText;
    TCPServer tcpServer;
    Thread threadFileReceiver;

    public Frame() {
        setTitle("Chat v0.0.1");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());
        init();
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);
    }

    @SuppressWarnings("ConstantConditions")
    void init() {
        getContentPane().setBackground(Color.WHITE);

        // 1st row: address & port settings
        GridBagConstraints gbcRoot = new GridBagConstraints();
        gbcRoot.insets = new Insets(4, 0, 4, 0);
        gbcRoot.fill = GridBagConstraints.HORIZONTAL;

        panelMeta = new JPanel(new GridBagLayout());
        panelMeta.setOpaque(false);
        gbcRoot.gridy = 0;
        gbcRoot.weighty = 0;
        add(panelMeta, gbcRoot);

        GridBagConstraints gbcPanelMeta = new GridBagConstraints();
        gbcPanelMeta.fill = GridBagConstraints.HORIZONTAL;
        gbcPanelMeta.insets = new Insets(0, 4, 0, 4);
        gbcPanelMeta.weightx = 0;

        textFieldHost = new JTextField(15);
        textFieldHost.addActionListener(this::onConnectAction);
        textFieldHost.setToolTipText("Host (IP or domain)");
        textFieldHost.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        textFieldHost.setFont(new Font(null, Font.PLAIN, 16));
        panelMeta.add(textFieldHost, gbcPanelMeta);

        textFieldPort = new JTextField(5);
        textFieldPort.addActionListener(this::onConnectAction);
        textFieldPort.setToolTipText("Base port (0-65534). The next following port is used to transfer files.");
        textFieldPort.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        textFieldPort.setFont(new Font(null, Font.PLAIN, 16));
        panelMeta.add(textFieldPort, gbcPanelMeta);

        gapConnect = new JPanel();
        gapConnect.setOpaque(false);
        gbcPanelMeta.weightx = 1;
        panelMeta.add(gapConnect, gbcPanelMeta);

        buttonConnect = new JButton(new ImageIcon(ChatSwing.class.getResource("link_FILL1_wght400_GRAD0_opsz24.png")));
        buttonConnect.addActionListener(this::onConnectAction);
        initButton(buttonConnect);
        gbcPanelMeta.weightx = 0;
        panelMeta.add(buttonConnect, gbcPanelMeta);

        // 2nd row: scrollable chat text
        textAreaChat = new JTextArea(24, 30);
        textAreaChat.setEditable(false);
        textAreaChat.setLineWrap(true);
        textAreaChat.setFont(new Font(null, Font.PLAIN, 16));

        scrollPaneChat = new JScrollPane(textAreaChat);
        scrollPaneChat.setOpaque(false);
        scrollPaneChat.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        scrollPaneChat.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        gbcRoot.gridy = 1;
        gbcRoot.weighty = 1;
        add(scrollPaneChat, gbcRoot);

        // 3rd row: send text message or file
        panelMessage = new JPanel(new GridBagLayout());
        panelMessage.setOpaque(false);
        gbcRoot.gridy = 2;
        gbcRoot.weighty = 0;
        add(panelMessage, gbcRoot);

        GridBagConstraints gbcPanelMessage = new GridBagConstraints();
        gbcPanelMessage.gridy = 0;
        gbcPanelMessage.insets = new Insets(0, 4, 0, 4);
        gbcPanelMessage.fill = GridBagConstraints.HORIZONTAL;

        textFieldMessage = new JTextField();
        textFieldMessage.addActionListener(this::onSendTextAction);
        textFieldMessage.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        textFieldMessage.setFont(new Font(null, Font.PLAIN, 16));
        gbcPanelMessage.weightx = 1;
        panelMessage.add(textFieldMessage, gbcPanelMessage);

        buttonSendText = new JButton(new ImageIcon(ChatSwing.class.getResource("send_FILL1_wght400_GRAD0_opsz24.png")));
        buttonSendText.addActionListener(this::onSendTextAction);
        initButton(buttonSendText);
        gbcPanelMessage.weightx = 0;
        panelMessage.add(buttonSendText, gbcPanelMessage);

        buttonSendFile = new JButton(new ImageIcon(ChatSwing.class.getResource("description_FILL1_wght400_GRAD0_opsz24.png")));
        buttonSendFile.addActionListener(this::onButtonSendFileAction);
        initButton(buttonSendFile);
        panelMessage.add(buttonSendFile, gbcPanelMessage);
    }

    private void initButton(JButton button) {
        button.setBorder(null);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
    }

    void onConnectAction(ActionEvent event) {
        if (textFieldPort.getText().isEmpty() || textFieldPort.getText().isEmpty()) {
            JOptionPane.showMessageDialog(getParent(), "Please specify host and port.");
            return;
        }

        // Create UDP client (text) and TCP server (file receiver)
        int basePort;
        try {
            basePort = Integer.parseUnsignedInt(textFieldPort.getText());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(getParent(), "Base port should be a non-negative whole number.");
            return;
        }
        if (basePort > 65_534) {
            JOptionPane.showMessageDialog(getParent(), "Base port should be less than or equal to 65,534.");
            return;
        }

        String host = textFieldHost.getText();
        try {
            udpClient = new UDPClient(host, basePort);
            tcpServer = new TCPServer(basePort + 1);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(getParent(), "Cannot connect to the specified host.");
            return;
        }

        // Inform user
        textAreaChat.append("Connected to " + host + " at UDP " + basePort + " and TCP " + (basePort + 1) + "\n\n");

        // Create thread for text and file operation
        if (threadText != null)
            threadText.interrupt();

        threadText = new Thread(() -> {
            byte[] buffer = new byte[TEXT_MAX];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try (DatagramSocket socket = new DatagramSocket(basePort)) {
                while (true) {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String chat = udpClient.getInetAddress() + ": \n" + message + "\n\n";
                    SwingUtilities.invokeLater(() -> textAreaChat.append(chat));
                }
            } catch (SocketException e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(getParent(),
                        "The specified host and port is not available right now. Please try another one."));
            } catch (IOException e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(getParent(),
                        "Error receiving text message: " + e.getMessage()));
            }
        }, "Text");
        threadText.setDaemon(true);
        threadText.start();

        if (threadFileReceiver != null) {
            threadFileReceiver.interrupt();
        }

        threadFileReceiver = new Thread(() -> {
            while (true) {
                long length;
                String name;

                try (Socket socket = tcpServer.getSocket().accept()) {
                    DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    // Get header
                    int nameLength = dis.readInt();
                    length = dis.readLong();
                    byte[] bytesName = new byte[nameLength];
                    int nameReceived = dis.read(bytesName);
                    if (nameReceived != nameLength)
                        throw new IOException("header is corrupted");
                    name = new String(bytesName, StandardCharsets.UTF_8);

                    // Get file content
                    long received = Files.copy(dis, new File(name).toPath());
                    if (received != length)
                        throw new IOException("file ended prematurely");

                    String chat = "Received file: " + name + " (" + length + " bytes)\n\n";
                    SwingUtilities.invokeLater(() -> textAreaChat.append(chat));
                } catch (IOException e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(getParent(),
                            "Error receiving file: " + e.getMessage()));
                }
            }
        }, "File receiver");
        threadFileReceiver.setDaemon(true);
        threadFileReceiver.start();
    }

    void onSendTextAction(ActionEvent event) {
        if (udpClient == null) {
            JOptionPane.showMessageDialog(getParent(), "Please connect to a host first.");
            return;
        }

        if (textFieldMessage.getText().isEmpty())
            return;

        try {
            udpClient.send(textFieldMessage.getText().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(getParent(),
                    "Error sending text message: " + e.getMessage());
            return;
        }

        textAreaChat.append("Me: \n" + textFieldMessage.getText() + "\n\n");
        textFieldMessage.setText(null);
    }

    // Stream format for sending & receiving files:
    // +---+-------+-------...-+---------...---+
    // | 1 |   2   |     3     |       4       |
    // +---+-------+-------...-+---------...---+
    // 1: length of file name (4 bytes integer)
    // 2: length of file content (8 bytes integer)
    // 3: file name (string in UTF-8)
    // 4: file content (raw bytes)
    void onButtonSendFileAction(ActionEvent event) {
        if (tcpServer == null) {
            JOptionPane.showMessageDialog(getParent(), "Please connect to a host first.");
            return;
        }

        JFileChooser fileChooser = new JFileChooser(".");
        fileChooser.showOpenDialog(getParent());
        File file = fileChooser.getSelectedFile();
        if (file == null)
            return;

        textAreaChat.append("Sending file: " + file.getName() + " (" + file.length() + " bytes)\n\n");

        Thread threadFileSender = new Thread(() -> {
            TCPClient tcpClient;
            try {
                tcpClient = new TCPClient(udpClient.getInetAddress(), tcpServer.getSocket().getLocalPort());
            } catch (IOException e)  {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(getParent(),
                        "Error creating TCP client for sending file: " + e.getMessage()));
                return;
            }

            try (OutputStream out = tcpClient.getSocket().getOutputStream()) {
                // Send header
                byte[] bytesName = file.getName().getBytes(StandardCharsets.UTF_8);
                int nameLength = bytesName.length;
                long contentLength = file.length();

                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(out));
                dos.writeInt(nameLength);
                dos.writeLong(contentLength);
                dos.write(bytesName);
                dos.flush();

                // Send file content
                Files.copy(file.toPath(), dos);
                dos.flush();
            } catch (IOException e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(getParent(),
                        "Error sending file: " + e.getMessage()));
                return;
            }

            SwingUtilities.invokeLater(() -> textAreaChat.append("File has been sent: " + file.getName() + " (" + file.length() + " bytes)\n\n"));
        }, "File sender");
        threadFileSender.setDaemon(true);
        threadFileSender.start();
    }
}
