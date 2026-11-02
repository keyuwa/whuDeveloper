package edu.whu.developer.completion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompletionRequest(
        @NotBlank(message = "请填写 PR 链接")
        @Size(max = 500, message = "PR 链接不超过 500 字")
        @Pattern(regexp = "https?://\\S+", message = "PR 链接需要以 http:// 或 https:// 开头")
        String prUrl,
        @NotBlank(message = "请填写结项说明")
        @Size(max = 1000, message = "结项说明不超过 1000 字")
        String summary
) {
}
