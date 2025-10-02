package tatar.eljah.hamsters.tools.blockeditor;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.svg.SVGDocument;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

final class SvgLoader {
    private SvgLoader() {
    }

    static SvgHandle load(File file) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        SVGDocument document = factory.createSVGDocument(file.toURI().toString());
        return buildHandle(document);
    }

    static SvgHandle load(InputStream inputStream, String documentUri) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        SVGDocument document = factory.createSVGDocument(documentUri, inputStream);
        return buildHandle(document);
    }

    private static SvgHandle buildHandle(SVGDocument document) {
        UserAgentAdapter userAgent = new UserAgentAdapter();
        DocumentLoader loader = new DocumentLoader(userAgent);
        BridgeContext context = new BridgeContext(userAgent, loader);
        context.setDynamicState(BridgeContext.STATIC);

        try {
            GVTBuilder builder = new GVTBuilder();
            GraphicsNode node = builder.build(context, document);

            Rectangle2D bounds = node.getPrimitiveBounds();
            if (bounds == null) {
                bounds = node.getBounds();
            }
            if (bounds == null) {
                bounds = new Rectangle2D.Double(0, 0, 0, 0);
            }

            return new SvgHandle(node, bounds);
        } finally {
            loader.dispose();
            context.dispose();
        }
    }
}
