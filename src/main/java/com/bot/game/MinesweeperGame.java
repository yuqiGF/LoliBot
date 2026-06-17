package com.bot.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MinesweeperGame {

    private static final int SIZE = 9;
    private static final int MINES = 10;
    private static final long TIMEOUT_MS = 3 * 60 * 1000;

    private final int[][] board = new int[SIZE][SIZE];
    private final boolean[][] revealed = new boolean[SIZE][SIZE];
    private final boolean[][] flagged = new boolean[SIZE][SIZE];
    private boolean minesPlaced;
    private boolean gameOver;
    private boolean won;
    private long lastActivity;
    private int revealedCount;

    private static final Random RNG = new Random();

    public MinesweeperGame() {
        this.lastActivity = System.currentTimeMillis();
    }

    // -1 = mine, 0-8 = adjacent count

    public enum Result {
        OK, ALREADY_REVEALED, FLAGGED, MINE, WIN, INVALID
    }

    public Result reveal(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return Result.INVALID;
        if (gameOver) return Result.INVALID;
        touchActivity();

        if (!minesPlaced) {
            placeMines(row, col);
            minesPlaced = true;
        }

        if (revealed[row][col]) return Result.ALREADY_REVEALED;
        if (flagged[row][col]) return Result.FLAGGED;

        if (board[row][col] == -1) {
            gameOver = true;
            revealed[row][col] = true;
            return Result.MINE;
        }

        floodFill(row, col);

        if (revealedCount == SIZE * SIZE - MINES) {
            gameOver = true;
            won = true;
            return Result.WIN;
        }
        return Result.OK;
    }

    public boolean toggleFlag(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
        if (gameOver) return false;
        if (revealed[row][col]) return false;
        touchActivity();
        flagged[row][col] = !flagged[row][col];
        return true;
    }

    public boolean isTimedOut() {
        return System.currentTimeMillis() - lastActivity > TIMEOUT_MS;
    }

    public boolean isGameOver() { return gameOver; }
    public boolean isWon() { return won; }

    private void touchActivity() {
        lastActivity = System.currentTimeMillis();
    }

    private void placeMines(int safeRow, int safeCol) {
        // collect cells excluding the safe cell and its 8 neighbors
        List<int[]> candidates = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (Math.abs(r - safeRow) <= 1 && Math.abs(c - safeCol) <= 1) continue;
                candidates.add(new int[]{r, c});
            }
        }

        // shuffle and pick first MINES
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int[] tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }

        for (int i = 0; i < MINES; i++) {
            int[] cell = candidates.get(i);
            board[cell[0]][cell[1]] = -1;
        }

        // compute adjacent counts
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == -1) continue;
                board[r][c] = countAdjacentMines(r, c);
            }
        }
    }

    private int countAdjacentMines(int row, int col) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr, nc = col + dc;
                if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && board[nr][nc] == -1) {
                    count++;
                }
            }
        }
        return count;
    }

    private void floodFill(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return;
        if (revealed[row][col]) return;
        if (flagged[row][col]) return;
        if (board[row][col] == -1) return;

        revealed[row][col] = true;
        revealedCount++;

        if (board[row][col] == 0) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    floodFill(row + dr, col + dc);
                }
            }
        }
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("   A B C D E F G H I\n");
        for (int r = 0; r < SIZE; r++) {
            sb.append(r + 1).append("  ");
            for (int c = 0; c < SIZE; c++) {
                sb.append(cellChar(r, c)).append(" ");
            }
            if (r < SIZE - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private String cellChar(int r, int c) {
        if (revealed[r][c]) {
            if (board[r][c] == -1) return "*";
            if (board[r][c] == 0) return ".";
            return String.valueOf(board[r][c]);
        }
        if (flagged[r][c]) return "F";
        return "#";
    }

    /**
     * 游戏结束时展示全部地雷位置
     */
    public String renderGameOver() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == -1) {
                    revealed[r][c] = true;
                }
            }
        }
        return render();
    }

    /**
     * 生成 Typst 代码，用于渲染棋盘为 PNG 图片
     */
    public String renderTypst() {
        StringBuilder sb = new StringBuilder();
        sb.append("#set page(width: auto, height: auto, margin: 14pt)\n");
        sb.append("#set text(font: (\"Noto Emoji\", \"Segoe UI Emoji\", \"DejaVu Sans\", \"Microsoft YaHei\", \"Arial\", \"Segoe UI\"))\n\n");
        sb.append("#let S = 34pt\n");
        sb.append("#let FS = 18pt\n");
        sb.append("#let GRAY = rgb(\"CCCCCC\")\n");
        sb.append("#let WHITE = rgb(\"FFFFFF\")\n");
        sb.append("#let RED = rgb(\"CC0000\")\n");
        sb.append("#let SK = 0.5pt + rgb(\"999999\")\n");
        sb.append("#let NC = (");
        for (int i = 0; i < NUM_COLORS.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(NUM_COLORS[i]);
        }
        sb.append(")\n\n");
        sb.append("#let c(bg, body) = rect(\n");
        sb.append("  width: S, height: S, fill: bg, stroke: SK,\n");
        sb.append("  align(center + horizon, text(size: FS, weight: \"bold\", body))\n");
        sb.append(")\n\n");
        sb.append("#align(center)[\n");
        sb.append("  #table(\n");
        sb.append("    columns: (auto,) + (S,) * 9,\n");
        sb.append("    align: center + horizon,\n\n");
        // header
        sb.append("    [ ], [A], [B], [C], [D], [E], [F], [G], [H], [I],\n");
        // rows
        for (int r = 0; r < SIZE; r++) {
            sb.append("    [").append(r + 1).append("],");
            for (int c = 0; c < SIZE; c++) {
                sb.append(" ").append(typstCell(r, c)).append(",");
            }
            sb.append("\n");
        }
        sb.append("  )\n");
        sb.append("]\n");
        return sb.toString();
    }

    private static final String[] NUM_COLORS = {
        "rgb(\"0000FF\")", "rgb(\"008013\")", "rgb(\"FF0000\")",
        "rgb(\"0000A0\")", "rgb(\"882200\")", "rgb(\"008080\")",
        "rgb(\"000000\")", "rgb(\"666666\")"
    };

    // 在 #table() 内部是 code mode，函数调用不加 # 前缀
    private String typstCell(int r, int c) {
        if (revealed[r][c]) {
            int val = board[r][c];
            if (val == -1) {
                return "c(WHITE, text(fill: RED)[💣])";
            } else if (val == 0) {
                return "c(WHITE, [])";
            } else {
                return "c(WHITE, text(fill: " + NUM_COLORS[val - 1] + ")[" + val + "])";
            }
        }
        if (flagged[r][c]) {
            return "c(GRAY, text(fill: RED)[🚩])";
        }
        return "c(GRAY, [])";
    }

    public long getRemainingSeconds() {
        long elapsed = System.currentTimeMillis() - lastActivity;
        long remaining = TIMEOUT_MS - elapsed;
        return Math.max(0, remaining / 1000);
    }
}
