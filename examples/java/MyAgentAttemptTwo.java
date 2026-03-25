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
public class MyAgentAttemptTwo {

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

    /**
     * Returns all valid neighbors of a cell on a hex board.
     *
     * @param row  the row index of the cell
     * @param col  the column index of the cell
     * @param size the board size (number of rows/columns)
     * @return list of valid neighbor coordinates as {row, col} arrays
     */
    private static List<int[]> getNeighbors(int row, int col, int size) {
        List<int[]> neighborList = new ArrayList<>();
        neighborList.add(new int[] { row + 1, col - 1 });
        neighborList.add(new int[] { row, col + 1 });
        neighborList.add(new int[] { row + 1, col });
        neighborList.add(new int[] { row - 1, col + 1 });
        neighborList.add(new int[] { row - 1, col });
        neighborList.add(new int[] { row, col - 1 });

        List<int[]> resultList = new ArrayList<>();
        for (int[] result : neighborList) {
            if (result[0] >= 0 && result[0] < size && result[1] >= 0 && result[1] < size) {
                resultList.add(new int[] { result[0], result[1] });
            }
        }
        return resultList;
    }

    private static int costCompute(int row, int col, String myColor, Map<String, String> board, int size) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            return -1;
        }
        String myColorShort = myColor.equals("RED") ? "R" : "B";
        if (!board.containsKey(row + "," + col)) {
            return 1;
        } else if (board.containsKey(row + "," + col) && board.get(row + "," + col).equals(myColorShort)) {
            return 0;
        } else {
            return Integer.MAX_VALUE;
        }
    }

    private static int DijkstraALgorithm(int size, String myColor, Map<String, String> board) {
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        int[][] dist = new int[size][size];
        for (int[] row : dist) {
            Arrays.fill(row, Integer.MAX_VALUE);
        }
        for (int i = 0; i < size; i++) {
            if (myColor.equals("RED")) {
                int cost = costCompute(0, i, myColor, board, size);
                dist[0][i] = cost;
                pq.add(new int[] { cost, 0, i });
            } else {
                int cost = costCompute(i, 0, myColor, board, size);
                dist[i][0] = cost;
                pq.add(new int[] { cost, i, 0 });
            }
        }
        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            if (myColor.equals("RED") && current[1] == size - 1) {
                return current[0];
            } else if (myColor.equals("BLUE") && current[2] == size - 1) {
                return current[0];
            }

            for (int[] neighbor : getNeighbors(current[1], current[2], size)) {
                int neighborCost = costCompute(neighbor[0], neighbor[1], myColor, board, size);

                if (neighborCost == Integer.MAX_VALUE) {
                    continue;
                }
                if (current[0] == Integer.MAX_VALUE)
                    continue;
                int newDist = current[0] + neighborCost;
                if (newDist < dist[neighbor[0]][neighbor[1]]) {
                    dist[neighbor[0]][neighbor[1]] = newDist;
                    pq.add(new int[] { newDist, neighbor[0], neighbor[1] });
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int[] chooseMove(int size, String myColor, Map<String, String> board) {
        List<int[]> emptyCells = getEmptyCells(size, board);

        if (emptyCells.isEmpty()) {
            return new int[] { 0, 0 };
        }

        // Center opening for RED
        if (myColor.equals("RED") && board.size() < 1) {
            return new int[] { size / 2, size / 2 };
        }

        int bestDist = Integer.MAX_VALUE;
        int[] bestMove = emptyCells.get(0);

        for (int[] cell : emptyCells) {
            // Simulate placing our stone
            String myColorShort = myColor.equals("RED") ? "R" : "B";
            board.put(cell[0] + "," + cell[1], myColorShort);

            // Evaluate
            int d = DijkstraALgorithm(size, myColor, board);

            // Undo
            board.remove(cell[0] + "," + cell[1]);

            if (d < bestDist) {
                bestDist = d;
                bestMove = cell;
            }
        }

        return bestMove;
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
