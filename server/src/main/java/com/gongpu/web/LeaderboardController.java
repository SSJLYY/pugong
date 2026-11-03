package com.gongpu.web;

import com.gongpu.stats.LeaderboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 排行榜（公开接口，无需登录）：board=maxhp(默认)/gold/gen/damage 四榜。 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService board;

    public LeaderboardController(LeaderboardService board) {
        this.board = board;
    }

    @GetMapping
    public Map<String, Object> top(@RequestParam(required = false) String board) {
        // 非法/缺省一律回落生命榜;避免参数直通 switch 之外的意外值
        String b = LeaderboardService.BOARD_GOLD.equals(board) ? LeaderboardService.BOARD_GOLD
                : LeaderboardService.BOARD_GEN.equals(board) ? LeaderboardService.BOARD_GEN
                : LeaderboardService.BOARD_HP;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("board", b);
        out.put("rows", this.board.top(b, 50));
        return out;
    }
}
