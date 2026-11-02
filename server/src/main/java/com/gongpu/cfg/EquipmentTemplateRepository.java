package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentTemplateRepository extends JpaRepository<EquipmentTemplate, Long> {

    List<EquipmentTemplate> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<EquipmentTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
