/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import one.chartsy.trade.data.TransactionData;
import one.chartsy.trade.metrics.PerformanceData;
import one.chartsy.trade.metrics.PerformanceData.Type;

import java.util.*;
import java.util.function.Consumer;

/**
 * The list of {@link TransactionData} objects generated usually as a result of
 * simulation or live-trading. Transactions are indexed sequentially meaning the
 * oldest transaction is available at index {@code 0}. The most recent
 * transaction is available at index equal to the total transaction count minus
 * 1, i.e. {@code this.size() - 1}.
 * 
 * @author Mariusz Bernacki
 *
 */
public class TransactionList extends AbstractList<TransactionData> implements RandomAccess, Consumer<TransactionData> {
    /** The underlying list of transactions. */
    private final List<TransactionData> transactions;
    /** The set of build-in performance metrics (created lazily on first access). */
    private final EnumMap<Type, PerformanceData> metrics = new EnumMap<>(Type.class);
    
    
    public TransactionList() {
        this.transactions = new ArrayList<>();
    }
    
    private TransactionList(ArrayList<TransactionData> transactions) {
        this.transactions = transactions;
        
        // trim provided ArrayList to conserve memory
        if (!transactions.isEmpty()) {
            transactions.trimToSize();
            
            // ... and update metrics for each transaction
            for (TransactionData trn : transactions)
                updateMetrics(trn);
        }
    }
    
    @Override
    public boolean add(TransactionData trn) {
        boolean result = transactions.add(trn);
        updateMetrics(trn);
        return result;
    }
    
    protected void updateMetrics(TransactionData trn) {
        if (!metrics.isEmpty())
            for (Map.Entry<Type, PerformanceData> stat : metrics.entrySet())
                if (stat.getKey().test(trn))
                    stat.getValue().add(trn);
    }
    
    public PerformanceData getPerformanceData() {
        return getPerformanceData(PerformanceData.Type.ALL);
    }
    
    public PerformanceData getPerformanceData(PerformanceData.Type type) {
        PerformanceData metric = metrics.get(type);
        if (metric == null) {
            metric = new PerformanceData();
            if (type == Type.ALL)
                metric.addAll(transactions);
            else
                for (TransactionData trn : transactions)
                    if (type.test(trn))
                        metric.add(trn);
            metrics.put(type, metric);
        }
        return metric;
    }
    
    @Override
    public TransactionData get(int index) {
        return transactions.get(index);
    }
    
    @Override
    public int size() {
        return transactions.size();
    }

	@Override
	public final void accept(TransactionData t) {
		add(t);
	}
}
