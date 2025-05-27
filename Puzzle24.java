import java.io.*;
import java.util.*;

public class Puzzle24 {

    public static class Puzzle {
        private int[] numbers;

        public Puzzle(int[] numbers) {
            this.numbers = numbers;
        }

        public int[] getNumbers() {
            return numbers;
        }

        @Override
        public String toString() {
            return Arrays.toString(numbers);
        }
    }

    public static List<Puzzle> loadCSV(String file) {
        List<Puzzle> puzzles = new ArrayList<>();
        try (BufferedReader csv24 = new BufferedReader(new FileReader(file))) {
            String line;
            boolean skipHeader = true;

            while ((line = csv24.readLine()) != null) {
                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }

                String[] column = line.split(",");
                if (column.length < 2) continue; 

                String[] nums4 = column[1].trim().split(" ");
                if (nums4.length != 4) continue;

                int[] numbers = new int[4];
                for (int i = 0; i < 4; i++) {
                    numbers[i] = Integer.parseInt(nums4[i]);
                }
                puzzles.add(new Puzzle(numbers));
            }
        } catch (IOException e) {
            System.out.println("cannot rad file: " + e.getMessage());
        }
        return puzzles;
    }
}
