package de.intranda.goobi.plugins;

import de.sub.goobi.helper.XmlTools;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathFactory;

import java.io.IOException;
import java.util.List;

public class MixElementSorter {
    private List<String> schemaElementOrder;

    public MixElementSorter() throws IOException, JDOMException {
        loadSchema();
    }

    private void loadSchema() throws IOException, JDOMException {
        SAXBuilder jdomBuilder = XmlTools.getSAXBuilder();
        Document jdomDocument = jdomBuilder.build(this.getClass().getResourceAsStream("/mix.xsd"));

        XPathFactory xPathFactory = XPathFactory.instance();

        List<Element> elements = xPathFactory.compile("//*[local-name()='element']", Filters.element()).evaluate(jdomDocument);
        schemaElementOrder = elements.stream().map(e -> e.getAttribute("name").getValue()).toList();
    }

    public void fixOrder(Element mixRoot) {
        mixRoot.sortChildren(this::elementComparator);
        for (Element child : mixRoot.getChildren()) {
            fixOrder(child);
        }
    }

    private int elementComparator(Element a, Element b) {
        int aOrder = this.schemaElementOrder.indexOf(a.getName());
        int bOrder = this.schemaElementOrder.indexOf(b.getName());
        return aOrder - bOrder;
    }
}
