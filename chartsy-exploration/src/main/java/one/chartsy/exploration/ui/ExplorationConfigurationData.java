package one.chartsy.exploration.ui;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Table;

@Getter
@Setter
@Table(name = "ONE_EXPLORATION_CONFIGURATIONS")
public class ExplorationConfigurationData {
    private Long id;

}
