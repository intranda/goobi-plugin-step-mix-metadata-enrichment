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
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.digester.plugins.PluginException;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Document;
import org.jdom2.Element;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@PluginImplementation
@Log4j2
public class MixMetadataEnrichmentPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_mix_metadata_enrichment";
    @Getter
    private Step step;
    @Getter
    private File jhoveConfigFile;
    private List<String> configuredFoldersToRename;
    private VariableReplacer variableReplacer;

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
            configuredFoldersToRename = myconfig.getList("folder", List.of("*"))
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
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

            List<Path> foldersToAnalyze = determineFoldersToAnalyze();
            log.trace("Performing analysis in these folders: {}", foldersToAnalyze.stream().map(Path::toString).collect(Collectors.joining(", ")));

            for (Path folder : foldersToAnalyze) {
                filesToAnalyze.addAll(StorageProvider.getInstance().listFiles(folder.toString(), NIOFileUtils.imageNameFilter));
            }

            for (Path file : filesToAnalyze) {
                String inputName = file.toString().replace("/", "_").replace("\\", "_"); // use full path as target to avoid file name conflicts in different sub directories
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

            for (AbstractMap.SimpleEntry<String, String> se : inputOutputList) {
                Document jdomDocument = jdomBuilder.build(se.getValue());

                XPathFactory xPathFactory = XPathFactory.instance();
                XPathExpression<Element> mixXPath = xPathFactory.compile("//*[local-name()='mix']", Filters.element());
                List<Element> result = mixXPath.evaluate(jdomDocument);

                if (result.isEmpty()) {
                    log.warn("No MIX metadata found for image: {}", se.getKey());
                    continue;
                }

                if (result.size() != 1) {
                    log.error("Only a single MIX metadata result expected, found: {}", result.size());
                    return PluginReturnValue.ERROR;
                }

                // Find relevant page element
                String currentImageName = Paths.get(se.getKey()).getFileName().toString();
                Optional<DocStruct> page = Optional.empty();
                if (physical.getAllChildren() != null) {
                    page = physical.getAllChildren().stream()
                            .filter(p -> p.getImageName().equals(currentImageName))
                            .findFirst();
                }

                if (page.isEmpty()) {
                    log.warn("Can't save MIX metadata to Mets file, file reference does not exist in Mets file: {}", se.getKey());
                    continue;
                }

                Md md = new Md(result.get(0), Md.MdType.TECH_MD);
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

    private List<Path> determineFoldersToAnalyze() throws IOException, SwapException, DAOException {
        List<Path> result = new LinkedList<>();
        for (String folderSpecification : configuredFoldersToRename) {
            result.addAll(determineRealPathsForConfiguredFolder(folderSpecification));
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    private List<Path> determineRealPathsForConfiguredFolder(String configuredFolder) throws IOException, SwapException, DAOException {
        if ("*".equals(configuredFolder)) {
            return determineDefaultFoldersToRename();
        } else {
            return transformConfiguredFolderSpecificationToRealPath(configuredFolder);
        }
    }

    private List<Path> determineDefaultFoldersToRename() throws IOException, SwapException, DAOException {
        Process process = getStep().getProzess();
        return Stream.of(
                        Paths.get(process.getImagesOrigDirectory(false)),
                        Paths.get(process.getImagesTifDirectory(false)),
                        Paths.get(process.getOcrAltoDirectory()),
                        Paths.get(process.getOcrPdfDirectory()),
                        Paths.get(process.getOcrTxtDirectory()),
                        Paths.get(process.getOcrXmlDirectory()))
                .filter(this::pathIsPresent)
                .collect(Collectors.toList());
    }

    private List<Path> transformConfiguredFolderSpecificationToRealPath(String folderSpecification) throws IOException, SwapException {
        String folder = ConfigurationHelper.getInstance().getAdditionalProcessFolderName(folderSpecification);
        folder = variableReplacer.replace(folder);
        Path configuredFolder = Paths.get(getStep().getProzess().getImagesDirectory(), folder);
        return List.of(configuredFolder);
    }

    private boolean pathIsPresent(Path path) {
        return StorageProvider.getInstance().isDirectory(path);
    }

    private void handleException(Exception e) {
        log.error(e);
        Helper.addMessageToProcessJournal(getStep().getProzess().getId(), LogType.ERROR, "The metadata extraction failed: " + e.getMessage(), "");
    }
}
