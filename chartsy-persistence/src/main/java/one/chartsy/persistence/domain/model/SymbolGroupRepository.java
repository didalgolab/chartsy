package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.SymbolGroupData;
import org.springframework.data.repository.CrudRepository;

public interface SymbolGroupRepository extends CrudRepository<SymbolGroupData, Long> {

    SymbolGroupData findById(long id);
}