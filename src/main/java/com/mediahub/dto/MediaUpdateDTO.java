package com.mediahub.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新条目 DTO：在新增字段的基础上强制要求条目id
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MediaUpdateDTO extends MediaSaveDTO {

    /**
     * 条目id
     */
    @NotNull(message = "条目id不能为空")
    private Long id;
}
