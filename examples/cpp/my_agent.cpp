#include <iostream>
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>
#include <cstdio>
#include <ctime>
#include <sstream>

using namespace std;

// Constants for speed and clarity
const int EMPTY = 0, RED = 1, BLUE = 2;
const int MAX_SIZE = 26;

// Global buffers to avoid repeated allocation (very important for speed!)
int board[MAX_SIZE * MAX_SIZE];
int sim_board[MAX_SIZE * MAX_SIZE];
int empty_indices[MAX_SIZE * MAX_SIZE];
int shuffle_pool[MAX_SIZE * MAX_SIZE];
int stack_nodes[MAX_SIZE * MAX_SIZE];
bool visited[MAX_SIZE * MAX_SIZE];

int get_iterations_for_size(int size) {
    if (size <= 11) return 12000;
    if (size <= 15) return 8000;
    if (size <= 19) return 5000;
    if (size <= 21) return 3500;
    return 2500;
}

// Fast win check using a manual stack-based DFS
bool fast_check_win(int size, int color) {
    int top = 0;
    memset(visited, 0, size * size);

    // Initial seeds (Edges)
    for (int i = 0; i < size; ++i) {
        int idx = (color == RED) ? i : i * size;
        if (sim_board[idx] == color) {
            visited[idx] = true;
            stack_nodes[top++] = idx;
        }
    }

    int dr[] = {-1, 1, 0, 0, -1, 1};
    int dc[] = {0, 0, -1, 1, 1, -1};

    while (top > 0) {
        int curr = stack_nodes[--top];
        int r = curr / size, c = curr % size;

        // Victory condition
        if ((color == RED && r == size - 1) || (color == BLUE && c == size - 1)) return true;

        for (int i = 0; i < 6; ++i) {
            int nr = r + dr[i], nc = c + dc[i];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                int nIdx = nr * size + nc;
                if (!visited[nIdx] && sim_board[nIdx] == color) {
                    visited[nIdx] = true;
                    stack_nodes[top++] = nIdx;
                }
            }
        }
    }
    return false;
}

int main() {
    srand(time(NULL));
    string line;

    while (getline(cin, line)) {
        if (line.empty()) continue;

        // 1. Parse protocol line: "<SIZE> <YOUR_COLOR> <MOVES>"
        istringstream iss(line);
        int size;
        string myColorStr;
        if (!(iss >> size >> myColorStr)) {
            continue;
        }
        int myColor = (myColorStr == "RED") ? RED : BLUE;

        if (size < 1 || size > MAX_SIZE) {
            printf("0 0\n");
            fflush(stdout);
            continue;
        }
        
        memset(board, 0, sizeof(board));
        int movesOnBoard = 0;

        string movesPart;
        if (getline(iss >> ws, movesPart) && !movesPart.empty()) {
            stringstream movesStream(movesPart);
            string token;
            while (getline(movesStream, token, ',')) {
                int r, c;
                char clr;
                if (sscanf(token.c_str(), "%d:%d:%c", &r, &c, &clr) == 3 &&
                    r >= 0 && r < size && c >= 0 && c < size) {
                    board[r * size + c] = (clr == 'R' ? RED : BLUE);
                    movesOnBoard++;
                }
            }
        }

        // 2. SWAP RULE (Strategic for Blue)
        if (myColor == BLUE && movesOnBoard == 1) {
            printf("swap\n");
            fflush(stdout);
            continue;
        }

        // 3. PREPARE EMPTY CELLS
        int num_empty = 0;
        for (int i = 0; i < size * size; i++) {
            if (board[i] == EMPTY) empty_indices[num_empty++] = i;
        }

        if (num_empty == 0) { printf("0 0\n"); fflush(stdout); continue; }

        // 4. MONTE CARLO LOOP
        int iterations = get_iterations_for_size(size);
        vector<int> wins(num_empty, 0);
        for (int i = 0; i < iterations; i++) {
            int move_to_test_idx = i % num_empty;
            
            // Setup simulation
            memcpy(sim_board, board, sizeof(int) * size * size);
            sim_board[empty_indices[move_to_test_idx]] = myColor;

            // Prepare shuffle pool (excluding the test move)
            int pool_size = 0;
            for (int j = 0; j < num_empty; j++) {
                if (j != move_to_test_idx) shuffle_pool[pool_size++] = empty_indices[j];
            }

            // Fisher-Yates Shuffle
            for (int j = pool_size - 1; j > 0; j--) {
                int k = rand() % (j + 1);
                swap(shuffle_pool[j], shuffle_pool[k]);
            }

            // Fill board randomly
            int turn = (myColor == RED) ? BLUE : RED;
            for (int j = 0; j < pool_size; j++) {
                sim_board[shuffle_pool[j]] = turn;
                turn = (turn == RED ? BLUE : RED);
            }

            if (fast_check_win(size, myColor)) wins[move_to_test_idx]++;
        }

        // 5. CHOOSE BEST MOVE
        int best_move_idx = 0;
        for (int i = 1; i < num_empty; i++) {
            if (wins[i] > wins[best_move_idx]) best_move_idx = i;
        }

        printf("%d %d\n", empty_indices[best_move_idx] / size, empty_indices[best_move_idx] % size);
        fflush(stdout);
    }

    return 0;
}
