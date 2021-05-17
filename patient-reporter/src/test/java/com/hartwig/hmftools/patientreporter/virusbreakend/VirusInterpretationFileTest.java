package com.hartwig.hmftools.patientreporter.virusbreakend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import com.google.common.io.Resources;

import org.junit.Test;

public class VirusInterpretationFileTest {

    private static final String VIRUS_INTERPRETATION_TSV = Resources.getResource("viral_reporting/virus_interpretation.tsv").getPath();

    @Test
    public void canReadVirusInterpretationTsv() throws IOException {
        VirusInterpretationModel virusInterpretationModel = VirusInterpretationFile.buildFromTsv(VIRUS_INTERPRETATION_TSV);
        assertEquals(1, virusInterpretationModel.count());

        assertTrue(virusInterpretationModel.hasInterpretation(1));
        assertFalse(virusInterpretationModel.hasInterpretation(2));

        assertEquals("virus", virusInterpretationModel.interpretVirusSpecies(1));
        assertNotEquals("virus1", virusInterpretationModel.interpretVirusSpecies(2));
    }
}