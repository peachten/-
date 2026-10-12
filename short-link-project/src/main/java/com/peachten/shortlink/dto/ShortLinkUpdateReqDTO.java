package com.peachten.shortlink.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链修改入参（可改备注、有效期、分组）
 */
@Data
public class ShortLinkUpdateReqDTO {

    @NotBlank(message = "完整短链不能为空")
    private String fullShortUrl;

    /** 目标分组，不传表示不改分组 */
    private String gid;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String describe;

    /** 有效期类型 0 永久 1 自定义 */
    private Integer validDateType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validDate;
}
