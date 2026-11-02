package com.lol.server.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 聊天里发的图片和视频存在后端本地磁盘上。
 *
 * 选本地而不是云存储，是因为答辩现场可能断网，外部依赖一断这块就演示不了。
 * 代价是磁盘由我们自己管，所以删除消息时必须把文件一起删掉（见 delete 与 sweep）。
 */
@Service
public class FileStorageService {

    /** 对外暴露的 URL 前缀，跟 UploadWebConfig 里注册的一致 */
    public static final String URL_PREFIX = "/uploads/";

    public enum Kind {
        IMAGE("图片", List.of("png", "jpg", "jpeg", "gif", "webp"), Set.of("image/"), "chat"),
        VIDEO("视频", List.of("mp4", "webm"), Set.of("video/"), "chat"),
        // 头像单独一个目录：清理头像孤儿文件时不用把聊天文件也翻一遍，
        // 而且头像的存活依据是用户表，不是聊天消息表
        AVATAR("头像", List.of("png", "jpg", "jpeg", "gif", "webp"), Set.of("image/"), "avatar");

        private final String label;
        private final Set<String> exts;
        /** 提示里给扩展名排好序。Set.of 的迭代顺序每次启动都不一样，
         *  直接拼出来的文案会是「只能发 webp / jpeg / jpg / gif / png」这种乱序。 */
        private final String extText;
        private final Set<String> contentTypePrefixes;
        private final String dir;

        Kind(String label, List<String> extList, Set<String> contentTypePrefixes, String dir) {
            this.label = label;
            this.exts = Set.copyOf(extList);
            this.extText = String.join(" / ", extList);
            this.contentTypePrefixes = contentTypePrefixes;
            this.dir = dir;
        }

        public String getDir() {
            return dir;
        }
    }

    /** 存下来的文件，交给调用方拼进消息里 */
    public record Stored(String url, String name, long size) {
    }

    @Value("${app.upload.dir:./uploads}")
    private String rootConfig;

    @Value("${app.upload.image-max-mb:5}")
    private long imageMaxMb;

    @Value("${app.upload.video-max-mb:20}")
    private long videoMaxMb;

    /** 头像会被到处展示（大厅、在线列表、聊天），没必要存原图，2MB 足够 */
    @Value("${app.upload.avatar-max-mb:2}")
    private long avatarMaxMb;

    /** 扫孤儿文件时，多少分钟内新建的文件不动它 —— 刚传完还没发出去的消息会被误删 */
    @Value("${app.upload.protect-minutes:30}")
    private long protectMinutes;

    private Path root;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @PostConstruct
    public void init() {
        // java -jar 的工作目录不固定，配置里的相对路径必须先解析成绝对路径，
        // 否则换个目录启动就存到别的地方去了。
        root = Paths.get(rootConfig).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("聊天文件目录创建失败：" + root, e);
        }
    }

    public Path getRoot() {
        return root;
    }

    public long maxBytes(Kind kind) {
        long mb = switch (kind) {
            case VIDEO -> videoMaxMb;
            case AVATAR -> avatarMaxMb;
            default -> imageMaxMb;
        };
        return mb * 1024 * 1024;
    }

    public String limitText(Kind kind) {
        return (maxBytes(kind) / 1024 / 1024) + "MB";
    }

    /**
     * 存一个文件，返回可访问的 URL。
     * 三道校验：扩展名白名单 → Content-Type → 大小。少任何一道都能被人塞进恶意文件。
     */
    public Stored store(MultipartFile file, Kind kind) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("没有收到文件");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(original);
        if (ext.isEmpty() || !kind.exts.contains(ext)) {
            throw new IllegalArgumentException(kind.label + "格式不支持，只能发 " + kind.extText);
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (kind.contentTypePrefixes.stream().noneMatch(contentType::startsWith)) {
            throw new IllegalArgumentException("文件内容看起来不是" + kind.label + "，换一个文件试试");
        }

        long max = maxBytes(kind);
        if (file.getSize() > max) {
            throw new IllegalArgumentException(
                    kind.label + "不能超过 " + limitText(kind) + "，当前 " + readableSize(file.getSize()));
        }

        // 文件名一律用 UUID 重新生成：原始名字可能带 ../../ 造成路径穿越，
        // 也可能是中文导致 URL 编码问题。原始名只用于展示。
        String storedName = UUID.randomUUID() + "." + ext;
        String day = LocalDate.now().format(DAY);
        Path dir = root.resolve(kind.getDir()).resolve(day);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Stored(URL_PREFIX + kind.getDir() + "/" + day + "/" + storedName,
                    safeName(original), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException(kind.label + "保存失败，请重试", e);
        }
    }

    /** 删掉一个 URL 对应的文件。只认我们自己前缀下的路径，防止被拿来删别的文件。 */
    public boolean delete(String url) {
        Path p = resolveOf(url);
        if (p == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 清理没人引用的文件。
     * 消息被删了、头像被换掉了、或者因为程序崩溃没删干净时，靠这个兜底。
     *
     * @param liveUrls 还在使用的 URL 白名单（聊天消息的 + 用户正在用的头像）
     */
    public int sweep(Set<String> liveUrls) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        long deadline = System.currentTimeMillis() - protectMinutes * 60 * 1000;
        List<Path> orphans = new ArrayList<>();
        // 整个上传目录都扫，chat 和 avatar 各管各的，不用分两次
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String url = URL_PREFIX + root.relativize(p).toString().replace('\\', '/');
                if (liveUrls.contains(url)) {
                    return;
                }
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < deadline) {
                        orphans.add(p);
                    }
                } catch (IOException ignored) {
                    // 读不到时间就先留着，宁可多占一点磁盘
                }
            });
        } catch (IOException e) {
            return 0;
        }

        int removed = 0;
        for (Path p : orphans) {
            try {
                Files.deleteIfExists(p);
                removed++;
            } catch (IOException ignored) {
                // 下次再试
            }
        }
        removeEmptyDirs(root);
        return removed;
    }

    /** 把空掉的日期目录收掉。自底向上，先清空的才能删掉父目录。 */
    private void removeEmptyDirs(Path base) {
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isDirectory).forEach(dirs::add);
        } catch (IOException ignored) {
            return;
        }
        dirs.sort((a, b) -> b.getNameCount() - a.getNameCount());
        for (Path d : dirs) {
            if (d.equals(base)) {
                continue;
            }
            try (Stream<Path> inner = Files.list(d)) {
                if (inner.findAny().isEmpty()) {
                    Files.deleteIfExists(d);
                }
            } catch (IOException ignored) {
                // 无所谓，下一轮再来
            }
        }
    }

    public static Set<String> emptyLiveSet() {
        return new HashSet<>();
    }

    /** URL 转磁盘路径。不是我们的前缀，或者拼出来的路径跑出了根目录，一律拒绝。 */
    private Path resolveOf(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return null;
        }
        String rel = url.substring(URL_PREFIX.length()).replace('\\', '/');
        if (rel.contains("..")) {
            return null;
        }
        Path p = root.resolve(rel).normalize();
        return p.startsWith(root) ? p : null;
    }

    private static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** 展示用的原始名：只保留文件名部分，去掉路径，长度截断 */
    private static String safeName(String original) {
        String n = original.replace('\\', '/');
        int i = n.lastIndexOf('/');
        if (i >= 0) {
            n = n.substring(i + 1);
        }
        n = n.trim();
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    public static String readableSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.0fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}
