import java.util.*;

public class MyAgentAttemptFour {
    private static final int EMPTY = 0, RED = 1, BLUE = 2;
    private static final Random RNG = new Random();
    private static final int[][] DIRECTIONS = {
        {-1, 0}, {-1, 1}, {0, 1}, {1, 0}, {1, -1}, {0, -1}
    };
    private static final int[][] BRIDGE_OFFSETS = {
        {-1, -1}, {-1, 2}, {-2, 1}, {1, 1}, {2, -1}, {1, -2}
    };

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
        return dist <= Math.max(2, size / 3);
    }

    private static long getThinkTimeMs(int size) {
        if (size == 11) return 120;
        if (size == 15) return 170;
        if (size == 19) return 210;
        if (size == 21) return 250;
        return 900;
    }

    private static int getCandidateLimit(int size) {
        if (size <= 11) return 12;
        if (size <= 15) return 16;
        if (size <= 19) return 20;
        return 24;
    }

    private static boolean hasOccupiedNeighbor(int size, int[] board, int idx) {
        int row = idx / size;
        int col = idx % size;
        for (int[] dir : DIRECTIONS) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                if (board[nr * size + nc] != EMPTY) return true;
            }
        }
        return false;
    }

    private static boolean isNearCenter(int size, int idx, int radius) {
        int row = idx / size;
        int col = idx % size;
        int center = size / 2;
        return Math.abs(row - center) + Math.abs(col - center) <= radius;
    }

    private static double scoreMove(int size, int myColor, int[] board, int idx) {
        int row = idx / size;
        int col = idx % size;
        int center = size / 2;

        int friendlyNeighbors = 0;
        int enemyNeighbors = 0;
        int bridgeLinks = 0;
        int friendlyForwardLinks = 0;
        int enemyForwardLinks = 0;

        for (int[] dir : DIRECTIONS) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                int cell = board[nr * size + nc];
                if (cell == myColor) {
                    friendlyNeighbors++;
                    if ((myColor == RED && dir[0] != 0) || (myColor == BLUE && dir[1] != 0)) {
                        friendlyForwardLinks++;
                    }
                } else if (cell != EMPTY) {
                    enemyNeighbors++;
                    if ((myColor == RED && dir[1] != 0) || (myColor == BLUE && dir[0] != 0)) {
                        enemyForwardLinks++;
                    }
                }
            }
        }

        for (int[] offset : BRIDGE_OFFSETS) {
            int nr = row + offset[0];
            int nc = col + offset[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                if (board[nr * size + nc] == myColor) bridgeLinks++;
            }
        }

        double score = 0.0;
        score += 2.0 * (size - (Math.abs(row - center) + Math.abs(col - center)));
        score += 14.0 * friendlyNeighbors;
        score += 5.0 * friendlyForwardLinks;
        score += 3.0 * enemyNeighbors;
        score += 2.0 * enemyForwardLinks;
        score += 6.0 * bridgeLinks;

        // Favor moves that stay on the player's connection corridor.
        if (myColor == RED) {
            score += 2.0 * (size - Math.abs(col - center));
        } else {
            score += 2.0 * (size - Math.abs(row - center));
        }

        if (friendlyNeighbors >= 2) score += 16.0;
        if (friendlyNeighbors >= 3) score += 12.0;

        // Strongly discourage isolated moves unless they are central opening shapes.
        if (friendlyNeighbors == 0 && enemyNeighbors == 0) score -= 20.0;
        if (friendlyNeighbors == 0 && enemyNeighbors > 0) score -= 6.0;

        return score;
    }

    private static boolean isWinningMove(int size, int color, int[] board, int move, int[] simBoard, int[] queue, boolean[] visited) {
        System.arraycopy(board, 0, simBoard, 0, board.length);
        simBoard[move] = color;
        return fastCheckWin(size, color, simBoard, queue, visited);
    }

    private static int findImmediateWinningMove(int size, int color, int[] emptyIndices, int numEmpty, int[] board,
                                                int[] simBoard, int[] queue, boolean[] visited) {
        for (int i = 0; i < numEmpty; i++) {
            int move = emptyIndices[i];
            if (isWinningMove(size, color, board, move, simBoard, queue, visited)) {
                return move;
            }
        }
        return -1;
    }

    private static int[] chooseMove(int size, int myColor, int[] board) {
        int[] emptyIndices = new int[board.length];
        int numEmpty = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] == EMPTY) emptyIndices[numEmpty++] = i;
        }

        if (numEmpty == board.length) return new int[]{size / 2, size / 2};

        int[] simBoard = new int[board.length];
        int[] queue = new int[board.length];
        boolean[] visited = new boolean[board.length];

        int winningMove = findImmediateWinningMove(size, myColor, emptyIndices, numEmpty, board, simBoard, queue, visited);
        if (winningMove != -1) {
            return new int[]{winningMove / size, winningMove % size};
        }

        int opponent = (myColor == RED) ? BLUE : RED;
        int blockMove = findImmediateWinningMove(size, opponent, emptyIndices, numEmpty, board, simBoard, queue, visited);
        if (blockMove != -1) {
            return new int[]{blockMove / size, blockMove % size};
        }

        int occupiedCount = board.length - numEmpty;
        int[] candidateIndices = new int[numEmpty];
        double[] candidateScores = new double[numEmpty];
        int candidateCount = 0;

        for (int i = 0; i < numEmpty; i++) {
            int idx = emptyIndices[i];
            boolean nearby = hasOccupiedNeighbor(size, board, idx);
            if (occupiedCount > 2 && !nearby && !isNearCenter(size, idx, 2)) {
                continue;
            }
            candidateIndices[candidateCount] = idx;
            candidateScores[candidateCount] = scoreMove(size, myColor, board, idx);
            candidateCount++;
        }

        if (candidateCount == 0) {
            for (int i = 0; i < numEmpty; i++) {
                candidateIndices[i] = emptyIndices[i];
                candidateScores[i] = scoreMove(size, myColor, board, emptyIndices[i]);
            }
            candidateCount = numEmpty;
        }

        int topK = Math.min(candidateCount, getCandidateLimit(size));
        for (int i = 0; i < topK; i++) {
            int best = i;
            for (int j = i + 1; j < candidateCount; j++) {
                if (candidateScores[j] > candidateScores[best]) {
                    best = j;
                }
            }
            double tempScore = candidateScores[i];
            candidateScores[i] = candidateScores[best];
            candidateScores[best] = tempScore;

            int tempIdx = candidateIndices[i];
            candidateIndices[i] = candidateIndices[best];
            candidateIndices[best] = tempIdx;
        }

        int[] wins = new int[topK];
        int[] trials = new int[topK];
        
        long deadline = System.nanoTime() + getThinkTimeMs(size) * 1_000_000L;

        // Reuse these arrays to avoid Garbage Collection pauses
        int[] shuffleBox = new int[numEmpty];

        while (System.nanoTime() < deadline) {
            for (int i = 0; i < topK; i++) {
                if (simulate(size, myColor, board, emptyIndices, numEmpty, candidateIndices[i], simBoard, shuffleBox, queue, visited)) {
                    wins[i]++;
                }
                trials[i]++;
                if ((i & 15) == 0 && System.nanoTime() >= deadline) break;
            }
        }

        int bestIdx = 0;
        double maxRate = -1.0;
        for (int i = 0; i < topK; i++) {
            if (trials[i] == 0) continue;
            double rate = (double) wins[i] / trials[i];
            if (rate > maxRate) {
                maxRate = rate;
                bestIdx = i;
            }
        }

        return new int[]{candidateIndices[bestIdx] / size, candidateIndices[bestIdx] % size};
    }

    private static boolean simulate(int size, int myColor, int[] board, int[] empty, int numEmpty, int firstMove,
                                    int[] simBoard, int[] shuffleBox, int[] queue, boolean[] visited) {
        System.arraycopy(board, 0, simBoard, 0, board.length);
        simBoard[firstMove] = myColor;

        int count = 0;
        for (int i = 0; i < numEmpty; i++) {
            if (empty[i] != firstMove) shuffleBox[count++] = empty[i];
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
