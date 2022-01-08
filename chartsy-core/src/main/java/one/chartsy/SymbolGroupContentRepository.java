package one.chartsy;

import java.util.List;

public interface SymbolGroupContentRepository {

    List<? extends SymbolGroupContent> findByParentGroupId(Long parentId);
}
