package one.chartsy.persistence.domain;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.SymbolGroupContent;
import one.chartsy.SymbolIdentity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Optional;

@Entity
@Getter
@Setter
public class SymbolGroupData implements SymbolGroupContent {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String typeName;

    @Override
    public Optional<SymbolIdentity> getSymbol() {
        return Optional.empty();
    }
}