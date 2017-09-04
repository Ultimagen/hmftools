package com.hartwig.hmftools.hmfslicer;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import com.hartwig.hmftools.common.exception.EmptyFileException;
import com.hartwig.hmftools.common.region.hmfslicer.HmfGenomeRegion;

import org.junit.Test;

public class HmfGeneRegionSupplierTest {

    @Test
    public void testGeneRegionsMatchGenes() throws IOException, EmptyFileException {
        final List<String> genes = HmfSlicerBuilderRunner.readGeneList();
        final List<HmfGenomeRegion> geneRegions = HmfGeneRegionSupplier.get();
        assertEquals(genes.size(), geneRegions.size());
    }
}
