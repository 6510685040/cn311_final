import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.event.*;

public class Client extends JFrame {
    private JTextArea display;
    private JTextField inputField;
    private JButton checkButton;
    private int[] currentNumbers;
    private Socket socket;
    private BufferedReader read;
    private PrintWriter write;
    private JPanel cardPanel;
    private JLabel sumLabel;
    private static final Color FELT_GREEN = new Color(34, 139, 34);
    private static final Color GOLD = new Color(255, 215, 0);
    private static final Font CARD_FONT = new Font("SansSerif", Font.BOLD, 28);
    private static final Font DISPLAY_FONT = new Font("SansSerif", Font.PLAIN, 16);
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 32);
    private static final Dimension CARD_SIZE = new Dimension(140, 200);
    private static final int CARD_ARC = 15;

    public Client(String host, int port) {
        setTitle("Game 24 & 21");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(FELT_GREEN);

        display = new JTextArea();
        display.setEditable(false);
        display.setFont(DISPLAY_FONT);
        display.setBackground(new Color(255, 255, 255, 220));
        display.setForeground(Color.BLACK);
        display.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(display);
        scrollPane.setBorder(BorderFactory.createLineBorder(GOLD, 2));
        add(scrollPane, BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.setFont(DISPLAY_FONT);
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GOLD, 2),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        checkButton = new JButton("Answer");
        checkButton.setFont(DISPLAY_FONT);
        checkButton.setBackground(new Color(0, 100, 0));
        checkButton.setForeground(Color.WHITE);
        checkButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GOLD, 2),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        checkButton.setFocusPainted(false);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(checkButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        checkButton.addActionListener(e -> {
            String input = inputField.getText();
            display.append("Your answer: " + input + "\n");
            inputField.setText("");

            if (write != null) {
                write.println(input);
            }
        });
        new Thread(new ConnectTask()).start();
    }

    public class ConnectTask implements Runnable {
        @Override
        public void run() {
            connectToServer("localhost", 12700);
        }
    }

    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host, port);
            read = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            write = new PrintWriter(socket.getOutputStream(), true);
    
            System.out.println("success to connect server");
    
            final String line = read.readLine();
            System.out.println(line);
    
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    display.setText(line + "\n");
    
                    String[] nums = line.replaceAll("[\\[\\]]", "").split(",");
                    currentNumbers = new int[nums.length];
                    for (int i = 0; i < nums.length; i++) {
                        currentNumbers[i] = Integer.parseInt(nums[i].trim());
                    }
                }
            });
    
        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    JOptionPane.showMessageDialog(Client.this, "cannot connect server " + e.getMessage());
                }
            });
        }
    
        Thread receiverThread = new Thread(new Runnable() {
            public void run() {
                try {
                    String serverMessage;
                    while ((serverMessage = read.readLine()) != null) {
                        final String finalMessage = serverMessage;
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (finalMessage.equals("NOT_YOUR_TURN")) {
                                    JOptionPane.showMessageDialog(Client.this, "It's not your turn yet.");
                                } else if (finalMessage.equals("YOUR_TURN")) {
                                    display.append("It's your turn! ");                                                              
                                } else if (finalMessage.startsWith("YOUR_CARD:")) {
                                    String card = finalMessage.substring("YOUR_CARD:".length());
                                    display.append("You drew card: " + card + "\n");
                                    updateCardDisplay(card);
                                } else if (finalMessage.startsWith("YOUR_DECK:")) {
                                    String deckStr = finalMessage.substring("YOUR_DECK:".length());
                                    // Clear existing cards
                                    if (cardPanel != null) {
                                        cardPanel.removeAll();
                                        // Parse and display all cards in deck
                                        String[] cards = deckStr.replaceAll("[\\[\\]]", "").split(",");
                                        for (String card : cards) {
                                            if (!card.trim().isEmpty()) {
                                                updateCardDisplay(card.trim());
                                            }
                                        }
                                        cardPanel.revalidate();
                                        cardPanel.repaint();
                                    }
                                } else if (finalMessage.equals("SWITCH_TO_GAME21_WINNER")) {
                                    switchToGame21WinnerGUI();
                                } else if (finalMessage.equals("SWITCH_TO_GAME21_LOSER")) {
                                    switchToGame21LoserGUI();
                                } else if (finalMessage.equals("ALREADY_DRAWN")) {
                                    JOptionPane.showMessageDialog(Client.this, "You already drew a card in this round. Please wait for the other player.");
                                } else if (finalMessage.equals("STOP")) {
                                    JOptionPane.showMessageDialog(Client.this, "You want to stop this game. Please wait for the other player to decide.");
                                } else if (finalMessage.equals("OTHER_PLAYER_WANTS_STOP")) {
                                    int choice = JOptionPane.showConfirmDialog(Client.this, 
                                        "The other player wants to stop the game. Do you want to stop too?",
                                        "Stop Game?",
                                        JOptionPane.YES_NO_OPTION);
                                    write.println(choice == JOptionPane.YES_OPTION ? "ACCEPT_STOP" : "REJECT_STOP");
                                } else if (finalMessage.startsWith("YOUR_SUM:")) {
                                    String sum = finalMessage.substring("YOUR_SUM:".length());
                                    updateSum(sum);
                                    display.append("Your current sum: " + sum + "\n");
                                } else if (finalMessage.equals("NEW_GAME24")) {
                                    getContentPane().removeAll();
                                    setLayout(new BorderLayout());
                                    
                                    display = new JTextArea();
                                    display.setEditable(false);
                                    display.setFont(new Font("Monospaced", Font.PLAIN, 16));
                                    add(new JScrollPane(display), BorderLayout.CENTER);
                                    
                                    inputField = new JTextField();
                                    checkButton = new JButton("Answer");
                                    
                                    JPanel bottom = new JPanel(new BorderLayout());
                                    bottom.add(inputField, BorderLayout.CENTER);
                                    bottom.add(checkButton, BorderLayout.EAST);
                                    add(bottom, BorderLayout.SOUTH);
                                    
                                    checkButton.addActionListener(e -> {
                                        String input = inputField.getText();
                                        display.append("คุณตอบ: " + input + "\n");
                                        inputField.setText("");
                                        
                                        if (write != null) {
                                            write.println(input);
                                        }
                                    });
                                    
                                    revalidate();
                                    repaint();
                                } else {
                                    display.append(finalMessage + "\n");
                                }
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        receiverThread.start();
    }
    
    public void switchToGame21GUI() {
        getContentPane().removeAll();
        setLayout(new BorderLayout(30, 30));
        getContentPane().setBackground(FELT_GREEN);
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JLabel titleLabel = new JLabel("Game 21");
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(20, 20));
        centerPanel.setOpaque(false);

        JPanel deckArea = createDeckPanel();
        centerPanel.add(deckArea, BorderLayout.WEST);

        cardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        cardPanel.setOpaque(false);
        centerPanel.add(cardPanel, BorderLayout.CENTER);

        sumLabel = new JLabel("Sum: 0");
        sumLabel.setFont(TITLE_FONT);
        sumLabel.setForeground(Color.WHITE);
        centerPanel.add(sumLabel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        controlPanel.setOpaque(false);

        JButton drawButton = createStyledButton("Draw");
        drawButton.addActionListener(e -> {
            if (write != null) {
                write.println("DRAW_ONE_CARD");
            }
        });

        JButton drawTwoButton = createStyledButton("Draw Two");
        drawTwoButton.addActionListener(e -> {
            if (write != null) {
                write.println("DRAW_TWO_CARDS");
            }
        });

        JButton stopButton = createStyledButton("Stop");
        stopButton.addActionListener(e -> {
            if (write != null) {
                write.println("STOP");
            }
        });

        controlPanel.add(drawButton);
        controlPanel.add(drawTwoButton);
        controlPanel.add(stopButton);
        add(controlPanel, BorderLayout.SOUTH);

        // messages
        display = new JTextArea(3, 30);
        display.setEditable(false);
        display.setFont(new Font("Arial", Font.PLAIN, 14));
        display.setBackground(new Color(0, 0, 0, 80));
        display.setForeground(Color.WHITE);
        display.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JScrollPane scrollPane = new JScrollPane(display);
        scrollPane.setBorder(BorderFactory.createLineBorder(GOLD));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.EAST);

        setMinimumSize(new Dimension(900, 600));
        revalidate();
        repaint();
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(DISPLAY_FONT);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(0, 100, 0));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GOLD, 2),
            BorderFactory.createEmptyBorder(10, 25, 10, 25)
        ));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(new Color(0, 120, 0));
                }
            }
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(new Color(0, 100, 0));
                }
            }
        });

        return button;
    }

    private void updateCardDisplay(String card) {
        if (cardPanel != null) {
            cardPanel.add(createCardComponent(card));
            cardPanel.revalidate();
            cardPanel.repaint();
        }
    }

    private void updateSum(String sum) {
        if (sumLabel != null) {
            sumLabel.setText("Total: " + sum);
            sumLabel.setFont(TITLE_FONT);
            sumLabel.setForeground(Color.WHITE);
        }
    }

    private JPanel createDeckPanel() {
        JPanel deckPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        deckPanel.setOpaque(false);

        JPanel deck = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                for (int i = 5; i > 0; i--) {
                    g2d.setColor(Color.WHITE);
                    g2d.fillRoundRect(i*2, i*2, CARD_SIZE.width-4, CARD_SIZE.height-4, CARD_ARC, CARD_ARC);
                    g2d.setColor(Color.BLACK);
                    g2d.drawRoundRect(i*2, i*2, CARD_SIZE.width-4, CARD_SIZE.height-4, CARD_ARC, CARD_ARC);
                }

                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(0, 0, CARD_SIZE.width-4, CARD_SIZE.height-4, CARD_ARC, CARD_ARC);
                g2d.setColor(Color.BLACK);
                g2d.drawRoundRect(0, 0, CARD_SIZE.width-4, CARD_SIZE.height-4, CARD_ARC, CARD_ARC);

                g2d.setColor(new Color(180, 0, 0));
                int patternSize = 15;
                for (int x = 10; x < CARD_SIZE.width-14; x += patternSize) {
                    for (int y = 10; y < CARD_SIZE.height-14; y += patternSize) {
                        g2d.fillOval(x, y, 8, 8);
                    }
                }
            }
        };
        deck.setPreferredSize(CARD_SIZE);
        deck.setCursor(new Cursor(Cursor.HAND_CURSOR));

        deckPanel.add(deck);
        return deckPanel;
    }

    private JPanel createCardComponent(String value) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, CARD_ARC, CARD_ARC);
                

                g2d.setColor(Color.BLACK);
                g2d.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, CARD_ARC, CARD_ARC);


                g2d.setFont(CARD_FONT);
                FontMetrics fm = g2d.getFontMetrics();
                String displayValue = value;
                int textWidth = fm.stringWidth(displayValue);
                int textHeight = fm.getHeight();


                g2d.drawString(displayValue, 
                    (getWidth() - textWidth) / 2,
                    (getHeight() + textHeight) / 2 - fm.getDescent());

                g2d.setFont(CARD_FONT.deriveFont(16f));
                fm = g2d.getFontMetrics();
                g2d.drawString(displayValue, 5, 20);
                g2d.drawString(displayValue, getWidth() - fm.stringWidth(displayValue) - 5, getHeight() - 5);
            }
        };
        card.setPreferredSize(CARD_SIZE);
        return card;
    }

    private void switchToGame21WinnerGUI() {
        switchToGame21GUI();
        display.append("You won Game 24! You can choose to draw 1 or 2 cards.\n");
    }

    private void switchToGame21LoserGUI() {
        switchToGame21GUI();
        display.append("You lost Game 24. You can only draw 1 card.\n");

        for (Component comp : ((JPanel)getContentPane().getComponent(2)).getComponents()) {
            if (comp instanceof JButton && ((JButton)comp).getText().equals("Draw Two Cards")) {
                comp.setEnabled(false);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Client client = new Client("localhost", 12700);
                client.setVisible(true);
            }
        });
    }
}