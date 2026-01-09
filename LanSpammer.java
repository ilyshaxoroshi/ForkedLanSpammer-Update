import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public class LanSpammer {
    public static void main(String[] args) {

        String yourIp = "0.0.0.0";
        int servers = 10;
        List<String> motds = new ArrayList<>();
        String suffixMode = "numbers";

        try (BufferedReader br = new BufferedReader(new FileReader("config.yml"))) {
            boolean inMotds = false;
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("motds:")) {
                    inMotds = true;
                    continue;
                }

                if (inMotds) {
                    if (line.startsWith("- ")) {
                        String motd = line.substring(2).trim();
                        if (motd.startsWith("\"") && motd.endsWith("\"")) {
                            motd = motd.substring(1, motd.length() - 1);
                        }
                        motds.add(motd);
                    } else if (!line.startsWith(" ") && !line.startsWith("\t")) {
                        inMotds = false;
                    }
                }

                if (!inMotds && line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    switch (key) {
                        case "ip":
                            yourIp = value;
                            break;
                        case "servers":
                            servers = Integer.parseInt(value);
                            break;
                        case "suffix-mode":
                            suffixMode = value.toLowerCase();
                            break;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("cant read or parse config.yml", e);
        }

        if (motds.isEmpty()) {
            throw new RuntimeException("motds in config.yml must contain at least one string");
        }

        if (servers <= 0) {
            throw new RuntimeException("servers must be > 0");
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

                    String motdBase = motds.get(random.nextInt(motds.size()));

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