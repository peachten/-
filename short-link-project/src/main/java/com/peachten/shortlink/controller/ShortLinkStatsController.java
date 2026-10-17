package com.peachten.shortlink.controller;

import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.Results;
import com.peachten.shortlink.job.ShortLinkStatsJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "统计模块")
@RestController
@RequestMapping("/api/short-link/admin/v1/stats")
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkStatsJob shortLinkStatsJob;

    /** 仅用于开发/验收；第 4 周上线前把开关关掉 */
    @Value("${short-link.stats.job.trigger-enabled:false}")
    private boolean triggerEnabled;

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
