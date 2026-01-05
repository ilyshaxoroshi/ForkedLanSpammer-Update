import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

public class LanSpammer {
    public static void main(String[] args) {

        String yourIp;
        int servers;
        String motdBase;

        try (InputStream in = new FileInputStream("config.yml")) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(in);

            yourIp = (String) config.get("ip");
            servers = ((Number) config.get("servers")).intValue();
            motdBase = (String) config.get("motd");
        } catch (Exception e) {
            throw new RuntimeException("cant read config.yml", e);
        }

        String multicastAddr = "224.0.2.60";
        int port = 4445;

        Random random = new Random();
        Set<Integer> usedPorts = new HashSet<>();

        try {
            InetAddress localAddr = InetAddress.getByName(yourIp);
            InetAddress group = InetAddress.getByName(multicastAddr);
            
            MulticastSocket socket =
                    new MulticastSocket(new InetSocketAddress(localAddr, 0));
            socket.setTimeToLive(4);

            System.out.println("Started on Ip: " + yourIp);

            while (true) {
                usedPorts.clear();

                for (int i = 1; i <= servers; i++) {

                    String motd = motdBase + i;

                    int serverPort;
                    do {
                        serverPort = random.nextInt(65535 - 1024 + 1) + 1024;
                    } while (!usedPorts.add(serverPort));

                    String message =
                            "[MOTD]" + motd + "[/MOTD][AD]" + serverPort + "[/AD]";
                    
                    byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet =
                            new DatagramPacket(buffer, buffer.length, group, port);
                    
                    socket.send(packet);
                }
                Thread.sleep(1500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}