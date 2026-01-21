import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class LanSpammerGUI {
    private static final String CONFIG_FILE = "config.yml";
    private static final String MULTICAST_ADDR = "224.0.2.60";
    private static final int PORT = 4445;

    private static JFrame frame;
    private static JTextField ipField;
    private static JTextField serversField;
    private static JComboBox<String> suffixCombo;
    private static JTextArea motdsArea;
    private static JTextField webhookUrlField;      // поле для webhook URL
    private static JButton testWebhookButton;       // кнопка теста
    private static JButton startButton;
    private static JButton stopButton;
    private static JLabel statusLabel;
    private static JLabel counterLabel;

    private static volatile boolean running = false;
    private static Thread spamThread;
    private static long totalSent = 0;

    private static final Random rnd = new Random();
    private static final String[] EMOJIS = {
        "🔥", "💀", "😂", "😈", "🚀", "🤡", "🖕", "🍆", "💥", "🤑", "👹", "⚡", "🌪️", "🧨", "💣"
    };

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI(args));
    }

    private static void createAndShowGUI(String[] args) {
        frame = new JFrame("ForkedLanSpammer GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(850, 750);
        frame.setLayout(new BorderLayout(10, 10));

        // Панель параметров
        JPanel paramsPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        paramsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        paramsPanel.add(new JLabel("IP (0.0.0.0 — все интерфейсы):"));
        ipField = new JTextField("0.0.0.0");
        paramsPanel.add(ipField);

        paramsPanel.add(new JLabel("Количество серверов за цикл:"));
        serversField = new JTextField("10");
        paramsPanel.add(serversField);

        paramsPanel.add(new JLabel("Режим суффикса:"));
        suffixCombo = new JComboBox<>(new String[]{"numbers", "random", "nothing"});
        paramsPanel.add(suffixCombo);

        paramsPanel.add(new JLabel("Discord Webhook URL:"));
        webhookUrlField = new JTextField("");
        paramsPanel.add(webhookUrlField);

        // MOTD
        JPanel motdPanel = new JPanel(new BorderLayout());
        motdPanel.setBorder(BorderFactory.createTitledBorder("MOTD (по одной строке, используй & для цветов)"));
        motdsArea = new JTextArea(14, 60);
        JScrollPane scroll = new JScrollPane(motdsArea);
        motdPanel.add(scroll, BorderLayout.CENTER);

        // Кнопки и статус
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton loadButton = new JButton("Загрузить config.yml");
        JButton saveButton = new JButton("Сохранить config.yml");
        testWebhookButton = new JButton("Тест Webhook");
        startButton = new JButton("Старт спама");
        stopButton = new JButton("Стоп спама");
        stopButton.setEnabled(false);

        buttonPanel.add(loadButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(testWebhookButton);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        statusLabel = new JLabel("Статус: готов");
        counterLabel = new JLabel("Отправлено серверов: 0");
        buttonPanel.add(statusLabel);
        buttonPanel.add(counterLabel);

        frame.add(paramsPanel, BorderLayout.NORTH);
        frame.add(motdPanel, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadConfig());
        saveButton.addActionListener(e -> saveConfig());
        testWebhookButton.addActionListener(e -> testWebhook());
        startButton.addActionListener(e -> startSpamming());
        stopButton.addActionListener(e -> stopSpamming());

        loadConfig(); // загружаем при старте
        frame.setVisible(true);
    }

    private static void loadConfig() {
        try (BufferedReader br = new BufferedReader(new FileReader(CONFIG_FILE))) {
            List<String> motds = new ArrayList<>();
            boolean inMotds = false;
            String ip = "0.0.0.0";
            int servers = 10;
            String suffix = "numbers";
            String webhook = "";

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

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
                    } else if (!line.matches("^\\s.*")) {
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
                        case "ip": ip = value; break;
                        case "servers": servers = Integer.parseInt(value); break;
                        case "suffix-mode": suffix = value.toLowerCase(); break;
                        case "webhook-url": webhook = value; break;
                    }
                }
            }

            ipField.setText(ip);
            serversField.setText(String.valueOf(servers));
            suffixCombo.setSelectedItem(suffix);
            webhookUrlField.setText(webhook);

            if (motds.isEmpty()) {
                motds.addAll(Arrays.asList(
                    "§c§lFREE OP §a§lJOIN FAST OR DIE",
                    "§k§lHACKED§r §b§lBY ilyshaxoroshi",
                    "§e§lGIRLS ONLY §d§l<3",
                    "§4§l dsc.gg/dxxtmine",
                    "§4§l Извините за неудобства, тестирую прогу"
                ));
                statusLabel.setText("Статус: MOTDs пустые → дефолтные загружены");
            }

            motdsArea.setText(String.join("\n", motds));

        } catch (Exception ex) {
            statusLabel.setText("Статус: config.yml не найден → дефолтные значения");
            motdsArea.setText("§c§lFREE OP §a§lJOIN FAST OR DIE\n§k§lHACKED§r §b§lBY ilyshaxoroshi\n§e§lGIRLS ONLY §d§l<3\n§4§l dsc.gg/dxxtmine\n§4§l Извините за неудобства, тестирую прогу");
        }
    }

    private static void saveConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println("ip: \"" + ipField.getText() + "\"");
            writer.println("servers: " + serversField.getText());
            writer.println("suffix-mode: \"" + suffixCombo.getSelectedItem() + "\"");
            writer.println("webhook-url: \"" + webhookUrlField.getText() + "\"");
            writer.println("motds:");
            for (String line : motdsArea.getText().split("\\r?\\n")) {
                if (!line.trim().isEmpty()) {
                    writer.println("  - \"" + line.trim() + "\"");
                }
            }
            statusLabel.setText("Статус: конфиг сохранён");
        } catch (Exception ex) {
            statusLabel.setText("Статус: ошибка сохранения конфига");
        }
    }

    private static void testWebhook() {
        String urlStr = webhookUrlField.getText().trim();
        if (urlStr.isEmpty()) {
            statusLabel.setText("Webhook URL пустой");
            return;
        }

        new Thread(() -> {
            try {
                URI uri = new URI(urlStr);
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                String emoji = EMOJIS[rnd.nextInt(EMOJIS.length)];
                String payload = "{\"content\": \"Webhook test from ForkedLanSpammer 🔥 " + emoji + " Дата: " + new java.util.Date() + "\"}";

                OutputStream os = conn.getOutputStream();
                os.write(payload.getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                SwingUtilities.invokeLater(() -> statusLabel.setText("Тест webhook: " + (code >= 200 && code < 300 ? "OK (код " + code + ")" : "Ошибка код " + code)));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Тест webhook провалился: " + ex.getMessage()));
            }
        }).start();
    }

    private static void startSpamming() {
        if (running) return;

        saveConfig();

        running = true;
        totalSent = 0;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Статус: спам запущен");

        spamThread = new Thread(() -> {
            long localCounter = 0;

            try {
                InetAddress localAddr = InetAddress.getByName(ipField.getText());
                InetAddress group = InetAddress.getByName(MULTICAST_ADDR);

                MulticastSocket socket = new MulticastSocket(new InetSocketAddress(localAddr, 0));
                socket.setTimeToLive(4);

                Random random = new Random();
                Set<Integer> usedPorts = new HashSet<>();

                int servers = Integer.parseInt(serversField.getText());
                String suffixMode = (String) suffixCombo.getSelectedItem();
                String[] motdArray = motdsArea.getText().split("\\r?\\n");

                if (motdArray.length == 0 || motdArray[0].trim().isEmpty()) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Статус: MOTDs пустые — спам остановлен"));
                    running = false;
                    return;
                }

                while (running) {
                    usedPorts.clear();

                    for (int i = 1; i <= servers; i++) {
                        if (!running) break;

                        String motdBase = motdArray[random.nextInt(motdArray.length)];

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

                        localCounter++;
                        final long current = localCounter;

                        SwingUtilities.invokeLater(() -> {
                            counterLabel.setText("Отправлено серверов: " + current);
                        });

                        // Отправляем в webhook
                        sendToWebhook(current, motd, serverPort);
                    }

                    Thread.sleep(1500);
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Статус: ошибка — " + ex.getMessage()));
            } finally {
                running = false;
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    statusLabel.setText("Статус: спам остановлен");
                });
            }
        });
        spamThread.start();
    }

    private static void sendToWebhook(long number, String motd, int port) {
        String urlStr = webhookUrlField.getText().trim();
        if (urlStr.isEmpty() || urlStr.equals("https://discord.com/api/webhooks/твой_ид/твой_токен")) return;

        try {
            URI uri = new URI(urlStr);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            String emoji = EMOJIS[rnd.nextInt(EMOJIS.length)];
            String cleanMotd = motd.replace("\"", "\\\"").replace("\n", "\\n");
            String payload = "{\"content\": \"Сервер #" + number + " запустился: " + cleanMotd + " (порт " + port + ") " + emoji + "\"}";

            OutputStream os = conn.getOutputStream();
            os.write(payload.getBytes("UTF-8"));
            os.flush();
            os.close();

            conn.getResponseCode(); // не ждём ответа, чтобы не тормозить спам
        } catch (Exception ignored) {
            // тихо игнорируем, чтобы спам не останавливался
        }
    }

    private static void stopSpamming() {
        running = false;
        statusLabel.setText("Статус: спам остановлен");
    }
}
