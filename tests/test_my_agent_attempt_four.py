"""
Integration tests for the Java Hex agent implementation in Attempt Four.
"""

import shutil
import subprocess
import unittest
from pathlib import Path

from engine.board import HexBoard
from engine.constants import Color, get_timeout_for_board_size
from engine.game import GameController
from players.base import Player
from players.subprocess_player import SubprocessPlayer


class FastTestPlayer(Player):
    """Simple in-process opponent used to isolate Java agent limits."""

    def initialize(self, board_size: int) -> bool:
        """Initializes the test player."""
        return True

    def get_move(self, board: HexBoard):
        """Returns the first legal move, or swap on the first BLUE turn."""
        if self.color == Color.BLUE and len(board.move_history) == 1 and not board.swap_used:
            return "swap"

        for row in range(board.size):
            for col in range(board.size):
                if board.get_cell(row, col) == Color.EMPTY:
                    return (row, col)
        return None


class TestMyAgentAttemptFour(unittest.TestCase):
    """Checks that Attempt Four stays within limits and handles key tactics."""

    ROOT = Path(__file__).resolve().parents[1]
    JAVA_SOURCE = ROOT / "examples" / "java" / "MyAgentAttemptFour.java"
    JAVA_CLASS_DIR = ROOT / "examples" / "java"
    JAVA_CLASS_NAME = "MyAgentAttemptFour"
    BOARD_SIZES = (11, 15, 19, 21)
    MAX_TURNS_PER_GAME = 24
    MEMORY_LIMIT_MB = 64.0

    @classmethod
    def setUpClass(cls):
        """Compiles the Java agent once for the integration tests."""
        if shutil.which("javac") is None or shutil.which("java") is None:
            raise unittest.SkipTest("Java runtime or compiler is not available")

        subprocess.run(
            ["javac", str(cls.JAVA_SOURCE)],
            check=True,
            cwd=cls.ROOT,
        )

    def test_red_agent_stays_within_limits_on_supported_board_sizes(self):
        """Runs the Java agent as RED and checks for timeout or memory failures."""
        for board_size in self.BOARD_SIZES:
            with self.subTest(board_size=board_size):
                controller, red_player, blue_player = self._start_game(board_size, Color.RED)
                try:
                    self._play_game_prefix(controller, self.MAX_TURNS_PER_GAME)
                    self._assert_agent_stayed_within_limits(controller, red_player, Color.RED, board_size)
                finally:
                    self._cleanup_player(red_player)
                    self._cleanup_player(blue_player)

    def test_blue_agent_swaps_against_central_opening(self):
        """BLUE should use the swap rule against a strong central first move."""
        board = HexBoard(11)
        self.assertEqual(board.make_move(5, 5, Color.RED).value, "success")

        player = self._create_agent(Color.BLUE, board.size)
        try:
            self.assertTrue(player.initialize(board.size))
            move = player.get_move(board)
            self.assertEqual(move, "swap")
            self.assertIsNone(player.last_error_reason)
        finally:
            self._cleanup_player(player)

    def test_agent_takes_immediate_winning_move(self):
        """The agent should finish a one-move win instead of searching deeper."""
        board = HexBoard(3)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 0, Color.RED)
        board.make_move(0, 1, Color.BLUE)

        move = self._get_agent_move(Color.RED, board)
        self.assertEqual(move, (2, 0))

    def test_agent_blocks_immediate_opponent_win(self):
        """The agent should block a one-move opponent win."""
        board = HexBoard(3)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 0, Color.RED)
        board.make_move(0, 1, Color.BLUE)

        move = self._get_agent_move(Color.BLUE, board)
        self.assertEqual(move, (2, 0))

    def _start_game(self, board_size: int, agent_color: Color):
        """Creates and initializes a game with the Java agent on the requested side."""
        controller = GameController(board_size)
        timeout = get_timeout_for_board_size(board_size)
        agent_player = self._create_agent(agent_color, board_size)
        opponent = FastTestPlayer(
            agent_color.opponent(),
            name=f"Fast Test {agent_color.opponent().name}",
        )

        if agent_color == Color.RED:
            red_player, blue_player = agent_player, opponent
        else:
            red_player, blue_player = opponent, agent_player

        started = controller.start_game(red_player, blue_player)
        self.assertTrue(started, f"Failed to start subprocess game on {board_size}x{board_size}")
        return controller, red_player, blue_player

    def _create_agent(self, color: Color, board_size: int) -> SubprocessPlayer:
        """Creates the Java subprocess player for a specific color."""
        return SubprocessPlayer(
            color,
            "java",
            ["-cp", str(self.JAVA_CLASS_DIR), self.JAVA_CLASS_NAME],
            timeout=get_timeout_for_board_size(board_size),
            memory_limit_mb=self.MEMORY_LIMIT_MB,
            name="Java Attempt Four",
        )

    def _get_agent_move(self, color: Color, board: HexBoard):
        """Starts the subprocess player, queries one move, and cleans up."""
        player = self._create_agent(color, board.size)
        try:
            self.assertTrue(player.initialize(board.size))
            move = player.get_move(board)
            self.assertIsNone(player.last_error_reason)
            return move
        finally:
            self._cleanup_player(player)

    def _play_game_prefix(self, controller: GameController, max_turns: int):
        """Advances the game for a bounded number of turns or until it ends."""
        turns_played = 0
        while turns_played < max_turns and controller.status.value == "ongoing":
            if not controller.play_turn():
                break
            turns_played += 1

        self.assertGreater(turns_played, 0, "Game did not progress")

    def _assert_agent_stayed_within_limits(
        self,
        controller: GameController,
        agent_player: SubprocessPlayer,
        agent_color: Color,
        board_size: int,
    ):
        """Checks that the subprocess agent ran without framework or limit failures."""
        self.assertIsNone(
            agent_player.last_error_reason,
            f"Agent hit a subprocess error on {board_size}x{board_size}: {agent_player.last_error_reason}",
        )
        self.assertGreater(
            len(controller.move_history),
            0,
            f"No moves were recorded on {board_size}x{board_size}",
        )
        self.assertEqual(
            controller.player_errors[agent_color],
            0,
            f"Agent made framework errors on {board_size}x{board_size}",
        )
        if agent_player.peak_memory_mb > 0:
            self.assertLessEqual(
                agent_player.peak_memory_mb,
                self.MEMORY_LIMIT_MB,
                f"Agent exceeded the memory budget on {board_size}x{board_size}",
            )

    def _cleanup_player(self, player):
        """Ensures player resources are released after each test."""
        player.cleanup()

        if not isinstance(player, SubprocessPlayer):
            return

        process = player.process
        if process is None:
            return

        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=2.0)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=2.0)

        for stream_name in ("stdin", "stdout", "stderr"):
            stream = getattr(process, stream_name, None)
            if stream is not None and not stream.closed:
                stream.close()


if __name__ == "__main__":
    unittest.main()
