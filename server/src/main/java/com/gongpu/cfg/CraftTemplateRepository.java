package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CraftTemplateRepository extends JpaRepository<CraftTemplate, Long> {

    List<CraftTemplate> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<CraftTemplate> findByCode(String code);
}
