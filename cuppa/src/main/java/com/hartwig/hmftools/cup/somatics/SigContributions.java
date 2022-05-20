package com.hartwig.hmftools.cup.somatics;

import static java.lang.Math.round;

import static com.hartwig.hmftools.common.stats.Percentiles.getPercentile;
import static com.hartwig.hmftools.cup.CuppaConfig.CUP_LOGGER;
import static com.hartwig.hmftools.cup.common.CategoryType.SNV;
import static com.hartwig.hmftools.cup.common.CupCalcs.calcPercentilePrevalence;
import static com.hartwig.hmftools.cup.common.CupConstants.UNDEFINED_SIG_PERC_MAX_MULTIPLE;
import static com.hartwig.hmftools.cup.common.ResultType.LIKELIHOOD;
import static com.hartwig.hmftools.cup.common.ResultType.PERCENTILE;
import static com.hartwig.hmftools.cup.somatics.RefSomatics.convertSignatureName;
import static com.hartwig.hmftools.cup.somatics.SomaticDataLoader.loadSigContribsFromCohortFile;
import static com.hartwig.hmftools.cup.somatics.SomaticDataLoader.loadSigContribsFromDatabase;
import static com.hartwig.hmftools.cup.somatics.SomaticSigs.SIG_NAME_13;
import static com.hartwig.hmftools.cup.somatics.SomaticSigs.SIG_NAME_2;
import static com.hartwig.hmftools.cup.somatics.SomaticSigs.signatureDisplayName;
import static com.hartwig.hmftools.cup.somatics.SomaticsCommon.INCLUDE_AID_APOBEC_SIG;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.sigs.SignatureAllocation;
import com.hartwig.hmftools.common.sigs.SignatureAllocationFile;
import com.hartwig.hmftools.common.utils.Matrix;
import com.hartwig.hmftools.cup.CuppaConfig;
import com.hartwig.hmftools.cup.common.SampleData;
import com.hartwig.hmftools.cup.common.SampleDataCache;
import com.hartwig.hmftools.cup.common.SampleResult;

public class SigContributions
{
    private final CuppaConfig mConfig;
    private final SampleDataCache mSampleDataCache;

    private final SomaticSigs mSomaticSigs;
    private final Map<String,Map<String,Double>> mSampleSigContributions;
    private final Map<String,Map<String,double[]>> mRefCancerSigContribPercentiles;
    private final boolean mAidApobecSigFeature;
    private final Map<String,double[]> mRefCancerAidApobecPercentiles;

    public SigContributions(final CuppaConfig config, final SampleDataCache sampleDataCache)
    {
        mConfig = config;
        mSampleDataCache = sampleDataCache;

        mSomaticSigs = new SomaticSigs(mConfig.RefSnvSignaturesFile);
        mSampleSigContributions = Maps.newHashMap();
        mRefCancerSigContribPercentiles = Maps.newHashMap();

        mAidApobecSigFeature = false;
        // mAidApobecSigFeature = cmd != null ? cmd.hasOption(INCLUDE_AID_APOBEC_SIG) : false;
        mRefCancerAidApobecPercentiles = Maps.newHashMap();

        if(mAidApobecSigFeature)
        {
            for(Map.Entry<String,Map<String,double[]>> entry : mRefCancerSigContribPercentiles.entrySet())
            {
                String cancerType = entry.getKey();
                final double[] aaPercentiles = entry.getValue().get(SIG_NAME_2);

                if(aaPercentiles != null)
                    mRefCancerAidApobecPercentiles.put(cancerType, aaPercentiles);
            }
        }
    }

    public Map<String,Map<String,double[]>> getRefCancerSigContribPercentiles() { return mRefCancerSigContribPercentiles; }

    public void addSigContributionResults(final SampleData sample, final List<SampleResult> results)
    {
        final Map<String,Double> sampleSigContribs = mSampleSigContributions.get(sample.Id);

        if(sampleSigContribs == null)
        {
            CUP_LOGGER.debug("sample({}) sig contributions not found", sample.Id);
            return;
        }

        // report on every one of the designated set
        for(final String sigName : SomaticSigs.REPORTABLE_SIGS.keySet())
        {
            double sampleSigContrib = sampleSigContribs.containsKey(sigName) ? sampleSigContribs.get(sigName) : 0;

            // report the AID/APOBEC sigs 2 & 13 together
            if(sigName.equalsIgnoreCase(SIG_NAME_2))
            {
                sampleSigContrib += sampleSigContribs.containsKey(SIG_NAME_13) ? sampleSigContribs.get(SIG_NAME_13) : 0;
            }
            else if(sigName.equalsIgnoreCase(SIG_NAME_13))
            {
                continue;
            }

            Map<String, Double> cancerResults = Maps.newHashMap();

            for(Map.Entry<String,Map<String,double[]>> cancerContribs : mRefCancerSigContribPercentiles.entrySet())
            {
                final String cancerType = cancerContribs.getKey();
                final double[] refSigPercentiles = cancerContribs.getValue().get(sigName);

                if(refSigPercentiles == null)
                {
                    // CUP_LOGGER.debug("missing sig({}) data for cancerType({})", sigName, cancerType);
                    cancerResults.put(cancerType, 0.0);
                }
                else
                {
                    double percentile = getPercentile(refSigPercentiles, sampleSigContrib, true, UNDEFINED_SIG_PERC_MAX_MULTIPLE);
                    cancerResults.put(cancerType, percentile);
                }
            }

            results.add(new SampleResult(
                    sample.Id, SNV, PERCENTILE, signatureDisplayName(sigName), String.valueOf(round(sampleSigContrib)), cancerResults));
        }

        /*
        if(mAidApobecSigFeature)
        {
            // note that in the ref data, sigs @ & 13 have already been combined
            double aidApobecContrib = sampleSigContribs.entrySet().stream()
                    .filter(x -> x.getKey().equals(SIG_NAME_2) || x.getKey().equals(SIG_NAME_13))
                    .mapToDouble(x -> x.getValue()).sum();

            int cancerTypeCount = mSampleDataCache.RefCancerSampleData.size();
            int cancerSampleCount = sample.isRefSample() ? mSampleDataCache.getCancerSampleCount(sample.cancerType()) : 0;

            final Map<String,Double> aidApobecSigsHigh = calcPercentilePrevalence(
                    sample, cancerSampleCount, cancerTypeCount, mRefCancerAidApobecPercentiles, aidApobecContrib, false);

            results.add(new SampleResult(
                    sample.Id, SNV, LIKELIHOOD, "AID_APOBEC_SIG", String.format("%.0f", aidApobecContrib), aidApobecSigsHigh));
        }
        */
    }


    public boolean loadSigContributions(final Matrix snvCounts)
    {
        if(!mConfig.SampleSigContribFile.isEmpty())
        {
            CUP_LOGGER.info("loading SNV sig contributions from file({})", mConfig.SampleSigContribFile);
            return loadSigContribsFromCohortFile(mConfig.SampleSigContribFile, mSampleSigContributions);
        }
        else if(mConfig.DbAccess != null)
        {
            CUP_LOGGER.info("loading SNV sig contributions from database");
            return loadSigContribsFromDatabase(mConfig.DbAccess, mSampleDataCache.SampleIds, mSampleSigContributions);
        }

        if(mSampleDataCache.isMultiSample())
        {
            CUP_LOGGER.error("missing loading config for SNV sig contributions - requires database or cohort file");
            return false;
        }

        final String sampleId = mSampleDataCache.SampleIds.get(0);

        // use sig-allocation file if exists
        final String sigAllocFile = SignatureAllocationFile.generateFilename(mConfig.SampleDataDir, sampleId);

        if(Files.exists(Paths.get(sigAllocFile)))
        {
            try
            {
                final List<SignatureAllocation> sigAllocations = SignatureAllocationFile.read(sigAllocFile);
                Map<String, Double> sigContribs = Maps.newHashMap();
                for(final SignatureAllocation sigAllocation : sigAllocations)
                {
                    final String sigName = convertSignatureName(sigAllocation.signature());
                    sigContribs.put(sigName, sigAllocation.allocation());
                }

                mSampleSigContributions.put(sampleId, sigContribs);
            }
            catch (Exception e)
            {
                CUP_LOGGER.error("sample({}) failed to load sig allocations file({}): {}",
                        sampleId, sigAllocFile, e.toString());
                return false;
            }
        }
        else if(mSomaticSigs.hasValidData() && snvCounts != null)
        {
            CUP_LOGGER.debug("sample({}) running SNV signatures", sampleId);

            final double[] sigAllocations = mSomaticSigs.fitSampleCounts(snvCounts.getCol(0));

            if(sigAllocations == null)
            {
                CUP_LOGGER.error("sample({}) failed signature fit", sampleId);
                return false;
            }

            final Map<String, Double> sigContribs = Maps.newHashMap();
            mSampleSigContributions.put(sampleId, sigContribs);

            for(int i = 0; i < sigAllocations.length; ++i)
            {
                final String sigName = mSomaticSigs.getSigName(i);
                sigContribs.put(sigName, sigAllocations[i]);
            }
        }

        return true;
    }

}