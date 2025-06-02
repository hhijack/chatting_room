package client;

import java.io.*;
import java.net.*;
import javax.swing.*;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8888;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ClientGUI gui;
    private boolean isConnected = false;

    public ChatClient(ClientGUI gui) {
        this.gui = gui;
    }

    public boolean connect() {
        try {
            // 设置连接超时为5秒
            socket = new Socket();
            socket.connect(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT), 5000);
            
            // 设置读取超时为30秒
            socket.setSoTimeout(30000);
            
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;

            // 启动消息接收线程
            new Thread(this::receiveMessages).start();

            gui.displayMessage("Successfully connected to server.");
            return true;
        } catch (SocketTimeoutException e) {
            gui.displayMessage("Connection timed out. Please check if the server is running.");
            return false;
        } catch (ConnectException e) {
            gui.displayMessage("Could not connect to server. Please check if the server is running on port " + SERVER_PORT);
            return false;
        } catch (IOException e) {
            gui.displayMessage("Error connecting to server: " + e.getMessage() + "\nPlease check your network connection and server status.");
            return false;
        }
    }

    public boolean authenticate(String username, String password) {
        if (!isConnected) {
            gui.displayMessage("Not connected to server.");
            return false;
        }

        try {
            // 等待服务器请求用户名
            String response = in.readLine();
            if (response == null) {
                gui.displayMessage("Connection lost while waiting for server prompt.");
                disconnect();
                return false;
            }
            gui.displayMessage("Server: " + response);
            
            // 发送用户名
            out.println(username);

            // 等待服务器请求密码
            response = in.readLine();
            if (response == null) {
                gui.displayMessage("Connection lost while waiting for password prompt.");
                disconnect();
                return false;
            }
            gui.displayMessage("Server: " + response);
            
            // 发送密码
            out.println(password);

            // 检查认证结果
            response = in.readLine();
            if (response == null) {
                gui.displayMessage("Connection lost while waiting for authentication result.");
                disconnect();
                return false;
            }
            gui.displayMessage("Server: " + response);
            
            // 精确检查服务器响应
            if (response.equals("LOGIN_SUCCESS")) {
                return true;
            } else if (response.startsWith("LOGIN_FAILED")) {
                gui.displayMessage("Authentication failed: " + response.substring(response.indexOf(':') + 1).trim());
                return false;
            } else if (response.contains("Maximum login attempts exceeded")) {
                disconnect();
                return false;
            } else {
                gui.displayMessage("Unexpected server response: " + response);
                return false;
            }
        } catch (IOException e) {
            gui.displayMessage("Error during authentication: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> gui.displayMessage(finalMessage));
            }
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> gui.displayMessage("Connection to server lost."));
                disconnect();
            }
        }
    }

    public void sendMessage(String message) {
        if (isConnected && out != null) {
            out.println(message);
        }
    }

    public void setAnonymous(boolean anonymous) {
        if (isConnected && out != null) {
            out.println("@@anonymous " + anonymous);
        }
    }

    public void disconnect() {
        try {
            isConnected = false;
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            gui.displayMessage("Error disconnecting: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
