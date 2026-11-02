package com.lol.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 上传文件的兜底清理。
 *
 * 解散房间时会按消息里的 URL 同步删文件，但有两类文件躲得过那一刀：
 * 1. 传成功了、消息却没发出去的（用户传完直接关了页面）—— 没有任何消息引用它；
 * 2. 删除瞬间出错的（Windows 上文件被占用时 deleteIfExists 会安静地失败）。
 *
 * 所以除了同步删除，还要周期性扫一遍：磁盘上的文件如果不在任何消息的 URL
 * 白名单里，且过了保护期（怕跟正在上传的文件撞车），就删掉。
 */
@Component
public class OrphanFileSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileSweeper.class);

    private final ChatService chatService;
    private final ProfileService profileService;
    private final FileStorageService storage;

    public OrphanFileSweeper(ChatService chatService, ProfileService profileService, FileStorageService storage) {
        this.chatService = chatService;
        this.profileService = profileService;
        this.storage = storage;
    }

    /** 启动时先扫一遍：上次进程退出前留下的没发出去的文件都在这里。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        sweep("启动");
    }

    /** 每 10 分钟扫一次，兜住运行期间的漏网之鱼。 */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    public void periodic() {
        sweep("定时");
    }

    private void sweep(String trigger) {
        try {
            // 白名单 = 聊天消息引用的文件 + 用户正在用的头像。
            // 少了后者，换掉的旧头像会一直堆在磁盘上。
            Set<String> live = new HashSet<>(chatService.liveFileUrls());
            live.addAll(profileService.liveAvatarUrls());
            int n = storage.sweep(live);
            if (n > 0) {
                log.info("{}清理：删除了 {} 个没人引用的上传文件", trigger, n);
            }
        } catch (Exception e) {
            // 清理失败不能影响服务，下一轮还会再来
            log.error("{}清理上传文件失败", trigger, e);
        }
    }
}
