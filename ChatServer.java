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
    private static final int PORT = 8080; // 服务器端口 (修改为8000以上)
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
        onlineUsers = new ConcurrentHashMap<>(); // 保证线程安全
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
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // 自定义关闭操作
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(ChatServer.this,
                        "确定要关闭服务器吗?", "关闭服务器",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    stopServer();
                    System.exit(0);
                }
            }
        });
        setLocationRelativeTo(null);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 日志显示区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("SimSun", Font.PLAIN, 12)); // 使用宋体显示中文
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // 命令输入区域
        JPanel commandPanel = new JPanel(new BorderLayout());
        JLabel commandLabel = new JLabel("服务器命令 (list/listall/quit): ");
        commandField = new JTextField();
        JButton executeButton = new JButton("执行");

        commandPanel.add(commandLabel, BorderLayout.WEST);
        commandPanel.add(commandField, BorderLayout.CENTER);
        commandPanel.add(executeButton, BorderLayout.EAST);

        // 启动按钮
        JButton startButton = new JButton("启动服务器");
        JButton stopButton = new JButton("停止服务器");
        stopButton.setEnabled(false); // 初始时停止按钮不可用

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(commandPanel, BorderLayout.SOUTH);
        mainPanel.add(buttonPanel, BorderLayout.NORTH);

        add(mainPanel);

        // 事件监听器
        CommandListener cmdListener = new CommandListener();
        executeButton.addActionListener(cmdListener);
        commandField.addActionListener(cmdListener);


        startButton.addActionListener(e -> {
            startServer();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        });
        stopButton.addActionListener(e -> {
            stopServer();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    /**
     * 从文件加载用户数据
     */
    private void loadUsers() {
        File userFile = new File("users.txt");
        if (!userFile.exists()) {
            createDefaultUserFile(userFile);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(userFile), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":", 2); // Limit split to 2 parts
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    userDatabase.put(parts[0], parts[1]);
                }
            }
            appendLog("用户数据加载完成，共" + userDatabase.size() + "个用户");
        } catch (IOException e) {
            appendLog("加载用户文件失败: " + e.getMessage());
        }
    }

    /**
     * 创建默认用户文件
     */
    private void createDefaultUserFile(File userFile) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(userFile), "UTF-8"))) {
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
            writer.println("张三:zhangsan123"); // 添加中文用户名示例
            writer.println("李四:lisi456");
            appendLog("已创建默认用户文件 " + userFile.getName());
        } catch (IOException e) {
            appendLog("创建用户文件失败: " + e.getMessage());
        }
    }

    /**
     * 初始化日志文件
     */
    private void initLogFile() {
        try {
            // 使用追加模式，并指定UTF-8编码
            logWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream("server_log.txt", true), "UTF-8")));
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            logWriter.println("[" + timestamp + "] 服务器日志模块初始化");
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
            logWriter.println("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] 服务器启动成功，监听端口: " + PORT);
            logWriter.flush();

            // 在新线程中接受客户端连接
            new Thread(this::acceptClients).start();

        } catch (IOException e) {
            appendLog("服务器启动失败: " + e.getMessage());
            if (logWriter != null) {
                logWriter.println("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] 服务器启动失败: " + e.getMessage());
                logWriter.flush();
            }
        }
    }

    /**
     * 停止服务器
     */
    public void stopServer() { // Made public for CommandListener
        if (!isRunning) {
            appendLog("服务器尚未启动或已停止");
            return;
        }
        isRunning = false;
        appendLog("正在停止服务器...");
        try {
            // 通知所有客户端服务器即将关闭
            for (ClientHandler handler : onlineUsers.values()) {
                handler.sendMessage("SYSTEM:服务器即将关闭，您将断开连接。");
                handler.disconnect(); // 主动断开客户端
            }
            onlineUsers.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // 关闭服务器套接字以停止接受新连接
            }
            appendLog("服务器已停止");
            if (logWriter != null) {
                logWriter.println("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] 服务器已停止");
                logWriter.flush();
                logWriter.close(); // 关闭日志文件写入器
                logWriter = null; // 置空以便下次启动时重新初始化
            }
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
                Socket clientSocket = serverSocket.accept(); // 阻塞等待连接
                String clientIP = clientSocket.getInetAddress().getHostAddress();
                appendLog("新客户端连接请求: " + clientIP);

                // 为每个客户端创建处理线程
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();

            } catch (SocketException e) {
                if (!isRunning) { // 如果服务器已停止，这是预期的异常
                    appendLog("服务器套接字已关闭，停止接受新连接。");
                } else {
                    appendLog("接受客户端连接时发生Socket错误: " + e.getMessage());
                }
            } catch (IOException e) {
                if (isRunning) { // 只有在服务器仍在运行时才记录为错误
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
        appendLog("用户 " + username + " 登录成功，当前在线用户数: " + onlineUsers.size() + " (" + String.join(", ", onlineUsers.keySet()) + ")");
        return true;
    }

    /**
     * 移除在线用户
     */
    public synchronized void removeOnlineUser(String username) {
        if (onlineUsers.remove(username) != null) {
            appendLog("用户 " + username + " 离线，当前在线用户数: " + onlineUsers.size() + " (" + String.join(", ", onlineUsers.keySet()) + ")");
        }
    }

    /**
     * 广播消息给所有在线用户 (除了发送者自己)
     */
    public void broadcastMessage(String message, String senderUsername) {
        appendLog("广播消息 (来自 " + senderUsername + "): " + message); // 服务器日志记录广播内容
        for (Map.Entry<String, ClientHandler> entry : onlineUsers.entrySet()) {
            // 不向发送者自己广播这条消息 (例如 "XXX 加入聊天室" 或用户自己的聊天内容)
            if (!entry.getKey().equals(senderUsername)) {
                entry.getValue().sendMessage("MESSAGE:" + message);
            }
        }
    }

    /**
     * 发送私聊消息
     */
    public void sendPrivateMessage(String message, String sender, String target) {
        ClientHandler targetHandler = onlineUsers.get(target);
        ClientHandler senderHandler = onlineUsers.get(sender); // 获取发送者handler

        if (targetHandler != null) {
            targetHandler.sendMessage("MESSAGE:[私聊] " + sender + " 对你说: " + message);
            appendLog("私聊消息: 从 " + sender + " 到 " + target + ": " + message);
            // 给发送者也发送一份，确认已发送
            if (senderHandler != null && !sender.equals(target)) { // 确保不是自己给自己发私聊
                senderHandler.sendMessage("MESSAGE:[私聊] 你对 " + target + " 说: " + message);
            } else if (sender.equals(target)) { // 自己给自己发
                senderHandler.sendMessage("MESSAGE:[私聊] 你对自己说: " + message);
            }
        } else {
            // 目标用户不在线
            if (senderHandler != null) {
                senderHandler.sendMessage("SYSTEM:用户 " + target + " 不在线或不存在，私聊发送失败。");
                appendLog("私聊失败: 从 " + sender + " 到 " + target + " (用户不在线)");
            }
        }
    }

    /**
     * 获取在线用户列表
     */
    public String getOnlineUserList() {
        if (onlineUsers.isEmpty()) {
            return "当前没有在线用户。";
        }
        return "在线用户 (" + onlineUsers.size() + "): " + String.join(", ", onlineUsers.keySet());
    }

    /**
     * 获取所有注册用户列表
     */
    public String getAllUserList() {
        if (userDatabase.isEmpty()) {
            return "没有已注册的用户。";
        }
        return "所有注册用户 (" + userDatabase.size() + "): " + String.join(", ", userDatabase.keySet());
    }

    /**
     * 写入日志到界面和文件
     */
    public synchronized void appendLog(String message) {
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
    public void logUserAction(String usernameOrIP, String action, String ip) {
        // usernameOrIP 可以是用户名，也可以是在用户名未知时的IP地址
        String logMessage = "用户行为 - " + usernameOrIP + ": " + action + " (IP: " + ip + ")";
        appendLog(logMessage); // appendLog 会处理时间戳和写入文件
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

            appendLog("服务器接收到命令: " + command); // 在日志中显示命令
            switch (command.toLowerCase()) {
                case "list":
                    appendLog(getOnlineUserList());
                    break;
                case "listall":
                    appendLog(getAllUserList());
                    break;
                case "quit":
                    // stopServer(); // 停止服务器
                    // System.exit(0); // 退出程序
                    // 改为模拟点击窗口关闭按钮的行为，以便触发确认对话框
                    ChatServer.this.dispatchEvent(new java.awt.event.WindowEvent(ChatServer.this, java.awt.event.WindowEvent.WINDOW_CLOSING));
                    break;
                default:
                    appendLog("未知服务器命令: " + command + "。可用命令: list, listall, quit");
            }
        }
    }

    /**
     * 程序入口
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("无法设置系统外观: " + e.getMessage());
            }
            new ChatServer().setVisible(true);
        });
    }
}