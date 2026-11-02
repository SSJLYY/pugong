package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillTemplateRepository extends JpaRepository<SkillTemplate, Long> {

    List<SkillTemplate> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<SkillTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
