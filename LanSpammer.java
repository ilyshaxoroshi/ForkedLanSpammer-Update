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
    private static JTextField webhookUrlField;
    private static JTextField lanIntervalField;
    private static JComboBox<String> adModeCombo;
    private static JTextField customAdTextField;
    private static JButton testWebhookButton;
    private static JButton startButton;
    private static JButton stopButton;
    private static JLabel statusLabel;
    private static JLabel counterLabel;

    private static volatile boolean running = false;
    private static Thread spamThread;
    private static Thread lanSenderThread;
    private static long totalSent = 0;

    private static final Random rnd = new Random();
    private static final String[] EMOJIS = {"🔥", "💀", "😂", "😈", "🚀", "🤡", "🖕", "🍆", "💥", "🤑"};

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LanSpammerGUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        frame = new JFrame("ForkedLanSpammer GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 850);
        frame.setLayout(new BorderLayout(10, 10));

        // Панель параметров
        JPanel paramsPanel = new JPanel(new GridLayout(7, 2, 10, 10));
        paramsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        paramsPanel.add(new JLabel("IP (0.0.0.0 — все интерфейсы):"));
        ipField = new JTextField("0.0.0.0");
        paramsPanel.add(ipField);

        paramsPanel.add(new JLabel("Количество серверов за цикл:"));
        serversField = new JTextField("10");
        paramsPanel.add(serversField);

        paramsPanel.add(new JLabel("Режим суффикса MOTD:"));
        suffixCombo = new JComboBox<>(new String[]{"numbers", "random", "nothing"});
        paramsPanel.add(suffixCombo);

        paramsPanel.add(new JLabel("Интервал отправки списка LAN (сек):"));
        lanIntervalField = new JTextField("300");
        paramsPanel.add(lanIntervalField);

        paramsPanel.add(new JLabel("Режим AD-порта:"));
        adModeCombo = new JComboBox<>(new String[]{"numbers (классика)", "random-digits", "custom-text", "nothing"});
        paramsPanel.add(adModeCombo);

        paramsPanel.add(new JLabel("Кастомный текст для AD (если выбран):"));
        customAdTextField = new JTextField("&cThats New version OF FLS on github!");
        paramsPanel.add(customAdTextField);

        paramsPanel.add(new JLabel("Discord Webhook URL:"));
        webhookUrlField = new JTextField("");
        paramsPanel.add(webhookUrlField);

        // MOTD
        JPanel motdPanel = new JPanel(new BorderLayout());
        motdPanel.setBorder(BorderFactory.createTitledBorder("MOTD (по одной строке, & для цветов)"));
        motdsArea = new JTextArea(14, 60);
        JScrollPane scroll = new JScrollPane(motdsArea);
        motdPanel.add(scroll, BorderLayout.CENTER);

        // Кнопки в 3 ряда по 2 + отступы
        JPanel buttonPanel = new JPanel(new GridLayout(3, 3, 20, 10));

        JButton loadButton = new JButton("Загрузить config.yml");
        JButton saveButton = new JButton("Сохранить config.yml");
        testWebhookButton = new JButton("Тест Webhook");
        startButton = new JButton("Старт спама");
        stopButton = new JButton("Стоп спама");
        stopButton.setEnabled(false);

        // Ряд 1
        buttonPanel.add(loadButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(saveButton);

        // Ряд 2
        buttonPanel.add(testWebhookButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(startButton);

        // Ряд 3
        buttonPanel.add(stopButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(new JLabel("")); // пустая ячейка для симметрии

        // Статус и счётчик под кнопками
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusLabel = new JLabel("Статус: готов");
        counterLabel = new JLabel("Отправлено серверов: 0");
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createHorizontalStrut(30));
        statusPanel.add(counterLabel);

        JPanel fullBottom = new JPanel(new BorderLayout());
        fullBottom.add(buttonPanel, BorderLayout.CENTER);
        fullBottom.add(statusPanel, BorderLayout.SOUTH);

        frame.add(paramsPanel, BorderLayout.NORTH);
        frame.add(motdPanel, BorderLayout.CENTER);
        frame.add(fullBottom, BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadConfig());
        saveButton.addActionListener(e -> saveConfig());
        testWebhookButton.addActionListener(e -> testWebhook());
        startButton.addActionListener(e -> startSpamming());
        stopButton.addActionListener(e -> stopSpamming());

        loadConfig();
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
            int lanInterval = 300;
            String adMode = "numbers (классика)";
            String customAdText = "&cThats New version OF FLS on github!";

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
                        case "lan-interval-sec": lanInterval = Integer.parseInt(value); break;
                        case "ad-mode": adMode = value; break;
                        case "custom-ad-text": customAdText = value; break;
                    }
                }
            }

            ipField.setText(ip);
            serversField.setText(String.valueOf(servers));
            suffixCombo.setSelectedItem(suffix);
            webhookUrlField.setText(webhook);
            lanIntervalField.setText(String.valueOf(lanInterval));
            adModeCombo.setSelectedItem(adMode);
            customAdTextField.setText(customAdText);

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
            statusLabel.setText("Статус: config.yml не найден → дефолт");
            motdsArea.setText("§c§lFREE OP §a§lJOIN FAST OR DIE\n§k§lHACKED§r §b§lBY ilyshaxoroshi\n§e§lGIRLS ONLY §d§l<3\n§4§l dsc.gg/dxxtmine\n§4§l Извините за неудобства, тестирую прогу");
            lanIntervalField.setText("300");
            adModeCombo.setSelectedItem("numbers (классика)");
            customAdTextField.setText("&cThats New version OF FLS on github!");
        }
    }

    private static void saveConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println("ip: \"" + ipField.getText() + "\"");
            writer.println("servers: " + serversField.getText());
            writer.println("suffix-mode: \"" + suffixCombo.getSelectedItem() + "\"");
            writer.println("webhook-url: \"" + webhookUrlField.getText() + "\"");
            writer.println("lan-interval-sec: " + lanIntervalField.getText());
            writer.println("ad-mode: \"" + adModeCombo.getSelectedItem() + "\"");
            writer.println("custom-ad-text: \"" + customAdTextField.getText() + "\"");
            writer.println("motds:");
            for (String line : motdsArea.getText().split("\\r?\\n")) {
                if (!line.trim().isEmpty()) {
                    writer.println("  - \"" + line.trim() + "\"");
                }
            }
            statusLabel.setText("Статус: конфиг сохранён");
        } catch (Exception ex) {
            statusLabel.setText("Статус: ошибка сохранения");
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
                String payload = "{\"content\": \"Тест webhook от ForkedLanSpammer 🔥 " + emoji + " | " + new java.util.Date() + "\"}";

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
                String adMode = (String) adModeCombo.getSelectedItem();

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
                                for (int j = 0; j < 6; j++) sb.append(chars.charAt(random.nextInt(chars.length())));
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

                        String adContent;
                        switch (adMode) {
                            case "random-digits":
                                adContent = String.valueOf(rnd.nextLong() % 100000000L + 10000000L); // 8 цифр
                                break;
                            case "custom-text":
                                adContent = customAdTextField.getText().replace("&", "\u00A7");
                                break;
                            case "nothing":
                                adContent = "";
                                break;
                            case "numbers (классика)":
                            default:
                                int serverPort;
                                do {
                                    serverPort = random.nextInt(65535 - 1024 + 1) + 1024;
                                } while (!usedPorts.add(serverPort));
                                adContent = String.valueOf(serverPort);
                                break;
                        }

                        String message = "[MOTD]" + motd + "[/MOTD][AD]" + adContent + "[/AD]";
                        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                        socket.send(packet);

                        localCounter++;
                        final long current = localCounter;

                        SwingUtilities.invokeLater(() -> {
                            counterLabel.setText("Отправлено серверов: " + current);
                        });

                        sendToWebhook(current, motd, adContent);
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

        // Периодическая отправка списка LAN
        lanSenderThread = new Thread(() -> {
            while (true) {
                try {
                    int interval = Integer.parseInt(lanIntervalField.getText());
                    if (interval < 5) interval = 5;
                    Thread.sleep(interval * 1000L);

                    if (running) {
                        sendLanListOnce();
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Статус: список LAN переотправлен"));
                    }
                } catch (InterruptedException ignored) {
                    break;
                } catch (Exception ex) {
                    // тихо
                }
            }
        });
        lanSenderThread.setDaemon(true);
        lanSenderThread.start();
    }

    private static void sendLanListOnce() {
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDR);
            MulticastSocket tempSocket = new MulticastSocket();
            tempSocket.setTimeToLive(4);

            Random random = new Random();
            Set<Integer> tempPorts = new HashSet<>();

            int servers = Integer.parseInt(serversField.getText());
            String suffixMode = (String) suffixCombo.getSelectedItem();
            String[] motdArray = motdsArea.getText().split("\\r?\\n");
            String adMode = (String) adModeCombo.getSelectedItem();

            for (int i = 1; i <= servers; i++) {
                String motdBase = motdArray[random.nextInt(motdArray.length)];

                String suffix;
                switch (suffixMode) {
                    case "random":
                        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
                        StringBuilder sb = new StringBuilder(6);
                        for (int j = 0; j < 6; j++) sb.append(chars.charAt(random.nextInt(chars.length())));
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

                String adContent;
                switch (adMode) {
                    case "random-digits":
                        adContent = String.valueOf(rnd.nextLong() % 100000000L + 10000000L);
                        break;
                    case "custom-text":
                        adContent = customAdTextField.getText().replace("&", "\u00A7");
                        break;
                    case "nothing":
                        adContent = "";
                        break;
                    default:
                        int p;
                        do {
                            p = random.nextInt(65535 - 1024 + 1) + 1024;
                        } while (!tempPorts.add(p));
                        adContent = String.valueOf(p);
                        break;
                }

                String message = "[MOTD]" + motd + "[/MOTD][AD]" + adContent + "[/AD]";
                byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                tempSocket.send(packet);
            }

            tempSocket.close();
        } catch (Exception ignored) {}
    }

    private static void sendToWebhook(long number, String motd, String adContent) {
        String urlStr = webhookUrlField.getText().trim();
        if (urlStr.isEmpty()) return;

        try {
            URI uri = new URI(urlStr);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            String emoji = EMOJIS[rnd.nextInt(EMOJIS.length)];
            String clean = motd.replace("\"", "\\\"").replace("\n", "\\n");
            String payload = "{\"content\": \"Сервер #" + number + " запустился: " + clean + " (" + adContent + ") " + emoji + "\"}";

            OutputStream os = conn.getOutputStream();
            os.write(payload.getBytes("UTF-8"));
            os.flush();
            os.close();

            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    private static void stopSpamming() {
        running = false;
        statusLabel.setText("Статус: спам остановлен");
    }
}
