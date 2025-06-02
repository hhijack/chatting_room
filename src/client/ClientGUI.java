package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ClientGUI {
    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JPanel loginPanel;
    private JPanel chatPanel;

    private ChatClient client;

    public ClientGUI() {
        client = new ChatClient(this);
        initializeGUI();
    }

    private void initializeGUI() {
        // 创建主窗口
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);

        // 创建登录面板
        createLoginPanel();

        // 创建聊天面板
        createChatPanel();

        // 初始显示登录面板
        frame.getContentPane().add(loginPanel);
        frame.setVisible(true);
    }
    
    private void createLoginPanel() {
        loginPanel = new JPanel();
        loginPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // 用户名标签和输入框
        gbc.gridx = 0;
        gbc.gridy = 0;
        loginPanel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        usernameField = new JTextField(15);
        loginPanel.add(usernameField, gbc);
        
        // 密码标签和输入框
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        loginPanel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        passwordField = new JPasswordField(15);
        loginPanel.add(passwordField, gbc);
        
        // 登录按钮
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        loginButton = new JButton("Login");
        loginPanel.add(loginButton, gbc);
        
        // 状态消息
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JLabel statusLabel = new JLabel("Click Login to connect...");
        loginPanel.add(statusLabel, gbc);

        // 修改登录按钮行为
        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Username and password cannot be empty");
                return;
            }

            loginButton.setEnabled(false);
            statusLabel.setText("Connecting to server...");

            new Thread(() -> {
                // 首先尝试连接
                boolean connected = client.connect();
                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Failed to connect to server");
                        loginButton.setEnabled(true);
                    });
                    return;
                }

                // 然后尝试认证
                boolean authenticated = client.authenticate(username, password);
                SwingUtilities.invokeLater(() -> {
                    if (authenticated) {
                        // 切换到聊天面板
                        frame.getContentPane().removeAll();
                        frame.getContentPane().add(chatPanel);
                        frame.revalidate();
                        frame.repaint();
                        messageField.requestFocus();
                        displayMessage("Welcome to the chat room!");
                    } else {
                        statusLabel.setText("Login failed. Please check your credentials.");
                        loginButton.setEnabled(true);
                        // 断开连接，以便重新尝试
                        client.disconnect();
                    }
                });
            }).start();
        });
    }

    private void createChatPanel() {
        chatPanel = new JPanel();
        chatPanel.setLayout(new BorderLayout());

        // 聊天区域
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        // 消息输入区域
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());

        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        inputPanel.add(messageField, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        JButton anonymousButton = new JButton("Toggle Anonymous");
        anonymousButton.addActionListener(e -> {
            client.sendMessage("@@anonymous");
            displayMessage("Toggled anonymous mode");
        });
        inputPanel.add(anonymousButton, BorderLayout.WEST);

        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // 添加帮助信息
        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new BorderLayout());
        JTextArea helpText = new JTextArea(
            "Commands:\n" +
            "- Normal message: Just type and send\n" +
            "- Private message: @username message\n" +
            "- System commands: @@command\n" +
            "  Available commands: list, quit, showanonymous, anonymous"
        );
        helpText.setEditable(false);
        helpText.setBackground(new Color(240, 240, 240));
        helpPanel.add(helpText, BorderLayout.CENTER);
        chatPanel.add(helpPanel, BorderLayout.NORTH);
    }

    // handleLogin方法已移至登录按钮的ActionListener中

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && client.isConnected()) {
            client.sendMessage(message);

            // 处理系统命令
            if (message.startsWith("@@")) {
                String command = message.substring(2).toLowerCase();
                switch (command) {
                    case "quit":
                        client.disconnect();
                        frame.dispose();
                        System.exit(0);
                        break;
                    case "list":
                    case "showanonymous":
                    case "anonymous":
                        // 这些命令由服务器处理
                        break;
                }
            }

            messageField.setText("");
        }
    }

    public void displayMessage(String message) {
        chatArea.append(message + "\n");
        // 滚动到最新消息
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI());
    }
}