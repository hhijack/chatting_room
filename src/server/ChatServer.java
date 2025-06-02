package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class ChatServer {
    private static final int PORT = 8888;
    private static final int MAX_CLIENTS = 5;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private Map<String, String> userCredentials = new HashMap<>();
    private Map<String, String> userAccounts = new HashMap<>();
    private static final String USER_FILE = "users.txt";
    private static final String LOG_FILE = "server_log.txt";

    public ChatServer() {
        loadUserAccounts();
    }

    private void loadUserAccounts() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    userAccounts.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading user accounts: " + e.getMessage());
            // 如果文件不存在，创建默认用户
            createDefaultUsers();
        }
    }

    private void createDefaultUsers() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_FILE))) {
            // 创建10个默认用户
            for (int i = 1; i <= 10; i++) {
                String username = "user" + i;
                String password = "pass" + i;
                userAccounts.put(username, password);
                writer.println(username + ":" + password);
            }
        } catch (IOException e) {
            System.out.println("Error creating default users: " + e.getMessage());
        }
    }

    private void logEvent(String event) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            writer.println("[" + sdf.format(new Date()) + "] " + event);
        } catch (IOException e) {
            System.out.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void start() {
        try {
            // 绑定到所有网络接口
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"));
            System.out.println("Server started successfully");
            System.out.println("Listening on all network interfaces, port " + PORT);
            System.out.println("Server IP addresses:");
            
            // 使用Java 8兼容的方式遍历网络接口
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress addr = inetAddresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        System.out.println("  - " + addr.getHostAddress());
                    }
                }
            }
            System.out.println("\nAvailable commands: list, listall, quit");

            // 启动命令处理线程
            new Thread(this::handleServerCommands).start();

            // 接受客户端连接
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connection from: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (BindException e) {
            System.err.println("Error: Could not bind to port " + PORT);
            System.err.println("Possible reasons:");
            System.err.println("1. Another program is already using port " + PORT);
            System.err.println("2. You need administrator privileges to bind to this port");
            System.err.println("3. The port is in a TIME_WAIT state from a previous execution");
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleServerCommands() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Server> ");
            String command = scanner.nextLine().trim();
            switch (command.toLowerCase()) {
                case "list":
                    listOnlineUsers();
                    break;
                case "listall":
                    listAllUsers();
                    break;
                case "quit":
                    shutdown();
                    return;
                default:
                    System.out.println("Unknown command. Available commands: list, listall, quit");
            }
        }
    }

    private void listOnlineUsers() {
        System.out.println("Online users:");
        for (ClientHandler client : clients) {
            if (client.getUsername() != null) {
                System.out.println("- " + client.getUsername() + 
                    (client.isAnonymous() ? " (Anonymous)" : ""));
            }
        }
    }

    private void listAllUsers() {
        System.out.println("All registered users:");
        for (String username : userAccounts.keySet()) {
            System.out.println("- " + username);
        }
    }

    public void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender && client.getUsername() != null) {
                if (sender != null && sender.isAnonymous()) {
                    client.sendMessage("Anonymous: " + message);
                } else {
                    client.sendMessage(message);
                }
            }
        }
    }

    public void privateMessage(String message, String recipient, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client.getUsername() != null && client.getUsername().equals(recipient)) {
                client.sendMessage("(Private from " +
                    (sender.isAnonymous() ? "Anonymous" : sender.getUsername()) +
                    "): " + message);
                return;
            }
        }
        sender.sendMessage("User '" + recipient + "' is not online.");
    }

    public boolean authenticateUser(String username, String password) {
        String storedPassword = userAccounts.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        if (client.getUsername() != null) {
            broadcast(client.getUsername() + " has left the chat.", null);
            logEvent("User " + client.getUsername() + " disconnected");
        }
    }

    public void shutdown() {
        try {
            for (ClientHandler client : new ArrayList<>(clients)) {
                client.sendMessage("Server is shutting down...");
                client.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.exit(0);
        } catch (IOException e) {
            System.out.println("Error during shutdown: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ChatServer().start();
    }
}
