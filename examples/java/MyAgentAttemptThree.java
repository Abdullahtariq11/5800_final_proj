import java.util.*;

public class MyAgentAttemptThree {
    private static final int EMPTY = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int[][] DIRECTIONS = {
        {-1, 1}, {1, -1}, {0, 1}, {1, 0}, {0, -1}, {-1, 0}
    };
    private static final Random RAND = new Random();

    /**
     * Parses the standard input stream to reconstruct the 2D board state.
     * * @param line Raw input string from the game engine.
     * @return Object array containing: [size(int), myColor(int), board(int[][]), pieceCount(int)]
     */
    private static Object[] parseBoard(String line) {
        String[] parts = line.split(" ", 3);

        int size = Integer.parseInt(parts[0]);
        int myColor = parts[1].equals("RED") ? RED : BLUE;

        int [][]board = new int[size][size];
        int pieceCount = 0;

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
     * Evaluates terminal state via Depth-First Search.
     * Optimized to only check the RED win condition (Hex implies BLUE wins otherwise).
     */
    private static boolean checkWin(int size, int[][] board) {
        boolean[][] visited = new boolean[size][size];
        for (int c = 0; c < size; c++) {
            if (board[0][c] == RED && !visited[0][c]) {
                if (dfs(0, c, RED, size, board, visited)) return true;
            }
        }
        return false;
    }

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
     * Executes a fast random rollout from the given board state.
     * Utilizes an in-place Fisher-Yates shuffle to minimize memory allocation.
     */
    private static boolean simulateRandomGame(int size, int[][] tempBoard, int currentTurn) {
        int[] emptySpots = new int[size * size];
        int emptyCount = 0;

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (tempBoard[r][c] == EMPTY) {
                    emptySpots[emptyCount++] = r * size + c;
                }
            }
        }

        for (int i = emptyCount - 1; i > 0; i--) {
            int j = RAND.nextInt(i + 1);
            int temp = emptySpots[i];
            emptySpots[i] = emptySpots[j];
            emptySpots[j] = temp;
        }

        int turn = currentTurn;
        for (int i = 0; i < emptyCount; i++) {
            int spot = emptySpots[i];
            tempBoard[spot / size][spot % size] = turn;
            turn = (turn == RED) ? BLUE : RED;
        }
        return checkWin(size, tempBoard);
    }

    /**
     * Determines the optimal move using a time-bounded Flat Monte Carlo search.
     */
    private static int[] chooseMove(int size, int myColor, int[][] board, int pieceCount) {
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

        long startTime = System.currentTimeMillis();
        long timeLimit = 120;

        if(size<=11){
            timeLimit=120;
        }
        else if(size<=15){
            timeLimit=170;
        }
        else if(size<=19){
            timeLimit=220;
        }
        else if(size <= 21){
            timeLimit=270;
        }

        int[] wins = new int[emptyCount];
        int[] visits = new int[emptyCount];

        while (System.currentTimeMillis() - startTime < timeLimit) {
            for (int i = 0; i < emptyCount; i++) {
                int spot = emptySpots[i];
                int r = spot / size;
                int c = spot % size;

                board[r][c] = myColor;
                int nextTurn = (myColor == RED) ? BLUE : RED;

                int[][] tempBoard = copyBoard(board, size);
                boolean redWon = simulateRandomGame(size, tempBoard, nextTurn);

                if ((myColor == RED && redWon) || (myColor == BLUE && !redWon)) {
                    wins[i]++;
                }
                visits[i]++;

                board[r][c] = EMPTY;
            }
        }

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
     * Creates a deep copy of the current board.
     */
    private static int[][] copyBoard(int[][] original, int size) {
        int[][] copy = new int[size][size];
        for (int r = 0; r < size; r++) {
            System.arraycopy(original[r], 0, copy[r], 0, size);
        }
        return copy;
    }

    /**
     * Entry point for the agent process.
     * Manages standard I/O communication with the game platform.
     */
    public static void main(String[] args) {
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

                if (myColor == BLUE && pieceCount == 1) {
                    System.out.println("swap");
                    System.out.flush();
                    continue;
                }

                int[] move = chooseMove(size, myColor, board, pieceCount);

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
