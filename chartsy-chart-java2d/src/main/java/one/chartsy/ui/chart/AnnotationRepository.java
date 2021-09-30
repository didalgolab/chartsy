package one.chartsy.ui.chart;

import java.util.UUID;

import one.chartsy.SymbolIdentity;
import one.chartsy.ui.chart.annotation.GraphicLayer;

public interface AnnotationRepository {
    
    public GraphicLayer getGraphicModel(String namespace, SymbolIdentity symbol, UUID uuid);
    
}
