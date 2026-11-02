package com.lol.server.service;

import com.lol.lcu.CredentialFinder;
import com.lol.lcu.LcuClient;
import com.lol.lcu.LcuCredentials;
import com.lol.lcu.MatchExtractor;
import com.lol.lcu.MatchHistoryReader;
import com.lol.lcu.RankedReader;
import com.lol.server.dto.LcuSyncResult;
import com.lol.server.dto.MatchImportItem;
import com.lol.server.dto.MatchImportRequest;
import com.lol.server.dto.UserProfileVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 直接连接本地游戏客户端（LCU）读取最近战绩、玩家身份与段位，并录入到当前用户名下。
 *
 * 前提：后端和游戏跑在同一台机器上，且游戏已启动并登录。
 * 召唤师名、puuid、段位一律以客户端读到为准，不由用户手填。
 */
@Service
public class LcuSyncService {

    /** 手动凭证兜底：自动发现失败时，在 application.properties 里配 lcu.port / lcu.token。 */
    @Value("${lcu.port:0}")
    private int manualPort;

    @Value("${lcu.token:}")
    private String manualToken;

    private final MatchRecordService matchRecordService;

    public LcuSyncService(MatchRecordService matchRecordService) {
        this.matchRecordService = matchRecordService;
    }

    /** 读取最近 count 场并录入，同时同步玩家身份与段位。 */
    public LcuSyncResult syncFromLcu(Long userId, int count) {
        LcuCredentials creds;
        try {
            creds = credentials();
        } catch (Exception e) {
            throw new IllegalArgumentException("未找到本地游戏客户端凭证，请确认游戏已启动并登录：" + e.getMessage());
        }

        try {
            LcuClient client = new LcuClient(creds);
            MatchHistoryReader reader = new MatchHistoryReader(client);
            MatchHistoryReader.Summoner me = reader.currentSummoner();

            // 段位来自客户端。读不到（未定级或排位接口暂不可用）就跳过，
            // 不因此让整次录入失败 —— 战绩本身才是重点。
            RankedReader.Rank rank = new RankedReader(client).currentRank();

            List<Long> ids = readMatchIds(reader, me.puuid(), count);
            List<MatchImportItem> items = new ArrayList<>();
            for (long id : ids) {
                MatchExtractor.SelfMatch m = MatchExtractor.extract(reader.gameDetail(id), me.puuid(), me.name());
                if (m == null) {
                    continue;
                }
                MatchImportItem item = new MatchImportItem();
                item.setGameId(m.gameId);
                item.setWin(m.win);
                item.setLane(m.lane);
                item.setKills(m.kills);
                item.setDeaths(m.deaths);
                item.setAssists(m.assists);
                item.setCs(m.cs);
                item.setVisionScore(m.visionScore);
                item.setDuration(m.duration);
                item.setPlayedAt(m.playedAt);
                items.add(item);
            }

            // 召唤师名存纯 gameName，不带 # 后缀：战绩数据里的名字是纯名字，
            // 带上后缀会导致按名字匹配不到自己。唯一性由 puuid 保证。
            MatchImportRequest req = new MatchImportRequest();
            req.setPuuid(me.puuid());
            req.setSummonerName(me.name());
            if (rank != null) {
                req.setRankTier(rank.tier());
            }
            req.setMatches(items);

            UserProfileVO profile = matchRecordService.importMatches(userId, req);

            LcuSyncResult result = new LcuSyncResult();
            result.setProfile(profile);
            result.setFetchedCount(items.size());
            result.setSummonerName(me.name());
            result.setRankSynced(rank != null);
            if (rank != null) {
                result.setRankTier(rank.tier());
                result.setRankDivision(rank.division());
                result.setLeaguePoints(rank.leaguePoints());
            }
            result.setNote(noteOf(rank, items.size()));
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("读取本地战绩失败：" + e.getMessage(), e);
        }
    }

    private String noteOf(RankedReader.Rank rank, int fetched) {
        if (fetched == 0) {
            return "客户端里没读到对局。确认游戏已登录，且这个账号打过至少一局排位或匹配。";
        }
        if (rank == null) {
            return "这个账号当前没有定级，段位保持原样；战绩和实力分已更新。";
        }
        return null;
    }

    private LcuCredentials credentials() throws Exception {
        if (manualPort > 0 && manualToken != null && !manualToken.isBlank()) {
            return new LcuCredentials(manualPort, manualToken);
        }
        return CredentialFinder.discover();
    }

    /** 对局列表可能因战绩服务未就绪返回 503，重试几次。 */
    private List<Long> readMatchIds(MatchHistoryReader reader, String puuid, int count)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                return reader.matchIds(puuid, count);
            } catch (IOException e) {
                last = e;
                Thread.sleep(1500);
            }
        }
        throw last;
    }
}
