package com.hartwig.hmftools.serve.transvar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.genepanel.HmfGenePanelSupplier;
import com.hartwig.hmftools.common.genome.region.HmfTranscriptRegion;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.serve.RefGenomeVersion;
import com.hartwig.hmftools.serve.transvar.datamodel.TransvarRecord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Transvar {

    private static final Logger LOGGER = LogManager.getLogger(Transvar.class);

    @NotNull
    private final TransvarProcessImpl process;
    @NotNull
    private final TransvarInterpreter interpreter;
    @NotNull
    private final Map<String, HmfTranscriptRegion> transcriptPerGeneMap;

    @NotNull
    public static Transvar withRefGenome(@NotNull RefGenomeVersion refGenomeVersion, @NotNull String refGenomeFastaFile)
            throws FileNotFoundException {
        return new Transvar(new TransvarProcessImpl(refGenomeVersion, refGenomeFastaFile),
                TransvarInterpreter.fromRefGenomeFastaFile(refGenomeFastaFile),
                HmfGenePanelSupplier.allGenesMap37());
    }

    private Transvar(@NotNull TransvarProcessImpl process, @NotNull TransvarInterpreter interpreter,
            @NotNull Map<String, HmfTranscriptRegion> transcriptPerGeneMap) {
        this.process = process;
        this.interpreter = interpreter;
        this.transcriptPerGeneMap = transcriptPerGeneMap;
    }

    @NotNull
    public List<VariantHotspot> extractHotspotsFromProteinAnnotation(@NotNull String gene, @Nullable String specificTranscript,
            @NotNull String proteinAnnotation) throws IOException, InterruptedException {
        List<TransvarRecord> records = process.runTransvarPanno(gene, proteinAnnotation);
        if (records.isEmpty()) {
            LOGGER.warn("Transvar could not resolve any genomic coordinates for '{}:p.{}'", gene, proteinAnnotation);
            return Lists.newArrayList();
        }

        HmfTranscriptRegion canonicalTranscript = transcriptPerGeneMap.get(gene);
        if (canonicalTranscript == null) {
            LOGGER.warn("Could not find canonical transcript for '{}' in HMF gene panel. Skipping hotspot extraction for 'p.{}'",
                    gene,
                    proteinAnnotation);
            return Lists.newArrayList();
        }

        TransvarRecord best = pickBestRecord(records, specificTranscript, canonicalTranscript.transcriptID());
        if (specificTranscript != null && !best.transcript().equals(specificTranscript)) {
            LOGGER.warn("No record found on specific transcript '{}'. "
                            + "Instead a record was resolved for '{}' for {}:p.{}. Skipping interpretation",
                    specificTranscript,
                    best.transcript(),
                    gene,
                    proteinAnnotation);
            return Lists.newArrayList();
        }

        LOGGER.debug("Interpreting transvar record: '{}'", best);
        // This is assuming every transcript on a gene lies on the same strand.
        List<VariantHotspot> hotspots = interpreter.convertRecordToHotspots(best, canonicalTranscript.strand());

        if (hotspots.isEmpty()) {
            LOGGER.warn("Could not derive any hotspots from record {} for '{}:p.{}'", best, gene, proteinAnnotation);
        }

        return hotspots;
    }

    @NotNull
    private static TransvarRecord pickBestRecord(@NotNull List<TransvarRecord> records, @Nullable String specificTranscript,
            @NotNull String canonicalTranscript) {
        assert !records.isEmpty();

        TransvarRecord specificRecord = null;
        TransvarRecord canonicalRecord = null;
        TransvarRecord bestRecord = null;
        for (TransvarRecord record : records) {
            if (specificTranscript != null && record.transcript().equals(specificTranscript)) {
                specificRecord = record;
            } else if (record.transcript().equals(canonicalTranscript)) {
                canonicalRecord = record;
            } else {
                bestRecord = record;
            }
        }

        if (specificRecord != null) {
            return specificRecord;
        }

        if (canonicalRecord != null) {
            return canonicalRecord;
        }

        assert bestRecord != null;
        return bestRecord;
    }
}
