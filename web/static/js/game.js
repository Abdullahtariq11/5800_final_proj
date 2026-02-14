/**
 * Main game logic and UI integration
 */

let hexBoard;
let socket;
let gameId = null;
let currentPlayer = null;
let currentTurn = 'RED';
let moveHistory = [];
let redMoves = 0;
let blueMoves = 0;

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    initializeGame();
    setupEventListeners();
    connectWebSocket();
});

/**
 * Initialize the hex board
 */
function initializeGame() {
    const boardSize = parseInt(document.getElementById('board-size').value);
    hexBoard = new HexBoard('hex-board', boardSize);
    
    hexBoard.setClickHandler((row, col) => {
        if (currentPlayer && currentTurn === currentPlayer) {
            makeMove(row, col);
        } else if (!gameId) {
            // Local game without server
            makeLocalMove(row, col);
        }
    });
}

/**
 * Setup UI event listeners
 */
function setupEventListeners() {
    document.getElementById('new-game-btn').addEventListener('click', createNewGame);
    document.getElementById('reset-btn').addEventListener('click', resetGame);
    document.getElementById('play-again-btn').addEventListener('click', createNewGame);
    document.getElementById('board-size').addEventListener('change', () => {
        const newSize = parseInt(document.getElementById('board-size').value);
        hexBoard.reset(newSize);
        resetGameState();
    });
}

/**
 * Connect to WebSocket server
 */
function connectWebSocket() {
    try {
        socket = io();
        
        socket.on('connect', () => {
            console.log('Connected to server');
            updateConnectionStatus(true);
        });
        
        socket.on('disconnect', () => {
            console.log('Disconnected from server');
            updateConnectionStatus(false);
        });
        
        socket.on('connected', (data) => {
            console.log('Socket ID:', data.socket_id);
        });
        
        socket.on('player_assigned', (data) => {
            currentPlayer = data.color;
            console.log('Assigned as:', currentPlayer);
        });
        
        socket.on('game_state', (data) => {
            updateGameState(data);
        });
        
        socket.on('move_made', (data) => {
            handleMoveMade(data);
        });
        
        socket.on('error', (data) => {
            showError(data.message);
        });
    } catch (error) {
        console.error('WebSocket connection failed:', error);
        updateConnectionStatus(false);
        // Enable local play mode
        enableLocalMode();
    }
}

/**
 * Update connection status indicator
 */
function updateConnectionStatus(connected) {
    const statusDot = document.querySelector('.status-dot');
    const statusText = document.getElementById('connection-text');
    
    if (connected) {
        statusDot.classList.add('connected');
        statusDot.classList.remove('disconnected');
        statusText.textContent = 'Connected';
    } else {
        statusDot.classList.remove('connected');
        statusDot.classList.add('disconnected');
        statusText.textContent = 'Offline (Local Mode)';
    }
}

/**
 * Create a new game
 */
function createNewGame() {
    const boardSize = parseInt(document.getElementById('board-size').value);
    const mode = document.getElementById('game-mode').value;
    
    if (socket && socket.connected) {
        // Create game on server
        fetch('/api/games', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ board_size: boardSize, mode: mode })
        })
        .then(response => response.json())
        .then(data => {
            gameId = data.game_id;
            socket.emit('join_game', { game_id: gameId, color: 'RED' });
            resetGameState();
        })
        .catch(error => {
            console.error('Error creating game:', error);
            enableLocalMode();
        });
    } else {
        // Local mode
        enableLocalMode();
    }
    
    hideWinnerOverlay();
}

/**
 * Enable local play mode (no server)
 */
function enableLocalMode() {
    gameId = null;
    currentPlayer = null;
    resetGameState();
}

/**
 * Reset game state
 */
function resetGameState() {
    currentTurn = 'RED';
    moveHistory = [];
    redMoves = 0;
    blueMoves = 0;
    updateTurnDisplay();
    updateMoveHistory();
    updateStats();
}

/**
 * Reset the game
 */
function resetGame() {
    hexBoard.reset();
    resetGameState();
    hideWinnerOverlay();
}

/**
 * Make a move (send to server if online)
 */
function makeMove(row, col) {
    if (socket && socket.connected && gameId) {
        socket.emit('make_move', {
            game_id: gameId,
            row: row,
            col: col
        });
    }
}

/**
 * Make a local move (no server)
 */
function makeLocalMove(row, col) {
    // Check if cell is empty
    if (hexBoard.boardState[row][col] !== null) {
        showError('Cell already occupied');
        return;
    }
    
    // Make the move
    hexBoard.setCell(row, col, currentTurn);
    
    // Record move
    recordMove(row, col, currentTurn);
    
    // Check for win
    if (checkWin(currentTurn)) {
        showWinner(currentTurn);
        return;
    }
    
    // Switch turns
    currentTurn = currentTurn === 'RED' ? 'BLUE' : 'RED';
    updateTurnDisplay();
}

/**
 * Handle move made event from server
 */
function handleMoveMade(data) {
    hexBoard.setCell(data.row, data.col, data.color);
    recordMove(data.row, data.col, data.color);
    currentTurn = data.current_turn;
    updateTurnDisplay();
    
    if (data.winner) {
        showWinner(data.winner);
    }
}

/**
 * Record a move in history
 */
function recordMove(row, col, color) {
    moveHistory.push({ row, col, color });
    
    if (color === 'RED') {
        redMoves++;
    } else {
        blueMoves++;
    }
    
    updateMoveHistory();
    updateStats();
}

/**
 * Update turn display
 */
function updateTurnDisplay() {
    const turnDisplay = document.getElementById('turn-display');
    const moveCount = document.getElementById('move-count');
    
    turnDisplay.textContent = `${currentTurn}'s Turn`;
    turnDisplay.style.color = currentTurn === 'RED' ? '#e74c3c' : '#3498db';
    moveCount.textContent = `Move: ${moveHistory.length}`;
}

/**
 * Update move history display
 */
function updateMoveHistory() {
    const historyDiv = document.getElementById('move-history');
    
    if (moveHistory.length === 0) {
        historyDiv.innerHTML = '<p class="empty-history">No moves yet</p>';
        return;
    }
    
    let html = '';
    moveHistory.forEach((move, index) => {
        const colorClass = move.color === 'RED' ? 'red-move' : 'blue-move';
        html += `
            <div class="move-item ${colorClass}">
                <span class="move-number">${index + 1}.</span>
                <span>${move.color}</span>
                <span class="move-coords">(${move.row}, ${move.col})</span>
            </div>
        `;
    });
    
    historyDiv.innerHTML = html;
    historyDiv.scrollTop = historyDiv.scrollHeight;
}

/**
 * Update stats display
 */
function updateStats() {
    document.getElementById('total-moves').textContent = moveHistory.length;
    document.getElementById('red-moves').textContent = redMoves;
    document.getElementById('blue-moves').textContent = blueMoves;
}

/**
 * Update game state from server
 */
function updateGameState(data) {
    hexBoard.setBoardState(data.board_state);
    currentTurn = data.current_turn;
    updateTurnDisplay();
    
    if (data.game_over) {
        showWinner(data.winner);
    }
}

/**
 * Simple win detection (BFS) - client-side version
 */
function checkWin(color) {
    const size = hexBoard.size;
    const board = hexBoard.boardState;
    const visited = Array(size).fill(null).map(() => Array(size).fill(false));
    
    // Direction offsets for hex neighbors
    const directions = [
        [-1, 0], [-1, 1], [0, -1], [0, 1], [1, -1], [1, 0]
    ];
    
    // BFS from starting edge
    const queue = [];
    
    if (color === 'RED') {
        // Check top to bottom connection
        for (let col = 0; col < size; col++) {
            if (board[0][col] === 'RED') {
                queue.push([0, col]);
                visited[0][col] = true;
            }
        }
        
        while (queue.length > 0) {
            const [row, col] = queue.shift();
            
            // Check if reached bottom edge
            if (row === size - 1) {
                return true;
            }
            
            // Check neighbors
            for (const [dr, dc] of directions) {
                const newRow = row + dr;
                const newCol = col + dc;
                
                if (newRow >= 0 && newRow < size && 
                    newCol >= 0 && newCol < size &&
                    !visited[newRow][newCol] && 
                    board[newRow][newCol] === 'RED') {
                    visited[newRow][newCol] = true;
                    queue.push([newRow, newCol]);
                }
            }
        }
    } else {
        // Check left to right connection
        for (let row = 0; row < size; row++) {
            if (board[row][0] === 'BLUE') {
                queue.push([row, 0]);
                visited[row][0] = true;
            }
        }
        
        while (queue.length > 0) {
            const [row, col] = queue.shift();
            
            // Check if reached right edge
            if (col === size - 1) {
                return true;
            }
            
            // Check neighbors
            for (const [dr, dc] of directions) {
                const newRow = row + dr;
                const newCol = col + dc;
                
                if (newRow >= 0 && newRow < size && 
                    newCol >= 0 && newCol < size &&
                    !visited[newRow][newCol] && 
                    board[newRow][newCol] === 'BLUE') {
                    visited[newRow][newCol] = true;
                    queue.push([newRow, newCol]);
                }
            }
        }
    }
    
    return false;
}

/**
 * Show winner overlay
 */
function showWinner(winner) {
    const overlay = document.getElementById('winner-overlay');
    const winnerText = document.getElementById('winner-text');
    
    winnerText.textContent = `${winner} Wins! 🎉`;
    winnerText.style.color = winner === 'RED' ? '#e74c3c' : '#3498db';
    
    overlay.classList.remove('hidden');
    hexBoard.setInteractive(false);
}

/**
 * Hide winner overlay
 */
function hideWinnerOverlay() {
    const overlay = document.getElementById('winner-overlay');
    overlay.classList.add('hidden');
    hexBoard.setInteractive(true);
}

/**
 * Show error message
 */
function showError(message) {
    // Simple alert for now - could be improved with a toast notification
    console.error('Game Error:', message);
    alert(message);
}
