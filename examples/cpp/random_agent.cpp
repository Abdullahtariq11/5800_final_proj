/**
 * Simple random Hex agent - C++ example.
 * 
 * SIMPLIFIED VERSION - Just one function!
 * 
 * Your agent receives ONE line:
 *   <SIZE> <YOUR_COLOR> <MOVES>
 *   Example: 11 RED 5:5:B,6:6:R
 * 
 * Your agent outputs ONE line:
 *   <ROW> <COL>
 *   Example: 7 7
 * 
 * That's it! No need to track state, handle errors, or manage game flow.
 *
 * Compile: g++ -std=c++11 -o random_agent random_agent.cpp
 * Run: ./random_agent
 */

#include <iostream>
#include <string>
#include <sstream>
#include <map>
#include <vector>
#include <cstdlib>
#include <ctime>

using namespace std;

/**
 * Parse the board state from input line.
 */
tuple<int, string, map<pair<int, int>, char>> parseBoard(const string& line) {
    istringstream iss(line);
    int size;
    string myColor, movesStr;
    
    iss >> size >> myColor;
    
    // Parse existing moves
    map<pair<int, int>, char> board;
    getline(iss, movesStr);
    
    if (!movesStr.empty()) {
        // Remove leading space
        movesStr = movesStr.substr(1);
        
        istringstream movesStream(movesStr);
        string move;
        
        while (getline(movesStream, move, ',')) {
            int row, col;
            char color;
            char colon1, colon2;
            
            istringstream moveStream(move);
            moveStream >> row >> colon1 >> col >> colon2 >> color;
            
            board[{row, col}] = color;
        }
    }
    
    return make_tuple(size, myColor, board);
}

/**
 * Get all empty cells on the board.
 */
vector<pair<int, int>> getEmptyCells(int size, const map<pair<int, int>, char>& board) {
    vector<pair<int, int>> empty;
    
    for (int row = 0; row < size; row++) {
        for (int col = 0; col < size; col++) {
            if (board.find({row, col}) == board.end()) {
                empty.push_back({row, col});
            }
        }
    }
    
    return empty;
}

/**
 * Choose your move. This is where your AI logic goes!
 */
pair<int, int> chooseMove(int size, const string& myColor, 
                          const map<pair<int, int>, char>& board) {
    // Simple strategy: pick a random empty cell
    vector<pair<int, int>> emptyCells = getEmptyCells(size, board);
    
    if (emptyCells.empty()) {
        return {0, 0};  // Shouldn't happen
    }
    
    int index = rand() % emptyCells.size();
    return emptyCells[index];
}

/**
 * Main function - this is all you need!
 */
int main() {
    // Seed random number generator
    srand(time(nullptr));
    
    try {
        // Read the board state (one line)
        string line;
        getline(cin, line);
        
        // Parse it
        int size;
        string myColor;
        map<pair<int, int>, char> board;
        tie(size, myColor, board) = parseBoard(line);
        
        // Choose your move
        pair<int, int> move = chooseMove(size, myColor, board);
        
        // Output your move (don't forget to flush!)
        cout << move.first << " " << move.second << endl;
        cout.flush();
        
    } catch (const exception& e) {
        cerr << "Error: " << e.what() << endl;
        return 1;
    }
    
    return 0;
}



