import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天室服务器端主类
 * 功能：管理用户连接、验证用户身份、转发消息、记录日志
 */
public class ChatServer extends JFrame {
    private static final int PORT = 8888; // 服务器端口
    private ServerSocket serverSocket; // 服务器套接字
    private Map<String, String> userDatabase; // 用户数据库：用户名->密码
    private Map<String, ClientHandler> onlineUsers; // 在线用户：用户名->处理器
    private JTextArea logArea; // 日志显示区域
    private JTextField commandField; // 命令输入框
    private PrintWriter logWriter; // 日志文件写入器
    private boolean isRunning = false; // 服务器运行状态

    /**
     * 构造函数：初始化服务器界面和数据结构
     */
    public ChatServer() {
        userDatabase = new HashMap<>();
        onlineUsers = new ConcurrentHashMap<>();
        initGUI();
        loadUsers();
        initLogFile();
    }

    /**
     * 初始化图形用户界面
     */
    private void initGUI() {
        setTitle("聊天室服务器");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 日志显示区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("SimSun", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // 命令输入区域
        JPanel commandPanel = new JPanel(new BorderLayout());
        JLabel commandLabel = new JLabel("命令输入 (list/listall/quit): ");
        commandField = new JTextField();
        JButton executeButton = new JButton("执行");

        commandPanel.add(commandLabel, BorderLayout.WEST);
        commandPanel.add(commandField, BorderLayout.CENTER);
        commandPanel.add(executeButton, BorderLayout.EAST);

        // 启动按钮
        JButton startButton = new JButton("启动服务器");
        JButton stopButton = new JButton("停止服务器");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(commandPanel, BorderLayout.SOUTH);
        mainPanel.add(buttonPanel, BorderLayout.NORTH);

        add(mainPanel);

        // 事件监听器
        executeButton.addActionListener(new CommandListener());
        commandField.addActionListener(new CommandListener());

        // 修复lambda表达式问题：使用final引用
        final ChatServer serverRef = this;
        startButton.addActionListener(e -> serverRef.startServer());
        stopButton.addActionListener(e -> serverRef.stopServer());
    }

    /**
     * 从文件加载用户数据
     */
    private void loadUsers() {
        try {
            // 如果用户文件不存在，创建默认用户文件
            File userFile = new File("users.txt");
            if (!userFile.exists()) {
                createDefaultUserFile();
            }

            BufferedReader reader = new BufferedReader(new FileReader("users.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":");
                if (parts.length == 2) {
                    userDatabase.put(parts[0], parts[1]);
                }
            }
            reader.close();
            appendLog("用户数据加载完成，共" + userDatabase.size() + "个用户");
        } catch (IOException e) {
            appendLog("加载用户文件失败: " + e.getMessage());
        }
    }

    /**
     * 创建默认用户文件
     */
    private void createDefaultUserFile() {
        try {
            PrintWriter writer = new PrintWriter(new FileWriter("users.txt"));
            writer.println("user1:pass1");
            writer.println("user2:pass2");
            writer.println("user3:pass3");
            writer.println("user4:pass4");
            writer.println("user5:pass5");
            writer.println("user6:pass6");
            writer.println("user7:pass7");
            writer.println("user8:pass8");
            writer.println("user9:pass9");
            writer.println("user10:pass10");
            writer.println("admin:admin");
            writer.println("test:test");
            writer.close();
            appendLog("已创建默认用户文件users.txt");
        } catch (IOException e) {
            appendLog("创建用户文件失败: " + e.getMessage());
        }
    }

    /**
     * 初始化日志文件
     */
    private void initLogFile() {
        try {
            logWriter = new PrintWriter(new FileWriter("server_log.txt", true));
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            logWriter.println("[" + timestamp + "] 服务器启动");
            logWriter.flush();
        } catch (IOException e) {
            appendLog("初始化日志文件失败: " + e.getMessage());
        }
    }

    /**
     * 启动服务器
     */
    private void startServer() {
        if (isRunning) {
            appendLog("服务器已经在运行中");
            return;
        }

        try {
            serverSocket = new ServerSocket(PORT);
            isRunning = true;
            appendLog("服务器在端口 " + PORT + " 启动成功");

            // 在新线程中接受客户端连接
            new Thread(this::acceptClients).start();

        } catch (IOException e) {
            appendLog("服务器启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止服务器
     */
    private void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            // 断开所有客户端连接
            for (ClientHandler handler : onlineUsers.values()) {
                handler.disconnect();
            }
            onlineUsers.clear();
            appendLog("服务器已停止");
        } catch (IOException e) {
            appendLog("停止服务器时出错: " + e.getMessage());
        }
    }

    /**
     * 接受客户端连接的循环
     */
    private void acceptClients() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientIP = clientSocket.getInetAddress().getHostAddress();
                appendLog("新客户端连接: " + clientIP);

                // 为每个客户端创建处理线程
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();

            } catch (IOException e) {
                if (isRunning) {
                    appendLog("接受客户端连接时出错: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 验证用户登录
     */
    public boolean authenticateUser(String username, String password) {
        return userDatabase.containsKey(username) && userDatabase.get(username).equals(password);
    }

    /**
     * 用户登录成功后添加到在线用户列表
     */
    public synchronized boolean addOnlineUser(String username, ClientHandler handler) {
        if (onlineUsers.containsKey(username)) {
            return false; // 用户已在线
        }
        onlineUsers.put(username, handler);
        appendLog("用户 " + username + " 登录成功，当前在线用户: " + onlineUsers.size());
        return true;
    }

    /**
     * 移除在线用户
     */
    public synchronized void removeOnlineUser(String username) {
        if (onlineUsers.remove(username) != null) {
            appendLog("用户 " + username + " 离线，当前在线用户: " + onlineUsers.size());
        }
    }

    /**
     * 广播消息给所有在线用户
     */
    public void broadcastMessage(String message, String sender) {
        for (Map.Entry<String, ClientHandler> entry : onlineUsers.entrySet()) {
            if (!entry.getKey().equals(sender)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    /**
     * 发送私聊消息
     */
    public void sendPrivateMessage(String message, String sender, String target) {
        ClientHandler targetHandler = onlineUsers.get(target);
        if (targetHandler != null) {
            targetHandler.sendMessage("[私聊]" + sender + ": " + message);
            // 给发送者确认消息
            ClientHandler senderHandler = onlineUsers.get(sender);
            if (senderHandler != null) {
                senderHandler.sendMessage("[私聊给" + target + "]: " + message);
            }
        } else {
            // 目标用户不在线
            ClientHandler senderHandler = onlineUsers.get(sender);
            if (senderHandler != null) {
                senderHandler.sendMessage("系统消息: 用户 " + target + " 不在线");
            }
        }
    }

    /**
     * 获取在线用户列表
     */
    public String getOnlineUserList() {
        return "在线用户: " + String.join(", ", onlineUsers.keySet());
    }

    /**
     * 获取所有用户列表
     */
    public String getAllUserList() {
        return "所有用户: " + String.join(", ", userDatabase.keySet());
    }

    /**
     * 写入日志到界面和文件
     */
    public void appendLog(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logMessage = "[" + timestamp + "] " + message;

        SwingUtilities.invokeLater(() -> {
            logArea.append(logMessage + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });

        if (logWriter != null) {
            logWriter.println(logMessage);
            logWriter.flush();
        }
    }

    /**
     * 记录用户行为日志
     */
    public void logUserAction(String username, String action, String ip) {
        String logMessage = "用户行为 - " + username + ": " + action + " (IP: " + ip + ")";
        appendLog(logMessage);
    }

    /**
     * 命令监听器内部类
     */
    private class CommandListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String command = commandField.getText().trim();
            commandField.setText("");

            if (command.isEmpty()) return;

            switch (command.toLowerCase()) {
                case "list":
                    appendLog(getOnlineUserList());
                    break;
                case "listall":
                    appendLog(getAllUserList());
                    break;
                case "quit":
                    stopServer();
                    System.exit(0);
                    break;
                default:
                    appendLog("未知命令: " + command + "。可用命令: list, listall, quit");
            }
        }
    }

    /**
     * 程序入口 - 修复UIManager调用
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 修复：使用正确的方法名 getSystemLookAndFeelClassName()
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ChatServer().setVisible(true);
        });
    }
}