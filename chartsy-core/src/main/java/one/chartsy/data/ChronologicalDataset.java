/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

public interface ChronologicalDataset {

    int length();

    long getTimeAt(int index);
}
