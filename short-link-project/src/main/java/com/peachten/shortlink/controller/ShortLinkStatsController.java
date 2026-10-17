package com.peachten.shortlink.controller;

import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.PageResult;
import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.Results;
import com.peachten.shortlink.dto.ShortLinkStatsAccessRecordReqDTO;
import com.peachten.shortlink.dto.ShortLinkStatsReqDTO;
import com.peachten.shortlink.job.ShortLinkStatsJob;
import com.peachten.shortlink.service.ShortLinkStatsService;
import com.peachten.shortlink.vo.ShortLinkAccessRecordVO;
import com.peachten.shortlink.vo.ShortLinkStatsDimensionVO;
import com.peachten.shortlink.vo.ShortLinkStatsRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "统计模块")
@RestController
@RequestMapping("/api/short-link/admin/v1/stats")
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkStatsService shortLinkStatsService;
    private final ShortLinkStatsJob shortLinkStatsJob;

    /** 仅用于开发/验收；第 4 周上线前把开关关掉 */
    @Value("${short-link.stats.job.trigger-enabled:false}")
    private boolean triggerEnabled;

    @Operation(summary = "汇总统计（今天读 Redis、历史读 DB）")
    @GetMapping
    public Result<ShortLinkStatsRespVO> getGroupStats(@Valid ShortLinkStatsReqDTO req) {
        return Results.success(shortLinkStatsService.getGroupStats(req));
    }

    @Operation(summary = "维度统计（browser / os / device / locale）")
    @GetMapping("/dimension")
    public Result<List<ShortLinkStatsDimensionVO>> getDimensionStats(
            @Valid ShortLinkStatsReqDTO req,
            @RequestParam(defaultValue = "browser") String dimension) {
        return Results.success(shortLinkStatsService.getDimensionStats(req, dimension));
    }

    @Operation(summary = "访问明细分页（按时间倒序）")
    @GetMapping("/access-record/page")
    public Result<PageResult<ShortLinkAccessRecordVO>> pageAccessRecords(
            @Valid ShortLinkStatsAccessRecordReqDTO req) {
        return Results.success(shortLinkStatsService.pageAccessRecords(req));
    }

    @Operation(summary = "手动触发统计聚合（仅开发测试）")
    @PostMapping("/job/trigger")
    public Result<String> triggerAggregate(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        if (!triggerEnabled) {
            throw new BizException("手动触发统计聚合已关闭");
        }
        LocalDate target = date == null ? LocalDate.now() : date;
        shortLinkStatsJob.aggregate(target);
        return Results.success("聚合完成: " + target);
    }

    @Operation(summary = "手动触发日志清理（仅开发测试）")
    @PostMapping("/job/cleanup")
    public Result<String> triggerCleanup() {
        if (!triggerEnabled) {
            throw new BizException("手动触发日志清理已关闭");
        }
        shortLinkStatsJob.dailyCleanup();
        return Results.success("清理完成");
    }
}
