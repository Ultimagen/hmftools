package com.hartwig.hmftools.serve.vicc;

import java.util.Map;

import com.hartwig.hmftools.common.genome.genepanel.HmfGenePanelSupplier;
import com.hartwig.hmftools.common.genome.region.HmfTranscriptRegion;
import com.hartwig.hmftools.serve.hotspot.ProteinResolver;
import com.hartwig.hmftools.serve.vicc.extractor.CopyNumberExtractor;
import com.hartwig.hmftools.serve.vicc.extractor.FusionExtractor;
import com.hartwig.hmftools.serve.vicc.extractor.GeneLevelEventExtractor;
import com.hartwig.hmftools.serve.vicc.extractor.GeneRangeExtractor;
import com.hartwig.hmftools.serve.vicc.extractor.HotspotExtractor;
import com.hartwig.hmftools.serve.vicc.extractor.SignaturesExtractor;

import org.jetbrains.annotations.NotNull;

public final class ViccExtractorFactory {

    private ViccExtractorFactory() {
    }

    @NotNull
    public static ViccExtractor buildViccExtractor(@NotNull ProteinResolver proteinResolver) {
        Map<String, HmfTranscriptRegion> transcriptPerGeneMap = HmfGenePanelSupplier.allGenesMap37();

        return new ViccExtractor(new HotspotExtractor(proteinResolver),
                new CopyNumberExtractor(),
                new FusionExtractor(),
                new GeneLevelEventExtractor(),
                new GeneRangeExtractor(transcriptPerGeneMap),
                new SignaturesExtractor());
    }
}
