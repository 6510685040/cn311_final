import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 12700;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static volatile boolean game24End = false;
    public static int[] numbers; 
    public static List<Integer> deck21 = new ArrayList<>(Arrays.asList(2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
    public static boolean game21End = false;
    public static boolean player1WinGame24 = false;
    public static boolean waitingForCardChoice = false;
    public static ClientHandler game24Winner = null;
    private static Timer gameTimer;
    private static final int GAME24_TIMEOUT = 180000; 

    public static void newGame24() {
        game24End = false;
        player1WinGame24 = false;
        game24Winner = null;
        waitingForCardChoice = false;
        List<Puzzle24.Puzzle> puzzleList = Puzzle24.loadCSV("24.csv");
        Puzzle24.Puzzle randomPuzzle = puzzleList.get(new Random().nextInt(puzzleList.size()));
        numbers = randomPuzzle.getNumbers();
        System.out.println("Problem: " + Arrays.toString(numbers));
    }

    

    public static void newGame21() {
        game21End = false;
        
        Collections.shuffle(deck21);
        System.out.println("Shuffled deck: " + deck21);
    }

    public static void broadcastNewGame21() {
        newGame21();
        Game21.startNewRound();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                synchronized (Server.class) {
                    Game21.setInitialTurn(game24Winner);
                    for (ClientHandler client : clients) {
                        if (client == game24Winner) {
                            client.write.println("SWITCH_TO_GAME21_WINNER");
                            client.write.println("YOUR_TURN");
                            client.hasDrawnThisTurn = false;
                            client.cardsToDrawThisTurn = 2;
                        } else {
                            client.write.println("SWITCH_TO_GAME21_LOSER");
                            client.hasDrawnThisTurn = false;
                            client.cardsToDrawThisTurn = 1;
                        }
                        Game21.broadcastCurrentState(client);
                    }
                    waitingForCardChoice = true;
                }
            }
        }, 7000);
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Waiting for connection...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (!serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }));

        newGame24();

        try {
            while (clients.size() < 2) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                System.out.println("ผู้เล่นคนที่ " + clients.size() + " เชื่อมต่อแล้ว");
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            throw e;
        }
    }

    public static class ClientHandler implements Runnable, AutoCloseable {
        private Socket socket;
        private BufferedReader read;
        private PrintWriter write;
        private boolean hasDrawnThisTurn = false;
        private int cardsToDrawThisTurn = 1;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void close() {
            try {
                if (read != null) {
                    read.close();
                }
                if (write != null) {
                    write.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }

        public void run() {
            try {
                setupConnectClient();
                sendPuzzle();
                startTimer();
                listenForClient();
            } catch (IOException e) {
                System.out.println("Client disconnected: " + socket);
            } finally {
                close();
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
            write.println("You have only 3 minutes ");
        }

        private void startTimer() {
            synchronized (Server.class) {
                if (gameTimer != null) {
                    gameTimer.cancel();
                }
                gameTimer = new Timer();
                gameTimer.schedule(new TimerTask() {
                    public void run() {
                        if (!game24End) {
                            synchronized (Server.class) {
                                if (!game24End) {
                                    game24End = true;
                                    for (ClientHandler output : clients) {
                                        output.write.println("Times out");
                                        output.write.println("Time is up! ⏰ No one wins this round. Let's continue to game 21.");
                                    }
                                    Server.broadcastNewGame21();
                                }
                            }
                        }
                        gameTimer.cancel();
                    }
                }, GAME24_TIMEOUT);
            }
        }

        private void listenForClient() throws IOException {
            String answered;
            while ((answered = read.readLine()) != null) {
                if (answered.equals("DRAW_ONE_CARD")) {
                    if ((this == clients.get(0) && !Game21.player1Stopped) ||
                        (this == clients.get(1) && !Game21.player2Stopped)) {
                        cardsToDrawThisTurn = 1;
                        handleDrawCard();
                    } else {
                        write.println("You have already stopped. Cannot draw more cards.");
                    }
                } else if (answered.equals("DRAW_TWO_CARDS")) {
                    if ((this == clients.get(0) && !Game21.player1Stopped) ||
                        (this == clients.get(1) && !Game21.player2Stopped)) {
                        if (this == game24Winner && !hasDrawnThisTurn) {
                            cardsToDrawThisTurn = 2;
                            handleDrawCard();
                        } else {
                            write.println("You are not allowed to draw two cards");
                        }
                    } else {
                        write.println("You have already stopped. Cannot draw more cards.");
                    }
                } else if (answered.equals("STOP")) {
                    Game21.handleStopRequest(this);
                } 
              
                else if (!game24End) {
                    String checkResult = Game24.checkExpression(answered);
                    if (checkResult.equals("correct")) {
                        synchronized (Server.class) {
                            if (!game24End) {
                                game24End = true;
                                game24Winner = this;
                                player1WinGame24 = (this == clients.get(0));
            
                                if (gameTimer != null) {
                                    gameTimer.cancel();
                                    gameTimer = null;
                                }
            
                                write.println("You win! 🥇 Let's continue to game 21. You can choose to draw 1 or 2 cards.");
                                for (ClientHandler other : clients) {
                                    if (other != this) {
                                        other.write.println("You lose! 😭 The other player answered correctly first. You will draw 1 card in game 21.");
                                    }
                                }
                                Server.broadcastNewGame21();
                            }
                        }
                    } else {
                        write.println("Incorrect expression. Try again.");
                    }
                }
            }
        }
            

        private void handleDrawCard() {
            synchronized (Server.class) {
                boolean isPlayer1 = (this == clients.get(0));
                
                if (!Game21.canDrawCard(this)) {
                    if (hasDrawnThisTurn) {
                        write.println("ALREADY_DRAWN");
                    } else {
                        write.println("NOT_YOUR_TURN");
                    }
                    return;
                }
                
                for (int i = 0; i < cardsToDrawThisTurn; i++) {
                    Integer card = Game21.drawCard();
                    if (card != null) {
                        if (isPlayer1) {
                            Game21.player1deck.add(card);
                            Game21.player1Sum += card;
                        } else {
                            Game21.player2deck.add(card);
                            Game21.player2Sum += card;
                        }
                        write.println("YOUR_CARD:" + card);
                    } else {
                        write.println("NO_CARD_LEFT");
                        break;
                    }
                }
                
                Game21.broadcastCurrentState(this);
                
                hasDrawnThisTurn = true;
                if (isPlayer1) {
                    Game21.player1HasDrawn = true;
                } else {
                    Game21.player2HasDrawn = true;
                }

                if (Server.deck21.isEmpty()) {
                    Game21.determineWinner();
                    return;
                }

                Game21.switchTurns();
                
                ClientHandler nextPlayer = isPlayer1 ? clients.get(1) : clients.get(0);
                nextPlayer.write.println("YOUR_TURN");
                Game21.broadcastCurrentState(nextPlayer);

                if ((isPlayer1 && Game21.player2HasDrawn) || (!isPlayer1 && Game21.player1HasDrawn)) {
                    Game21.bothPlayersDrawn = true;
                    
                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        public void run() {
                            if (!Game21.playerWantsToStop) {
                                hasDrawnThisTurn = false;
                                cardsToDrawThisTurn = 1;
                                Game21.resetTurnState();
                                sendPuzzle();
                            }
                        }
                    }, 2000);
                    
                }
            }
        }
    }

    public static class Game24 {

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

    public static class Game21 {
        public static List<Integer> player1deck = new ArrayList<>();
        public static List<Integer> player2deck = new ArrayList<>();
        public static int player1Sum = 0;
        public static int player2Sum = 0;
        public static boolean player1Turn = true;
        public static boolean player2Turn = false;
        public static boolean player1HasDrawn = false;
        public static boolean player2HasDrawn = false;
        public static boolean playerWantsToStop = false;
        public static ClientHandler stoppingPlayer = null;
        public static boolean bothPlayersDrawn = false;
        public static boolean player1Stopped = false;
        public static boolean player2Stopped = false;

        public static void resetGame() {
            synchronized (Server.class) {
                resetTurnState();
                playerWantsToStop = false;
                stoppingPlayer = null;
                System.out.println("Game21 state reset. Turn state - P1: " + player1Turn + ", P2: " + player2Turn);
                System.out.println("Current decks - P1: " + player1deck + " (sum: " + player1Sum + "), P2: " + player2deck + " (sum: " + player2Sum + ")");
            }
        }

        public static void startNewRound() {
            synchronized (Server.class) {
               // player1deck.clear();
               // player2deck.clear();
                resetTurnState();
                playerWantsToStop = false;
                stoppingPlayer = null;
                System.out.println("New round started. All decks cleared.");
            }
        }

        public static void resetTurnState() {
            synchronized (Server.class) {
                player1HasDrawn = false;
                player2HasDrawn = false;
                bothPlayersDrawn = false;
                System.out.println("Turn state reset - P1 drawn: " + player1HasDrawn + ", P2 drawn: " + player2HasDrawn);
            }
        }
        
        public static void broadcastCurrentState(ClientHandler client) {
            synchronized (Server.class) {
                boolean isPlayer1 = (client == clients.get(0));
                client.write.println("YOUR_DECK:" + (isPlayer1 ? player1deck : player2deck));
                client.write.println("YOUR_SUM:" + (isPlayer1 ? player1Sum : player2Sum));
                System.out.println("State broadcast to " + (isPlayer1 ? "P1" : "P2") + 
                                 " - Deck: " + (isPlayer1 ? player1deck : player2deck) + 
                                 ", Sum: " + (isPlayer1 ? player1Sum : player2Sum));
            }
        }

        public static void setInitialTurn(ClientHandler winner) {
            synchronized (Server.class) {
                boolean isPlayer1 = (winner == clients.get(0));
                player1Turn = isPlayer1;
                player2Turn = !isPlayer1;
                System.out.println("Initial turn set - P1: " + player1Turn + ", P2: " + player2Turn);
            }
        }
        
        public static boolean canDrawCard(ClientHandler player) {
            synchronized (Server.class) {
                boolean isPlayer1 = (player == clients.get(0));
                boolean hasStopped = isPlayer1 ? player1Stopped : player2Stopped;
                
                if (hasStopped) return false;
        
                boolean canDraw = (isPlayer1 && player1Turn && !player1HasDrawn) || 
                                  (!isPlayer1 && player2Turn && !player2HasDrawn);
                System.out.println("Can draw check - Player: " + (isPlayer1 ? "1" : "2") + 
                                   ", Turn: " + (isPlayer1 ? player1Turn : player2Turn) + 
                                   ", HasDrawn: " + (isPlayer1 ? player1HasDrawn : player2HasDrawn) +
                                   ", Stopped: " + hasStopped +
                                   " = " + canDraw);
                return canDraw;
            }
        }
        

        public static Integer drawCard() {
            synchronized (Server.class) {
                if (deck21.isEmpty()) {
                    System.out.println("No cards left in deck");
                    return null;
                }
                Integer card = deck21.remove(0);
                System.out.println("Card drawn: " + card + ", Remaining cards: " + deck21.size());
                return card;
            }
        }

        public static void switchTurns() {
            synchronized (Server.class) {
                boolean oldP1Turn = player1Turn;
                boolean oldP2Turn = player2Turn;
                player1Turn = !player1Turn;
                player2Turn = !player2Turn;
                System.out.println("Turns switched - P1: " + oldP1Turn + "->" + player1Turn + 
                                 ", P2: " + oldP2Turn + "->" + player2Turn);
            }
        }

        public static void handleStopRequest(ClientHandler player) {
            synchronized (Server.class) {
                boolean isPlayer1 = (player == clients.get(0));
        
                if (isPlayer1) {
                    player1Stopped = true;
                    System.out.println("Player 1 has stopped.");
                } else {
                    player2Stopped = true;
                    System.out.println("Player 2 has stopped.");
                }
                player.write.println("YOU_STOPPED");
                for (ClientHandler client : clients) {
                    if (client != player) {
                        client.write.println("OTHER_PLAYER_STOPPED");
                    }
                }
                if (player1Stopped && player2Stopped) {
                    System.out.println("Both players stopped. Determining winner...");
                    determineWinner();
                }
                else {
                    if (isPlayer1 && !player2Stopped) {
                        player1Turn = false;
                        player2Turn = true;
                    } else if (!isPlayer1 && !player1Stopped) {
                        player2Turn = false;
                        player1Turn = true;
                    }
                    resetTurnState();
                }
            }
        }

        private static void determineWinner() {
            synchronized (Server.class) {
                int player1Score = player1Sum;
                int player2Score = player2Sum;
                int player1Difference = Math.abs(player1Score - 21);
                int player2Difference = Math.abs(player2Score - 21);
                
                String player1Message;
                String player2Message;

                if (player1Score > 21 && player2Score > 21) {
                    if (player1Difference < player2Difference) {
                        player1Message = "You win with " + player1Score + " points! (closer to 21)";
                        player2Message = "Player 1 wins with " + player1Score + " points! (closer to 21)";
                    } else if (player2Difference < player1Difference) {
                        player1Message = "Player 2 wins with " + player2Score + " points! (closer to 21)";
                        player2Message = "You win with " + player2Score + " points! (closer to 21)";
                    } else {
                        player1Message = "It's a tie! Both players busted.";
                        player2Message = "It's a tie! Both players busted.";
                    }                  
                } else if (player1Score > 21) {
                    player1Message = "You busted with " + player1Score + " points! Player 2 wins!";
                    player2Message = "Player 1 busted with " + player1Score + " points! You win!";
                } else if (player2Score > 21) {
                    player1Message = "Player 2 busted with " + player2Score + " points! You win!";
                    player2Message = "You busted with " + player2Score + " points! Player 1 wins!";
                } else if (player1Difference < player2Difference) {                
                    player1Message = "You win with " + player1Score + " points! (closer to 21)";
                    player2Message = "Player 1 wins with " + player1Score + " points! (closer to 21)";
                } else if (player2Difference < player1Difference) {
                    player1Message = "Player 2 wins with " + player2Score + " points! (closer to 21)";
                    player2Message = "You win with " + player2Score + " points! (closer to 21)";
                } else {
                    player1Message = "It's a tie! Both players have " + player1Score + " points.";
                    player2Message = "It's a tie! Both players have " + player2Score + " points.";
                }
                
                clients.get(0).write.println(player1Message);
                clients.get(1).write.println(player2Message);
                game21End = true;
            }
        }
    }
}
