/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import org.apache.batik.svggen.SVGCSSStyler;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class SvgWriter {

    private SvgWriter() {
    }

    static void writeSvg(Path outputFile,
                         SVGGraphics2D svgGraphics) throws IOException {
        Element root = svgGraphics.getRoot();
        SVGCSSStyler.style(root);
        SvgMinifier.minify(root);
        root.setAttributeNS(SVGConstants.XMLNS_NAMESPACE_URI, SVGConstants.XMLNS_PREFIX, SVGConstants.SVG_NAMESPACE_URI);
        if (SvgMinifier.usesXlink(root)) {
            root.setAttributeNS(SVGConstants.XMLNS_NAMESPACE_URI,
                    SVGConstants.XMLNS_PREFIX + ":" + SVGConstants.XLINK_PREFIX,
                    SVGConstants.XLINK_NAMESPACE_URI);
        } else {
            root.removeAttributeNS(SVGConstants.XMLNS_NAMESPACE_URI,
                    SVGConstants.XMLNS_PREFIX + ":" + SVGConstants.XLINK_PREFIX);
            root.removeAttribute("xmlns:xlink");
        }

        TransformerFactory factory = TransformerFactory.newInstance();
        try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.transform(new DOMSource(root), new StreamResult(writer));
        } catch (TransformerException e) {
            throw new IOException("Failed to write SVG output", e);
        }
    }
}
