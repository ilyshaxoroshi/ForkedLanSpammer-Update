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
        String suffixMode;

        try (InputStream in = new FileInputStream("config.yml")) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(in);

            yourIp = (String) config.get("ip");
            servers = ((Number) config.get("servers")).intValue();
            motdBase = (String) config.get("motd");
            Object modeObj = config.get("suffix-mode");
            suffixMode = (modeObj != null) ? modeObj.toString().toLowerCase() : "numbers";
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

                    String suffix;
                    switch (suffixMode) {
                        case "random":
                            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
                            StringBuilder sb = new StringBuilder(6);
                            for (int j = 0; j < 6; j++) {
                                sb.append(chars.charAt(random.nextInt(chars.length())));
                            }
                            suffix = sb.toString();
                            break;
                        case "nothing":
                            suffix = "";
                            break;
                        case "numbers":
                        default:
                            suffix = String.valueOf(i);
                            break;
                    }

                    String motdRaw = motdBase + suffix;
                    String motd = motdRaw.replace("&", "\u00A7");

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