package com.lol.lcu;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 负责发现本地 LCU 客户端的凭证（端口 + remoting-auth-token）。
 *
 * 国服（WeGame）实测结论：
 *  - token 和端口直接写在 LeagueClientUx.exe 的命令行里（最可靠，优先用这个）；
 *  - lockfile 常被清空为 0 字节，不可靠；
 *  - 日志在 Game\Logs\LeagueClient Logs\ 下且文件名带时间戳，不是国际服的
 *    Logs\LeagueClient Ux\LeagueClientUx.log。
 */
public final class CredentialFinder {

    /** 手动填当前凭证（重启客户端会变）。想立刻验证就填这里，留空走自动发现。 */
    private static final String MANUAL_TOKEN = ""; // 例如 "cmhdUQmNxdM-WRkSY_sRNQ"
    private static final int MANUAL_PORT = 0;     // 例如 64253

    /** 你的游戏安装目录。 */
    private static final String MANUAL_INSTALL_DIR = "C:\\WeGameApps\\英雄联盟";

    /** 自动发现时逐个尝试的根目录。 */
    private static final String[] COMMON_INSTALL_DIRS = {
            "C:\\WeGameApps\\英雄联盟",
            "C:\\WeGameApps\\英雄联盟\\LeagueClient",
            "C:\\WeGameApps\\英雄联盟\\Game",
            "C:\\Riot Games\\League of Legends",
            "D:\\Riot Games\\League of Legends",
            "D:\\英雄联盟",
            "E:\\英雄联盟"
    };

    private static final int MAX_DEPTH = 6;

    private CredentialFinder() {
    }

    public static LcuCredentials discover() throws IOException {
        // 0) 手动凭证优先
        if (!MANUAL_TOKEN.isBlank() && MANUAL_PORT > 0) {
            return new LcuCredentials(MANUAL_PORT, MANUAL_TOKEN);
        }

        // 1) 命令行（国服最可靠）
        LcuCredentials fromCmd = discoverFromCommandLine();
        if (fromCmd != null) {
            return fromCmd;
        }

        // 2) 日志（国服实际路径 Game\Logs\LeagueClient Logs\，文件名带时间戳，
        //    所以不能 endsWith("LeagueClientUx.log")，改用 contains + 后缀）
        for (Path root : roots()) {
            Optional<Path> log = findFile(root,
                    name -> name.contains("LeagueClientUx") && name.endsWith(".log"));
            if (log.isPresent()) {
                LcuCredentials c = parseLog(log.get());
                if (c != null) {
                    return c;
                }
            }
        }

        // 3) lockfile（国服可能为空，最后兜底）
        for (Path root : roots()) {
            Optional<Path> lock = findFile(root, name -> name.equalsIgnoreCase("lockfile"));
            if (lock.isPresent()) {
                LcuCredentials c = parseLockfile(lock.get());
                if (c != null) {
                    return c;
                }
            }
        }

        throw new IOException(
                "未找到 LCU 凭证。请确认游戏客户端正在运行并已登录。"
                        + "若仍失败：以管理员身份运行本程序，或把当前凭证填进 MANUAL_TOKEN / MANUAL_PORT。");
    }

    // ---------- 1) 命令行 ----------

    private static LcuCredentials discoverFromCommandLine() {
        String wmic = readCommandLineViaWmic();
        System.err.println("[诊断] wmic 读到: " + abbreviate(wmic));

        String ps = readCommandLineViaPowerShell();
        System.err.println("[诊断] PowerShell 读到: " + abbreviate(ps));

        String ph = readCommandLineViaProcessHandle();
        System.err.println("[诊断] ProcessHandle 读到: " + abbreviate(ph));

        LcuCredentials c = parseCommandLine(wmic);
        if (c == null) {
            c = parseCommandLine(ps);
        }
        if (c == null) {
            c = parseCommandLine(ph);
        }
        return c;
    }

    private static String readCommandLineViaWmic() {
        try {
            Process p = new ProcessBuilder(
                    "wmic", "process", "where", "name='LeagueClientUx.exe'",
                    "get", "CommandLine", "/value")
                    .redirectErrorStream(true)
                    .start();
            String out = stripNulls(p.getInputStream().readAllBytes());
            p.waitFor();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * wmic 在新版 Windows 已被移除，改用 PowerShell 的 Get-CimInstance 读命令行。
     * 用 -EncodedCommand + base64(UTF-16LE) 传脚本，避免 ProcessBuilder 破坏引号；
     * 同时强制输出 UTF-8，避免中文乱码。
     */
    private static String readCommandLineViaPowerShell() {
        try {
            String script = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;"
                    + "$cl=(Get-CimInstance Win32_Process -Filter \"Name='LeagueClientUx.exe'\").CommandLine;"
                    + "if($cl){[Console]::Out.Write(($cl -join \"`n\"))}";
            String encoded = Base64.getEncoder().encodeToString(
                    script.getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readCommandLineViaProcessHandle() {
        return ProcessHandle.allProcesses()
                .map(p -> p.info().commandLine().orElse(""))
                .filter(c -> c.contains("LeagueClientUx.exe") && c.contains("--remoting-auth-token="))
                .findFirst()
                .orElse(null);
    }

    private static LcuCredentials parseCommandLine(String cmdLine) {
        if (cmdLine == null || cmdLine.isBlank()) {
            return null;
        }
        Matcher portMatcher = Pattern.compile("--app-port=(\\d+)").matcher(cmdLine);
        Matcher tokenMatcher = Pattern.compile("--remoting-auth-token=([^\\s\"]+)").matcher(cmdLine);
        if (portMatcher.find() && tokenMatcher.find()) {
            return new LcuCredentials(Integer.parseInt(portMatcher.group(1)), tokenMatcher.group(1));
        }
        return null;
    }

    // ---------- 2) 日志 ----------

    private static LcuCredentials parseLog(Path log) {
        if (!Files.isRegularFile(log)) {
            return null;
        }
        try {
            String content = Files.readString(log);
            Matcher portMatcher = Pattern.compile("--app-port=(\\d+)").matcher(content);
            Matcher tokenMatcher = Pattern.compile("--remoting-auth-token=([^\\s]+)").matcher(content);

            String port = lastGroup(portMatcher);
            String token = lastGroup(tokenMatcher);
            if (port == null || token == null) {
                return null;
            }
            return new LcuCredentials(Integer.parseInt(port), token);
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    private static String lastGroup(Matcher m) {
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    // ---------- 3) lockfile ----------

    private static LcuCredentials parseLockfile(Path lockfile) {
        if (!Files.isRegularFile(lockfile)) {
            return null;
        }
        try {
            String line = Files.readString(lockfile).trim();
            if (line.isBlank()) {
                return null; // 国服下 lockfile 可能被清空
            }
            String[] parts = line.split(":");
            if (parts.length < 5) {
                return null;
            }
            // 结构：[0]=name, [1]=pid, [2]=port, [3]=password, [4]=protocol
            return new LcuCredentials(Integer.parseInt(parts[2]), parts[3]);
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    // ---------- 工具 ----------

    private static Set<Path> roots() {
        Set<Path> roots = new LinkedHashSet<>();
        if (!MANUAL_INSTALL_DIR.isBlank()) {
            roots.add(Path.of(MANUAL_INSTALL_DIR));
        }
        for (String d : COMMON_INSTALL_DIRS) {
            roots.add(Path.of(d));
        }
        roots.removeIf(p -> p == null || !Files.isDirectory(p));
        return roots;
    }

    private static Optional<Path> findFile(Path root, Predicate<String> nameMatches) {
        try (Stream<Path> stream = Files.walk(root, MAX_DEPTH)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> nameMatches.test(p.getFileName().toString()))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** 诊断用：把命令行截短打印，避免刷屏。 */
    private static String abbreviate(String s) {
        if (s == null) {
            return "(null)";
        }
        String t = s.trim().replace('\r', ' ').replace('\n', ' ');
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }

    /** wmic 输出可能是 UTF-16LE，去掉空字节后剩下的就是干净 ASCII。 */
    private static String stripNulls(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        for (byte b : bytes) {
            if (b != 0) {
                out.write(b);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
