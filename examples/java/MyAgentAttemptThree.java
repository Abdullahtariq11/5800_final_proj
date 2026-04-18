import java.util.*;

/**
 * ============================================================================
 * CS 5800 Final Project: MyAgentAttemptThree.java/MVP Submission
 * Author: Abdullah Tariq, Chendong Yu
 * ============================================================================
 * Architecture: Time-Bounded Flat Monte Carlo Search
 * Key Optimizations: Zero-GC Rollouts, Asymmetric DFS, Intelligent Swap Rule
 * ============================================================================
 */
public class MyAgentAttemptThree {
    private static final int EMPTY = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int[][] DIRECTIONS = {
        {-1, 1}, {1, -1}, {0, 1}, {1, 0}, {0, -1}, {-1, 0}
    };
    private static final Random RAND = new Random();

    /**
     * Parses the standard input stream from the game engine to reconstruct the board state.
     * Extracts the board dimensions, player color, and populates the 2D array with existing moves.
     *
     * @param line The raw protocol string (e.g., "11 BLUE 5:5:R,6:6:B").
     * @return An Object array containing: [size (int), myColor (int), board (int[][]), pieceCount (int)].
     */
    private static Object[] parseBoard(String line) {
        String[] parts = line.split(" ", 3);

        int size = Integer.parseInt(parts[0]);
        int myColor = parts[1].equals("RED") ? RED : BLUE;

        int [][]board = new int[size][size];
        int pieceCount = 0;

        // Parse existing moves if they exist
        if (parts.length == 3 && !parts[2].isEmpty()) {
            String[] movesStr = parts[2].split(",");
            for (String move : movesStr) {
                String[] moveParts = move.split(":");
                int r = Integer.parseInt(moveParts[0]);
                int c = Integer.parseInt(moveParts[1]);
                board[r][c] = moveParts[2].equals("R") ? RED : BLUE;
                pieceCount++;
            }
        }

        return new Object[] { size, myColor, board, pieceCount };
    }

    /**
     * Evaluates the terminal state of the board.
     * Optimization: Due to the mathematical properties of Hex, there will be no draws,
     * this method only verifies if RED has formed a top-to-bottom connection.
     * If RED has not won on a fully populated board, BLUE wins.
     *
     * @param size    The dimension of the board.
     * @param board   The current simulation board state.
     * @param visited Pre-allocated boolean array to track visited nodes (Zero-GC).
     * @return True if RED wins, False if BLUE wins.
     */
    private static boolean checkWin(int size, int[][] board, boolean[][] visited) {
        // Reset visited array
        for (int i = 0; i < size; i++) {
            Arrays.fill(visited[i], false);
        }
        // Check if Red wins
        for (int c = 0; c < size; c++) {
            if (board[0][c] == RED && !visited[0][c]) {
                if (dfs(0, c, RED, size, board, visited)) return true;
            }
        }
        return false;
    }

    /**
     * Recursive Depth-First Search (DFS) helper to trace continuous paths of stones.
     * @param r       Current row index.
     * @param c       Current column index.
     * @param color   The color of the stone being traced (RED).
     * @param size    The dimension of the board.
     * @param board   The current simulation board state.
     * @param visited Pre-allocated boolean array to prevent cyclic loops.
     * @return True if a path successfully reaches the bottom edge (row == size - 1).
     */
    private static boolean dfs(int r, int c, int color, int size, int[][] board, boolean[][] visited) {
        if (r == size - 1) return true;
        visited[r][c] = true;
        for (int[] d : DIRECTIONS) {
            int nr = r + d[0], nc = c + d[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr][nc] == color && !visited[nr][nc]) {
                if (dfs(nr, nc, color, size, board, visited)) return true;
            }
        }
        return false;
    }

    /**
     * Conducts a single random rollout from the current board state until a terminal condition is met.
     *
     * @param size         The dimension of the board.
     * @param tempBoard    Pre-allocated board array specifically for this simulation.
     * @param currentTurn  The color of the player who makes the next random move.
     * @param emptySpots   Pre-allocated 1D array to track available coordinates.
     * @param visited      Pre-allocated boolean array for DFS win evaluation.
     * @return True if RED wins the rollout, False if BLUE wins.
     */
    private static boolean simulateRandomGame(int size, int[][] tempBoard, int currentTurn, int[] emptySpots, boolean[][] visited) {
        int emptyCount = 0;

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (tempBoard[r][c] == EMPTY) {
                    emptySpots[emptyCount++] = r * size + c;
                }
            }
        }

        // In-place Fisher-Yates shuffle
        for (int i = emptyCount - 1; i > 0; i--) {
            int j = RAND.nextInt(i + 1);
            int temp = emptySpots[i];
            emptySpots[i] = emptySpots[j];
            emptySpots[j] = temp;
        }

        // Execute randomized moves
        int turn = currentTurn;
        for (int i = 0; i < emptyCount; i++) {
            int spot = emptySpots[i];
            tempBoard[spot / size][spot % size] = turn;
            turn = (turn == RED) ? BLUE : RED;
        }
        return checkWin(size, tempBoard, visited);
    }

    /**
     * The core Time-Bounded Flat Monte Carlo Search decision engine.
     * Iterates over all legal moves, running repeated simulations for each until the strict
     * time limit is approached. Selects the move with the highest empirical win rate.
     *
     * @param size       The dimension of the board.
     * @param myColor    The agent's assigned color.
     * @param board      The actual game board state.
     * @param pieceCount The number of stones currently on the board.
     * @return An integer array [row, col] representing the optimal chosen move.
     */
    private static int[] chooseMove(int size, int myColor, int[][] board, int pieceCount) {
        // Center opening for RED
        if (myColor == RED && pieceCount == 0) {
            return new int[] { size / 2, size / 2 };
        }
        int[] emptySpots = new int[size * size];
        int emptyCount = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] == EMPTY) {
                    emptySpots[emptyCount++] = r * size + c;
                }
            }
        }

        // Time constraint satisfaction
        long startTime = System.currentTimeMillis();
        long timeLimit = 900;
        if(size <= 11) {
            timeLimit = 130;
        } else if(size <= 15) {
            timeLimit = 180;
        } else if(size <= 19) {
            timeLimit = 225;
        } else if(size <= 21) {
            timeLimit = 275;
        }

        int[] wins = new int[emptyCount];
        int[] visits = new int[emptyCount];

        int[][] tempBoard = new int[size][size];
        int[] rolloutEmptySpots = new int[size * size];
        boolean[][] visited = new boolean[size][size];

        // Flat Monte Carlo rollouts execution
        while (System.currentTimeMillis() - startTime < timeLimit) {
            for (int i = 0; i < emptyCount; i++) {
                int spot = emptySpots[i];
                int r = spot / size;
                int c = spot % size;
                board[r][c] = myColor;
                int nextTurn = (myColor == RED) ? BLUE : RED;

                for (int row = 0; row < size; row++) {
                    System.arraycopy(board[row], 0, tempBoard[row], 0, size);
                }

                boolean redWon = simulateRandomGame(size, tempBoard, nextTurn, rolloutEmptySpots, visited);

                if ((myColor == RED && redWon) || (myColor == BLUE && !redWon)) {
                    wins[i]++;
                }
                visits[i]++;
                board[r][c] = EMPTY;
            }
        }

        // Aggregate results and locate the most robust move
        int bestSpot = emptySpots[0];
        double bestRate = -1;
        for (int i = 0; i < emptyCount; i++) {
            if (visits[i] == 0) continue;
            double rate = (double) wins[i] / visits[i];
            if (rate > bestRate) {
                bestRate = rate;
                bestSpot = emptySpots[i];
            }
        }

        return new int[] { bestSpot / size, bestSpot % size };
    }

    /**
     * Strategic evaluation logic for the First-Turn Swap Rule.
     * Avoids blindly swapping if near the edges.
     * Only invokes the swap rule if RED's opening is in the central strategic zone.
     *
     * @param size  The dimension of the board.
     * @param board The current game board.
     * @return True if BLUE should steal RED's move, False to play normally.
     */
    private static boolean shouldSwap(int size, int[][] board) {
        int redRow = -1, redCol = -1;

        // Locate RED's first and only move
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] == RED) {
                    redRow = r;
                    redCol = c;
                    break;
                }
            }
        }
        if (redRow == -1) return false;
        int center = size / 2;
        int threshold = size / 4;

        return Math.abs(redRow - center) <= threshold && Math.abs(redCol - center) <= threshold;
    }

    /**
     * JVM Warmup Routine
     */
    private static void warmupJVM() {
        int size = 11;
        int[][] tempBoard = new int[size][size];
        int[] rolloutEmptySpots = new int[size * size];
        boolean[][] visited = new boolean[size][size];

        // 1000 iterations and C2 compiler
        for (int i = 0; i < 1000; i++) {
            for (int r = 0; r < size; r++) {
                Arrays.fill(tempBoard[r], EMPTY);
            }
            simulateRandomGame(size, tempBoard, RED, rolloutEmptySpots, visited);
        }
    }

    /**
     * Entry point for the agent process.
     * Continuously reads state updates from stdin, computes optimal responses,
     * and flushes coordinates to stdout as per the communication protocol.
     */
    public static void main(String[] args) {
        warmupJVM();
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in));

            String line;
            while ((line = reader.readLine()) != null) {
                Object[] parsed = parseBoard(line);
                int size = (int) parsed[0];
                int myColor = (int) parsed[1];
                int[][] board = (int[][]) parsed[2];
                int pieceCount = (int) parsed[3];

                // Phase 1: Evaluate the Swap Rule condition
                if (myColor == BLUE && pieceCount == 1) {
                    if (shouldSwap(size, board)) {
                        System.out.println("swap");
                        System.out.flush();
                        continue;
                    } else {
                        System.err.println("Error found");
                    }
                }

                // Phase 2: Compute and transmit the optimal move
                int[] move = chooseMove(size, myColor, board, pieceCount);
                System.out.println(move[0] + " " + move[1]);
                System.out.flush();
            }

            reader.close();

        } catch (Exception e) {
            System.err.println("Exception occurred: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}