import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class LanSpammerGUI {
    private static final String CONFIG_FILE = "config.yml";
    private static final String MULTICAST_ADDR = "224.0.2.60";
    private static final int PORT = 4445;

    private static JFrame frame;
    private static JTextField ipField;
    private static JTextField serversField;
    private static JComboBox<String> suffixCombo;
    private static JTextArea motdsArea;
    private static JButton startButton;
    private static JButton stopButton;
    private static JLabel statusLabel;

    private static volatile boolean running = false;
    private static Thread spamThread;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI(args));
    }

    private static void createAndShowGUI(String[] args) {
        frame = new JFrame("ForkedLanSpammer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BorderLayout(10, 10));

        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        inputPanel.add(new JLabel("IP:"));
        ipField = new JTextField("0.0.0.0");
        inputPanel.add(ipField);

        inputPanel.add(new JLabel("Servers:"));
        serversField = new JTextField("10");
        inputPanel.add(serversField);

        inputPanel.add(new JLabel("Suffix Mode:"));
        suffixCombo = new JComboBox<>(new String[]{"numbers", "random", "nothing"});
        inputPanel.add(suffixCombo);

        JPanel motdPanel = new JPanel(new BorderLayout());
        motdPanel.add(new JLabel("MOTDs (one per line):"), BorderLayout.NORTH);
        motdsArea = new JTextArea(10, 40);
        JScrollPane scroll = new JScrollPane(motdsArea);
        motdPanel.add(scroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton loadButton = new JButton("Load Config");
        JButton saveButton = new JButton("Save Config");
        startButton = new JButton("Start Spam");
        stopButton = new JButton("Stop Spam");
        stopButton.setEnabled(false);

        buttonPanel.add(loadButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        statusLabel = new JLabel("Status: Ready");
        buttonPanel.add(statusLabel);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(motdPanel, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadConfig());
        saveButton.addActionListener(e -> saveConfig());
        startButton.addActionListener(e -> startSpamming());
        stopButton.addActionListener(e -> stopSpamming());

        loadConfig();
        frame.setVisible(true);
    }

    private static void loadConfig() {
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
            ipField.setText(yourIp);
            serversField.setText(String.valueOf(servers));
            suffixCombo.setSelectedItem(suffixMode);
            motdsArea.setText(String.join("\n", motds));
            statusLabel.setText("Status: Config loaded");
        } catch (Exception e) {
            statusLabel.setText("Status: Config load error");
        }
    }

    private static void saveConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("config.yml"))) {
            writer.println("ip: \"" + ipField.getText() + "\"");
            writer.println("servers: " + serversField.getText());
            writer.println("suffix-mode: \"" + (String) suffixCombo.getSelectedItem() + "\"");
            writer.println("motds:");
            String[] lines = motdsArea.getText().split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    writer.println("- \"" + line.trim() + "\"");
                }
            }
            statusLabel.setText("Status: Config saved");
        } catch (Exception e) {
            statusLabel.setText("Status: Config save error");
        }
    }

    private static void startSpamming() {
        if (running) return;

        running = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Status: Spamming started");

        spamThread = new Thread(() -> {
            String yourIp = ipField.getText();
            int servers = Integer.parseInt(serversField.getText());
            List<String> motds = Arrays.asList(motdsArea.getText().split("\n"));
            String suffixMode = (String) suffixCombo.getSelectedItem();

            if (motds.isEmpty()) {
                statusLabel.setText("Status: MOTDs empty");
                running = false;
                return;
            }

            if (servers <= 0) {
                statusLabel.setText("Status: Servers must be > 0");
                running = false;
                return;
            }

            Random random = new Random();
            Set<Integer> usedPorts = new HashSet<>();

            try {
                InetAddress localAddr = InetAddress.getByName(yourIp);
                InetAddress group = InetAddress.getByName(MULTICAST_ADDR);

                MulticastSocket socket = new MulticastSocket(new InetSocketAddress(localAddr, 0));
                socket.setTimeToLive(4);

                statusLabel.setText("Status: Started on IP: " + yourIp);

                while (running) {
                    usedPorts.clear();

                    for (int i = 1; i <= servers; i++) {
                        if (!running) break;

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

                        String message = "[MOTD]" + motd + "[/MOTD][AD]" + serverPort + "[/AD]";
                        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                        socket.send(packet);
                    }
                    Thread.sleep(1500);
                }
            } catch (Exception e) {
                statusLabel.setText("Status: Error - " + e.getMessage());
            } finally {
                running = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            }
        });
        spamThread.start();
    }

    private static void stopSpamming() {
        running = false;
    }
}
