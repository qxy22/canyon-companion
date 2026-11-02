package com.lol.server.service;

import com.lol.server.constant.GameEnums;
import com.lol.server.dto.ProfileUpdateRequest;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.entity.User;
import com.lol.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final FileStorageService storage;
    private final UserDirectory directory;
    private final PresenceService presence;
    private final OfflineTeamupCleaner offlineCleaner;

    public ProfileService(UserRepository userRepository, FileStorageService storage,
                          UserDirectory directory, PresenceService presence,
                          OfflineTeamupCleaner offlineCleaner) {
        this.userRepository = userRepository;
        this.storage = storage;
        this.directory = directory;
        this.presence = presence;
        this.offlineCleaner = offlineCleaner;
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    /**
     * 查一个人的公开资料。走 UserDirectory：命中缓存就不碰数据库。
     *
     * 这是全站最常被读的数据 —— 资料页、组员卡片、比赛记录里都要它，
     * 而它每 30 分钟才会变一次。
     *
     * 注意这里拿的是缓存快照，所有「改资料」的路径都必须调 directory.evict，
     * 否则用户改完昵称刷新页面还是旧的。
     */
    public UserProfileVO getProfile(Long userId) {
        UserProfileVO vo = directory.profile(userId);
        if (vo == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return vo;
    }

    /**
     * 改完资料顺手把缓存删掉。
     *
     * 顺序必须是先落库再删缓存：反过来会有一个窗口，删完缓存、还没写库的时候
     * 另一个请求正好读到了旧数据并写回缓存，那份旧数据就再也没人去删了。
     */
    private UserProfileVO saved(User user) {
        User saved = userRepository.save(user);
        directory.evict(saved.getId());
        return UserProfileVO.from(saved);
    }

    public UserProfileVO updateProfile(Long userId, ProfileUpdateRequest req) {
        User user = getById(userId);
        // 传空串 = 清空该字段。
        // 原来的判断是 `!= null`，而前端把"用户清空了输入框"表达成 null，
        // 于是清空被当成"不更新"，字段一旦填上就再也删不掉。
        // 现在统一：null = 不动这个字段，空串 = 清空。
        if (req.getGender() != null) {
            user.setGender(blankToNull(req.getGender()));
        }
        if (req.getBio() != null) {
            user.setBio(blankToNull(req.getBio()));
        }
        // 召唤师名和段位的权威来源是游戏客户端，不是用户自己。
        // 手填会造成「段位填王者、战绩全是青铜局」这种自相矛盾的档案，
        // 所以这两个字段只接受「录入战绩」那一条链路写入。
        if (req.getSummonerName() != null && !req.getSummonerName().isBlank()) {
            throw new IllegalArgumentException("召唤师名由「录入战绩」时从游戏客户端自动同步，不能手动填写");
        }
        if (req.getRankTier() != null && !req.getRankTier().isBlank()) {
            throw new IllegalArgumentException("段位由「录入战绩」时从游戏客户端自动同步，不能手动填写");
        }
        // 头像同理，且更实际：允许手填 URL 的话，别人可以拿一个外站链接当头像，
        // 头像会跟着变成那台外部服务器上的任何东西（还能当探针看谁在访问）。
        // 头像只有一个来源 —— 上传接口。
        if (req.getAvatar() != null && !req.getAvatar().isBlank()) {
            throw new IllegalArgumentException("头像请通过「更换头像」上传图片，不能直接填写图片地址");
        }

        if (req.getRegion() != null) {
            user.setRegion(blankToNull(req.getRegion()));
        }
        if (req.getPosition() != null) {
            if (req.getPosition().isBlank()) {
                user.setPosition(null);
            } else {
                if (!GameEnums.POSITIONS.contains(req.getPosition())) {
                    throw new IllegalArgumentException("位置不合法，可选：TOP/JUNGLE/MIDDLE/BOTTOM/SUPPORT");
                }
                user.setPosition(req.getPosition());
            }
        }
        return saved(user);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 换头像：先存新文件，再改数据库，最后删掉旧文件。
     *
     * 顺序有讲究 —— 先删旧的，万一后面存新文件失败了，用户就变成没有头像了。
     * 旧文件删不掉也不要紧，孤儿文件扫描会收掉它。
     */
    @Transactional
    public UserProfileVO updateAvatar(Long userId, MultipartFile file) {
        FileStorageService.Stored stored = storage.store(file, FileStorageService.Kind.AVATAR);
        User user = getById(userId);
        String old = user.getAvatar();
        user.setAvatar(stored.url());
        User saved = userRepository.save(user);
        if (old != null && !old.isBlank()) {
            storage.delete(old);
        }
        directory.evict(userId);
        return UserProfileVO.from(saved);
    }

    /** 移除头像，回到昵称首字母。没人引用的旧文件同样会被扫描收掉。 */
    @Transactional
    public UserProfileVO removeAvatar(Long userId) {
        User user = getById(userId);
        String old = user.getAvatar();
        if (old != null && !old.isBlank()) {
            user.setAvatar(null);
            userRepository.save(user);
            storage.delete(old);
            directory.evict(userId);
        }
        return UserProfileVO.from(user);
    }

    /** 所有还在用的头像 URL，给孤儿文件扫描做白名单。 */
    public List<String> liveAvatarUrls() {
        return userRepository.findAllAvatars();
    }

    public UserProfileVO updateStatus(Long userId, String status) {
        if (status == null || !GameEnums.STATUS.contains(status)) {
            throw new IllegalArgumentException("状态不合法，可选：ONLINE/OFFLINE/BUSY");
        }
        User user = getById(userId);
        boolean wasOffline = "OFFLINE".equals(user.getStatus());
        user.setStatus(status);
        user.setLastActiveAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        // 在线索引跟着一起动。只有 ONLINE 会出现在「在线可开黑」里 ——
        // BUSY 是「游戏进行中」，语义上不该被拉进来，所以和离线一样从索引里摘掉。
        if ("ONLINE".equals(status)) {
            presence.markOnline(userId);
            // 页面回来了（刷新 / 断线重连）：撤掉可能排着队的自动退队
            offlineCleaner.cancel(userId);
        } else {
            presence.remove(userId);
            // 离线就退出所在队伍。这里刻意不立刻退：刷新页面同样会报一次离线，
            // 马上退队等于「按一下 F5 就被踢出房间」。交给它等几秒确认。
            if ("OFFLINE".equals(status) && !wasOffline) {
                offlineCleaner.schedule(userId);
            }
        }
        directory.evict(userId);
        return UserProfileVO.from(saved);
    }
}
