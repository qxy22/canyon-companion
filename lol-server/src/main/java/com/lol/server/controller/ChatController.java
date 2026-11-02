package com.lol.server.controller;

import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.ChatMessageVO;
import com.lol.server.entity.ChatMessage;
import com.lol.server.service.ChatService;
import com.lol.server.service.FileStorageService;
import com.lol.server.service.TeamupService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间聊天。房间 = 一支组队，所有接口都要求「你是这个队的成员」。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final TeamupService teamupService;
    private final FileStorageService storage;

    public ChatController(ChatService chatService, TeamupService teamupService, FileStorageService storage) {
        this.chatService = chatService;
        this.teamupService = teamupService;
        this.storage = storage;
    }

    /**
     * 上传聊天里的图片或视频，拿到 URL 后再当一条消息发出去。
     * 文件走普通 HTTP multipart，不占 WebSocket 通道 —— 大文件塞进长连接会把它堵死。
     */
    @PostMapping("/{teamupId}/upload")
    public ApiResponse<Map<String, Object>> upload(HttpServletRequest request,
                                                   @PathVariable Long teamupId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "kind", defaultValue = "IMAGE") String kind) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            teamupService.requireMember(teamupId, userId);
            FileStorageService.Kind parsed = parseKind(kind);
            FileStorageService.Stored stored = storage.store(file, parsed);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", stored.url());
            data.put("name", stored.name());
            data.put("size", stored.size());
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /** 房间历史消息，正序。beforeId 用来往上翻页。 */
    @GetMapping("/{teamupId}/messages")
    public ApiResponse<List<ChatMessageVO>> history(HttpServletRequest request,
                                                    @PathVariable Long teamupId,
                                                    @RequestParam(required = false) Long beforeId,
                                                    @RequestParam(required = false) Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(chatService.history(teamupId, userId, beforeId, size));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 我所在的各房间未读数。返回 { 队伍id: 未读条数 }，没有未读的房间不出现。 */
    @GetMapping("/unread")
    public ApiResponse<Map<Long, Long>> unread(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.ok(chatService.unreadSummary(userId));
    }

    /** 把已读位置推上去，未读红点就消了。 */
    @PostMapping("/{teamupId}/read")
    public ApiResponse<Map<String, Object>> read(HttpServletRequest request,
                                                 @PathVariable Long teamupId,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long lastId = null;
        if (body != null && body.get("lastId") != null) {
            try {
                lastId = Long.valueOf(String.valueOf(body.get("lastId")));
            } catch (NumberFormatException ignored) {
                // 前端传了个没法解析的值，按"读到最新一条"处理
            }
        }
        chatService.markRead(teamupId, userId, lastId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("teamupId", teamupId);
        return ApiResponse.ok(data);
    }

    /** 房间支持哪些消息类型、各类文件的大小上限。前端拿到后不用把限制写死在代码里。 */
    @GetMapping("/limits")
    public ApiResponse<Map<String, Object>> limits() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("textMax", ChatService.MAX_TEXT);
        data.put("imageMaxMb", storage.maxBytes(FileStorageService.Kind.IMAGE) / 1024 / 1024);
        data.put("videoMaxMb", storage.maxBytes(FileStorageService.Kind.VIDEO) / 1024 / 1024);
        data.put("videoMaxSeconds", 15);
        data.put("types", List.of(ChatMessage.TYPE_TEXT, ChatMessage.TYPE_IMAGE,
                ChatMessage.TYPE_VIDEO, ChatMessage.TYPE_STICKER, ChatMessage.TYPE_SYSTEM));
        return ApiResponse.ok(data);
    }

    private static FileStorageService.Kind parseKind(String raw) {
        if (raw == null) {
            return FileStorageService.Kind.IMAGE;
        }
        try {
            return FileStorageService.Kind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("文件类型只能是 IMAGE 或 VIDEO");
        }
    }
}
