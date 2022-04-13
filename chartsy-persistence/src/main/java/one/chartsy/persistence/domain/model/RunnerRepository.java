package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.RunnerAggregateData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RunnerRepository extends JpaRepository<RunnerAggregateData, Long> {
}