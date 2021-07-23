package com.hartwig.hmftools.neo;

import static com.hartwig.hmftools.neo.cohort.AlleleCoverage.EXPECTED_ALLELE_COUNT;
import static com.hartwig.hmftools.neo.cohort.AllelePredictions.buildPercentiles;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.neo.cohort.PredictionData;

import org.junit.Test;

public class CohortTest
{
    @Test
    public void testPredictionAlleles()
    {
        List<PredictionData> predictions = Lists.newArrayList();

        // test all homozygous
        predictions.add(new PredictionData("A0101", 1, "ABC", 1, 0.95));
        predictions.add(new PredictionData("B0101", 1, "ABC", 1, 0.95));
        predictions.add(new PredictionData("C0101", 1, "ABC", 1, 0.95));

        PredictionData.expandHomozygous(predictions);
        assertEquals(EXPECTED_ALLELE_COUNT, predictions.size());
        assertEquals(2, predictions.stream().filter(x -> x.Allele.equals("A0101")).count());
        assertEquals(2, predictions.stream().filter(x -> x.Allele.equals("B0101")).count());
        assertEquals(2, predictions.stream().filter(x -> x.Allele.equals("C0101")).count());

        // test each of only 1 gene being homozygous
        predictions.remove(0);
        PredictionData.expandHomozygous(predictions);
        assertEquals(EXPECTED_ALLELE_COUNT, predictions.size());
        assertEquals(2, predictions.stream().filter(x -> x.Allele.equals("A0101")).count());

        predictions.remove(2);
        PredictionData.expandHomozygous(predictions);
        assertEquals(EXPECTED_ALLELE_COUNT, predictions.size());
        assertEquals(2, predictions.stream().filter(x -> x.Allele.equals("B0101")).count());

        predictions.remove(4);
        PredictionData.expandHomozygous(predictions);
        assertEquals(EXPECTED_ALLELE_COUNT, predictions.size());
        assertEquals(2, predictions.stream().filter(x -> x.Allele.equals("C0101")).count());
    }

    @Test
    public void testAllelePercentiles()
    {
        int[] frequencies = new int[] {10, 50, 410, 40, 0, 50, 500, 900, 150, 50};
        int total = Arrays.stream(frequencies).sum();

        int[] percentiles = buildPercentiles(frequencies, total, 6);

        assertEquals(2, percentiles[0]);
        assertEquals(6, percentiles[1]);
        assertEquals(7, percentiles[2]);
        assertEquals(7, percentiles[3]);
        assertEquals(7, percentiles[4]);
        assertEquals(9, percentiles[5]);
    }
}