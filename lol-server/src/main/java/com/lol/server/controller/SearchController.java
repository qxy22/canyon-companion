package com.lol.server.controller;

import com.lol.common.result.PageResult;
import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.UserProfileVO;
import com.lol.server.service.MatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final MatchService matchService;

    public SearchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /** 多维搜索：位置、段位、性别、关键字，分页。 */
    @GetMapping
    public ApiResponse<PageResult<UserProfileVO>> search(
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String rankTier,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(matchService.search(position, rankTier, gender, keyword, page, size));
    }
}
