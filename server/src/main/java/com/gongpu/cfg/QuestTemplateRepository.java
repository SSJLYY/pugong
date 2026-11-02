package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestTemplateRepository extends JpaRepository<QuestTemplate, Long> {

    List<QuestTemplate> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<QuestTemplate> findByCode(String code);
}
