package com.hartwig.hmftools.ecrfchecker;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.hartwig.hmftools.common.ecrf.CpctEcrfModel;
import com.hartwig.hmftools.common.ecrf.datamodel.EcrfPatient;
import com.hartwig.hmftools.common.ecrf.reader.XMLEcrfChecker;
import com.hartwig.hmftools.common.ecrf.reader.XMLEcrfDatamodel;
import com.hartwig.hmftools.common.ecrf.reader.XMLEcrfDatamodelReader;
import com.hartwig.hmftools.patientdb.CpctPatientReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class EcrfCheckerApplication {
    private static final Logger LOGGER = LogManager.getLogger(EcrfCheckerApplication.class);
    private static final String ECRF_XML_PATH = "ecrf";
    private static final String CHECK_NEW_CPCT = "check_new_cpct";
    private static final String CHECK_REFERENCES = "references";
    private static final String OLD_ECRF_XML_PATH = "old_ecrf";

    public static void main(final String... args) throws ParseException, IOException, XMLStreamException {
        final Options options = createOptions();
        final CommandLine cmd = createCommandLine(options, args);
        final boolean checkNewCpct = cmd.hasOption(CHECK_NEW_CPCT);
        final boolean checkReferences = cmd.hasOption(CHECK_REFERENCES);

        if (checkReferences) {
            final String ecrfXmlPath = cmd.getOptionValue(ECRF_XML_PATH);
            if (ecrfXmlPath == null) {
                final HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Ecrf-Checker", options);
            } else {
                checkReferences(ecrfXmlPath);
            }
        }

        if (checkNewCpct) {
            final String ecrfXmlPath = cmd.getOptionValue(ECRF_XML_PATH);
            final String oldEcrfXmlPath = cmd.getOptionValue(OLD_ECRF_XML_PATH);
            if (oldEcrfXmlPath == null || ecrfXmlPath == null) {
                final HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Ecrf-Checker", options);
            } else {
                checkNewCpctPatients(ecrfXmlPath, oldEcrfXmlPath);
            }
        }
    }

    @NotNull
    private static Options createOptions() {
        final Options options = new Options();
        options.addOption(CHECK_REFERENCES, false,
                "Flag, if used will check if referenced items are defined in the data model. Uses the file passed with the -ecrf param.");
        options.addOption(CHECK_NEW_CPCT, false,
                "Flag, if used will find newly added CPCT patients and check if the fields used for patient-db can be read. Reads patients from the -ecrf file and checks the -old_ecrf file to determine which patients are new.");
        options.addOption(ECRF_XML_PATH, true, "The path to the ecrf xml file.");
        options.addOption(OLD_ECRF_XML_PATH, true, "The path to the old ecrf xml file.");
        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final Options options, @NotNull final String... args)
            throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    private static void checkReferences(@NotNull final String ecrfXmlPath) throws IOException, XMLStreamException {
        LOGGER.info("Checking references...");
        final XMLInputFactory factory = XMLInputFactory.newInstance();
        final XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream(ecrfXmlPath));
        final XMLEcrfDatamodel datamodel = XMLEcrfDatamodelReader.readXMLDatamodel(reader);
        final List<String> missingItems = XMLEcrfChecker.checkReferences(datamodel);
        missingItems.forEach(LOGGER::info);
        LOGGER.info("Checking references...Done");
    }

    private static void checkNewCpctPatients(@NotNull final String ecrfXmlPath, @NotNull final String oldEcrfXmlPath)
            throws FileNotFoundException, XMLStreamException {
        LOGGER.info("Checking new patients...");
        final CpctEcrfModel oldModel = CpctEcrfModel.loadFromXML(oldEcrfXmlPath);
        final CpctEcrfModel newModel = CpctEcrfModel.loadFromXML(ecrfXmlPath);
        final CpctPatientReader cpctPatientReader = new CpctPatientReader(newModel);
        for (final EcrfPatient patient : newModel.patients()) {
            if (oldModel.findPatientById(patient.patientId()) == null) {
                LOGGER.info("Found new CPCT patient: " + patient.patientId());
                cpctPatientReader.read(patient);
            }
        }
        LOGGER.info("Checking new patients...Done");
    }
}
