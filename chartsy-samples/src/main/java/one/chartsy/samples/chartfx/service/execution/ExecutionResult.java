/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.execution;

import one.chartsy.samples.chartfx.dos.Order;

public class ExecutionResult {
    public enum ExecutionResultEnum {
        OK,
        ERROR,
        CANCEL
    }

    private final Order order;
    private ExecutionResultEnum result;
    private String errorMessage;

    public ExecutionResult(Order order) {
        this(ExecutionResultEnum.OK, order);
    }

    public ExecutionResult(ExecutionResultEnum resultEnum, Order order) {
        this.order = order;
        setResult(resultEnum);
    }

    public Order getOrder() {
        return order;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ExecutionResultEnum getResult() {
        return result;
    }

    public void setResult(ExecutionResultEnum result) {
        this.result = result;
    }
}
