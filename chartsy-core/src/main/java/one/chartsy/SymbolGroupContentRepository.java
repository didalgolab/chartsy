/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.util.List;

public interface SymbolGroupContentRepository {

    List<? extends SymbolGroupContent> findByParentGroupId(Long parentId);
}
