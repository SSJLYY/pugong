package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AffixTemplateRepository extends JpaRepository<AffixTemplate, Long> {

    List<AffixTemplate> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<AffixTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
