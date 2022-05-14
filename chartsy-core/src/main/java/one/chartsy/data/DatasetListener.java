/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

public interface DatasetListener<E> {

    void onLastValueChange(Dataset<E> source);

    void onLastValueAppend(Dataset<E> source);
}
