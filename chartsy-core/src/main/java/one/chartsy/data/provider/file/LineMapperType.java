/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

public interface LineMapperType<T> {

    LineMapper<T> createLineMapper(ExecutionContext context);

}
