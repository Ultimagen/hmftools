package com.hartwig.hmftools.neo.scorer;

import static com.hartwig.hmftools.common.fusion.FusionCommon.FS_UP;
import static com.hartwig.hmftools.neo.NeoCommon.NE_LOGGER;
import static com.hartwig.hmftools.neo.bind.FlankCounts.FLANK_AA_COUNT;
import static com.hartwig.hmftools.neo.scorer.DataLoader.loadAlleleCoverage;
import static com.hartwig.hmftools.neo.scorer.DataLoader.loadNeoEpitopes;
import static com.hartwig.hmftools.neo.scorer.DataLoader.loadPurpleContext;
import static com.hartwig.hmftools.neo.scorer.DataLoader.loadRnaNeoData;
import static com.hartwig.hmftools.neo.scorer.DataLoader.loadSomaticVariants;
import static com.hartwig.hmftools.neo.scorer.NeoScorerConfig.RNA_SAMPLE_APPEND_SUFFIX;
import static com.hartwig.hmftools.neo.scorer.TpmCalculator.DEFAULT_PEPTIDE_LENGTH_RANGE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.isofox.TranscriptExpressionLoader;
import com.hartwig.hmftools.common.neo.RnaNeoEpitope;
import com.hartwig.hmftools.common.purple.PurityContext;
import com.hartwig.hmftools.common.purple.PurityContextFile;
import com.hartwig.hmftools.common.rna.RnaExpressionMatrix;
import com.hartwig.hmftools.neo.bind.BindData;
import com.hartwig.hmftools.neo.bind.BindScorer;

import org.jetbrains.annotations.Nullable;

public class NeoScorerTask implements Callable
{
    private final List<SampleData> mSamples;

    private final NeoScorerConfig mConfig;
    private final ReferenceData mReferenceData;
    private final NeoDataWriter mWriters;

    public NeoScorerTask(
            final NeoScorerConfig config, final ReferenceData referenceData, final NeoDataWriter writers)
    {
        mSamples = Lists.newArrayList();
        mConfig = config;
        mReferenceData = referenceData;
        mWriters = writers;
    }

    public void addSample(final SampleData sampleData) { mSamples.add(sampleData); }

    @Override
    public Long call()
    {
        NE_LOGGER.info("processing {} samples", mSamples.size());

        int sampleIndex = 0;

        try
        {
            for(; sampleIndex < mSamples.size(); ++sampleIndex)
            {
                processSample(mSamples.get(sampleIndex));

                if(sampleIndex > 0 && (sampleIndex % 10) == 0)
                {
                    NE_LOGGER.info("processed {} samples of {}", sampleIndex, mSamples.size());
                }
            }
        }
        catch(Exception e)
        {
            NE_LOGGER.error("sample({}) failed processing", mSamples.get(sampleIndex).Id, e.toString());
            e.printStackTrace();
            System.exit(1);
        }

        NE_LOGGER.info("processing complete", mSamples.size());

        return (long)1;
    }

    public void processSample(final SampleData sample) throws Exception
    {
        String sampleId = sample.Id;

        List<NeoEpitopeData> neoDataList = loadNeoEpitopes(sampleId, mConfig.NeoDir);
        List<AlleleCoverage> alleleCoverages = loadAlleleCoverage(sampleId, mConfig.LilacDir);

        if(neoDataList == null || alleleCoverages == null)
            System.exit(1);

        Set<String> uniqueAlleles = alleleCoverages.stream().map(x -> x.Allele).collect(Collectors.toSet());

        PurityContext purityContext = loadPurpleContext(mConfig.PurpleDir, sampleId);
        double samplePloidy = purityContext.bestFit().ploidy();

        List<RnaNeoEpitope> rnaNeoDataList = loadRnaNeoData(sample, mConfig.IsofoxDir);

        Map<String,Double> sampleTPMs = Maps.newHashMap();

        if(mReferenceData.TranscriptExpression == null || !mReferenceData.TranscriptExpression.hasSampleId(sampleId))
        {
            NE_LOGGER.debug("sample({}) loading transcript expression", sampleId);

            try
            {
                sampleTPMs.putAll(TranscriptExpressionLoader.loadTranscriptExpression(mConfig.IsofoxDir, sampleId));
            }
            catch(Exception e)
            {
                NE_LOGGER.error("failed to load sample({}) transcript expression", sampleId, e.toString());
            }
        }

        List<SomaticVariant> somaticVariants = null;

        if(!mConfig.RnaSomaticVcf.isEmpty())
        {
            String rnaSampleId = sampleId + RNA_SAMPLE_APPEND_SUFFIX;
            List<NeoEpitopeData> pointNeos = neoDataList.stream().filter(x -> x.VariantType.isPointMutation()).collect(Collectors.toList());
            somaticVariants = loadSomaticVariants(sample, rnaSampleId, mConfig.RnaSomaticVcf, pointNeos);
        }

        if(sample.HasRna && (sampleTPMs.isEmpty() || rnaNeoDataList == null || somaticVariants == null))
        {
            NE_LOGGER.error("sample({}) missing required RNA: TPMs({}) fusions({}) variants({})",
                    sampleId, sampleTPMs.isEmpty() ? "missing" : "present",
                    rnaNeoDataList == null ? "missing" : "present", somaticVariants == null ? "missing" : "present");
            System.exit(1);
        }

        // set TPM and RNA fragment & depth as available
        for(NeoEpitopeData neoData : neoDataList)
        {
            // set sample and cohort TPM values
            neoData.setExpressionData(sample, sampleTPMs, mReferenceData.TranscriptExpression, mReferenceData.TpmMedians);

            // set RNA fragment counts for the specific variant or neoepitope
            neoData.setFusionRnaSupport(rnaNeoDataList);
            neoData.setMutationRnaSupport(somaticVariants);
        }

        TpmCalculator tpmCalculator = new TpmCalculator(FLANK_AA_COUNT, DEFAULT_PEPTIDE_LENGTH_RANGE);

        tpmCalculator.compute(sampleId, neoDataList, samplePloidy);

        // build out results per allele and score them
        int scoreCount = 0;

        for(NeoEpitopeData neoData : neoDataList)
        {
            int i = 0;
            while(i < neoData.peptides().size())
            {
                PeptideScoreData peptideScoreData = neoData.peptides().get(i);

                // check for a wild-type match
                if(neoData.Transcripts[FS_UP].stream().anyMatch(x -> mReferenceData.peptideMatchesWildtype(peptideScoreData.Peptide, x)))
                {
                    neoData.peptides().remove(i);
                    continue;
                }

                if(peptideScoreData.alleleScoreData().isEmpty())
                {
                    uniqueAlleles.forEach(x -> peptideScoreData.addAllele(x));

                    for(BindData bindData : peptideScoreData.alleleScoreData())
                    {
                        mReferenceData.PeptideScorer.calcScoreData(bindData);
                        ++scoreCount;
                    }
                }

                ++i;
            }
        }

        NE_LOGGER.debug("sample({}) neoepitopes({}) scored {} allele-peptides",
                sampleId, neoDataList.size(), scoreCount);

        if(mConfig.WriteTypes.contains(OutputType.ALLELE_PEPTIDE))
        {
            for(NeoEpitopeData neoData : neoDataList)
            {
                mWriters.writePeptideData(sampleId, neoData, alleleCoverages);
            }
        }

        if(mConfig.WriteTypes.contains(OutputType.NEOEPITOPE))
        {
            neoDataList.forEach(x -> mWriters.writeNeoData(sampleId, x));
        }
    }
}
