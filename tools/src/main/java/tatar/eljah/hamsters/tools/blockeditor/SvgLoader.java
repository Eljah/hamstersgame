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

final class SvgLoader {
    private SvgLoader() {
    }

    static SvgHandle load(File file) throws IOException {
        return load(file.toURI().toString());
    }

    static SvgHandle load(String uri) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        SVGDocument document = factory.createSVGDocument(uri);
        return buildHandle(document);
    }

    private static SvgHandle buildHandle(SVGDocument document) throws IOException {
        UserAgentAdapter userAgent = new UserAgentAdapter();
        DocumentLoader loader = new DocumentLoader(userAgent);
        BridgeContext context = new BridgeContext(userAgent, loader);
        context.setDynamicState(BridgeContext.STATIC);

        GVTBuilder builder = new GVTBuilder();
        GraphicsNode node = builder.build(context, document);

        Rectangle2D bounds = node.getPrimitiveBounds();
        if (bounds == null) {
            bounds = node.getBounds();
        }
        if (bounds == null) {
            bounds = new Rectangle2D.Double(0, 0, 0, 0);
        }
        loader.dispose();
        context.dispose();

        return new SvgHandle(node, bounds);
    }
}
