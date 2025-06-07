import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;

/**
 * 聊天室客户端主类
 * 功能：连接服务器、用户认证、发送接收消息
 */
public class ChatClient extends JFrame {
    private static final String SERVER_HOST = "localhost"; // 服务器地址
    private static final int SERVER_PORT = 8888; // 服务器端口

    private Socket socket; // 客户端套接字
    private BufferedReader reader; // 输入流读取器
    private PrintWriter writer; // 输出流写入器
    private boolean isConnected = false; // 连接状态
    private boolean isAuthenticated = false; // 认证状态

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
        setTitle("聊天室客户端");
        setSize(700, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

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
        panel.setBorder(BorderFactory.createTitledBorder("连接设置"));
        GridBagConstraints gbc = new GridBagConstraints();

        // 用户名输入
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(new JLabel("用户名:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        usernameField = new JTextField("user1", 15);
        panel.add(usernameField, gbc);

        // 密码输入
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("密码:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        passwordField = new JPasswordField("pass1", 15);
        panel.add(passwordField, gbc);

        // 按钮
        gbc.gridx = 2; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        connectButton = new JButton("连接");
        panel.add(connectButton, gbc);

        gbc.gridy = 1;
        disconnectButton = new JButton("断开");
        panel.add(disconnectButton, gbc);

        return panel;
    }

    /**
     * 创建聊天面板
     */
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("聊天室"));

        // 聊天显示区域
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("SimSun", Font.PLAIN, 12));
        chatArea.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setPreferredSize(new Dimension(650, 350));

        // 消息输入区域
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setEnabled(false);
        sendButton = new JButton("发送");
        sendButton.setEnabled(false);

        inputPanel.add(new JLabel("消息: "), BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);

        // 添加使用说明
        JTextArea helpArea = new JTextArea(3, 0);
        helpArea.setEditable(false);
        helpArea.setBackground(getBackground());
        helpArea.setText("使用说明:\n" +
                "1. 直接输入消息发送给所有人\n" +
                "2. @用户名 消息内容 - 发送私聊\n" +
                "3. @@命令 - 系统命令(list/quit/showanonymous/anonymous)");
        panel.add(helpArea, BorderLayout.NORTH);

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
     * 设置事件处理器 - 使用传统的ActionListener避免lambda问题
     */
    private void setupEventHandlers() {
        // 连接按钮事件 - 使用匿名内部类
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectToServer();
            }
        });

        // 断开按钮事件 - 使用匿名内部类
        disconnectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnect();
            }
        });

        // 发送按钮事件 - 使用匿名内部类
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // 消息输入框回车事件 - 使用匿名内部类
        messageField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // 密码输入框回车事件 - 使用匿名内部类
        passwordField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectToServer();
            }
        });

        // 窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });
    }

    /**
     * 连接到服务器
     */
    private void connectToServer() {
        if (isConnected) {
            appendMessage("已经连接到服务器");
            return;
        }

        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            appendMessage("请输入用户名和密码");
            return;
        }

        try {
            // 建立连接
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // 等待服务器连接确认
            String response = reader.readLine();
            if ("CONNECT_SUCCESS".equals(response)) {
                isConnected = true;
                updateStatus("已连接，正在认证...");

                // 发送认证信息
                writer.println("AUTH:" + username + ":" + password);

                // 启动消息接收线程
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        receiveMessages();
                    }
                }).start();

                updateButtonStates();
            } else {
                appendMessage("连接服务器失败");
                disconnect();
            }

        } catch (IOException e) {
            appendMessage("连接服务器时出错: " + e.getMessage());
            disconnect();
        }
    }

    /**
     * 接收服务器消息的循环
     */
    private void receiveMessages() {
        try {
            String message;
            while (isConnected && (message = reader.readLine()) != null) {
                if (message.startsWith("AUTH_SUCCESS:")) {
                    // 认证成功
                    isAuthenticated = true;
                    final String welcomeMsg = message.substring(13);
                    appendMessage("系统消息: " + welcomeMsg);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("已连接并认证成功");
                            updateButtonStates();
                        }
                    });
                } else if (message.startsWith("AUTH_FAIL:")) {
                    // 认证失败
                    final String errorMsg = message.substring(10);
                    appendMessage("认证失败: " + errorMsg);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("认证失败");
                            // 如果认证失败，可以重新输入密码
                            if (!errorMsg.contains("尝试次数过多")) {
                                JOptionPane.showMessageDialog(ChatClient.this, errorMsg, "认证失败", JOptionPane.ERROR_MESSAGE);
                            } else {
                                disconnect();
                            }
                        }
                    });
                } else if (message.startsWith("MESSAGE:")) {
                    // 聊天消息
                    String chatMessage = message.substring(8);
                    appendMessage(chatMessage);
                } else if (message.startsWith("SYSTEM:")) {
                    // 系统消息
                    String systemMessage = message.substring(7);
                    appendMessage("系统消息: " + systemMessage);
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                appendMessage("接收消息时出错: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * 发送消息
     */
    private void sendMessage() {
        if (!isAuthenticated) {
            appendMessage("请先登录");
            return;
        }

        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        // 发送消息到服务器
        writer.println(message);

        // 如果是普通消息，在本地显示
        if (!message.startsWith("@")) {
            appendMessage("我: " + message);
        }

        messageField.setText("");
    }

    /**
     * 断开连接
     */
    private void disconnect() {
        isConnected = false;
        isAuthenticated = false;

        try {
            if (writer != null) {
                writer.println("@@quit");
                writer.close();
            }
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            appendMessage("断开连接时出错: " + e.getMessage());
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                updateStatus("未连接");
                updateButtonStates();
                appendMessage("已断开连接");
            }
        });
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        connectButton.setEnabled(!isConnected);
        disconnectButton.setEnabled(isConnected);
        messageField.setEnabled(isAuthenticated);
        sendButton.setEnabled(isAuthenticated);
        usernameField.setEnabled(!isConnected);
        passwordField.setEnabled(!isConnected);
    }

    /**
     * 更新状态显示
     */
    private void updateStatus(String status) {
        statusLabel.setText("状态: " + status);
    }

    /**
     * 在聊天区域添加消息
     */
    private void appendMessage(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chatArea.append(message + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    /**
     * 程序入口 - 修复UIManager调用
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    // 修复：使用正确的方法名 getSystemLookAndFeelClassName()
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                new ChatClient().setVisible(true);
            }
        });
    }
}