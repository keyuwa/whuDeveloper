package edu.whu.developer.dict;

import edu.whu.developer.dict.DictService.DictItemView;
import edu.whu.developer.dict.DictService.DictRequest;
import edu.whu.developer.dict.DictService.DictTypeView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DictController {

    private final DictService dicts;

    public DictController(DictService dicts) {
        this.dicts = dicts;
    }

    @GetMapping("/dicts")
    public Map<String, List<DictItemView>> catalog() {
        return dicts.publicCatalog();
    }

    @GetMapping("/dicts/types")
    public List<DictTypeView> types() {
        return DictService.DICT_TYPES.stream()
                .sorted()
                .map(type -> new DictTypeView(type, DictService.DICT_TYPE_LABELS.getOrDefault(type, type)))
                .toList();
    }

    @GetMapping("/dicts/{type}")
    public List<DictItemView> byType(@PathVariable String type) {
        return dicts.publicByType(type);
    }

    @GetMapping("/admin/dicts")
    public List<DictItemView> adminList(@RequestParam(required = false) String type) {
        return dicts.adminList(type);
    }

    @PostMapping("/admin/dicts")
    public DictItemView create(@Valid @RequestBody DictRequest request) {
        return dicts.create(request);
    }

    @PutMapping("/admin/dicts/{id}")
    public DictItemView update(@PathVariable Long id, @Valid @RequestBody DictRequest request) {
        return dicts.update(id, request);
    }

    @DeleteMapping("/admin/dicts/{id}")
    public void delete(@PathVariable Long id) {
        dicts.delete(id);
    }
}
