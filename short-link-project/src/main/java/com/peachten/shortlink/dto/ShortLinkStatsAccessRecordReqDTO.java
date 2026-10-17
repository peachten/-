package com.peachten.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 访问明细分页入参
 */
@Data
public class ShortLinkStatsAccessRecordReqDTO {

    /** 分组标识；传 all 表示当前用户的全部分组 */
    @NotBlank(message = "分组不能为空")
    private String gid;

    /** 完整短链；不传则查询该分组下的全部短链 */
    private String fullShortUrl;

    @NotNull(message = "开始日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Long current = 1L;

    private Long size = 10L;
}
