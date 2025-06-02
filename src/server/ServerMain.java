package server;

public class ServerMain {
    public static void main(String[] args) {
        System.out.println("正在启动服务器...");
        System.out.println("服务器命令：");
        System.out.println("- list：列出在线用户");
        System.out.println("- listall：列出所有注册用户");
        System.out.println("- quit：关闭服务器");
        new ChatServer().start();
    }
}
