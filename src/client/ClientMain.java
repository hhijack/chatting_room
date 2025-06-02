package client;

public class ClientMain {
    public static void main(String[] args) {
        // 在EDT (Event Dispatch Thread) 中启动客户端GUI
        javax.swing.SwingUtilities.invokeLater(() -> new ClientGUI());
    }
}
