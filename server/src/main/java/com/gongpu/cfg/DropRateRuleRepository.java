package com.gongpu.cfg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DropRateRuleRepository extends JpaRepository<DropRateRule, Long> {

    List<DropRateRule> findByEnabledTrueOrderBySortNoAsc();

    Optional<DropRateRule> findByZone(String zone);
}
