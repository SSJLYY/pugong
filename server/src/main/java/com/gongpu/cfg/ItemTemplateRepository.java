package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemTemplateRepository extends JpaRepository<ItemTemplate, Long> {

    List<ItemTemplate> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<ItemTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
