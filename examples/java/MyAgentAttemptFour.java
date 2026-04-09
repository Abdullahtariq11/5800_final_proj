import java.util.*;

public class MyAgentAttemptFour {
    private static final int EMPTY = 0, RED = 1, BLUE = 2;
    private static final Random RNG = new Random();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isEmpty()) continue;

            String[] parts = line.trim().split("\\s+", 3);
            int size = Integer.parseInt(parts[0]);
            int myColor = parts[1].equals("RED") ? RED : BLUE;

            int[] board = new int[size * size];
            int movesCount = 0;
            int firstMoveIndex = -1;
            if (parts.length == 3 && !parts[2].isEmpty()) {
                for (String move : parts[2].split(",")) {
                    String[] m = move.split(":");
                    int row = Integer.parseInt(m[0]);
                    int col = Integer.parseInt(m[1]);
                    int idx = row * size + col;
                    board[idx] = m[2].equals("R") ? RED : BLUE;
                    if (movesCount == 0) firstMoveIndex = idx;
                    movesCount++;
                }
            }

            // Swap only for strong opening moves near the center.
            if (myColor == BLUE && movesCount == 1 && shouldSwap(size, firstMoveIndex)) {
                System.out.println("swap");
                System.out.flush();
                continue;
            }

            int[] move = chooseMove(size, myColor, board);
            System.out.println(move[0] + " " + move[1]);
            System.out.flush();
        }
    }

    private static boolean shouldSwap(int size, int firstMoveIndex) {
        if (firstMoveIndex < 0) return false;
        int row = firstMoveIndex / size;
        int col = firstMoveIndex % size;
        int center = size / 2;
        int dist = Math.abs(row - center) + Math.abs(col - center);
        return dist <= Math.max(1, size / 5);
    }

    private static long getThinkTimeMs(int size) {
        if (size == 11) return 120;
        if (size == 15) return 170;
        if (size == 19) return 210;
        if (size == 21) return 250;
        return 900;
    }

    private static int[] chooseMove(int size, int myColor, int[] board) {
        int[] emptyIndices = new int[board.length];
        int numEmpty = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] == EMPTY) emptyIndices[numEmpty++] = i;
        }

        if (numEmpty == board.length) return new int[]{size / 2, size / 2};

        int[] wins = new int[numEmpty];
        int[] trials = new int[numEmpty];
        
        long deadline = System.nanoTime() + getThinkTimeMs(size) * 1_000_000L;

        // Reuse these arrays to avoid Garbage Collection pauses
        int[] simBoard = new int[board.length];
        int[] shuffleBox = new int[numEmpty];
        int[] queue = new int[board.length];
        boolean[] visited = new boolean[board.length];

        while (System.nanoTime() < deadline) {
            for (int i = 0; i < numEmpty; i++) {
                if (simulate(size, myColor, board, emptyIndices, numEmpty, i, simBoard, shuffleBox, queue, visited)) {
                    wins[i]++;
                }
                trials[i]++;
                if ((i & 15) == 0 && System.nanoTime() >= deadline) break;
            }
        }

        int bestIdx = 0;
        double maxRate = -1.0;
        for (int i = 0; i < numEmpty; i++) {
            if (trials[i] == 0) continue;
            double rate = (double) wins[i] / trials[i];
            if (rate > maxRate) {
                maxRate = rate;
                bestIdx = i;
            }
        }

        return new int[]{emptyIndices[bestIdx] / size, emptyIndices[bestIdx] % size};
    }

    private static boolean simulate(int size, int myColor, int[] board, int[] empty, int numEmpty, int firstIdx, 
                                    int[] simBoard, int[] shuffleBox, int[] queue, boolean[] visited) {
        System.arraycopy(board, 0, simBoard, 0, board.length);
        simBoard[empty[firstIdx]] = myColor;

        int count = 0;
        for (int i = 0; i < numEmpty; i++) {
            if (i != firstIdx) shuffleBox[count++] = empty[i];
        }

        // Fast Fisher-Yates
        for (int i = count - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int temp = shuffleBox[i];
            shuffleBox[i] = shuffleBox[j];
            shuffleBox[j] = temp;
        }

        int turn = (myColor == RED) ? BLUE : RED;
        for (int i = 0; i < count; i++) {
            simBoard[shuffleBox[i]] = turn;
            turn = (turn == RED) ? BLUE : RED;
        }

        return fastCheckWin(size, myColor, simBoard, queue, visited);
    }

    private static boolean fastCheckWin(int size, int color, int[] board, int[] queue, boolean[] visited) {
        int head = 0, tail = 0;
        Arrays.fill(visited, false);

        for (int i = 0; i < size; i++) {
            int idx = (color == RED) ? i : i * size;
            if (board[idx] == color) {
                visited[idx] = true;
                queue[tail++] = idx;
            }
        }

        int[] dr = {-1, 1, 0, 0, -1, 1};
        int[] dc = {0, 0, -1, 1, 1, -1};

        while (head < tail) {
            int curr = queue[head++];
            int r = curr / size, c = curr % size;

            if ((color == RED && r == size - 1) || (color == BLUE && c == size - 1)) return true;

            for (int i = 0; i < 6; i++) {
                int nr = r + dr[i], nc = c + dc[i];
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    int nIdx = nr * size + nc;
                    if (!visited[nIdx] && board[nIdx] == color) {
                        visited[nIdx] = true;
                        queue[tail++] = nIdx;
                    }
                }
            }
        }
        return false;
    }
}
