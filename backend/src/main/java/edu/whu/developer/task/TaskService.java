package edu.whu.developer.task;

import edu.whu.developer.application.TaskApplicationRepository;
import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.completion.CompletionRepository;
import edu.whu.developer.dict.DictService;
import edu.whu.developer.message.TaskMessageRepository;
import edu.whu.developer.notify.AuditService;
import edu.whu.developer.project.MilestoneRepository;
import edu.whu.developer.project.Project;
import edu.whu.developer.project.ProjectRepository;
import edu.whu.developer.report.WeeklyReportRepository;
import edu.whu.developer.shortlist.TaskShortlistRepository;
import edu.whu.developer.task.TaskDtos.PositionRequest;
import edu.whu.developer.task.TaskDtos.PositionView;
import edu.whu.developer.task.TaskDtos.PublisherTaskPage;
import edu.whu.developer.task.TaskDtos.TaskPage;
import edu.whu.developer.task.TaskDtos.TaskRequest;
import edu.whu.developer.task.TaskDtos.TaskResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TaskService {

    public static final Set<String> ROLE_TYPES = Set.of("TA", "INTERN", "RA", "TEAM");
    public static final Set<String> TA_SCHEDULE_TYPES = Set.of("CURRENT_SEMESTER", "NEXT_SEMESTER", "LONG_TERM");
    public static final Set<String> RA_SCHEDULE_TYPES = Set.of("ONE_SEMESTER", "TWO_SEMESTERS", "YEAR_PLUS");
    public static final Set<String> EDUCATION_LEVELS = Set.of("本科生", "硕士生", "博士生");
    public static final Set<String> TECH_FIELDS = Set.of(
            "前端开发", "后端开发", "全栈应用", "人工智能", "系统与底层", "开发工具", "数据与存储"
    );
    public static final Set<String> RESEARCH_DIRECTIONS = Set.of(
            "人工智能", "机器学习与数据挖掘", "计算机视觉", "自然语言处理", "网络安全",
            "系统与网络", "软件工程", "人机交互", "边缘计算与物联网", "科学计算"
    );
    public static final Set<String> TEAM_POSITIONS = Set.of(
            "研发", "运营", "数据采集", "数据标注", "质检", "其它"
    );
    public static final Set<String> LEVELS = Set.of("L1", "L2", "L3", "L4", "L5");
    public static final Set<String> SUPPORT_LANGUAGES = Set.of("中文", "English", "中英双语");

    private final TaskRepository tasks;
    private final TaskPositionRepository taskPositions;
    private final UserRepository users;
    private final TaskApplicationRepository applications;
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final WeeklyReportRepository reports;
    private final CompletionRepository completions;
    private final TaskShortlistRepository shortlists;
    private final TaskMessageRepository messages;
    private final AuditService audit;
    private final DictService dicts;

    public TaskService(TaskRepository tasks,
                       TaskPositionRepository taskPositions,
                       UserRepository users,
                       TaskApplicationRepository applications,
                       ProjectRepository projects,
                       MilestoneRepository milestones,
                       WeeklyReportRepository reports,
                       CompletionRepository completions,
                       TaskShortlistRepository shortlists,
                       TaskMessageRepository messages,
                       AuditService audit,
                       DictService dicts) {
        this.tasks = tasks;
        this.taskPositions = taskPositions;
        this.users = users;
        this.applications = applications;
        this.projects = projects;
        this.milestones = milestones;
        this.reports = reports;
        this.completions = completions;
        this.shortlists = shortlists;
        this.messages = messages;
        this.audit = audit;
        this.dicts = dicts;
    }

    @Transactional(readOnly = true)
    public TaskPage listOpen(String roleType,
                             String difficulty,
                             String techField,
                             String supportLanguage,
                             String programmingLanguage,
                             String scheduleType,
                             Integer durationMin,
                             Integer durationMax,
                             Integer hoursMin,
                             Integer hoursMax,
                             int page,
                             int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        var result = tasks.searchOpen(
                blankToNull(roleType),
                blankToNull(difficulty),
                blankToNull(techField),
                blankToNull(supportLanguage),
                blankToNull(programmingLanguage),
                blankToNull(scheduleType),
                durationMin,
                durationMax,
                hoursMin,
                hoursMax,
                PageRequest.of(safePage, safeSize)
        );
        AuthUser viewer = currentOrNull();
        return new TaskPage(
                result.getContent().stream().map(task -> toResponse(task, viewer)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        Task task = tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        AuthUser viewer = currentOrNull();
        if (!"OPEN".equals(task.getStatus()) && !canViewNonOpen(task, viewer)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return toResponse(task, viewer);
    }

    @Transactional(readOnly = true)
    public PublisherTaskPage mine(String status, String roleType, String keyword, int page, int size) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String statusFilter = blankToNull(status);
        if (statusFilter != null && !Set.of("OPEN", "DRAFT", "CLOSED").contains(statusFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态不合法");
        }
        String roleFilter = blankToNull(roleType);
        if (roleFilter != null) {
            roleFilter = roleFilter.toUpperCase(Locale.ROOT);
            if (!ROLE_TYPES.contains(roleFilter)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务类型不合法");
            }
        }
        Long publisherId = publisher.getId();
        String keywordFilter = blankToNull(keyword);
        var result = tasks.searchMine(publisherId, roleFilter, statusFilter, keywordFilter, PageRequest.of(safePage, safeSize));
        List<Task> pageItems = result.getContent();
        Map<Long, Long> filledMap = new HashMap<>();
        Map<Long, Long> pendingMap = new HashMap<>();
        if (!pageItems.isEmpty()) {
            List<Long> ids = pageItems.stream().map(Task::getId).toList();
            for (Object[] row : projects.countGroupedByTaskIdExcludingStatus(ids, "TERMINATED")) {
                filledMap.put((Long) row[0], (Long) row[1]);
            }
            for (Object[] row : applications.countGroupedByTaskIdAndStatus(ids, "SUBMITTED")) {
                pendingMap.put((Long) row[0], (Long) row[1]);
            }
        }
        List<TaskResponse> items = pageItems.stream()
                .map(task -> toResponse(
                        task,
                        publisher,
                        filledMap.getOrDefault(task.getId(), 0L),
                        pendingMap.getOrDefault(task.getId(), 0L)))
                .toList();
        long totalAll = roleFilter == null
                ? tasks.countByPublisherId(publisherId)
                : tasks.countByPublisherIdAndRoleType(publisherId, roleFilter);
        long totalOpen = roleFilter == null
                ? tasks.countByPublisherIdAndStatus(publisherId, "OPEN")
                : tasks.countByPublisherIdAndRoleTypeAndStatus(publisherId, roleFilter, "OPEN");
        long totalDraft = roleFilter == null
                ? tasks.countByPublisherIdAndStatus(publisherId, "DRAFT")
                : tasks.countByPublisherIdAndRoleTypeAndStatus(publisherId, roleFilter, "DRAFT");
        long totalClosed = roleFilter == null
                ? tasks.countByPublisherIdAndStatus(publisherId, "CLOSED")
                : tasks.countByPublisherIdAndRoleTypeAndStatus(publisherId, roleFilter, "CLOSED");
        return new PublisherTaskPage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext(),
                totalAll,
                totalOpen,
                totalDraft,
                totalClosed,
                tasks.countByPublisherIdAndRoleType(publisherId, "TA"),
                tasks.countByPublisherIdAndRoleType(publisherId, "INTERN"),
                tasks.countByPublisherIdAndRoleType(publisherId, "RA"),
                tasks.countByPublisherIdAndRoleType(publisherId, "TEAM"));
    }

    @Transactional
    public TaskResponse create(TaskRequest request, boolean publish) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        User owner = users.findById(publisher.getId()).orElseThrow();
        Task task = new Task();
        task.setPublisher(owner);
        apply(task, request);
        assignPublicCode(task);
        task.setStatus(publish ? "OPEN" : "DRAFT");
        tasks.save(task);
        syncPositions(task, request.positions());
        audit.record(publisher.getId(), "TASK", task.getId(), publish ? "PUBLISH" : "CREATE", task.getTitle());
        return toResponse(task, publisher);
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request, Boolean publish) {
        Task task = owned(id);
        String previousRole = task.getRoleType();
        apply(task, request);
        if (!previousRole.equals(task.getRoleType())) {
            task.setPublicCode(null);
            task.setRoleSerial(null);
            assignPublicCode(task);
        }
        if (Boolean.FALSE.equals(publish)) {
            task.setStatus("DRAFT");
            if ("CHALLENGE".equals(task.getPostingMode()) && !"AWARDED".equals(task.getChallengeStatus())) {
                task.setChallengeStatus(null);
            }
        } else if (Boolean.TRUE.equals(publish)) {
            task.setStatus("OPEN");
            if ("CHALLENGE".equals(task.getPostingMode())) {
                task.setChallengeStatus("OPEN");
            }
        }
        syncPositions(task, request.positions());
        audit.record(
                AuthSupport.current().getId(),
                "TASK",
                task.getId(),
                Boolean.TRUE.equals(publish) ? "PUBLISH" : "UPDATE",
                task.getTitle());
        return toResponse(task, AuthSupport.current());
    }

    @Transactional
    public TaskResponse publish(Long id) {
        Task task = owned(id);
        task.setStatus("OPEN");
        if ("CHALLENGE".equals(task.getPostingMode())) {
            task.setChallengeStatus("OPEN");
        }
        audit.record(AuthSupport.current().getId(), "TASK", task.getId(), "PUBLISH", task.getTitle());
        return toResponse(task, AuthSupport.current());
    }

    @Transactional
    public TaskResponse close(Long id) {
        Task task = owned(id);
        task.setStatus("CLOSED");
        if ("CHALLENGE".equals(task.getPostingMode()) && !"AWARDED".equals(task.getChallengeStatus())) {
            task.setChallengeStatus("CLOSED");
        }
        audit.record(AuthSupport.current().getId(), "TASK", task.getId(), "CLOSE", task.getTitle());
        return toResponse(task, AuthSupport.current());
    }

    @Transactional
    public void deleteUnpublished(Long id) {
        Task task = owned(id);
        if ("OPEN".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布任务请先下架后再删除");
        }
        if (!Set.of("DRAFT", "CLOSED").contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能删除未发布或已下架的任务");
        }
        if (projects.countByTaskIdAndStatusNot(task.getId(), "TERMINATED") > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仍有进行中的协作项目，无法删除");
        }
        Long taskId = task.getId();
        String title = task.getTitle();
        shortlists.deleteByTaskId(taskId);
        messages.deleteByTaskId(taskId);
        for (Project project : projects.findByTaskId(taskId)) {
            milestones.deleteByProjectId(project.getId());
            reports.deleteByProjectId(project.getId());
            completions.deleteByProjectId(project.getId());
            projects.delete(project);
        }
        applications.deleteByTaskId(taskId);
        taskPositions.deleteByTaskId(taskId);
        tasks.delete(task);
        audit.record(AuthSupport.current().getId(), "TASK", taskId, "DELETE", title);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> allForAdmin() {
        AuthSupport.requireRole(Role.ADMIN);
        return tasks.findAll().stream().map(task -> toResponse(task, AuthSupport.current())).toList();
    }

    @Transactional
    public TaskResponse adminStatus(Long id, String status) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        if (!Set.of("DRAFT", "OPEN", "CLOSED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "状态不合法");
        }
        Task task = tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        task.setStatus(status);
        audit.record(admin.getId(), "TASK", task.getId(), "ADMIN_STATUS", status);
        return toResponse(task, admin);
    }

    private Task owned(Long id) {
        AuthUser publisher = AuthSupport.requireRole(Role.PUBLISHER);
        Task task = tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getPublisher().getId().equals(publisher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能管理自己发布的任务");
        }
        return task;
    }

    private void apply(Task task, TaskRequest request) {
        String roleType = request.roleType().trim().toUpperCase(Locale.ROOT);
        String field = request.techField() == null ? "" : request.techField().trim();
        String level = request.difficulty() == null || request.difficulty().isBlank()
                ? "L3"
                : request.difficulty().trim().toUpperCase(Locale.ROOT);
        if (!ROLE_TYPES.contains(roleType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务类型只能是 TA、INTERN、RA 或 TEAM");
        }
        // 一期不采集支持语言，库字段保留默认值
        String support = "中文";
        Set<String> techFields = dictValues("TECH_FIELD", TECH_FIELDS);
        Set<String> researchDirections = dictValues("RESEARCH_DIRECTION", RESEARCH_DIRECTIONS);
        Set<String> teamPositions = dictValues("TEAM_POSITION", TEAM_POSITIONS);
        Set<String> difficulties = dictValues("DIFFICULTY", LEVELS);
        Set<String> taSchedules = dictValues("TA_SCHEDULE", TA_SCHEDULE_TYPES);
        Set<String> identities = dictValues("STUDENT_IDENTITY", EDUCATION_LEVELS);
        String languages = normalizeCsv(request.programmingLanguages() == null ? "" : request.programmingLanguages());
        String techStack = request.techStack() == null ? "" : request.techStack().trim();
        String educationLevels = normalizeCsv(request.educationLevels() == null ? "" : request.educationLevels());
        String scheduleType = blankToNull(request.scheduleType());
        Integer durationWeeks = request.durationWeeks();
        if ("INTERN".equals(roleType)) {
            if (!techFields.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技术领域不合法");
            }
            if (!difficulties.contains(level)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "难度不合法");
            }
            if (languages.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少填写一种编程语言");
            }
            if (techStack.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写技术栈");
            }
            if (request.durationWeeks() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写开发周期");
            }
            educationLevels = null;
            scheduleType = null;
        } else if ("TA".equals(roleType)) {
            level = "L3";
            field = "教学助教";
            languages = "不限";
            techStack = "教学助教";
            if (scheduleType == null || !taSchedules.contains(scheduleType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择助教学期");
            }
            if (educationLevels.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择在读身份");
            }
            for (String item : educationLevels.split(",")) {
                if (!identities.contains(item)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "在读身份不合法");
                }
            }
            durationWeeks = weeksForSchedule(scheduleType);
        } else if ("RA".equals(roleType)) {
            level = "L3";
            if (!researchDirections.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "研究方向不合法");
            }
            if (languages.isBlank()) {
                languages = "不限";
            }
            if (techStack.isBlank()) {
                techStack = "科研协作";
            }
            educationLevels = null;
            scheduleType = null;
            durationWeeks = 16;
        } else {
            // TEAM
            level = "L3";
            List<PositionRequest> positions = request.positions() == null ? List.of() : request.positions();
            if (positions.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少添加一个岗位");
            }
            int totalQuota = 0;
            for (PositionRequest position : positions) {
                String direction = position.direction() == null || position.direction().isBlank()
                        ? (position.title() == null ? "" : position.title().trim())
                        : position.direction().trim();
                if (!teamPositions.contains(direction)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "岗位不合法，请从字典选择或联系管理员新增");
                }
                if (position.quota() == null || position.quota() < 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "岗位名额至少为 1");
                }
                totalQuota += position.quota();
            }
            if (totalQuota > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目总名额不能超过 200");
            }
            PositionRequest first = positions.get(0);
            String firstDirection = first.direction() == null || first.direction().isBlank()
                    ? (first.title() == null ? "" : first.title().trim())
                    : first.direction().trim();
            field = firstDirection;
            languages = "不限";
            techStack = "按岗协作";
            educationLevels = null;
            scheduleType = null;
            durationWeeks = request.durationWeeks() == null ? 16 : request.durationWeeks();
        }
        String stipend = request.stipendText().trim();
        if (stipend.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写补助水平");
        }
        String output = request.outputRequirement() == null ? "" : request.outputRequirement().trim();
        String techReq = request.techRequirement() == null ? "" : request.techRequirement().trim();
        if ("RA".equals(roleType)) {
            if (output.isBlank()) {
                output = "提交可落地的研究方案与阶段性产出";
            }
            if (techReq.isBlank()) {
                techReq = "具备相关领域基础研究能力";
            }
        } else if ("TEAM".equals(roleType)) {
            if (output.isBlank()) {
                output = "按所选岗位完成约定工作并提交周报";
            }
            if (techReq.isBlank()) {
                techReq = "具备对应岗位所需基础能力，能按时参与协作";
            }
        } else {
            if (output.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写产出要求");
            }
            if (techReq.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写能力要求");
            }
        }
        task.setTitle(request.title().trim());
        task.setRoleType(roleType);
        task.setTechField(field);
        task.setDifficulty(level);
        task.setBackground(request.background().trim());
        task.setTechStack(techStack);
        task.setProgrammingLanguages(languages);
        task.setSupportLanguages(support);
        task.setOutputRequirement(output);
        task.setTechRequirement(techReq);
        Integer estimatedHours = request.estimatedHours();
        if ("RA".equals(roleType)) {
            estimatedHours = estimatedHours == null ? 1 : estimatedHours;
        } else if ("TEAM".equals(roleType)) {
            estimatedHours = estimatedHours == null ? 8 : estimatedHours;
        } else if (estimatedHours == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写预估工时");
        }
        task.setDurationWeeks(durationWeeks);
        task.setScheduleType(scheduleType);
        task.setEducationLevels(educationLevels);
        task.setEstimatedHours(estimatedHours);
        task.setRepoUrl("INTERN".equals(roleType) ? blankToNull(request.repoUrl()) : null);
        task.setLearningMaterials(blankToNull(request.learningMaterials()));
        task.setStipendText(stipend);
        String prdFileUrl = blankToNull(request.prdFileUrl());
        if ("INTERN".equals(roleType) && prdFileUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传需求文档 PDF");
        }
        if (task.getPrdMd() == null) {
            task.setPrdMd("");
        }
        if (!"INTERN".equals(roleType)) {
            task.setPrdFileUrl(null);
            task.setPrdFileName(null);
            task.setTechDocFileUrl(null);
            task.setTechDocFileName(null);
            task.setTechDocMd(null);
        } else {
            task.setPrdFileUrl(prdFileUrl);
            task.setPrdFileName(blankToNull(request.prdFileName()));
            String techDocFileUrl = blankToNull(request.techDocFileUrl());
            task.setTechDocFileUrl(techDocFileUrl);
            task.setTechDocFileName(techDocFileUrl == null ? null : blankToNull(request.techDocFileName()));
        }
        if ("RA".equals(roleType)) {
            task.setPostingMode("CHALLENGE");
            task.setBidDeadline(request.bidDeadline());
            Integer quota = request.quota() == null ? 1 : request.quota();
            if (quota < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名额至少为 1");
            }
            task.setQuota(quota);
            if (!"AWARDED".equals(task.getChallengeStatus()) && !"CLOSED".equals(task.getChallengeStatus())) {
                task.setChallengeStatus("OPEN");
            }
        } else if ("TEAM".equals(roleType)) {
            task.setPostingMode("STANDARD");
            task.setBidDeadline(null);
            task.setChallengeStatus(null);
            int totalQuota = request.positions().stream().mapToInt(PositionRequest::quota).sum();
            task.setQuota(totalQuota);
        } else {
            task.setPostingMode("STANDARD");
            task.setBidDeadline(null);
            task.setChallengeStatus(null);
            if (request.quota() == null || request.quota() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写名额");
            }
            task.setQuota(request.quota());
        }
    }

    private void syncPositions(Task task, List<PositionRequest> requests) {
        if (!"TEAM".equals(task.getRoleType())) {
            taskPositions.deleteByTaskId(task.getId());
            return;
        }
        List<PositionRequest> positions = requests == null ? List.of() : requests;
        if (positions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少添加一个岗位");
        }
        List<TaskPosition> existing = taskPositions.findByTaskIdOrderBySortOrderAscIdAsc(task.getId());
        Set<Long> keep = new HashSet<>();
        int order = 0;
        for (PositionRequest req : positions) {
            TaskPosition pos;
            if (req.id() != null) {
                pos = taskPositions.findByIdAndTaskId(req.id(), task.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "岗位不存在或不属于本任务"));
            } else {
                pos = new TaskPosition();
                pos.setTask(task);
            }
            String direction = req.direction() == null || req.direction().isBlank()
                    ? req.title().trim()
                    : req.direction().trim();
            String title = req.title() == null || req.title().isBlank() ? direction : req.title().trim();
            pos.setTitle(title);
            pos.setDirection(direction);
            pos.setQuota(req.quota());
            pos.setRequirement(req.requirement() == null || req.requirement().isBlank() ? null : req.requirement().trim());
            pos.setSortOrder(order++);
            taskPositions.save(pos);
            keep.add(pos.getId());
        }
        for (TaskPosition old : existing) {
            if (keep.contains(old.getId())) {
                continue;
            }
            if (applications.existsByPositionId(old.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "岗位「" + old.getTitle() + "」已有申请，无法删除");
            }
            taskPositions.delete(old);
        }
    }

    private TaskResponse toResponse(Task task, AuthUser viewer) {
        return toResponse(
                task,
                viewer,
                projects.countByTaskIdAndStatusNot(task.getId(), "TERMINATED"),
                applications.countByTaskIdAndStatus(task.getId(), "SUBMITTED"));
    }

    private TaskResponse toResponse(Task task, AuthUser viewer, long filled, long pendingApplications) {
        Long applicationId = null;
        String applicationStatus = null;
        Long projectId = null;
        boolean shortlisted = false;
        if (viewer != null && viewer.getRole() == Role.STUDENT) {
            var application = applications.findByTaskIdAndStudentId(task.getId(), viewer.getId());
            if (application.isPresent()) {
                applicationId = application.get().getId();
                applicationStatus = application.get().getStatus();
                projectId = projects.findByApplicationId(applicationId).map(project -> project.getId()).orElse(null);
            }
            shortlisted = shortlists.existsByStudentIdAndTaskId(viewer.getId(), task.getId());
        }
        List<PositionView> positionViews = new ArrayList<>();
        if ("TEAM".equals(task.getRoleType())) {
            for (TaskPosition position : taskPositions.findByTaskIdOrderBySortOrderAscIdAsc(task.getId())) {
                positionViews.add(new PositionView(
                        position.getId(),
                        position.getTitle(),
                        position.getDirection(),
                        position.getQuota(),
                        projects.countByPositionIdAndStatusNot(position.getId(), "TERMINATED"),
                        position.getRequirement()));
            }
        }
        return new TaskResponse(
                task.getId(),
                task.getPublicCode(),
                task.getTitle(),
                task.getRoleType(),
                task.getTechField(),
                task.getDifficulty(),
                task.getBackground(),
                task.getTechStack(),
                task.getProgrammingLanguages(),
                task.getSupportLanguages(),
                task.getOutputRequirement(),
                task.getTechRequirement(),
                task.getDurationWeeks(),
                task.getScheduleType(),
                task.getEducationLevels(),
                task.getEstimatedHours(),
                task.getRepoUrl(),
                task.getLearningMaterials(),
                task.getStipendText(),
                task.getPrdMd(),
                task.getPrdFileUrl(),
                task.getPrdFileName(),
                task.getTechDocMd(),
                task.getTechDocFileUrl(),
                task.getTechDocFileName(),
                task.getQuota(),
                filled,
                pendingApplications,
                task.getStatus(),
                task.getPostingMode(),
                task.getBidDeadline(),
                task.getChallengeStatus(),
                task.getPublisher().getId(),
                task.getPublisher().getRealName() == null ? task.getPublisher().getDisplayName() : task.getPublisher().getRealName(),
                task.getCreatedAt(),
                applicationId,
                applicationStatus,
                projectId,
                shortlisted,
                positionViews);
    }

    private Set<String> dictValues(String type, Set<String> fallback) {
        Set<String> values = dicts.enabledValues(type);
        return values.isEmpty() ? fallback : values;
    }

    private void assignPublicCode(Task task) {
        if (task.getPublicCode() != null && !task.getPublicCode().isBlank()) {
            return;
        }
        String roleType = task.getRoleType();
        int next = tasks.maxRoleSerial(roleType) + 1;
        String prefix = switch (roleType) {
            case "TA" -> "TA";
            case "RA" -> "RA";
            case "TEAM" -> "TEAM";
            default -> "DEV";
        };
        task.setRoleSerial(next);
        task.setPublicCode(prefix + "-" + String.format("%04d", next));
    }

    private int weeksForSchedule(String scheduleType) {
        return switch (scheduleType) {
            case "CURRENT_SEMESTER", "NEXT_SEMESTER", "ONE_SEMESTER" -> 16;
            case "LONG_TERM", "TWO_SEMESTERS" -> 32;
            case "YEAR_PLUS" -> 52;
            default -> 16;
        };
    }

    private boolean canManage(Task task, AuthUser viewer) {
        if (viewer == null) {
            return false;
        }
        return viewer.getRole() == Role.ADMIN || task.getPublisher().getId().equals(viewer.getId());
    }

    /** 已下架/草稿任务：发布方、管理员、已申请/已入项/已备选的学生仍可查看原文。 */
    private boolean canViewNonOpen(Task task, AuthUser viewer) {
        if (canManage(task, viewer)) {
            return true;
        }
        if (viewer == null || viewer.getRole() != Role.STUDENT) {
            return false;
        }
        Long studentId = viewer.getId();
        return applications.findByTaskIdAndStudentId(task.getId(), studentId).isPresent()
                || shortlists.existsByStudentIdAndTaskId(studentId, task.getId())
                || projects.existsByTaskIdAndStudentId(task.getId(), studentId);
    }

    private AuthUser currentOrNull() {
        try {
            return AuthSupport.current();
        } catch (ResponseStatusException ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCsv(String value) {
        return Arrays.stream(value.split("[,，、/|]+"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(","));
    }
}
