package com.lol.server.service;

import com.lol.common.result.PageResult;
import com.lol.server.dto.MatchImportItem;
import com.lol.server.dto.MatchImportRequest;
import com.lol.server.constant.GameEnums;
import com.lol.server.dto.MatchRecordVO;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.entity.MatchRecord;
import com.lol.server.entity.User;
import com.lol.server.repository.MatchRecordRepository;
import com.lol.server.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MatchRecordService {

    private final MatchRecordRepository matchRecordRepository;
    private final UserRepository userRepository;
    private final UserDirectory directory;

    public MatchRecordService(MatchRecordRepository matchRecordRepository, UserRepository userRepository,
                              UserDirectory directory) {
        this.matchRecordRepository = matchRecordRepository;
        this.userRepository = userRepository;
        this.directory = directory;
    }

    /**
     * 导入战绩：逐场计算评分入库（按 gameId 去重），重算实力分与等级。
     *
     * 必须包在一个事务里：这里既写了不少战绩行，又改了用户的段位/昵称/实力分，
     * 中途任何一步失败都应该整批回滚，否则会出现「战绩进去了、实力分还是旧的」。
     *
     * 以前这段代码靠 open-in-view 才跑得通 —— 它改的 User 实体没有任何 save，
     * 是被后面 recalcRating 里那次 save 顺带 flush 的。关掉 open-in-view 后
     * 这条隐式耦合断了（段位怎么录都写不进去），加事务才是对的做法。
     */
    @Transactional
    public UserProfileVO importMatches(Long userId, MatchImportRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        // 玩家身份与段位的权威来源是游戏客户端：只要 LCU 读到了就覆盖，
        // 不再像原来那样"本地为空才填"。段位和召唤师名已不允许手填，
        // 否则会和战绩数据源对不上（比如把段位改成王者但战绩全是青铜局）。
        applyLcuIdentity(user, req);

        if (req.getRegion() != null && !req.getRegion().isBlank() && user.getRegion() == null) {
            user.setRegion(req.getRegion());
        }

        if (req.getMatches() != null) {
            for (MatchImportItem item : req.getMatches()) {
                if (item.getGameId() == null) {
                    continue;
                }
                if (matchRecordRepository.existsByUserIdAndGameId(userId, item.getGameId())) {
                    continue; // 去重
                }
                MatchRecord record = new MatchRecord();
                record.setUserId(userId);
                record.setGameId(item.getGameId());
                record.setWin(item.getWin() != null && item.getWin());
                record.setLane(normalizeLane(item.getLane()));
                record.setKills(item.getKills() == null ? 0 : item.getKills());
                record.setDeaths(item.getDeaths() == null ? 0 : item.getDeaths());
                record.setAssists(item.getAssists() == null ? 0 : item.getAssists());
                record.setCs(item.getCs() == null ? 0 : item.getCs());
                record.setVisionScore(item.getVisionScore() == null ? 0 : item.getVisionScore());
                record.setDuration(item.getDuration() == null ? 0 : item.getDuration());
                record.setPlayedAt(item.getPlayedAt() == null ? LocalDateTime.now()
                        : LocalDateTime.ofInstant(Instant.ofEpochMilli(item.getPlayedAt()), ZoneId.systemDefault()));
                record.setScore(scoreMatch(record));
                record.setCreatedAt(LocalDateTime.now());
                matchRecordRepository.save(record);
            }
        }

        recalcRating(userId);
        // 段位、昵称、实力分都变了，缓存里那份必须作废，
        // 否则大厅和在线列表里看到的还是上一份资料
        directory.evict(userId);
        return UserProfileVO.from(userRepository.findById(userId).orElse(user));
    }

    /** 把 LCU 读到的玩家身份与段位写进资料。读不到的字段保持原样，不清除。 */
    private void applyLcuIdentity(User user, MatchImportRequest req) {
        if (req.getPuuid() != null && !req.getPuuid().isBlank()) {
            user.setPuuid(req.getPuuid().trim());
        }
        if (req.getSummonerName() != null && !req.getSummonerName().isBlank()) {
            user.setSummonerName(req.getSummonerName().trim());
        }
        if (req.getRankTier() != null && !req.getRankTier().isBlank()) {
            String tier = req.getRankTier().trim().toUpperCase();
            if (!GameEnums.RANK_SET.contains(tier)) {
                throw new IllegalArgumentException("客户端读到的段位不在已知范围内：" + req.getRankTier());
            }
            user.setRankTier(tier);
        }
    }

    /** 查看某用户战绩（分页）。 */
    public PageResult<MatchRecordVO> userMatches(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        Page<MatchRecord> result = matchRecordRepository.findByUserIdOrderByPlayedAtDesc(userId, pageable);
        List<MatchRecordVO> list = result.getContent().stream()
                .map(MatchRecordVO::from)
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotalElements(), safePage, safeSize);
    }

    /** 重算实力分：取最近 10 场单场评分均值，映射成等级。 */
    private void recalcRating(Long userId) {
        List<MatchRecord> records = matchRecordRepository
                .findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, 10)).getContent();
        if (records.isEmpty()) {
            return;
        }
        double avg = records.stream()
                .mapToDouble(r -> r.getScore() == null ? 0 : r.getScore())
                .average().orElse(0);
        int score = (int) Math.round(avg);
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setSkillScore(score);
            user.setSkillTier(tierOf(score));
            user.setMatchCount((int) matchRecordRepository.countByUserId(userId));
            userRepository.save(user);
        }
    }

    /** 单场评分：KDA + 补刀 + 视野 + 胜负，输出 0-100。 */
    private double scoreMatch(MatchRecord m) {
        int kills = m.getKills();
        int deaths = m.getDeaths();
        int assists = m.getAssists();
        double kda = deaths == 0 ? (double) (kills + assists) : (kills + assists) / (double) deaths;
        double kdaScore = Math.min(100, kda / 5.0 * 100);

        int duration = m.getDuration();
        double csPerMin = duration > 0 ? m.getCs() / (duration / 60.0) : 0;
        double csTarget = "SUPPORT".equals(m.getLane()) ? 1.5 : 8.0;
        double csScore = Math.min(100, csPerMin / csTarget * 100);

        double visionScore = Math.min(100, m.getVisionScore() / 30.0 * 100);
        double winScore = Boolean.TRUE.equals(m.getWin()) ? 100 : 35;

        return 0.35 * kdaScore + 0.25 * csScore + 0.15 * visionScore + 0.25 * winScore;
    }

    private String tierOf(int score) {
        if (score >= 85) {
            return "S";
        }
        if (score >= 70) {
            return "A";
        }
        if (score >= 55) {
            return "B";
        }
        if (score >= 40) {
            return "C";
        }
        return "D";
    }

    private String normalizeLane(String lane) {
        if (lane == null) {
            return "UNKNOWN";
        }
        switch (lane.toUpperCase()) {
            case "TOP":
                return "TOP";
            case "JUNGLE":
                return "JUNGLE";
            case "MID":
            case "MIDDLE":
                return "MIDDLE";
            case "BOTTOM":
            case "BOT":
            case "ADC":
                return "BOTTOM";
            case "SUPPORT":
            case "SUP":
                return "SUPPORT";
            default:
                return "UNKNOWN";
        }
    }
}
