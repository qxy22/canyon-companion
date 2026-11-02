package com.lol.server.controller;

import com.lol.common.result.PageResult;
import com.lol.server.dto.ApiResponse;
import com.lol.server.dto.TeamupRequest;
import com.lol.server.dto.TeamupVO;
import com.lol.server.service.TeamupService;
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
@RequestMapping("/api/teamup")
public class TeamupController {

    private final TeamupService teamupService;

    public TeamupController(TeamupService teamupService) {
        this.teamupService = teamupService;
    }

    /** 发布开黑组队。 */
    @PostMapping
    public ApiResponse<TeamupVO> publish(HttpServletRequest request, @RequestBody TeamupRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(teamupService.publish(userId, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 招募中的组队列表，分页。不传 page/size 时默认第一页 10 条。
     *
     * 返回的已经是「我还没加入」的那些队伍，我加入过的归 /teamup/my 显示。
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<TeamupVO>> list(HttpServletRequest request,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.ok(teamupService.list(userId, page, size));
    }

    /** 我所在的队伍（含已关闭的），用来给「退出队伍 / 踢人」提供入口。 */
    @GetMapping("/my")
    public ApiResponse<List<TeamupVO>> my(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.ok(teamupService.myTeamups(userId));
    }

    /** 加入组队。 */
    @PostMapping("/{id}/join")
    public ApiResponse<TeamupVO> join(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(teamupService.join(id, userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 退出组队。
     * 最后一个人退出时队伍会解散，此时 data 为 null。
     */
    @PostMapping("/{id}/leave")
    public ApiResponse<TeamupVO> leave(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(teamupService.leave(id, userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 队长移除某个成员。 */
    @PostMapping("/{id}/kick/{userId}")
    public ApiResponse<TeamupVO> kick(HttpServletRequest request, @PathVariable Long id,
                                      @PathVariable Long userId) {
        Long operatorId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.ok(teamupService.kick(id, operatorId, userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
