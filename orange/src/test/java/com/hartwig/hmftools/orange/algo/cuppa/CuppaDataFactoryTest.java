package com.hartwig.hmftools.orange.algo.cuppa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.io.Resources;
import com.hartwig.hmftools.common.cuppa.CuppaDataFile;
import com.hartwig.hmftools.common.cuppa2.CuppaPredictions;
import com.hartwig.hmftools.datamodel.cuppa.CuppaData;
import com.hartwig.hmftools.datamodel.cuppa.CuppaPrediction;
import com.hartwig.hmftools.datamodel.cuppa.ImmutableCuppaPrediction;

import org.junit.Test;

public class CuppaDataFactoryTest
{
    private static final double EPSILON = 1.0E-10;

    private static final String CUPPA_VIS_DATA_TSV = Resources.getResource("test_run/cuppa/tumor_sample.cuppa.vis_data.tsv").getPath();

    @Test
    public void canExtractPredictionsFromCuppaV2() throws IOException
    {
        CuppaPredictions cuppaPredictions = CuppaPredictions.fromTsv(CUPPA_VIS_DATA_TSV);

        CuppaData cuppaData = CuppaDataFactory.create(cuppaPredictions);
        List<CuppaPrediction> predictionEntries = cuppaData.predictions();
        //predictionEntries.stream().forEach(o -> System.out.println(o.toString()));

        assertEquals(40, predictionEntries.size());
        assertEquals(cuppaData.bestPrediction().cancerType().toString(), "Skin: Melanoma");


        Map<String, CuppaPrediction> actualPredictionsByCancerType = new HashMap<>();
        for(CuppaPrediction prediction : predictionEntries)
        {
            actualPredictionsByCancerType.put(prediction.cancerType(), prediction);
        }

        Map<String, CuppaPrediction> expectedPredictionsByCancerType = new HashMap<>();
        expectedPredictionsByCancerType.put(
                "Skin: Melanoma",
                ImmutableCuppaPrediction.builder()
                        .cuppaMajorVersion("v2").cancerType("Skin: Melanoma").likelihood(0.9968)
                        .genomicPositionClassifier(0.9966).snv96Classifier(0.8176).eventClassifier(0.7976)
                        .geneExpressionClassifier(1.0).altSjClassifier(0.9999)
                        .build()
        );
        expectedPredictionsByCancerType.put(
                "Skin: Other",
                ImmutableCuppaPrediction.builder()
                        .cuppaMajorVersion("v2").cancerType("Skin: Other").likelihood(1.005E-4)
                        .genomicPositionClassifier(4.215E-4).snv96Classifier(0.1806).eventClassifier(4.02E-5)
                        .geneExpressionClassifier(8.853E-6).altSjClassifier(1.528E-7)
                        .build()
        );
        expectedPredictionsByCancerType.put(
                "Prostate",
                ImmutableCuppaPrediction.builder()
                        .cuppaMajorVersion("v2").cancerType("Prostate").likelihood(1.005E-4)
                        .genomicPositionClassifier(9.123E-5).snv96Classifier(5.793E-12).eventClassifier(3.039E-4)
                        .geneExpressionClassifier(7.483E-7).altSjClassifier(9.215E-7)
                        .build()
        );

        for(String cancerType : expectedPredictionsByCancerType.keySet())
        {
            CuppaPrediction expectedPrediction = expectedPredictionsByCancerType.get(cancerType);
            CuppaPrediction actualPrediction = actualPredictionsByCancerType.get(cancerType);

            assertEquals(expectedPrediction.genomicPositionClassifier(), actualPrediction.genomicPositionClassifier(), EPSILON);
            assertEquals(expectedPrediction.snv96Classifier(), actualPrediction.snv96Classifier(), EPSILON);
            assertEquals(expectedPrediction.eventClassifier(), actualPrediction.eventClassifier(), EPSILON);
            assertEquals(expectedPrediction.geneExpressionClassifier(), actualPrediction.geneExpressionClassifier(), EPSILON);
            assertEquals(expectedPrediction.altSjClassifier(), actualPrediction.altSjClassifier(), EPSILON);
        }
    }

    private static final String CUPPA_TEST_CSV = Resources.getResource("test_run/cuppa/tumor_sample.cup.data.csv").getPath();

    @Test
    public void canExtractPredictionsFromCuppaV1() throws IOException
    {
        List<CuppaDataFile> cuppaPredictions = CuppaDataFile.read(CUPPA_TEST_CSV);
        List<CuppaPrediction> predictionEntries = CuppaDataFactory.extractProbabilitiesCuppaV1(cuppaPredictions);
        assertEquals(36, predictionEntries.size());

        Map<String, CuppaPrediction> actualPredictionsByCancerType = new HashMap<>();
        for(CuppaPrediction prediction : predictionEntries)
        {
            actualPredictionsByCancerType.put(prediction.cancerType(), prediction);
        }

        Map<String, CuppaPrediction> expectedPredictionsByCancerType = new HashMap<>();
        expectedPredictionsByCancerType.put(
                "Melanoma",
                ImmutableCuppaPrediction.builder()
                        .cuppaMajorVersion("v1").cancerType("Breast: Triple negative").likelihood(0.996)
                        .genomicPositionClassifier(0.990).snv96Classifier(0.979).eventClassifier(0.972)
                        .build()
        );
        expectedPredictionsByCancerType.put(
                "Pancreas",
                ImmutableCuppaPrediction.builder()
                        .cuppaMajorVersion("v1").cancerType("Pancreas").likelihood(0.00013)
                        .genomicPositionClassifier(6.05e-05).snv96Classifier(7.89E-29).eventClassifier(1.8e-05)
                        .build()
        );
        expectedPredictionsByCancerType.put(
                "Lung: Non-small Cell",
                ImmutableCuppaPrediction.builder()
                        .cuppaMajorVersion("v1").cancerType("Gynecologic: Ovary/Fallopian tube").likelihood(0.00013)
                        .genomicPositionClassifier(2.91e-05).snv96Classifier(4.75E-28).eventClassifier(0.00518)
                        .build()
        );

        for(String cancerType : expectedPredictionsByCancerType.keySet())
        {
            CuppaPrediction expectedPrediction = expectedPredictionsByCancerType.get(cancerType);
            CuppaPrediction actualPrediction = actualPredictionsByCancerType.get(cancerType);

            assertEquals(expectedPrediction.genomicPositionClassifier(), actualPrediction.genomicPositionClassifier(), EPSILON);
            assertEquals(expectedPrediction.snv96Classifier(), actualPrediction.snv96Classifier(), EPSILON);
            assertEquals(expectedPrediction.eventClassifier(), actualPrediction.eventClassifier(), EPSILON);
        }
    }

    @Test
    public void doNotCrashOnMissingEntries()
    {
        CuppaPredictions cuppaPredictionsV2 = new CuppaPredictions(new ArrayList<>());
        List<CuppaDataFile> cuppaPredictionsV1 = new ArrayList<>();

        assertNotNull(CuppaDataFactory.create(cuppaPredictionsV2, cuppaPredictionsV1));
    }
}