package com.bot.plugin;

import com.bot.config.BotProperties;
import com.bot.game.MinesweeperGame;
import com.bot.utils.common.TypstRenderUtils;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

@Component
@Shiro
public class MinesweeperPlugin extends BotPlugin {

    private static final Logger logger = LoggerFactory.getLogger(MinesweeperPlugin.class);

    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(15);

    private final ConcurrentHashMap<Long, MinesweeperGame> games = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> gameEnabled = new ConcurrentHashMap<>();

    @Value("${typst.path:typst}")
    private String typstPath;

    @Value("${typst.font-path:}")
    private String typstFontPath;

    @Resource
    private BotProperties botProperties;

    /**
     * 管理员开关扫雷功能
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^boom\\s+(on|off)$")
    public void toggleGame(Bot bot, GroupMessageEvent event, Matcher matcher) {
        if (!botProperties.isMaster(event.getUserId())) {
            bot.sendGroupMsg(event.getGroupId(),
                    MsgUtils.builder().text("仅管理员可操作开关").build(), false);
            return;
        }

        long groupId = event.getGroupId();
        boolean enable = matcher.group(1).equalsIgnoreCase("on");
        gameEnabled.put(groupId, enable);
        if (!enable) games.remove(groupId);

        String msg = enable ? "扫雷功能已开启" : "扫雷功能已关闭";
        bot.sendGroupMsg(groupId, MsgUtils.builder().text(msg).build(), false);
    }

    /**
     * 开始新游戏 / 查看当前棋盘
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^boom$")
    public void startGame(Bot bot, GroupMessageEvent event, Matcher matcher) {
        long groupId = event.getGroupId();

        if (!gameEnabled.getOrDefault(groupId, false)) return;

        MinesweeperGame existing = games.get(groupId);

        if (existing != null && !existing.isGameOver() && !existing.isTimedOut()) {
            sendBoard(bot, groupId, existing, "游戏进行中，当前棋盘:");
            return;
        }

        games.remove(groupId);
        MinesweeperGame game = new MinesweeperGame();
        games.put(groupId, game);

        String intro = "=== 扫雷游戏开始 ===\n"
                + "输入坐标翻开格子（如 A1、b3、1C）\n"
                + "输入 f+坐标 标记旗帜（如 f A1）\n"
                + "3分钟无操作自动结束";

        bot.sendGroupMsg(groupId, MsgUtils.builder().text(intro).build(), false);
        sendBoard(bot, groupId, game, null);
    }

    /**
     * 翻开格子
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^[a-iA-I][1-9]$|^[1-9][a-iA-I]$")
    public void revealCell(Bot bot, GroupMessageEvent event, Matcher matcher) {
        long groupId = event.getGroupId();
        if (!gameEnabled.getOrDefault(groupId, false)) return;
        MinesweeperGame game = games.get(groupId);
        if (game == null) return;

        if (checkTimeout(bot, groupId, game)) return;

        int[] coord = parseCoord(event.getMessage());
        if (coord == null) return;

        int row = coord[0], col = coord[1];
        MinesweeperGame.Result result = game.reveal(row, col);

        switch (result) {
            case MINE -> {
                game.renderGameOver();
                games.remove(groupId);
                sendBoard(bot, groupId, game, null);
                bot.sendGroupMsg(groupId,
                        MsgUtils.builder().text("你踩到地雷了！游戏结束").build(),
                        false);
            }
            case WIN -> {
                games.remove(groupId);
                sendBoard(bot, groupId, game, null);
                bot.sendGroupMsg(groupId,
                        MsgUtils.builder().text("恭喜你赢了！").build(),
                        false);
            }
            case FLAGGED -> {
                bot.sendGroupMsg(groupId,
                        MsgUtils.builder().text("该位置已标记旗帜，请先取消标记 (f " + event.getMessage() + ")").build(),
                        false);
            }
            case ALREADY_REVEALED, OK -> sendBoard(bot, groupId, game, null);
        }
    }

    /**
     * 标记 / 取消旗帜
     */
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "^[fF]\\s+[a-iA-I][1-9]$|^[fF]\\s+[1-9][a-iA-I]$")
    public void flagCell(Bot bot, GroupMessageEvent event, Matcher matcher) {
        long groupId = event.getGroupId();
        if (!gameEnabled.getOrDefault(groupId, false)) return;
        MinesweeperGame game = games.get(groupId);
        if (game == null) return;

        if (checkTimeout(bot, groupId, game)) return;

        String msg = event.getMessage().trim();
        String coordStr = msg.substring(1).trim();
        int[] coord = parseCoord(coordStr);
        if (coord == null) return;

        game.toggleFlag(coord[0], coord[1]);
        sendBoard(bot, groupId, game, null);
    }

    /**
     * 通过 Typst 渲染棋盘为 PNG 并发送图片，失败时回退到文本
     */
    private void sendBoard(Bot bot, long groupId, MinesweeperGame game, String prefix) {
        String typstCode = game.renderTypst();
        File png = compileTypst(typstCode, groupId);

        if (png != null) {
            logger.debug("扫雷棋盘 PNG 渲染成功: {} ({} bytes)", png.getAbsolutePath(), png.length());
            if (prefix != null) {
                bot.sendGroupMsg(groupId, MsgUtils.builder().text(prefix).build(), false);
            }
            String imgMsg = MsgUtils.builder().img("file://" + png.getAbsolutePath()).build();
            bot.sendGroupMsg(groupId, imgMsg, false);
        } else {
            logger.warn("扫雷棋盘 Typst 渲染失败，回退到文本棋盘");
            String board = game.render();
            String text = prefix != null ? prefix + "\n" + board : board;
            bot.sendGroupMsg(groupId, MsgUtils.builder().text(text).build(), false);
        }
    }

    /**
     * 调用 Typst CLI 将代码编译为 PNG
     */
    private File compileTypst(String typstCode, long groupId) {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"), "lolibot_minesweeper");
        return TypstRenderUtils.compileToPng(
                typstPath,
                typstFontPath,
                tmpDir,
                "board_" + groupId,
                typstCode,
                RENDER_TIMEOUT
        );
    }

    /**
     * 解析坐标，支持 "A1" 和 "1A" 两种格式，返回 [row, col]
     */
    private int[] parseCoord(String input) {
        if (input == null || input.length() < 2) return null;
        char first = input.charAt(0);
        char second = input.charAt(1);

        int row, col;
        if (Character.isLetter(first) && Character.isDigit(second)) {
            col = Character.toUpperCase(first) - 'A';
            row = second - '1';
        } else if (Character.isDigit(first) && Character.isLetter(second)) {
            row = first - '1';
            col = Character.toUpperCase(second) - 'A';
        } else {
            return null;
        }

        if (row < 0 || row >= 9 || col < 0 || col >= 9) return null;
        return new int[]{row, col};
    }

    /**
     * 检查超时，超时则清理游戏并返回 true
     */
    private boolean checkTimeout(Bot bot, long groupId, MinesweeperGame game) {
        if (game.isTimedOut()) {
            games.remove(groupId);
            bot.sendGroupMsg(groupId,
                    MsgUtils.builder().text("扫雷游戏超时，已自动结束").build(),
                    false);
            return true;
        }
        return false;
    }
}
