package com.lol.server.controller;

import com.lol.common.result.PageResult;
import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.LcuSyncResult;
import com.lol.server.dto.MatchImportRequest;
import com.lol.server.dto.MatchRecordVO;
import com.lol.server.dto.RecommendVO;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.service.LcuSyncService;
import com.lol.server.service.MatchRecordService;
import com.lol.server.service.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MatchController {

    private final MatchService matchService;
    private final MatchRecordService matchRecordService;
    private final LcuSyncService lcuSyncService;

    public MatchController(MatchService matchService, MatchRecordService matchRecordService,
                           LcuSyncService lcuSyncService) {
        this.matchService = matchService;
        this.matchRecordService = matchRecordService;
        this.lcuSyncService = lcuSyncService;
    }

    /**
     * 在线可开黑用户列表，分页。
     *
     * 前端每 2 秒刷新一次，全量返回会越来越重，分页把每次的载荷钉在 10 条。
     */
    @GetMapping("/online/list")
    public ApiResponse<PageResult<UserProfileVO>> onlineList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(matchService.onlineList(page, size));
    }

    /** 匹配推荐，分页。不传 page/size 时默认第一页 10 条。 */
    @GetMapping("/match/recommend")
    public ApiResponse<PageResult<RecommendVO>> recommend(HttpServletRequest request,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.ok(matchService.recommend(userId, page, size));
    }

    /** 战绩录入：接收本地读取器上传的战绩，重算实力分。 */
    @PostMapping("/match/import")
    public ApiResponse<UserProfileVO> importMatches(HttpServletRequest request,
                                                    @RequestBody MatchImportRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(matchRecordService.importMatches(userId, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 战绩录入（网页按钮触发）：后端直接连本地客户端，读最近 10 场入库，
     * 同时把客户端里的玩家 id、召唤师名、段位写进资料。
     */
    @PostMapping("/match/sync")
    public ApiResponse<LcuSyncResult> sync(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(lcuSyncService.syncFromLcu(userId, 10));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /** 查看某用户的战绩（分页）。 */
    @GetMapping("/user/{id}/matches")
    public ApiResponse<PageResult<MatchRecordVO>> userMatches(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(matchRecordService.userMatches(id, page, size));
    }
}
