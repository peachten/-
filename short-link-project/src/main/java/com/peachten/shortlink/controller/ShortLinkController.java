package com.peachten.shortlink.controller;

import com.peachten.shortlink.common.annotation.RateLimit;
import com.peachten.shortlink.common.enums.LimitType;
import com.peachten.shortlink.common.result.PageResult;
import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.Results;
import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.dto.ShortLinkPageReqDTO;
import com.peachten.shortlink.dto.ShortLinkUpdateReqDTO;
import com.peachten.shortlink.service.ShortLinkService;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;
import com.peachten.shortlink.vo.ShortLinkPageRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "短链模块")
@RestController
@RequestMapping("/api/short-link/admin/v1/link")
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @Operation(summary = "创建短链（幂等，重复提交返回同一短链）")
    @RateLimit(type = LimitType.USER, window = 60, limit = 30)
    @PostMapping
    public Result<ShortLinkCreateRespVO> create(@Valid @RequestBody ShortLinkCreateReqDTO req) {
        return Results.success(shortLinkService.create(req));
    }

    @Operation(summary = "分页查询短链（gid / 关键词 / 排序）")
    @GetMapping("/page")
    public Result<PageResult<ShortLinkPageRespVO>> page(@Valid ShortLinkPageReqDTO req) {
        return Results.success(shortLinkService.pageLink(req));
    }

    @Operation(summary = "修改短链（备注 / 有效期 / 分组）")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ShortLinkUpdateReqDTO req) {
        shortLinkService.updateLink(req);
        return Results.success();
    }

    @Operation(summary = "批量修改短链")
    @PostMapping("/update")
    public Result<Void> updateBatch(@RequestBody List<ShortLinkUpdateReqDTO> reqList) {
        shortLinkService.updateBatch(reqList);
        return Results.success();
    }

    @Operation(summary = "启用 / 禁用短链（0 启用 1 禁用）")
    @PostMapping("/enable")
    public Result<Void> enable(@RequestParam String fullShortUrl, @RequestParam Integer enableStatus) {
        shortLinkService.enableLink(fullShortUrl, enableStatus);
        return Results.success();
    }

    @Operation(summary = "删除短链（逻辑删除，支持批量）")
    @DeleteMapping
    public Result<Void> delete(@RequestBody List<String> fullShortUrls) {
        shortLinkService.deleteLink(fullShortUrls);
        return Results.success();
    }

    @Operation(summary = "分组内短链数量")
    @GetMapping("/count")
    public Result<Long> count(@RequestParam String gid) {
        return Results.success(shortLinkService.countLink(gid));
    }
}
