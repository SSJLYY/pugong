package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuffTemplateRepository extends JpaRepository<BuffTemplate, Long> {

    List<BuffTemplate> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<BuffTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
