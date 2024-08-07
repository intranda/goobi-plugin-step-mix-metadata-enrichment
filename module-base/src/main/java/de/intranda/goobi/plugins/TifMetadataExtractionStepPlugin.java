package de.intranda.goobi.plugins;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.XmlTools;
import edu.harvard.hul.ois.jhove.App;
import edu.harvard.hul.ois.jhove.JhoveBase;
import edu.harvard.hul.ois.jhove.Module;
import edu.harvard.hul.ois.jhove.OutputHandler;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.configuration.SubnodeConfiguration;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@PluginImplementation
@Log4j2
public class TifMetadataExtractionStepPlugin implements IStepPluginVersion2 {
    
    @Getter
    private String title = "intranda_step_tif_metadata_extraction";
    @Getter
    private Step step;
    @Getter
    private String value;
    @Getter 
    private boolean allowTaskFinishButtons;
    private String returnPath;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
                
        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        value = myconfig.getString("value", "default value"); 
        allowTaskFinishButtons = myconfig.getBoolean("allowTaskFinishButtons", false);
        log.info("TifMetadataExtraction step plugin initialized");
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
            App app = new App(TifMetadataExtractionStepPlugin.class.getSimpleName(), "1.0",
                    new int[] { calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH) }, "jHove", "");

            JhoveBase jhoveBase = new JhoveBase();

//            File jhoveConfigFile = new File(configuration.getJhoveConfigurationFile());
            File jhoveConfigFile = new File("/opt/digiverso/goobi/config/jhove/jhove.conf");

            jhoveBase.init(jhoveConfigFile.getAbsolutePath(), null);
            Path outputPath = Paths.get(getStep().getProzess().getProcessDataDirectory(), "validation", System.currentTimeMillis() + "_jhove");
            Files.createDirectories(outputPath);

            List<AbstractMap.SimpleEntry<String, String>> inputOutputList = new ArrayList<>();

            Module module = jhoveBase.getModule(null);
            OutputHandler xmlHandler = jhoveBase.getHandler("XML");

            jhoveBase.setEncoding("utf-8");
            jhoveBase.setBufferSize(4096);
            jhoveBase.setChecksumFlag(false);
            jhoveBase.setShowRawFlag(true);
            jhoveBase.setSignatureFlag(false);
            List<Path> imagesInFolder = new ArrayList<>();

//        for (String f : configuration.getFolders()) {
            imagesInFolder.addAll(StorageProvider.getInstance().listFiles(getStep().getProzess().getImagesTifDirectory(false), NIOFileUtils.imageNameFilter));
//        }

            for (Path image : imagesInFolder) {
                String inputName = image.getFileName().toString();
                String outputName = inputName.substring(0, inputName.lastIndexOf('.')) + ".xml";
                Path fOutputPath = outputPath.resolve(outputName);
                inputOutputList.add(new AbstractMap.SimpleEntry<>(image.toString(), fOutputPath.toString()));
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
                Element rootNode = jdomDocument.getRootElement();

                XPathFactory xPathFactory = XPathFactory.instance();
                XPathExpression<Element> mixXPath = xPathFactory.compile("//*[local-name()='mix']", Filters.element());
                List<Element> result = mixXPath.evaluate(jdomDocument);
                System.err.println(result.size());

                if (result.isEmpty()) {
                    log.warn("No MIX metadata found for image: " + se.getKey());
                    continue;
                }

                if (result.size() != 1) {
                    log.error("Only a single MIX metadata result expected, found: " + result.size());
                    return PluginReturnValue.ERROR;
                }

                // Find relevant page element
                String currentImageName = Paths.get(se.getKey()).getFileName().toString();
                Optional<DocStruct> page = physical.getAllChildren().stream()
                        .filter(p -> p.getImageName().equals(currentImageName))
                        .findFirst();

                if (page.isEmpty()) {
                    log.warn("Can't save MIX metadata to Mets file, file reference does not exist in Mets file: " + se.getKey());
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
        
        log.info("TifMetadataExtraction step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    private void handleException(Exception e) {
        log.error(e);
        Helper.addMessageToProcessJournal(getStep().getProzess().getId(), LogType.ERROR, "The image metadata extraction failed: " + e.getMessage(), "");
    }
}
