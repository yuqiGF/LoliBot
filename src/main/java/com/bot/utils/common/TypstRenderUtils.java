package com.bot.utils.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Typst 渲染工具。
 *
 * <p>项目里有多个功能需要把 Typst 源码渲染成 PNG（例如扫雷棋盘、萌百信息卡）。
 * 这里统一处理临时文件、命令行调用、超时和错误日志，让业务类只关心“生成什么内容”。</p>
 */
public final class TypstRenderUtils {

    private static final Logger logger = LoggerFactory.getLogger(TypstRenderUtils.class);

    private TypstRenderUtils() {
    }

    /**
     * 将 Typst 源码编译为 PNG。
     *
     * @param typstPath Typst 可执行文件路径或命令名，例如 typst
     * @param fontPath  额外字体路径，多个路径使用系统 pathSeparator 分隔；可为空
     * @param workDir   临时工作目录
     * @param fileStem  临时文件名主干，不包含扩展名
     * @param typstCode Typst 源码
     * @param timeout   编译超时时间
     * @return 编译成功时返回 PNG 文件；失败时返回 null
     */
    public static File compileToPng(
            String typstPath,
            String fontPath,
            Path workDir,
            String fileStem,
            String typstCode,
            Duration timeout
    ) {
        try {
            Files.createDirectories(workDir);
            Path typFile = workDir.resolve(fileStem + ".typ");
            Path pngFile = workDir.resolve(fileStem + ".png");
            Files.writeString(typFile, typstCode, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    typstPath,
                    "compile",
                    typFile.toAbsolutePath().toString(),
                    pngFile.toAbsolutePath().toString(),
                    "--format",
                    "png"
            );
            addFontPaths(pb, fontPath);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(
                    () -> readProcessOutput(process)
            );

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                String output = readOutputSafely(outputFuture);
                logger.warn("Typst 渲染超时，fileStem={}, output={}", fileStem, output);
                return null;
            }

            String output = readOutputSafely(outputFuture);

            if (process.exitValue() == 0 && Files.exists(pngFile) && Files.size(pngFile) > 0) {
                return pngFile.toFile();
            }

            Files.deleteIfExists(pngFile);
            logger.warn("Typst 渲染失败，fileStem={}, exit={}, output={}, source={}",
                    fileStem, process.exitValue(), output, typstCode);
            return null;
        } catch (Exception e) {
            logger.warn("Typst 渲染异常，fileStem={}", fileStem, e);
            return null;
        } finally {
            try {
                Files.deleteIfExists(workDir.resolve(fileStem + ".typ"));
            } catch (Exception ignored) {
                // 临时源码清理失败不影响主流程。
            }
        }
    }

    /**
     * 附加字体目录，避免中文/Emoji 在服务器上渲染为空白。
     */
    private static void addFontPaths(ProcessBuilder pb, String fontPath) {
        if (fontPath == null || fontPath.isBlank()) {
            return;
        }

        String[] parts = fontPath.split(java.util.regex.Pattern.quote(File.pathSeparator));
        for (String rawPath : parts) {
            if (rawPath == null || rawPath.isBlank()) {
                continue;
            }
            File path = new File(rawPath.trim());
            if (!path.isAbsolute()) {
                path = path.getAbsoluteFile();
            }
            if (path.exists()) {
                pb.command().add("--font-path");
                pb.command().add(path.getAbsolutePath());
            } else {
                logger.warn("Typst 字体目录不存在，已跳过: {}", path.getAbsolutePath());
            }
        }
    }

    /**
     * 必须消费进程输出，否则 Typst 输出较多时可能填满管道导致进程卡住。
     */
    private static String readProcessOutput(Process process) {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        } catch (Exception e) {
            output.append("[读取 Typst 输出失败: ").append(e.getMessage()).append(']');
        }
        return output.toString();
    }

    private static String readOutputSafely(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "[Typst 输出尚未读取完成: " + e.getMessage() + "]";
        }
    }
}
