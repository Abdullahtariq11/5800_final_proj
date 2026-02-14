"""
Unit tests for Hex board logic.
"""

from engine.constants import Color, MoveResult
from engine.board import HexBoard
import pytest
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))


class TestBoardBasics:
    """Test basic board initialization and operations."""

    def test_board_initialization(self):
        """Test board is initialized correctly."""
        board = HexBoard(11)
        assert board.size == 11
        assert board.get_move_count() == 0
        assert len(board.get_empty_cells()) == 121

    def test_board_size_validation(self):
        """Test board size constraints."""
        with pytest.raises(ValueError):
            HexBoard(2)  # Too small
        with pytest.raises(ValueError):
            HexBoard(27)  # Too large

    def test_empty_board(self):
        """Test all cells are empty initially."""
        board = HexBoard(5)
        for row in range(5):
            for col in range(5):
                assert board.is_empty(row, col)
                assert board.get_cell(row, col) == Color.EMPTY


class TestMoveValidation:
    """Test move validation logic."""

    def test_valid_move(self):
        """Test making a valid move."""
        board = HexBoard(11)
        result = board.make_move(5, 5, Color.RED)
        assert result == MoveResult.SUCCESS
        assert board.get_cell(5, 5) == Color.RED
        assert board.get_move_count() == 1

    def test_out_of_bounds(self):
        """Test moves outside board boundaries."""
        board = HexBoard(11)
        assert board.make_move(-1, 5, Color.RED) == MoveResult.OUT_OF_BOUNDS
        assert board.make_move(5, -1, Color.RED) == MoveResult.OUT_OF_BOUNDS
        assert board.make_move(11, 5, Color.RED) == MoveResult.OUT_OF_BOUNDS
        assert board.make_move(5, 11, Color.RED) == MoveResult.OUT_OF_BOUNDS

    def test_cell_occupied(self):
        """Test cannot place stone on occupied cell."""
        board = HexBoard(11)
        board.make_move(5, 5, Color.RED)
        assert board.make_move(5, 5, Color.BLUE) == MoveResult.CELL_OCCUPIED
        assert board.make_move(5, 5, Color.RED) == MoveResult.CELL_OCCUPIED

    def test_move_history(self):
        """Test move history is tracked correctly."""
        board = HexBoard(11)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 1, Color.BLUE)
        board.make_move(2, 2, Color.RED)

        assert board.get_move_count() == 3
        assert board.move_history[0] == (0, 0, Color.RED)
        assert board.move_history[1] == (1, 1, Color.BLUE)
        assert board.move_history[2] == (2, 2, Color.RED)

    def test_undo_move(self):
        """Test undoing moves."""
        board = HexBoard(11)
        board.make_move(5, 5, Color.RED)
        assert not board.is_empty(5, 5)

        board.undo_move()
        assert board.is_empty(5, 5)
        assert board.get_move_count() == 0

    def test_undo_empty_board(self):
        """Test undo on empty board returns False."""
        board = HexBoard(11)
        assert board.undo_move() == False


class TestNeighbors:
    """Test neighbor calculation."""

    def test_corner_neighbors(self):
        """Test neighbors for corner positions."""
        board = HexBoard(11)

        # Top-left corner
        neighbors = board.get_neighbors(0, 0)
        assert len(neighbors) == 2  # Only right and bottom neighbors
        assert (0, 1) in neighbors
        assert (1, 0) in neighbors

        # Bottom-right corner
        neighbors = board.get_neighbors(10, 10)
        assert len(neighbors) == 2  # Only left and top neighbors

    def test_edge_neighbors(self):
        """Test neighbors for edge positions."""
        board = HexBoard(11)

        # Top edge (not corner)
        neighbors = board.get_neighbors(0, 5)
        assert len(neighbors) == 4

        # Left edge (not corner)
        neighbors = board.get_neighbors(5, 0)
        assert len(neighbors) == 4

    def test_center_neighbors(self):
        """Test neighbors for center positions."""
        board = HexBoard(11)
        neighbors = board.get_neighbors(5, 5)
        assert len(neighbors) == 6  # All 6 hex directions


class TestWinDetection:
    """Test win detection logic."""

    def test_no_winner_empty_board(self):
        """Test empty board has no winner."""
        board = HexBoard(11)
        assert board.get_winner() is None
        assert not board.check_win(Color.RED)
        assert not board.check_win(Color.BLUE)

    def test_red_wins_vertical(self):
        """Test RED wins by connecting top to bottom."""
        board = HexBoard(5)
        # Create vertical path for RED from top to bottom
        for row in range(5):
            board.make_move(row, 2, Color.RED)

        assert board.check_win(Color.RED)
        assert board.get_winner() == Color.RED
        assert not board.check_win(Color.BLUE)

    def test_blue_wins_horizontal(self):
        """Test BLUE wins by connecting left to right."""
        board = HexBoard(5)
        # Create horizontal path for BLUE from left to right
        for col in range(5):
            board.make_move(2, col, Color.BLUE)

        assert board.check_win(Color.BLUE)
        assert board.get_winner() == Color.BLUE
        assert not board.check_win(Color.RED)

    def test_red_wins_diagonal(self):
        """Test RED wins with diagonal path."""
        board = HexBoard(5)
        # Create zigzag path for RED from top to bottom
        # Each position is a valid hex neighbor of the previous
        positions = [(0, 0), (1, 0), (1, 1), (2, 1), (3, 1), (4, 1)]
        for row, col in positions:
            board.make_move(row, col, Color.RED)

        assert board.check_win(Color.RED)
        assert board.get_winner() == Color.RED

    def test_blue_wins_diagonal(self):
        """Test BLUE wins with diagonal path."""
        board = HexBoard(5)
        # Create zigzag path for BLUE from left to right
        # Each position is a valid hex neighbor of the previous
        positions = [(0, 0), (0, 1), (1, 1), (1, 2), (1, 3), (1, 4)]
        for row, col in positions:
            board.make_move(row, col, Color.BLUE)

        assert board.check_win(Color.BLUE)
        assert board.get_winner() == Color.BLUE

    def test_incomplete_path_no_win(self):
        """Test incomplete path does not result in win."""
        board = HexBoard(5)
        # Create incomplete path for RED (missing one cell)
        for row in range(4):  # Only goes to row 3, not 4
            board.make_move(row, 2, Color.RED)

        assert not board.check_win(Color.RED)
        assert board.get_winner() is None

    def test_blocked_path_no_win(self):
        """Test blocked path does not result in win."""
        board = HexBoard(5)
        # Create path for RED but block it with BLUE
        for row in range(5):
            if row == 2:
                board.make_move(row, 2, Color.BLUE)  # Block
            else:
                board.make_move(row, 2, Color.RED)

        assert not board.check_win(Color.RED)
        assert board.get_winner() is None


class TestBoardUtilities:
    """Test utility methods."""

    def test_is_full(self):
        """Test board full detection."""
        board = HexBoard(3)
        assert not board.is_full()

        # Fill the board
        for row in range(3):
            for col in range(3):
                color = Color.RED if (row + col) % 2 == 0 else Color.BLUE
                board.make_move(row, col, color)

        assert board.is_full()
        assert len(board.get_empty_cells()) == 0

    def test_clone(self):
        """Test board cloning."""
        board = HexBoard(5)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 1, Color.BLUE)

        cloned = board.clone()
        assert cloned.size == board.size
        assert cloned.get_cell(0, 0) == Color.RED
        assert cloned.get_cell(1, 1) == Color.BLUE
        assert cloned.get_move_count() == board.get_move_count()

        # Verify independence
        cloned.make_move(2, 2, Color.RED)
        assert cloned.get_move_count() == 3
        assert board.get_move_count() == 2

    def test_to_string(self):
        """Test string representation."""
        board = HexBoard(3)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 1, Color.BLUE)

        board_str = board.to_string()
        assert 'R' in board_str
        assert 'B' in board_str
        assert '.' in board_str

    def test_serialization(self):
        """Test board serialization and deserialization."""
        board = HexBoard(5)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 1, Color.BLUE)
        board.make_move(2, 2, Color.RED)

        # Serialize
        data = board.to_dict()
        assert data['size'] == 5
        assert len(data['move_history']) == 3

        # Deserialize
        restored = HexBoard.from_dict(data)
        assert restored.size == board.size
        assert restored.get_cell(0, 0) == Color.RED
        assert restored.get_cell(1, 1) == Color.BLUE
        assert restored.get_cell(2, 2) == Color.RED
        assert restored.get_move_count() == 3


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
