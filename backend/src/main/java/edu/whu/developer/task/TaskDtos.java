package edu.whu.developer.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record TaskRequest(
            @NotBlank(message = "请填写任务名称") @Size(max = 200) String title,
            @NotBlank(message = "请选择类型") String roleType,
            @NotBlank(message = "请选择难度") String difficulty,
            @NotBlank(message = "请填写背景与目标") String background,
            @NotBlank(message = "请填写技术栈") @Size(max = 300) String techStack,
            @NotBlank(message = "请填写产出要求") String outputRequirement,
            @NotBlank(message = "请填写技术要求") String techRequirement,
            @NotNull(message = "请填写周期") @Min(1) @Max(52) Integer durationWeeks,
            @NotNull(message = "请填写预估工时") @Min(1) @Max(40) Integer estimatedHours,
            @Size(max = 500) String repoUrl,
            String learningMaterials,
            @NotBlank(message = "请填写补助说明") @Size(max = 500) String stipendText,
            @NotBlank(message = "请填写 PRD") String prdMd,
            String techDocMd,
            @NotNull(message = "请填写名额") @Min(1) @Max(50) Integer quota
    ) {
    }

    public record TaskResponse(
            Long id,
            String title,
            String roleType,
            String difficulty,
            String background,
            String techStack,
            String outputRequirement,
            String techRequirement,
            Integer durationWeeks,
            Integer estimatedHours,
            String repoUrl,
            String learningMaterials,
            String stipendText,
            String prdMd,
            String techDocMd,
            Integer quota,
            long filled,
            String status,
            Long publisherId,
            String publisherName,
            LocalDateTime createdAt,
            Long myApplicationId,
            String myApplicationStatus,
            Long projectId
    ) {
    }
}
