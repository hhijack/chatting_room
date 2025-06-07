import java.io.*;
import java.net.Socket;

/**
 * 客户端连接处理器类
 * 功能：处理单个客户端的连接、认证、消息收发
 */
public class ClientHandler implements Runnable {
    private Socket clientSocket; // 客户端套接字
    private ChatServer server; // 服务器引用
    private BufferedReader reader; // 输入流读取器
    private PrintWriter writer; // 输出流写入器
    private String username; // 当前用户名
    private String clientIP; // 客户端IP地址
    private boolean isAuthenticated = false; // 认证状态
    private boolean isRunning = true; // 运行状态

    /**
     * 构造函数
     */
    public ClientHandler(Socket clientSocket, ChatServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.clientIP = clientSocket.getInetAddress().getHostAddress();

        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
        } catch (IOException e) {
            server.appendLog("初始化客户端处理器失败: " + e.getMessage());
        }
    }

    /**
     * 线程运行方法
     */
    @Override
    public void run() {
        try {
            // 先进行用户认证
            if (authenticate()) {
                // 认证成功后处理消息
                handleMessages();
            }
        } catch (IOException e) {
            server.appendLog("处理客户端时出错: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    /**
     * 用户认证过程
     */
    private boolean authenticate() throws IOException {
        writer.println("CONNECT_SUCCESS"); // 通知客户端连接成功

        int maxAttempts = 3; // 最大尝试次数
        int attempts = 0;

        while (attempts < maxAttempts && isRunning) {
            // 等待客户端发送认证信息
            String authMessage = reader.readLine();
            if (authMessage == null) {
                return false; // 客户端断开连接
            }

            if (authMessage.startsWith("AUTH:")) {
                String[] parts = authMessage.substring(5).split(":");
                if (parts.length == 2) {
                    String inputUsername = parts[0];
                    String inputPassword = parts[1];

                    // 验证用户名和密码
                    if (server.authenticateUser(inputUsername, inputPassword)) {
                        // 检查用户是否已经在线
                        if (server.addOnlineUser(inputUsername, this)) {
                            this.username = inputUsername;
                            this.isAuthenticated = true;
                            writer.println("AUTH_SUCCESS:登录成功！欢迎 " + username);

                            // 记录登录成功日志
                            server.logUserAction(username, "登录成功", clientIP);

                            // 通知其他用户有新用户加入
                            server.broadcastMessage("系统消息: " + username + " 加入了聊天室", username);

                            return true;
                        } else {
                            writer.println("AUTH_FAIL:该用户已在线，请使用其他账号");
                            server.logUserAction(inputUsername, "登录失败(用户已在线)", clientIP);
                        }
                    } else {
                        writer.println("AUTH_FAIL:用户名或密码错误");
                        server.logUserAction(inputUsername, "登录失败(认证失败)", clientIP);
                    }
                } else {
                    writer.println("AUTH_FAIL:认证信息格式错误");
                }
            }

            attempts++;
        }

        if (attempts >= maxAttempts) {
            writer.println("AUTH_FAIL:尝试次数过多，连接将被断开");
        }

        return false;
    }

    /**
     * 处理客户端消息
     */
    private void handleMessages() throws IOException {
        String message;
        while (isRunning && (message = reader.readLine()) != null) {
            if (message.startsWith("@@")) {
                // 系统命令处理
                handleSystemCommand(message.substring(2));
            } else if (message.startsWith("@")) {
                // 私聊消息处理
                handlePrivateMessage(message);
            } else {
                // 普通广播消息
                if (!message.trim().isEmpty()) {
                    String broadcastMessage = username + ": " + message;
                    server.broadcastMessage(broadcastMessage, username);
                }
            }
        }
    }

    /**
     * 处理系统命令
     */
    private void handleSystemCommand(String command) {
        switch (command.toLowerCase()) {
            case "list":
                writer.println("SYSTEM:" + server.getOnlineUserList());
                break;
            case "quit":
                writer.println("SYSTEM:再见！");
                isRunning = false;
                break;
            case "showanonymous":
                writer.println("SYSTEM:当前聊天方式: 实名聊天"); // 简化实现，默认实名
                break;
            case "anonymous":
                writer.println("SYSTEM:匿名聊天功能暂未实现");
                break;
            default:
                writer.println("SYSTEM:未知命令: " + command);
                writer.println("SYSTEM:可用命令: list, quit, showanonymous, anonymous");
        }
    }

    /**
     * 处理私聊消息
     */
    private void handlePrivateMessage(String message) {
        // 格式: @username message
        int spaceIndex = message.indexOf(' ');
        if (spaceIndex > 1) {
            String targetUser = message.substring(1, spaceIndex);
            String privateMessage = message.substring(spaceIndex + 1);
            server.sendPrivateMessage(privateMessage, username, targetUser);
        } else {
            writer.println("SYSTEM:私聊格式错误，正确格式: @用户名 消息内容");
        }
    }

    /**
     * 向客户端发送消息
     */
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println("MESSAGE:" + message);
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        isRunning = false;

        if (isAuthenticated && username != null) {
            server.removeOnlineUser(username);
            server.broadcastMessage("系统消息: " + username + " 离开了聊天室", username);
            server.logUserAction(username, "退出聊天室", clientIP);
        }

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            server.appendLog("关闭客户端连接时出错: " + e.getMessage());
        }
    }
}
