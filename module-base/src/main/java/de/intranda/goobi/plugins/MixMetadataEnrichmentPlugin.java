package de.intranda.goobi.plugins;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 * <p>
 * Visit the websites for more information.
 * - https://goobi.io
 * - https://www.intranda.com
 * - https://github.com/intranda/goobi
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.XmlTools;
import de.sub.goobi.helper.exceptions.SwapException;
import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.digester.plugins.PluginException;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Md;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@PluginImplementation
@Log4j2
public class MixMetadataEnrichmentPlugin implements IStepPluginVersion2 {

    @Data
    class ExtraMapping {
        private String source;
        private String target;
        private String transform;

        private XPathExpression<Element> sourceXPathExpression;

        public ExtraMapping(String source, String target, String transform) {
            this.source = source;
            this.target = target;
            this.transform = transform;
            this.sourceXPathExpression = XPathFactory.instance().compile(source, Filters.element(), null, NAMESPACE_JHOVE);
        }

        public Element find(Document doc) {
            return this.sourceXPathExpression.evaluateFirst(doc);
        }
    }

    @Getter
    private String title = "intranda_step_mix_metadata_enrichment";
    @Getter
    private Step step;
    @Getter
    private File jhoveConfigFile;
    private String configuredFolderToScan;
    private List<ExtraMapping> extraMappings;
    private VariableReplacer variableReplacer;


    private static final Namespace NAMESPACE_JHOVE = Namespace.getNamespace("jhove", "http://hul.harvard.edu/ois/xml/ns/jhove");
    private static final Namespace NAMESPACE_MIX = Namespace.getNamespace("mix", "http://www.loc.gov/mix/v20");

    @Getter
    private String returnPath;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        try {
            this.variableReplacer = getVariableReplacer();

            // read parameters from correct block in configuration file
            SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
            jhoveConfigFile = new File(myconfig.getString("jhoveConf", "/opt/digiverso/goobi/config/jhove/jhove.conf"));
            configuredFolderToScan = myconfig.getString("folder", "master");
            extraMappings = parseExtraMappings(myconfig.configurationsAt("extraMappings"));
            log.info("MixMetadataEnrichmentPlugin step plugin initialized");
        } catch (PluginException e) {
            log.error(e.getMessage());
            log.error(e);
        }
    }

    private VariableReplacer getVariableReplacer() throws PluginException {
        try {
            Fileformat fileformat = getStep().getProzess().readMetadataFile();
            return new VariableReplacer(fileformat != null ? fileformat.getDigitalDocument() : null,
                    getStep().getProzess().getRegelsatz().getPreferences(), getStep().getProzess(), step);
        } catch (ReadException | IOException | SwapException | PreferencesException e1) {
            throw new PluginException("Errors happened while trying to initialize the Fileformat and VariableReplacer", e1);
        }
    }

    private @NonNull List<ExtraMapping> parseExtraMappings(List<HierarchicalConfiguration> config) {
        return config.stream()
                .flatMap(c -> c.configurationsAt("value").stream())
                .map(c -> new ExtraMapping(
                                c.getString("@source"),
                                c.getString("@target"),
                                c.getString("@transform")
                        )
                )
                .collect(Collectors.toList());
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = true;
        // your logic goes here

        try {
            Calendar calendar = Calendar.getInstance();
            App app = new App(MixMetadataEnrichmentPlugin.class.getSimpleName(), "1.0",
                    new int[]{calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)}, "jHove", "");

            JhoveBase jhoveBase = new JhoveBase();

            jhoveBase.init(jhoveConfigFile.getAbsolutePath(), null);
            Path outputPath = Paths.get(getStep().getProzess().getProcessDataDirectory(), "mix_metadata", System.currentTimeMillis() + "_jhove");
            Files.createDirectories(outputPath);

            List<AbstractMap.SimpleEntry<String, String>> inputOutputList = new ArrayList<>();

            Module module = jhoveBase.getModule(null);
            OutputHandler xmlHandler = jhoveBase.getHandler("XML");

            jhoveBase.setEncoding("utf-8");
            jhoveBase.setBufferSize(4096);
            jhoveBase.setChecksumFlag(false);
            jhoveBase.setShowRawFlag(true);
            jhoveBase.setSignatureFlag(false);
            List<Path> filesToAnalyze = new ArrayList<>();

            Path folderToAnalyze = determineFolderToAnalyze();
            log.trace("Performing analysis in the folder: {}", folderToAnalyze.toString());

            filesToAnalyze.addAll(StorageProvider.getInstance().listFiles(folderToAnalyze.toString(), NIOFileUtils.imageNameFilter));

            for (Path file : filesToAnalyze) {
                String inputName = file.getFileName().toString();
                String outputName = inputName.substring(0, inputName.lastIndexOf('.')) + ".xml";
                Path fOutputPath = outputPath.resolve(outputName);
                inputOutputList.add(new AbstractMap.SimpleEntry<>(file.toString(), fOutputPath.toString()));
            }

            for (AbstractMap.SimpleEntry<String, String> se : inputOutputList) {
                jhoveBase.dispatch(app, module, null, xmlHandler, se.getValue(), new String[]{se.getKey()});
            }

            // After all jhove metadata files have been generated, populate the mets file
            Fileformat ff = getStep().getProzess().readMetadataFile();
            DigitalDocument dd = ff.getDigitalDocument();
            DocStruct physical = dd.getPhysicalDocStruct();
            SAXBuilder jdomBuilder = XmlTools.getSAXBuilder();
            MixElementSorter mixElementSorter = new MixElementSorter();

            for (AbstractMap.SimpleEntry<String, String> se : inputOutputList) {
                Document jdomDocument = jdomBuilder.build(se.getValue());

                XPathFactory xPathFactory = XPathFactory.instance();

                XPathExpression<Element> mixXPath = xPathFactory.compile("//*[local-name()='mix']", Filters.element());

                List<Element> resultSet = mixXPath.evaluate(jdomDocument);

                if (resultSet.isEmpty()) {
                    log.warn("No MIX metadata found for image: {}", se.getKey());
                    continue;
                }

                if (resultSet.size() != 1) {
                    log.error("Only a single MIX metadata result expected, found: {}", resultSet.size());
                    return PluginReturnValue.ERROR;
                }

                Element result = resultSet.get(0);

                for (ExtraMapping em : extraMappings) {
                    Element source = em.find(jdomDocument);
                    if (source == null) {
                        continue;
                    }
                    String value = source.getText();
                    Element target = getOrCreateTarget(result, em.target, NAMESPACE_MIX);
                    saveTransformedValue(target, value, em.transform);
                }

                mixElementSorter.fixOrder(result);

                // Find relevant page element
                String currentImageName = Paths.get(se.getKey()).getFileName().toString();
                Optional<DocStruct> page = Optional.empty();
                if (physical.getAllChildren() != null) {
                    page = physical.getAllChildren().stream()
                            .filter(p -> filePrefixEquals(p.getImageName(), currentImageName))
                            .findFirst();
                }

                if (page.isEmpty()) {
                    log.warn("Can't save MIX metadata to Mets file, file reference does not exist in Mets file: {}", se.getKey());
                    continue;
                }

                Md md = new Md(result, Md.MdType.TECH_MD);
                md.generateId();
                dd.addTechMd(md);
                page.get().setAdmId(md.getId());
            }

            getStep().getProzess().writeMetadataFile(ff);
        } catch (Exception e) {
            handleException(e);
            successful = false;
        }

        log.info("MixMetadataEnrichmentPlugin step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    private void saveTransformedValue(Element element, String value, String transform) {
        if (transform == null || transform.isBlank()) {
            element.setText(value);
            return;
        }
        switch (transform) {
            case "rational2real" -> element.setText(calculateDivision(value));
            case "rational2rationalType" -> element.addContent(generateRationalTypeElement(value));
            default -> element.setText(calculateDivision(value));
        }
    }

    private String calculateDivision(String value) {
        if (!value.contains("/")) {
            return value;
        }
        String[] parts = value.split("/");
        double a = Double.parseDouble(parts[0]);
        double b = Double.parseDouble(parts[1]);
        double result = a / b;
        return "" + result;
    }

    private List<Element> generateRationalTypeElement(String value) {
        if (!value.contains("/")) {
            throw new IllegalArgumentException("Unsupported value \"" + value + "\"");
        }
        String[] parts = value.split("/");
        Element numerator = new Element("numerator", NAMESPACE_MIX);
        numerator.setText(parts[0]);
        Element denominator = new Element("denominator", NAMESPACE_MIX);
        denominator.setText(parts[1]);
        return List.of(numerator, denominator);
    }

    private Element getOrCreateTarget(Element result, String target, Namespace namespace) {
        String[] parts = target.split("/");
        Element currentElement = result;
        for (String currentPart : parts) {
            Optional<Element> nextElement = currentElement.getChildren().stream()
                    .filter(e -> e.getName().equals(currentPart))
                    .findFirst();
            if (nextElement.isEmpty()) {
                Element newChild = new Element(currentPart, namespace);
                currentElement.addContent(newChild);
                nextElement = Optional.of(newChild);
            }
            currentElement = nextElement.orElseThrow();
        }
        return currentElement;
    }

    private boolean filePrefixEquals(String a, String b) {
        return a.substring(0, a.lastIndexOf('.')).equals(b.substring(0, b.lastIndexOf('.')));
    }

    private Path determineFolderToAnalyze() throws IOException, SwapException {
        String folder = variableReplacer.replace(determineFolderName(configuredFolderToScan));
        return Paths.get(getStep().getProzess().getImagesDirectory(), folder);
    }

    private String determineFolderName(String configuredFolderToScan) {
        return switch (configuredFolderToScan) {
            case "master" -> ConfigurationHelper.getInstance().getProcessImagesMasterDirectoryName();
            case "media", "main" -> ConfigurationHelper.getInstance().getProcessImagesMainDirectoryName();
            default -> ConfigurationHelper.getInstance().getAdditionalProcessFolderName(configuredFolderToScan);
        };
    }

    private void handleException(Exception e) {
        log.error(e);
        Helper.addMessageToProcessJournal(getStep().getProzess().getId(), LogType.ERROR, "The metadata extraction failed: " + e.getMessage(), "");
    }
}
