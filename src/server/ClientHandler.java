package server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean isAnonymous = false;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.out.println("Error creating client handler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            // 身份验证
            if (!authenticate()) {
                return;
            }

            // 发送欢迎消息
            out.println("Welcome to the chat room, " + username + "!");
            server.broadcast(username + " has joined the chat.", this);

            // 处理客户端消息
            String message;
            while ((message = in.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        } finally {
            close();
        }
    }

    private boolean authenticate() throws IOException {
        int attempts = 0;
        final int MAX_ATTEMPTS = 3;
        final int DELAY_MS = 2000; // 2秒延迟

        while (attempts < MAX_ATTEMPTS) {
            try {
                // 如果不是第一次尝试，添加延迟
                if (attempts > 0) {
                    Thread.sleep(DELAY_MS);
                }

                // 发送用户名提示
                out.println("Please enter your username:");
                String inputUsername = in.readLine();
                
                if (inputUsername == null) {
                    logEvent("Connection lost during username input from " + socket.getInetAddress().getHostAddress());
                    return false;
                }
                
                if (inputUsername.trim().isEmpty()) {
                    logEvent("Empty username attempt from " + socket.getInetAddress().getHostAddress());
                    out.println("LOGIN_FAILED: Username cannot be empty");
                    continue;
                }

                // 发送密码提示
                out.println("Please enter your password:");
                String inputPassword = in.readLine();

                if (inputPassword == null) {
                    logEvent("Connection lost during password input from " + socket.getInetAddress().getHostAddress());
                    return false;
                }
                
                if (inputPassword.trim().isEmpty()) {
                    logEvent("Empty password attempt for user '" + inputUsername + "' from " + 
                        socket.getInetAddress().getHostAddress());
                    out.println("LOGIN_FAILED: Password cannot be empty");
                    continue;
                }

                if (server.authenticateUser(inputUsername.trim(), inputPassword.trim())) {
                    this.username = inputUsername.trim();
                    logEvent("Login successful - User: '" + username + "' from IP: " + 
                        socket.getInetAddress().getHostAddress());
                    out.println("LOGIN_SUCCESS");
                    return true;
                } else {
                    attempts++;
                    logEvent("Login failed - User: '" + inputUsername + "' from IP: " + 
                        socket.getInetAddress().getHostAddress() + " - Attempt " + attempts + " of " + MAX_ATTEMPTS);
                    
                    if (attempts < MAX_ATTEMPTS) {
                        out.println("LOGIN_FAILED: Invalid username or password. " +
                            (MAX_ATTEMPTS - attempts) + " attempts remaining.");
                    } else {
                        out.println("LOGIN_FAILED: Maximum login attempts exceeded. Connection will be closed.");
                        logEvent("Maximum login attempts exceeded for IP: " + 
                            socket.getInetAddress().getHostAddress());
                    }
                }
            } catch (InterruptedException e) {
                logEvent("Authentication interrupted for IP: " + socket.getInetAddress().getHostAddress());
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException e) {
                logEvent("IO Error during authentication from IP: " + 
                    socket.getInetAddress().getHostAddress() + " - " + e.getMessage());
                throw e;
            }
        }
        return false;
    }

    private void handleMessage(String message) {
        if (message.startsWith("@@")) {
            handleCommand(message.substring(2));
        } else if (message.startsWith("@")) {
            handlePrivateMessage(message);
        } else {
            // 广播消息
            server.broadcast((isAnonymous ? "Anonymous" : username) + ": " + message, this);
        }
    }

    private void handleCommand(String command) {
        switch (command.toLowerCase()) {
            case "list":
                sendOnlineUsers();
                break;
            case "quit":
                close();
                break;
            case "showanonymous":
                out.println("Current chat mode: " + (isAnonymous ? "Anonymous" : "Normal"));
                break;
            case "anonymous":
                isAnonymous = !isAnonymous;
                out.println("Chat mode changed to: " + (isAnonymous ? "Anonymous" : "Normal"));
                break;
            default:
                out.println("Unknown command. Available commands: list, quit, showanonymous, anonymous");
        }
    }

    private void handlePrivateMessage(String message) {
        int spaceIndex = message.indexOf(' ');
        if (spaceIndex > 1) {
            String recipient = message.substring(1, spaceIndex);
            String content = message.substring(spaceIndex + 1);
            server.privateMessage(content, recipient, this);
        } else {
            out.println("Invalid private message format. Use: @username message");
        }
    }

    private void sendOnlineUsers() {
        out.println("Online users:");
        // 实际的在线用户列表将由服务器处理
    }

    private void logEvent(String event) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("server_log.txt", true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            writer.println("[" + sdf.format(new Date()) + "] " + event);
        } catch (IOException e) {
            System.out.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getUsername() {
        return username;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.isAnonymous = anonymous;
    }

    public void close() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            server.removeClient(this);
        } catch (IOException e) {
            System.out.println("Error closing client handler: " + e.getMessage());
        }
    }
}
