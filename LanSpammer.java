
import java.net.*;
import java.nio.charset.StandardCharsets;

public class LanSpammer {
    public static void main(String[] args) {
        // YOUR IP
        String yourIp = "26.30.15.74"; 
        String multicastAddr = "224.0.2.60";
        int port = 4445;

        try {
            InetAddress localAddr = InetAddress.getByName(yourIp);
            InetAddress group = InetAddress.getByName(multicastAddr);
            
            MulticastSocket socket = new MulticastSocket(new InetSocketAddress(localAddr, 0));
            socket.setTimeToLive(4);

            System.out.println("Started on Ip: " + yourIp);

            while (true) { // 10 - COUNT OF SERVERS
                for (int i = 1; i <= 10; i++) {
					// MOTD
                    String motd = "hawwlkxd52 и Elisey два без мамных пидора у которых 2 отца " + i;
					// PORT
                    int serverPort = 25560 + i;
                    String message = "[MOTD]" + motd + "[/MOTD][AD]" + serverPort + "[/AD]";
                    
                    byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
                    
                    socket.send(packet);
                }
                Thread.sleep(1500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}