package edu.whu.developer.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record PositionRequest(
            Long id,
            @Size(max = 64) String title,
            /** 岗位字典值 TEAM_POSITION；为空时用 title */
            @Size(max = 64) String direction,
            @NotNull(message = "请填写岗位名额") @Min(1) @Max(200) Integer quota,
            @Size(max = 2000) String requirement
    ) {
    }

    public record PositionView(
            Long id,
            String title,
            String direction,
            Integer quota,
            long filled,
            String requirement
    ) {
    }

    public record TaskRequest(
            @NotBlank(message = "请填写任务名称") @Size(max = 200) String title,
            @NotBlank(message = "请选择任务类型") String roleType,
            String techField,
            String difficulty,
            @NotBlank(message = "请填写背景与目标") String background,
            @Size(max = 300) String techStack,
            @Size(max = 300) String programmingLanguages,
            @Size(max = 64) String supportLanguages,
            String outputRequirement,
            String techRequirement,
            @Min(1) @Max(52) Integer durationWeeks,
            @Size(max = 32) String scheduleType,
            @Size(max = 64) String educationLevels,
            @Min(1) @Max(40) Integer estimatedHours,
            @Size(max = 500) String repoUrl,
            String learningMaterials,
            @NotBlank(message = "请填写补助水平") @Size(max = 500) String stipendText,
            String prdMd,
            @Size(max = 500) String prdFileUrl,
            @Size(max = 255) String prdFileName,
            String techDocMd,
            @Size(max = 500) String techDocFileUrl,
            @Size(max = 255) String techDocFileName,
            @Min(1) @Max(200) Integer quota,
            LocalDateTime bidDeadline,
            @Valid List<PositionRequest> positions
    ) {
    }

    public record TaskPage(
            java.util.List<TaskResponse> items,
            int page,
            int size,
            long total,
            boolean hasMore
    ) {
    }

    /** 发布方「我的任务」分页，附带类型/状态总数供 Tab 展示。 */
    public record PublisherTaskPage(
            java.util.List<TaskResponse> items,
            int page,
            int size,
            long total,
            boolean hasMore,
            long totalAll,
            long totalOpen,
            long totalDraft,
            long totalClosed,
            long totalTa,
            long totalIntern,
            long totalRa,
            long totalTeam
    ) {
    }

    public record TaskResponse(
            Long id,
            String publicCode,
            String title,
            String roleType,
            String techField,
            String difficulty,
            String background,
            String techStack,
            String programmingLanguages,
            String supportLanguages,
            String outputRequirement,
            String techRequirement,
            Integer durationWeeks,
            String scheduleType,
            String educationLevels,
            Integer estimatedHours,
            String repoUrl,
            String learningMaterials,
            String stipendText,
            String prdMd,
            String prdFileUrl,
            String prdFileName,
            String techDocMd,
            String techDocFileUrl,
            String techDocFileName,
            Integer quota,
            long filled,
            long pendingApplications,
            String status,
            String postingMode,
            LocalDateTime bidDeadline,
            String challengeStatus,
            Long publisherId,
            String publisherName,
            LocalDateTime createdAt,
            Long myApplicationId,
            String myApplicationStatus,
            Long projectId,
            boolean shortlisted,
            List<PositionView> positions
    ) {
    }
}
