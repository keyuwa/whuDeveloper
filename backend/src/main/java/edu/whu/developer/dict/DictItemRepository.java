package edu.whu.developer.dict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DictItemRepository extends JpaRepository<DictItem, Long> {

    List<DictItem> findByEnabledTrueOrderByDictTypeAscSortOrderAscIdAsc();

    List<DictItem> findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(String dictType);

    List<DictItem> findByDictTypeOrderBySortOrderAscIdAsc(String dictType);

    List<DictItem> findAllByOrderByDictTypeAscSortOrderAscIdAsc();

    Optional<DictItem> findByDictTypeAndItemValue(String dictType, String itemValue);

    boolean existsByDictTypeAndItemValueAndIdNot(String dictType, String itemValue, Long id);
}
