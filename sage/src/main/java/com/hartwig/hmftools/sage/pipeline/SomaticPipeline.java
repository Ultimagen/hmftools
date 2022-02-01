package com.hartwig.hmftools.sage.pipeline;

import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.sv.BaseRegion;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.candidate.Candidate;
import com.hartwig.hmftools.sage.config.SageConfig;
import com.hartwig.hmftools.sage.coverage.Coverage;
import com.hartwig.hmftools.sage.phase.PhaseSetCounter;
import com.hartwig.hmftools.sage.quality.QualityRecalibrationMap;
import com.hartwig.hmftools.sage.evidence.ReadContextCounter;
import com.hartwig.hmftools.sage.evidence.ReadContextCounters;
import com.hartwig.hmftools.sage.common.RefSequence;
import com.hartwig.hmftools.sage.common.SageVariant;
import com.hartwig.hmftools.sage.common.SageVariantFactory;

import htsjdk.samtools.reference.ReferenceSequenceFile;

public class SomaticPipeline
{
    private final SageConfig mConfig;
    private final Executor mExecutor;
    private final ReferenceSequenceFile mRefGenome;
    private final CandidateStage mCandidateState;
    private final EvidenceStage mEvidenceStage;

    public SomaticPipeline(
            final SageConfig config, final Executor executor, final ReferenceSequenceFile refGenome,
            final List<VariantHotspot> hotspots, final List<BaseRegion> panelRegions,
            final List<BaseRegion> highConfidenceRegions, final Map<String, QualityRecalibrationMap> qualityRecalibrationMap,
            final PhaseSetCounter phaseSetCounter, final Coverage coverage)
    {
        mConfig = config;
        mExecutor = executor;
        mRefGenome = refGenome;
        mCandidateState = new CandidateStage(config, refGenome, hotspots, panelRegions, highConfidenceRegions, coverage);
        mEvidenceStage = new EvidenceStage(config, refGenome, qualityRecalibrationMap, phaseSetCounter);
    }

    public CompletableFuture<List<SageVariant>> findVariants(final ChrBaseRegion region)
    {
        final CompletableFuture<RefSequence> refSequenceFuture = supplyAsync(() -> new RefSequence(region, mRefGenome), mExecutor);

        final CompletableFuture<List<Candidate>> initialCandidates = mCandidateState.findCandidatesOld(region, refSequenceFuture);

        final CompletableFuture<ReadContextCounters> tumorEvidence =
                mEvidenceStage.findEvidenceOld(region, "tumor", mConfig.TumorIds, mConfig.TumorBams, initialCandidates, true);

        final CompletableFuture<List<Candidate>> finalCandidates = filteredCandidates(tumorEvidence);

        final CompletableFuture<ReadContextCounters> normalEvidence =
                mEvidenceStage.findEvidenceOld(region, "normal", mConfig.ReferenceIds, mConfig.ReferenceBams, finalCandidates, false);

        return createSageVariants(finalCandidates, tumorEvidence, normalEvidence);
    }

    private CompletableFuture<List<SageVariant>> createSageVariants(
            final CompletableFuture<List<Candidate>> candidates, final CompletableFuture<ReadContextCounters> doneTumor,
            final CompletableFuture<ReadContextCounters> doneNormal)
    {
        return doneNormal.thenCombine(doneTumor, (normalCandidates, tumorCandidates) ->
        {
            // SG_LOGGER.trace("gathering evidence in {}:{}", region.Chromosome, region.start());

            final SageVariantFactory variantFactory = new SageVariantFactory(mConfig.Filter);

            // Combine normal and tumor together and create variants
            final List<SageVariant> result = Lists.newArrayList();
            for(Candidate candidate : candidates.join())
            {
                final List<ReadContextCounter> normal = normalCandidates.readContextCounters(candidate.variant());
                final List<ReadContextCounter> tumor = tumorCandidates.readContextCounters(candidate.variant());
                SageVariant sageVariant = variantFactory.create(candidate, normal, tumor);
                result.add(sageVariant);
            }

            return result;
        });
    }

    private CompletableFuture<List<Candidate>> filteredCandidates(final CompletableFuture<ReadContextCounters> tumorEvidence)
    {
        return tumorEvidence.thenApply(x -> x.candidates(mConfig.Filter.readContextFilter()));
    }
}
