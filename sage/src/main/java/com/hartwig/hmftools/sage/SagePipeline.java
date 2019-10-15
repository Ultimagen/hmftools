package com.hartwig.hmftools.sage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.sage.context.AltContext;
import com.hartwig.hmftools.sage.context.NormalRefContextSupplier;
import com.hartwig.hmftools.sage.context.RefContext;
import com.hartwig.hmftools.sage.context.TumorReadContextSupplier;
import com.hartwig.hmftools.sage.context.TumorRefContextCandidates;
import com.hartwig.hmftools.sage.context.TumorRefContextSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;

public class SagePipeline {

    private static final Logger LOGGER = LogManager.getLogger(SagePipeline.class);

    private final GenomeRegion region;
    private final SageConfig config;
    private final Executor executor;
    private final IndexedFastaSequenceFile refGenome;

    public SagePipeline(final GenomeRegion region, final SageConfig config, final Executor executor,
            final IndexedFastaSequenceFile refGenome) {
        this.region = region;
        this.config = config;
        this.executor = executor;
        this.refGenome = refGenome;
    }

    @NotNull
    public CompletableFuture<List<List<AltContext>>> submit() {

        final SagePipelineData sagePipelineData = new SagePipelineData(config.reference(), config.tumor().size());
        List<String> samples = config.tumor();
        List<String> bams = config.tumorBam();

        final List<CompletableFuture<List<AltContext>>> tumorFutures = Lists.newArrayList();
        for (int i = 0; i < samples.size(); i++) {
            final String sample = samples.get(i);
            final String bam = bams.get(i);

            CompletableFuture<List<AltContext>> candidateFuture =
                    CompletableFuture.supplyAsync(new TumorRefContextSupplier(config.minMapQuality(),
                            sample,
                            region,
                            bam,
                            refGenome,
                            new TumorRefContextCandidates(sample)), executor)
                            .thenApply(this::altSupportFilter)
                            .thenApply(x -> new TumorReadContextSupplier(config.minMapQuality(), sample, region, bam, x).get())
                            .thenApply(this::qualityFilter);

            tumorFutures.add(candidateFuture);

        }

        final CompletableFuture<Void> doneTumor = CompletableFuture.allOf(tumorFutures.toArray(new CompletableFuture[tumorFutures.size()]));

        final CompletableFuture<List<RefContext>> normalFuture = doneTumor.thenApply(aVoid -> {

            for (int i = 0; i < tumorFutures.size(); i++) {
                CompletableFuture<List<AltContext>> future = tumorFutures.get(i);
                sagePipelineData.addTumor(i, future.join());
            }

            return new NormalRefContextSupplier(config.minMapQuality(),
                    region,
                    config.referenceBam(),
                    refGenome,
                    sagePipelineData.normalCandidates()).get();
        });

        return normalFuture.thenApply(aVoid -> {

            sagePipelineData.addNormal(normalFuture.join());

            return sagePipelineData.altContexts();
        });
    }

    @NotNull
    private List<AltContext> altSupportFilter(@NotNull final List<RefContext> refContexts) {
        return refContexts.stream().flatMap(x -> x.alts().stream()).filter(x -> x.altSupport() >= config.minTumorAltSupport()).collect(Collectors.toList());
    }

    @NotNull
    private List<AltContext> qualityFilter(@NotNull final List<AltContext> contexts) {
        return contexts.stream().filter(x -> x.primaryReadContext().quality() >= config.minVariantQuality()).collect(Collectors.toList());
    }

}
