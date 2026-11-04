package edu.whu.developer.dict;

import edu.whu.developer.auth.Role;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.notify.AuditService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DictService {

    public static final Set<String> DICT_TYPES = Set.of(
            "STUDENT_IDENTITY",
            "TA_SCHEDULE",
            "RA_SCHEDULE",
            "TECH_FIELD",
            "SUPPORT_LANGUAGE",
            "PROGRAMMING_LANGUAGE",
            "DIFFICULTY",
            "DURATION_RANGE",
            "WEEKLY_HOURS_RANGE"
    );

    public static final Map<String, String> DICT_TYPE_LABELS = Map.of(
            "STUDENT_IDENTITY", "在读身份",
            "TA_SCHEDULE", "助教学期",
            "RA_SCHEDULE", "科研周期",
            "TECH_FIELD", "技术领域 / 研究方向",
            "SUPPORT_LANGUAGE", "支持语言",
            "PROGRAMMING_LANGUAGE", "编程语言",
            "DIFFICULTY", "项目难度",
            "DURATION_RANGE", "开发周期筛选",
            "WEEKLY_HOURS_RANGE", "每周投入筛选"
    );

    private final DictItemRepository items;
    private final AuditService audit;

    public DictService(DictItemRepository items, AuditService audit) {
        this.items = items;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, List<DictItemView>> publicCatalog() {
        Map<String, List<DictItemView>> grouped = new LinkedHashMap<>();
        for (DictItem item : items.findByEnabledTrueOrderByDictTypeAscSortOrderAscIdAsc()) {
            grouped.computeIfAbsent(item.getDictType(), key -> new java.util.ArrayList<>()).add(toView(item));
        }
        return grouped;
    }

    @Transactional(readOnly = true)
    public List<DictItemView> publicByType(String dictType) {
        requireType(dictType);
        return items.findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(dictType.trim().toUpperCase())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> enabledValues(String dictType) {
        return publicByType(dictType).stream().map(DictItemView::value).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<DictItemView> adminList(String dictType) {
        AuthSupport.requireRole(Role.ADMIN);
        if (dictType == null || dictType.isBlank()) {
            return items.findAllByOrderByDictTypeAscSortOrderAscIdAsc().stream().map(this::toView).toList();
        }
        requireType(dictType);
        return items.findByDictTypeOrderBySortOrderAscIdAsc(dictType.trim().toUpperCase())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public DictItemView create(DictRequest request) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        String type = requireType(request.dictType());
        String value = request.itemValue().trim();
        if (items.findByDictTypeAndItemValue(type, value).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该字典值已存在");
        }
        DictItem item = new DictItem();
        item.setDictType(type);
        item.setItemValue(value);
        item.setItemLabel(request.itemLabel().trim());
        item.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        item.setEnabled(request.enabled() == null || request.enabled());
        items.save(item);
        audit.record(admin.getId(), "DICT", item.getId(), "CREATE", type + ":" + value);
        return toView(item);
    }

    @Transactional
    public DictItemView update(Long id, DictRequest request) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        DictItem item = items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "字典项不存在"));
        String type = requireType(request.dictType());
        String value = request.itemValue().trim();
        if (items.existsByDictTypeAndItemValueAndIdNot(type, value, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该字典值已存在");
        }
        item.setDictType(type);
        item.setItemValue(value);
        item.setItemLabel(request.itemLabel().trim());
        item.setSortOrder(request.sortOrder() == null ? item.getSortOrder() : request.sortOrder());
        if (request.enabled() != null) {
            item.setEnabled(request.enabled());
        }
        items.save(item);
        audit.record(admin.getId(), "DICT", item.getId(), "UPDATE", type + ":" + value);
        return toView(item);
    }

    @Transactional
    public void delete(Long id) {
        AuthUser admin = AuthSupport.requireRole(Role.ADMIN);
        DictItem item = items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "字典项不存在"));
        items.delete(item);
        audit.record(admin.getId(), "DICT", id, "DELETE", item.getDictType() + ":" + item.getItemValue());
    }

    private String requireType(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择字典类型");
        }
        String type = dictType.trim().toUpperCase();
        if (!DICT_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的字典类型");
        }
        return type;
    }

    private DictItemView toView(DictItem item) {
        return new DictItemView(
                item.getId(),
                item.getDictType(),
                DICT_TYPE_LABELS.getOrDefault(item.getDictType(), item.getDictType()),
                item.getItemValue(),
                item.getItemLabel(),
                item.getSortOrder(),
                item.isEnabled()
        );
    }

    public record DictItemView(
            Long id,
            String dictType,
            String dictTypeLabel,
            String value,
            String label,
            Integer sortOrder,
            boolean enabled
    ) {
    }

    public record DictRequest(
            @NotBlank String dictType,
            @NotBlank @Size(max = 64) String itemValue,
            @NotBlank @Size(max = 128) String itemLabel,
            Integer sortOrder,
            Boolean enabled
    ) {
    }

    public record DictTypeView(String value, String label) {
    }
}
