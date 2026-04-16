"""
Integration tests for the Java Hex agent implementation.
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


class TestMyAgentAttemptThree(unittest.TestCase):
    """Checks that the Java agent stays within runtime and memory bounds."""

    ROOT = Path(__file__).resolve().parents[1]
    JAVA_SOURCE = ROOT / "examples" / "java" / "MyAgentAttemptThree.java"
    JAVA_CLASS_DIR = ROOT / "examples" / "java"
    JAVA_CLASS_NAME = "MyAgentAttemptThree"
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

    def test_blue_agent_stays_within_limits_on_supported_board_sizes(self):
        """Runs the Java agent as BLUE and checks for timeout or memory failures."""
        for board_size in self.BOARD_SIZES:
            with self.subTest(board_size=board_size):
                controller, red_player, blue_player = self._start_game(board_size)
                try:
                    self._play_game_prefix(controller, self.MAX_TURNS_PER_GAME)
                    self.assertIsNone(
                        blue_player.last_error_reason,
                        f"Agent hit a subprocess error on {board_size}x{board_size}: {blue_player.last_error_reason}",
                    )
                    self.assertGreater(
                        len(controller.move_history),
                        0,
                        f"No moves were recorded on {board_size}x{board_size}",
                    )
                    self.assertEqual(
                        controller.player_errors[Color.BLUE],
                        0,
                        f"Blue agent made framework errors on {board_size}x{board_size}",
                    )
                    if blue_player.peak_memory_mb > 0:
                        self.assertLessEqual(
                            blue_player.peak_memory_mb,
                            self.MEMORY_LIMIT_MB,
                            f"Agent exceeded the memory budget on {board_size}x{board_size}",
                        )
                finally:
                    self._cleanup_player(red_player)
                    self._cleanup_player(blue_player)

    def _start_game(self, board_size: int):
        """Creates and initializes a game with a fast local RED player and Java BLUE agent."""
        controller = GameController(board_size)
        timeout = get_timeout_for_board_size(board_size)

        red_player = FastTestPlayer(
            Color.RED,
            name="Fast Test Red",
        )
        blue_player = SubprocessPlayer(
            Color.BLUE,
            "java",
            ["-cp", str(self.JAVA_CLASS_DIR), self.JAVA_CLASS_NAME],
            timeout=timeout,
            memory_limit_mb=self.MEMORY_LIMIT_MB,
            name="Java Attempt Three",
        )

        started = controller.start_game(red_player, blue_player)
        self.assertTrue(started, f"Failed to start subprocess game on {board_size}x{board_size}")
        return controller, red_player, blue_player

    def _play_game_prefix(self, controller: GameController, max_turns: int):
        """Advances the game for a bounded number of turns or until it ends."""
        turns_played = 0
        while turns_played < max_turns and controller.status.value == "ongoing":
            if not controller.play_turn():
                break
            turns_played += 1

        self.assertGreater(turns_played, 0, "Game did not progress")

    def _cleanup_player(self, player):
        """Ensures player resources are released after each subtest."""
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
