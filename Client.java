import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class Client extends JFrame {
    private TextArea display;
    private TextField inputField;
    private Button checkButton;
    private int[] currentNumbers;
    private Socket socket;
    private BufferedReader read;
    private PrintWriter write;

    public Client(String host, int port) {
        setTitle("player");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        display = new TextArea();
        display.setEditable(false);
        display.setFont(new Font("Monospaced", Font.PLAIN, 16));
        add(new JScrollPane(display), BorderLayout.CENTER);

        inputField = new TextField();
        checkButton = new Button("Answer");

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
                                display.append(finalMessage + "\n");
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
    
    

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Client client = new Client("localhost", 12700);
                client.setVisible(true);
            }
        });
    }
    
}
