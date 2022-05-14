/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.converters;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

import java.nio.file.Path;

@ConfigurationPropertiesBinding
public class PathToStringConverter implements Converter<Path, String> {

    @Override
    public String convert(Path path) {
        return (path == null)? null : path.toString();
    }
}
