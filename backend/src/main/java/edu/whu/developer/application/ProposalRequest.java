package edu.whu.developer.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposalRequest(
        @NotBlank(message = "请填写申请说明")
        @Size(max = 2000, message = "申请说明不超过 2000 字")
        String proposal
) {
}
