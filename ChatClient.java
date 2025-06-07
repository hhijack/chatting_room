import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;

/**
 * 聊天室客户端主类
 * 功能：连接服务器、用户认证、发送接收消息
 */
public class ChatClient extends JFrame {
    private static final String SERVER_HOST = "localhost"; // 服务器地址
    private static final int SERVER_PORT = 8080; // 服务器端口 (与服务器一致)

    private Socket socket; // 客户端套接字
    private BufferedReader reader; // 输入流读取器
    private PrintWriter writer; // 输出流写入器
    private volatile boolean isConnected = false; // 连接状态 (volatile)
    private volatile boolean isAuthenticated = false; // 认证状态 (volatile)
    private String currentUsername = null; // 当前用户名

    // GUI组件
    private JTextArea chatArea; // 聊天显示区域
    private JTextField messageField; // 消息输入框
    private JButton sendButton; // 发送按钮
    private JButton connectButton; // 连接按钮
    private JButton disconnectButton; // 断开连接按钮
    private JTextField usernameField; // 用户名输入框
    private JPasswordField passwordField; // 密码输入框
    private JLabel statusLabel; // 状态标签

    /**
     * 构造函数：初始化客户端界面
     */
    public ChatClient() {
        initGUI();
        setupEventHandlers();
    }

    /**
     * 初始化图形用户界面
     */
    private void initGUI() {
        setTitle("简易聊天室客户端");
        setSize(700, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // 自定义关闭操作
        setLocationRelativeTo(null);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10)); // 添加间距
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // 整体边距

        // 顶部连接面板
        JPanel connectionPanel = createConnectionPanel();

        // 中部聊天面板
        JPanel chatPanel = createChatPanel();

        // 底部状态面板
        JPanel statusPanel = createStatusPanel();

        mainPanel.add(connectionPanel, BorderLayout.NORTH);
        mainPanel.add(chatPanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // 初始状态设置
        updateButtonStates();
    }

    /**
     * 创建连接面板
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("连接与认证"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 组件间距

        // 用户名输入
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("用户名:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.8;
        usernameField = new JTextField("user1", 15);
        panel.add(usernameField, gbc);

        // 密码输入
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("密码:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        passwordField = new JPasswordField("pass1", 15);
        panel.add(passwordField, gbc);

        // 按钮面板
        JPanel buttonSubPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        connectButton = new JButton("连接认证");
        disconnectButton = new JButton("断开连接");
        buttonSubPanel.add(connectButton);
        buttonSubPanel.add(disconnectButton);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(buttonSubPanel, gbc);


        return panel;
    }

    /**
     * 创建聊天面板
     */
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(5,5));
        panel.setBorder(BorderFactory.createTitledBorder("聊天窗口"));

        // 聊天显示区域
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("SimSun", Font.PLAIN, 14)); // 宋体，稍大字号
        chatArea.setLineWrap(true); // 自动换行
        chatArea.setWrapStyleWord(true); // 按单词换行
        chatArea.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // 消息输入区域
        JPanel inputPanel = new JPanel(new BorderLayout(5,0));
        messageField = new JTextField();
        sendButton = new JButton("发送");

        inputPanel.add(new JLabel("消息: "), BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // 使用说明区域
        JTextArea helpArea = new JTextArea(4, 0); // 增加行数以容纳更多文本
        helpArea.setEditable(false);
        helpArea.setFont(new Font("SimSun", Font.PLAIN, 12));
        helpArea.setBackground(UIManager.getColor("Panel.background")); // 与面板背景色一致
        helpArea.setText("聊天提示符 (输入框):\n" +
                "1. 直接输入: 广播消息 (所有人可见)\n" +
                "2. @用户名 消息: 私聊指定用户\n" +
                "3. @@命令: 执行系统命令 (list, quit, showanonymous, anonymous)");
        helpArea.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));


        panel.add(helpArea, BorderLayout.NORTH); // 说明放上面
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);


        return panel;
    }

    /**
     * 创建状态面板
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("状态: 未连接");
        panel.add(statusLabel);
        return panel;
    }

    /**
     * 设置事件处理器
     */
    private void setupEventHandlers() {
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer()); // Renamed for clarity
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        passwordField.addActionListener(e -> { // 回车认证
            if (!isConnected) connectToServer();
        });
        usernameField.addActionListener(e -> { // 回车认证
            if (!isConnected) connectToServer();
        });


        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isConnected) {
                    if (JOptionPane.showConfirmDialog(ChatClient.this,
                            "您已连接到服务器，确定要退出吗?", "退出确认",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                        disconnectFromServer();
                        System.exit(0);
                    }
                } else {
                    System.exit(0);
                }
            }
        });
    }

    /**
     * 连接到服务器并认证
     */
    private void connectToServer() {
        if (isConnected) {
            appendMessageToChatArea("提示: 您已经连接到服务器。");
            return;
        }

        final String username = usernameField.getText().trim();
        final String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "用户名和密码不能为空！", "输入错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        this.currentUsername = username; // 暂存用户名

        // 在新线程中执行网络操作，避免阻塞GUI
        new Thread(() -> {
            try {
                updateStatus("正在连接服务器 " + SERVER_HOST + ":" + SERVER_PORT + "...");
                socket = new Socket(SERVER_HOST, SERVER_PORT);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                String serverResponse = reader.readLine();
                if ("CONNECT_SUCCESS".equals(serverResponse)) {
                    isConnected = true; // 标记为已连接，但尚未认证
                    appendMessageToChatArea("系统: 成功连接到服务器，等待认证...");
                    updateStatus("已连接，正在认证...");

                    // 发送认证信息
                    writer.println("AUTH:" + username + ":" + password);
                    // 认证结果将在 receiveMessages 线程中处理
                    new Thread(this::receiveMessages).start(); // 启动消息接收线程
                } else {
                    appendMessageToChatArea("系统: 连接服务器失败。服务器响应: " + serverResponse);
                    updateStatus("连接失败");
                    cleanUpConnection(); // 清理资源
                }
            } catch (IOException ex) {
                appendMessageToChatArea("系统: 连接服务器时出错 - " + ex.getMessage());
                updateStatus("连接错误");
                cleanUpConnection();
            } finally {
                SwingUtilities.invokeLater(this::updateButtonStates);
            }
        }).start();
    }

    /**
     * 接收服务器消息的循环
     */
    private void receiveMessages() {
        try {
            String messageFromServer;
            while (isConnected && (messageFromServer = reader.readLine()) != null) {
                final String finalMessage = messageFromServer; // For lambda
                if (finalMessage.startsWith("AUTH_SUCCESS:")) {
                    isAuthenticated = true;
                    String welcomeMsg = finalMessage.substring(13);
                    appendMessageToChatArea("系统: " + welcomeMsg);
                    updateStatus("已认证: " + currentUsername);
                } else if (finalMessage.startsWith("AUTH_FAIL:")) {
                    isAuthenticated = false;
                    String errorMsg = finalMessage.substring(10);
                    appendMessageToChatArea("系统: 认证失败 - " + errorMsg);
                    updateStatus("认证失败");
                    // 如果认证失败是因为尝试次数过多或用户已在线，则断开连接
                    if (errorMsg.contains("尝试次数过多") || errorMsg.contains("用户已在线")) {
                        disconnectFromServer(); // 服务器会主动断开，客户端也应清理
                    } else {
                        // 允许用户修改用户名密码后重试
                        JOptionPane.showMessageDialog(this, "认证失败: " + errorMsg + "\n请检查您的用户名和密码后重试。", "认证失败", JOptionPane.ERROR_MESSAGE);
                        // 不需要主动断开，用户可以修改信息后再次点击连接认证
                        // 但服务器可能会在几次失败后主动断开，所以这里要小心处理
                        // isConnected 状态可能已经被服务器端断开而改变
                    }
                } else if (finalMessage.startsWith("AUTH_RETRY:")) {
                    String retryMsg = finalMessage.substring(11);
                    appendMessageToChatArea("系统: " + retryMsg);
                    updateStatus("认证中，请重试...");
                }
                else if (finalMessage.startsWith("MESSAGE:")) {
                    appendMessageToChatArea(finalMessage.substring(8));
                } else if (finalMessage.startsWith("SYSTEM:")) {
                    appendMessageToChatArea("系统消息: " + finalMessage.substring(7));
                } else {
                    appendMessageToChatArea("服务器原始消息: " + finalMessage); // 未知格式
                }
                SwingUtilities.invokeLater(this::updateButtonStates); // 每次收到消息都可能影响状态
            }
        } catch (SocketException e) {
            if (isConnected) { // 只有在期望连接时才报告错误
                appendMessageToChatArea("系统: 与服务器的连接中断 - " + e.getMessage());
            }
        } catch (IOException e) {
            if (isConnected) {
                appendMessageToChatArea("系统: 接收消息时出错 - " + e.getMessage());
            }
        } finally {
            // 如果循环退出（例如服务器关闭连接或readLine返回null），则执行断开逻辑
            if (isConnected) { // 避免在已经手动断开时重复执行
                appendMessageToChatArea("系统: 与服务器连接已断开。");
                disconnectFromServer(); // 确保资源被清理
            }
        }
    }


    /**
     * 发送消息
     */
    private void sendMessage() {
        if (!isAuthenticated) {
            JOptionPane.showMessageDialog(this, "您尚未通过认证，不能发送消息。", "未认证", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        writer.println(message); // 发送原始消息，服务器会处理格式

        // 如果是普通广播消息 (非命令，非私聊)，在本地立即显示 "我: ..."
        if (!message.startsWith("@") && !message.startsWith("@@")) {
            appendMessageToChatArea("我: " + message);
        }
        // 私聊和命令消息，等待服务器回显或响应

        messageField.setText("");
        messageField.requestFocusInWindow(); // 让输入框重新获得焦点
    }

    /**
     * 断开与服务器的连接
     */
    private void disconnectFromServer() {
        if (!isConnected && !isAuthenticated) { // 如果本来就没连上或没认证，直接返回
            // appendMessageToChatArea("系统: 当前未连接或未认证。");
            // return;
        }

        if (writer != null) {
            writer.println("@@quit"); // 通知服务器客户端主动退出
        }
        cleanUpConnection();
        currentUsername = null; // 清除当前用户名
        appendMessageToChatArea("系统: 您已断开与服务器的连接。");
        updateStatus("未连接");
        SwingUtilities.invokeLater(this::updateButtonStates);
    }

    /**
     * 清理连接资源
     */
    private void cleanUpConnection() {
        isConnected = false;
        isAuthenticated = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // appendMessageToChatArea("系统: 清理连接资源时出错 - " + e.getMessage());
            System.err.println("清理连接资源时出错: " + e.getMessage());
        } finally {
            reader = null;
            writer = null;
            socket = null;
        }
    }


    /**
     * 更新按钮和输入框状态
     */
    private void updateButtonStates() {
        connectButton.setEnabled(!isAuthenticated); // 只要没认证成功，就可以点连接/认证
        disconnectButton.setEnabled(isConnected); // 只要连接上了（无论是否认证），就可以点断开

        usernameField.setEnabled(!isAuthenticated); // 认证成功后不应修改用户名
        passwordField.setEnabled(!isAuthenticated); // 认证成功后不应修改密码

        messageField.setEnabled(isAuthenticated);
        sendButton.setEnabled(isAuthenticated);

        if (isAuthenticated) {
            messageField.requestFocusInWindow();
        }
    }

    /**
     * 更新状态栏标签
     */
    private void updateStatus(final String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("状态: " + status));
    }

    /**
     * 在聊天区域添加消息 (确保在EDT线程执行)
     */
    private void appendMessageToChatArea(final String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength()); // 自动滚动到底部
        });
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
            new ChatClient().setVisible(true);
        });
    }
}