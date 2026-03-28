import java.util.*;

/**
 * Simple random Hex agent - Java example.
 * 
 * SIMPLIFIED VERSION - Just one function!
 * 
 * Your agent receives ONE line:
 * <SIZE> <YOUR_COLOR> <MOVES>
 * Example: 11 RED 5:5:B,6:6:R
 * 
 * Your agent outputs ONE line:
 * <ROW> <COL>
 * Example: 7 7
 * 
 * That's it! No need to track state, handle errors, or manage game flow.
 */
public class MyAgentAttemptThree {

    /**
     * Parse the board state from input line.
     * 
     * @return Object array: [size(int), myColor(String), board(Map)]
     */
    private static Object[] parseBoard(String line) {
        String[] parts = line.split(" ", 3);

        int size = Integer.parseInt(parts[0]);
        String myColor = parts[1]; // "RED" or "BLUE"

        // Parse existing moves
        Map<String, String> board = new HashMap<>();
        if (parts.length == 3 && !parts[2].isEmpty()) {
            String movesStr = parts[2];
            for (String move : movesStr.split(",")) {
                String[] moveParts = move.split(":");
                int row = Integer.parseInt(moveParts[0]);
                int col = Integer.parseInt(moveParts[1]);
                String color = moveParts[2];
                board.put(row + "," + col, color);
            }
        }

        return new Object[] { size, myColor, board };
    }

    /**
     * Get all empty cells on the board.
     */
    private static List<int[]> getEmptyCells(int size, Map<String, String> board) {
        List<int[]> empty = new ArrayList<>();

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!board.containsKey(row + "," + col)) {
                    empty.add(new int[] { row, col });
                }
            }
        }

        return empty;
    }

    private static boolean checkWin(int size, String myColor, Map<String, String> board) {
        String colorShort = myColor.equals("RED") ? "R" : "B";
        Queue<int[]> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        if (myColor.equals("RED")) {
            for (int i = 0; i < size; i++) {
                String key = "0," + i;
                if (colorShort.equals(board.get(key))) {
                    queue.add(new int[] { 0, i });
                    visited.add(key);
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                String key = i + ",0";
                if (colorShort.equals(board.get(key))) {
                    queue.add(new int[] { i, 0 });
                    visited.add(key);
                }
            }
        }

        int[][] directions = {
                { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 },
                { -1, 1 }, { 1, -1 }
        };

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int r = current[0];
            int c = current[1];

            if (myColor.equals("RED") && r == size - 1) {
                return true;
            }
            if (myColor.equals("BLUE") && c == size - 1) {
                return true;
            }

            for (int[] d : directions) {
                int nr = r + d[0];
                int nc = c + d[1];

                if (nr < 0 || nr >= size || nc < 0 || nc >= size) {
                    continue;
                }

                String neighborKey = nr + "," + nc;
                if (!visited.contains(neighborKey) && colorShort.equals(board.get(neighborKey))) {
                    visited.add(neighborKey);
                    queue.add(new int[] { nr, nc });
                }
            }
        }

        return false;
    }

    private static boolean simulateRandomGame(int size, String myColor, Map<String, String> board, String currentTurn) {
        Map<String, String> tempBoard = new HashMap<>(board);
        List<String> emptyCells = new ArrayList<>();

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                String key = r + "," + c;
                if (!tempBoard.containsKey(key) || tempBoard.get(key) == null || tempBoard.get(key).isEmpty()) {
                    emptyCells.add(key);
                }
            }
        }

        Collections.shuffle(emptyCells);

        String turn = currentTurn;
        for (String cell : emptyCells) {
            tempBoard.put(cell, turn.equals("RED") ? "R" : "B");
            turn = turn.equals("RED") ? "BLUE" : "RED";
        }

        return checkWin(size, myColor, tempBoard);
    }

    /**
     * Choose your move.
     * 
     * @param size    Board size
     * @param myColor Your color
     * @param board   Dictionary of existing moves
     * @return Array of [row, col] for your move
     */
    private static int[] chooseMove(int size, String myColor, Map<String, String> board) {
        // Center opening for RED
        if (myColor.equals("RED") && board.isEmpty()) {
            return new int[] { size / 2, size / 2 };
        }

        List<String> emptyCells = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                String key = r + "," + c;
                String value = board.get(key);
                if (value == null || value.isEmpty()) {
                    emptyCells.add(key);
                }
            }
        }

        long startTime = System.currentTimeMillis();
        long timeLimit = 120;

        int[] wins = new int[emptyCells.size()];
        int[] visits = new int[emptyCells.size()];

        while (System.currentTimeMillis() - startTime < timeLimit) {
            for (int i = 0; i < emptyCells.size(); i++) {
                if (System.currentTimeMillis() - startTime >= timeLimit)
                    break;

                Map<String, String> tempBoard = new HashMap<>(board);
                tempBoard.put(emptyCells.get(i), myColor.equals("RED") ? "R" : "B");
                String nextTurn = myColor.equals("RED") ? "BLUE" : "RED";

                if (simulateRandomGame(size, myColor, tempBoard, nextTurn)) {
                    wins[i]++;
                }
                visits[i]++;
            }
        }

        // Pick cell with best win rate
        String bestCell = emptyCells.get(0);
        double bestRate = -1;
        for (int i = 0; i < emptyCells.size(); i++) {
            if (visits[i] == 0)
                continue;
            double rate = (double) wins[i] / visits[i];
            if (rate > bestRate) {
                bestRate = rate;
                bestCell = emptyCells.get(i);
            }
        }

        String[] parts = bestCell.split(",");
        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }

    /**
     * Main function - this is all you need!
     */
    public static void main(String[] args) {
        try {
            // Use BufferedReader for better subprocess compatibility
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in));

            // Loop version - handles multiple moves
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse it
                Object[] parsed = parseBoard(line);
                int size = (int) parsed[0];
                String myColor = (String) parsed[1];
                @SuppressWarnings("unchecked")
                Map<String, String> board = (Map<String, String>) parsed[2];

                // Handles first turn logic
                if (myColor.equals("BLUE") && board.size() == 1) {
                    System.out.println("swap");
                    System.out.flush();
                    continue;
                }

                // Choose your move
                int[] move = chooseMove(size, myColor, board);

                // Output your move (don't forget to flush!)
                System.out.println(move[0] + " " + move[1]);
                System.out.flush();
            }

            reader.close();

        } catch (Exception e) {
            System.err.println("[ERROR] Exception occurred: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
