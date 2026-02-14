# Hex Game Web Interface

A web-based UI for playing and visualizing Hex games.

## Features

- **Interactive Hex Board**: Canvas-based hexagonal grid with click-to-play
- **Multiple Game Modes**:
  - Local (2 players on same device)
  - vs AI (coming soon)
  - Online multiplayer (coming soon)
- **Real-time Updates**: WebSocket support for live gameplay
- **Move History**: Track all moves with visual indicators
- **Game Statistics**: View move counts and game progress
- **Responsive Design**: Works on desktop and mobile devices

## Quick Start

### 1. Install Dependencies

```bash
pip install -r requirements.txt
```

### 2. Run the Web Server

```bash
python run_web.py
```

Or directly:

```bash
python -m web.app
```

### 3. Open Browser

Navigate to: `http://localhost:5000`

## Game Rules

- **RED** connects **top to bottom**
- **BLUE** connects **left to right**
- Players alternate placing pieces
- First player to connect their sides wins
- No draws are possible in Hex

## Architecture

### Backend (Flask)

- `web/app.py`: Flask application with REST API and WebSocket endpoints
- `web/websocket.py`: WebSocket event handlers (integrated in app.py)

### Frontend

- `web/templates/index.html`: Main game page
- `web/static/css/style.css`: Styling and layout
- `web/static/js/hex-board.js`: Canvas hex board renderer
- `web/static/js/game.js`: Game logic and WebSocket integration

## API Endpoints

### REST API

- `GET /`: Main game page
- `GET /api/games`: List all active games
- `GET /api/games/<id>`: Get game state
- `POST /api/games`: Create new game

### WebSocket Events

**Client → Server:**
- `join_game`: Join a game room
- `make_move`: Make a move

**Server → Client:**
- `connected`: Connection established
- `player_assigned`: Player color assigned
- `game_state`: Current game state
- `move_made`: Move was made
- `error`: Error message

## Local Mode

If the WebSocket connection fails, the UI automatically falls back to **local mode** where two players can play on the same device without a server connection.

## Customization

### Board Size

Select from 5x5 to 13x13 boards using the dropdown menu. Default is 11x11.

### Colors

Colors are defined in CSS variables in `style.css`:
- `--red-color`: Primary red color
- `--blue-color`: Primary blue color
- Modify these to customize the appearance

### Hex Rendering

The hex board rendering can be customized in `hex-board.js`:
- `hexRadius`: Size of hexagons
- `colors`: Color definitions
- Drawing style and effects

## Development

### Running in Debug Mode

```bash
python run_web.py
```

Debug mode enables:
- Auto-reload on code changes
- Detailed error messages
- Flask debugger

### Testing

The web interface works with the existing game engine tests:

```bash
pytest tests/
```

## Integration with Game Engine

The web interface integrates with the core game engine:

```python
from engine import Game, HexBoard, Color
from players import RandomPlayer, SubprocessPlayer

# Create game
game = Game(board_size=11, red_player=player1, blue_player=player2)
result = game.play()
```

## Future Enhancements

- [ ] AI opponent using RandomPlayer
- [ ] Online multiplayer with matchmaking
- [ ] Game replay/analysis
- [ ] Tournament mode
- [ ] Move suggestions/hints
- [ ] Undo/redo functionality
- [ ] Save/load games
- [ ] Player statistics

## Browser Support

Tested on:
- Chrome/Edge (recommended)
- Firefox
- Safari

Requires JavaScript and HTML5 Canvas support.

## Troubleshooting

### WebSocket Connection Failed

If you see "Offline (Local Mode)", the WebSocket connection failed. The UI will still work for local 2-player games.

### Canvas Not Rendering

Ensure your browser supports HTML5 Canvas. Try refreshing the page or using a modern browser.

### Port Already in Use

If port 5000 is occupied, modify `run_web.py`:

```python
run_app(host='0.0.0.0', port=8080, debug=True)
```

## License

Part of the 5800 Final Project - Hex Game Framework
