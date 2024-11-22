---
title: MIX Metadata Enrichment Plugin
identifier: intranda_step_mix_metadata_enrichment
description: This Step plugin for Goobi workflow uses JHove to extract technical metadata from files and save the results into the METS file of a Goobi process.
published: true
---

## Introduction
This documentation explains the plugin for enriching MIX Metadata.

## Installation
To be able to use the plugin, the following files must be installed:

```bash
/opt/digiverso/goobi/plugins/step/plugin-step-mix-metadata-enrichment-base.jar
/opt/digiverso/goobi/config/plugin_intranda_step_mix_metadata_enrichment.xml
/opt/digiverso/goobi/config/jhove/jhove.conf
```

Once the plugin has been installed, it can be selected within the workflow for the respective work steps and thus executed automatically. A workflow could look like the following example:

![Example of a workflow structure](screen1_en.png)

To use the plugin, it must be selected in a workflow step:

![Configuration of the workflow step for using the plugin](screen2_en.png)


## Overview and functionality
When the plugin is executed, all image files in the configured folders are analyzed with JHove and the technical metadata is extracted in MIX format.
This technical metadata is then added to the Mets file of the process and linked there to the respective image files.


## Configuration
The plugin is configured in the file `plugin_intranda_step_mix_metadata_enrichment.xml` as shown here:

{{CONFIG_CONTENT}}

{{CONFIG_DESCRIPTION_PROJECT_STEP}}

Parameter               | Explanation
------------------------|------------------------------------
`folder`                | Specifies the folder to be analyzed by JHove to extract technical metadata. <br /><br />The configured folder is used to store the technical metadata in Mets. It is perfectly possible to analyze `master` images and then add technical metadata to derivatives.
`jhoveConfig`           | The path to the JHove configuration file. A sample configuration is included with the plugin.
`renameMappings`        | Any number of renames can be defined in MIX in this element. The child elements must have the following form: `<value from=“a/b/c” to=“d/e” removeEmptyParents=“true\|false”/>` The element `c`, which is in the hierarchy `a/b/c` in MIX, is renamed to `e` as a child element of `d`. If `removeEmptyParents` is set to `true`, both `b` and `a` are removed if they have no further child elements. This can be useful, for example, if data in MIX is predictably in the wrong fields (camera is recognized as a scanner): `<value from=“ImageCaptureMetadata/ScannerCapture/scannerManufacturer” to=“ImageCaptureMetadata/DigitalCameraCapture/digitalCameraManufacturer” removeEmptyParents=“true”/>`. 
`extraMappings`         | Any number of additional MIX fields can be defined in this element, which are not automatically recognized correctly by JHove.<br /><br />The child elements must have the following form: `<value source=“//some/xpath” target=“a/b/c” transform=“TRANSFORM”/>`. `source` contains an XPath expression for a value that can be found in the JHove result. `target` contains the path in MIX where the value is to be stored. `transform` can optionally be specified if a value conversion is required. There are currently two possible conversions: `rational2real` and `rational2rationalType`. `rational2real` converts fractions into numbers with a dot. `rational2rationalType` converts fractions into a special MIX type for fractions. To additionally save the aperture, you could configure something like this: `<value source=“//jhove:property[jhove:name=‘FNumber’]//jhove:value[1]” target=“ImageCaptureMetadata/DigitalCameraCapture/CameraCaptureSettings/ImageData/fNumber” transform=“rational2real”/>`.