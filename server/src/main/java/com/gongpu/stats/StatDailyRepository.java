package com.gongpu.stats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StatDailyRepository extends JpaRepository<StatDaily, LocalDate> {

    List<StatDaily> findTop30ByDayLessThanEqualOrderByDayDesc(LocalDate day);
}
