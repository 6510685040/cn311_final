import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 12700;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static volatile boolean gameEnd = false;
    public static int[] numbers; 

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Waiting for connection...");

        List<Puzzle24.Puzzle> puzzleList = Puzzle24.loadCSV("24.csv");
        Puzzle24.Puzzle randomPuzzle = puzzleList.get(new Random().nextInt(puzzleList.size()));
        numbers = randomPuzzle.getNumbers();
        System.out.println("Problem: " + Arrays.toString(numbers));

        while (clients.size() < 2) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            System.out.println("ผู้เล่นคนที่ " + clients.size() + " เชื่อมต่อแล้ว");
            new Thread(handler).start();
        }
    }

    public static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader read;
        private PrintWriter write;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                setupConnectClient();
                sendPuzzle();
                startTimer();
                listenForClient();
            } catch (IOException e) {
                System.out.println("Client disconnected: " + socket);
            }
        }

        private void setupConnectClient() throws IOException {
            read = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            write = new PrintWriter(socket.getOutputStream(), true);
        }

        private void sendPuzzle() {
            write.println("Make 24 using all 4 numbers exactly once " + Arrays.toString(numbers));
            write.println("You may use: +, -, *, / and ()");
            write.println("Example: (2+2)*3*2");
            write.println("You have only 3️⃣ minutes ");
        }

        private void startTimer() {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    if (!gameEnd) {
                        gameEnd = true;
                        for (ClientHandler output : clients) {
                            output.write.println("Times out");
                        }
                    }
                }
            }, 180000);
        }

        private void listenForClient() throws IOException {
            String answered;
            while (!gameEnd && (answered = read.readLine()) != null) {
                System.out.println("Answer from client: " + answered);

                if (gameEnd) break;

                String checkResult = Game24.checkExpression(answered);
                if (checkResult.equals("correct")) {
                    synchronized (Server.class) {
                        if (!gameEnd) {
                            gameEnd = true;
                            write.println("You win! 🥇");
                            for (ClientHandler other : clients) {
                                if (other != this) {
                                    other.write.println("You lose! 😭 The other player answered correctly first ");
                                }
                            }
                        }
                    }
                } else {
                    write.println( checkResult + " Try again 👌🏻 (you still have time)");
                }
            }
        }
    }

    public static class Game24 {
        public static boolean isCorrect(String expression) {
            try {
                List<Integer> usedNumbers = extractNumbers(expression);
                Map<Integer, Integer> usedCount = getFrequencyMap(usedNumbers);
                List<Integer> numberList = new ArrayList<>();
                for (int n : Server.numbers) {
                    numberList.add(n);
                }
                Map<Integer, Integer> requiredCount = getFrequencyMap(numberList);


                if (!usedCount.equals(requiredCount)) {
                    return false;
                }

                double result = SimpleEvaluator.evaluate(expression);
                return Math.abs(result - 24) < 1e-6;
            } catch (Exception e) {
                return false;
            }
        }

        private static Map<Integer, Integer> getFrequencyMap(List<Integer> list) {
            Map<Integer, Integer> frequency = new HashMap<>();
            for (int n : list) {
                if (frequency.containsKey(n)) {
                    frequency.put(n, frequency.get(n) + 1);
                } else {
                    frequency.put(n, 1);
                }
            }
            return frequency;
        }
        

        private static List<Integer> extractNumbers(String expression) {
            List<Integer> numbers = new ArrayList<>();
            String cleaned = expression.replaceAll("[^0-9]", " ");
            String[] tokens = cleaned.trim().split("\\s+");
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    numbers.add(Integer.parseInt(token));
                }
            }
            return numbers;
        }

        public static String checkExpression(String expression) {
            try {
                List<Integer> usedNumbers = extractNumbers(expression);
                Map<Integer, Integer> usedCount = getFrequencyMap(usedNumbers);
                Map<Integer, Integer> requiredCount = getFrequencyMap(Arrays.asList(Arrays.stream(Server.numbers).boxed().toArray(Integer[]::new)));

                if (!usedCount.equals(requiredCount)) {
                    return "You must use all 4 numbers exactly once.";
                }

                double result = SimpleEvaluator.evaluate(expression);
                if (Math.abs(result - 24) < 1e-6) {
                    return "correct";
                } else {
                    return "Result is not 24";
                }
            } catch (Exception e) {
                return "Error in expression: " + e.getMessage();
            }
        }

        public static class SimpleEvaluator {
            private static int current;
            private static String input;

            public static double evaluate(String expr) {
                input = expr.replaceAll("\\s+", "");
                current = 0;
                double result = addminus();
                if (current != input.length()) throw new RuntimeException("Unexpected: " + input.charAt(current));
                return result;
            }

            private static double addminus() {
                double value = multipledevide();
                while (current < input.length()) {
                    char operation = input.charAt(current);
                    if (operation == '+' || operation == '-') {
                        current++;
                        double next = multipledevide();
                        value = (operation == '+') ? value + next : value - next;
                    } else break;
                }
                return value;
            }

            private static double multipledevide() {
                double value = parseFactor();
                while (current < input.length()) {
                    char op = input.charAt(current);
                    if (op == '*' || op == '/') {
                        current++;
                        double next = parseFactor();
                        value = (op == '*') ? value * next : value / next;
                    } else break;
                }
                return value;
            }

            private static double parseFactor() {
                if (current >= input.length()) throw new RuntimeException("Unexpected end of input");

                char ch = input.charAt(current);
                if (ch == '(') {
                    current++;
                    double value = addminus();
                    if (current >= input.length() || input.charAt(current) != ')') {
                        throw new RuntimeException("Expected ')'");
                    }
                    current++;
                    return value;
                }

                StringBuilder changetonum = new StringBuilder();
                while (current < input.length() && (Character.isDigit(input.charAt(current)))) {
                    changetonum.append(input.charAt(current++));
                }
                if (changetonum.length() == 0) throw new RuntimeException("Expected number");
                return Double.parseDouble(changetonum.toString());
            }
        }
    }
}
