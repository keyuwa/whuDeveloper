package edu.whu.developer.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "请填写周次")
        @Min(value = 1, message = "周次从 1 开始")
        @Max(value = 30, message = "周次不超过 30")
        Integer weekNumber,
        @NotBlank(message = "请填写周报内容")
        @Size(max = 4000, message = "周报内容不超过 4000 字")
        String content
) {
}
