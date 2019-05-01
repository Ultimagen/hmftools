package com.hartwig.hmftools.sig_analyser.buckets;

import static java.lang.Math.abs;
import static java.lang.Math.log10;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.round;

import static com.hartwig.hmftools.sig_analyser.SigAnalyser.OUTPUT_DIR;
import static com.hartwig.hmftools.sig_analyser.SigAnalyser.OUTPUT_FILE_ID;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.BA_EXT_SAMPLE_DATA_FILE;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.BA_PREDEFINED_SIGS;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.BA_SAMPLE_CALC_DATA_FILE;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.DEFAULT_SIG_RATIO_RANGE_PERCENT;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.DOMINANT_CATEGORY_PERCENT;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MAJOR_GROUP_ALLOC_PERC;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MAJOR_GROUP_SAMPLE_PERC;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MAX_CANDIDATE_GROUPS;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MAX_ELEVATED_PROB;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MAX_NOISE_TO_SAMPLE_RATIO;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MIN_DISCOVERY_SAMPLE_COUNT;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MIN_GROUP_ALLOC_PERCENT;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.MIN_GROUP_ALLOC_PERCENT_LOWER;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.PERMITTED_PROB_NOISE;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.SAMPLE_ALLOCATED_PERCENT;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.SIG_SIMILAR_CSS;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.SKIP_ALLOC_FACTOR;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.UNIQUE_SIG_CSS_THRESHOLD;
import static com.hartwig.hmftools.sig_analyser.buckets.BaConfig.UNIQUE_SIG_MIN_ALLOC_PERCENT;
import static com.hartwig.hmftools.sig_analyser.common.CosineSim.calcCSS;
import static com.hartwig.hmftools.sig_analyser.common.CosineSim.getTopCssPairs;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.addVector;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.calcBestFitWithinProbability;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.convertToPercentages;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.copyVector;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.doubleToStr;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.getDiffList;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.getMatchingList;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.getNewFile;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.getSortedVectorIndices;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.listToArray;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.sizeToStr;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.sumVector;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.vectorMultiply;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.writeMatrixData;
import static com.hartwig.hmftools.sig_analyser.nmf.NmfConfig.NMF_REF_SIG_FILE;
import static com.hartwig.hmftools.sig_analyser.buckets.SigOptimiser.BUCKET_RANGE_MAX_PERCENT;
import static com.hartwig.hmftools.sig_analyser.buckets.SigOptimiser.SMALL_RATIO_PERC_CUTOFF;
import static com.hartwig.hmftools.sig_analyser.buckets.BucketGroup.BG_TYPE_BACKGROUND;
import static com.hartwig.hmftools.sig_analyser.buckets.BucketGroup.BG_TYPE_MAJOR;
import static com.hartwig.hmftools.sig_analyser.buckets.BucketGroup.BG_TYPE_UNIQUE;
import static com.hartwig.hmftools.common.utils.GenericDataCollection.GD_TYPE_STRING;
import static com.hartwig.hmftools.sig_analyser.common.SigMatrix.redimension;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.common.utils.GenericDataLoader;
import com.hartwig.hmftools.sig_analyser.common.CosineSim;
import com.hartwig.hmftools.sig_analyser.common.DataUtils;
import com.hartwig.hmftools.common.utils.GenericDataCollection;
import com.hartwig.hmftools.sig_analyser.common.SigMatrix;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BucketAnalyser {

    private static final Logger LOGGER = LogManager.getLogger(BucketAnalyser.class);

    private GenericDataCollection mDataCollection;
    private SigDiscovery mSigDiscovery;
    private BaReporter mReporter;
    private BaConfig mConfig;

    private SigMatrix mSampleCounts;

    // for convenience
    private double[] mSampleTotals;
    private double mTotalCount;
    private int mBucketCount;
    private int mSampleCount;
    private int mActiveSampleCount; // minus the excluded samples

    private Map<String, List<Double>> mBucketMediansMap; // cancer-type to median bucket ratios (ie background sigs)
    private SigMatrix mBucketProbs;
    private SigMatrix mBackgroundCounts;
    private SigMatrix mElevatedCounts; // actual - expected, capped at zero
    private double mElevatedCount;
    private double mBackgroundCount;
    private SigMatrix mPermittedElevRange;
    private SigMatrix mPermittedBgRange;
    private List<Double> mSampleBgAllocations;

    private SigMatrix mProposedSigs;
    private List<Integer> mSigToBgMapping; // for each proposed sig, a mapping can be made back to the bucket group that it came from
    private SigMatrix mPredefinedSigs;
    private boolean mFinalFitOnly; // using predefined sigs
    private boolean mUsingRefSigs; // using reference sigs as predefined sigs

    // external sample data for verification and correlation
    private List<SampleData> mSampleData;
    private GenericDataCollection mExtSampleData;
    private HashMap<String,Integer> mExtCategoriesMap;
    private HashMap<String, List<Integer>> mCancerSamplesMap;
    private boolean mLoadedSampleCalcData;

    private int mNextBucketId;
    private List<BucketGroup> mBucketGroups;
    private List<BucketGroup> mFinalBucketGroups;
    private List<BucketGroup> mTopAllocBucketGroups;
    private List<BucketGroup> mBackgroundGroups;
    private List<BucketGroup> mSkippedBucketGroups;
    private List<Integer> mSkippedSamples;
    private List<Integer> mReassessSamples;
    private int mLastRunGroupCount;

    BufferedWriter mBgInterimFileWriter;
    BufferedWriter mBgRatioRangeFileWriter;
    PerformanceCounter mPerfCounter;

    // config
    private String mOutputDir;
    private String mOutputFileId;

    private int mRunId; // current run iteration
    private boolean mLogVerbose;

    // external data file attributes
    private static int COL_SAMPLE_ID = 0;
    private static int COL_CANCER_TYPE = 1;
    private static int CATEGORY_COL_COUNT = 3;
    private static String CATEGORY_CANCER_TYPE = "Cancer";

    private static int SCD_COL_SAMPLE_ID = 0;
    private static int SCD_COL_BG_ALLOC = 2;

    private boolean mHasErrors;

    public BucketAnalyser()
    {
        mOutputDir = "";
        mOutputFileId = "";
        mConfig = new BaConfig();
        mSigDiscovery = null;
        mHasErrors = false;

        mDataCollection = null;
        mSampleCounts = null;
        mSampleTotals = null;
        mTotalCount = 0;
        mActiveSampleCount = 0;
        mBucketCount = 0;
        mSampleCount = 0;
        mBackgroundCounts = null;
        mElevatedCounts = null;
        mElevatedCount = 0;
        mBackgroundCount = 0;
        mPermittedElevRange = null;
        mPermittedBgRange = null;
        mBucketMediansMap = null;
        mSampleBgAllocations = Lists.newArrayList();

        mSampleData = Lists.newArrayList();

        mExtSampleData = null;
        mExtCategoriesMap = null;
        mCancerSamplesMap = null;
        mProposedSigs = null;
        mSigToBgMapping = Lists.newArrayList();
        mLoadedSampleCalcData = false;

        mNextBucketId = 0;
        mBucketGroups = Lists.newArrayList();
        mTopAllocBucketGroups = Lists.newArrayList();
        mFinalBucketGroups = Lists.newArrayList();
        mBackgroundGroups = Lists.newArrayList();
        mSkippedSamples = Lists.newArrayList();
        mReassessSamples = Lists.newArrayList();
        mSkippedBucketGroups = Lists.newArrayList();
        mBgInterimFileWriter = null;
        mBgRatioRangeFileWriter = null;

        mLogVerbose = false;
        mRunId = 0;
        mLastRunGroupCount = 0;
        mFinalFitOnly = false;
        mUsingRefSigs = false;

        mPerfCounter = new PerformanceCounter("BucketAnalyser");
    }

    public double getMinSampleAllocCount() { return mConfig.MinSampleAllocCount; }
    public List<Integer> getReassessSamples() { return mReassessSamples; }
    public int getNextBucketId() { return mNextBucketId++; }
    public void addBucketGroup(BucketGroup bucketGroup) { mBucketGroups.add(bucketGroup); }

    public static void addCmdLineArgs(Options options)
    {
        BaConfig.addCmdLineArgs(options);
    }

    public boolean initialise(GenericDataCollection collection, final CommandLine cmd)
    {
        mReporter = new BaReporter(this);
        mSigDiscovery = new SigDiscovery(this);
        mDataCollection = collection;
        mOutputFileId = cmd.getOptionValue(OUTPUT_FILE_ID);
        mOutputDir = cmd.getOptionValue(OUTPUT_DIR);

        mConfig.load(cmd);

        mSampleCounts = DataUtils.createMatrixFromListData(mDataCollection.getData());
        mSampleCounts.cacheTranspose();
        mSampleCount = mSampleCounts.Cols;
        mBucketCount = mSampleCounts.Rows;

        if(cmd.hasOption(NMF_REF_SIG_FILE))
        {
            final String refSigFilename = cmd.getOptionValue(NMF_REF_SIG_FILE);
            mUsingRefSigs = cmd.hasOption(BA_PREDEFINED_SIGS) && cmd.getOptionValue(BA_PREDEFINED_SIGS).equals(refSigFilename);
            mReporter.loadReferenceSigs(cmd.getOptionValue(NMF_REF_SIG_FILE), mUsingRefSigs);
        }

        if(cmd.hasOption(BA_PREDEFINED_SIGS))
        {
            GenericDataCollection dataCollection = GenericDataLoader.loadFile(cmd.getOptionValue(BA_PREDEFINED_SIGS));
            mPredefinedSigs = DataUtils.createMatrixFromListData(dataCollection.getData());
            mPredefinedSigs.cacheTranspose();
            mFinalFitOnly = (mConfig.ApplyPredefinedSigCount == mPredefinedSigs.Cols && mConfig.RunCount == 0);
        }

        LOGGER.info("bucketCount({}) sampleCount({})", mBucketCount, mSampleCount);

        if(cmd.hasOption(BA_EXT_SAMPLE_DATA_FILE))
        {
            final String fileName = cmd.getOptionValue(BA_EXT_SAMPLE_DATA_FILE);
            mExtSampleData = GenericDataLoader.loadFile(fileName, GD_TYPE_STRING);
        }
        else
        {
            // auto-generate a default collection with unknown cancer type and extra data
            mExtSampleData = new GenericDataCollection(GD_TYPE_STRING);

            List<String> fieldNames = Lists.newArrayList();
            fieldNames.add("SampleId");
            fieldNames.add("CancerType");
            mExtSampleData.setFieldNames(fieldNames);

            for (final String sampleId : mDataCollection.getFieldNames())
            {
                List<String> fieldValues = Lists.newArrayList();
                fieldValues.add(sampleId);
                fieldValues.add("Unknown");
                mExtSampleData.addStringValues(fieldValues);
            }
        }

        List<Integer> sampleIds = Lists.newArrayList();
        for (int i = 0; i < mSampleCount; ++i)
        {
            sampleIds.add(i);
        }

        mExtCategoriesMap = populateSampleCategoryMap(sampleIds);

        LOGGER.debug("registered {} cancer types and sub-categories", mExtCategoriesMap.size());

        for(int sampleId = 0; sampleId < mSampleCount; ++sampleId)
        {
            SampleData sample = new SampleData(sampleId);
            sample.setBucketCounts(mSampleCounts.getCol(sampleId));

            sample.setSampleName(mDataCollection.getFieldNames().get(sampleId));

            final List<String> extSampleData = getSampleExtData(sample.getSampleName());
            if(extSampleData != null)
            {
                sample.setCancerType(extSampleData.get(COL_CANCER_TYPE));
                sample.setCategoryData(extSampleData);
            }
            else
            {
                LOGGER.error("sample({}) cannot find external data", sample.getSampleName());
                mHasErrors = true;
            }

            if(!mConfig.SpecificCancer.isEmpty() && !sample.getCancerType().equals(mConfig.SpecificCancer))
            {
                sample.setExcluded(true);
            }
            else if(mConfig.SpecificSampleId >= 0 && sample.Id != mConfig.SpecificSampleId)
            {
                sample.setExcluded(true);
            }
            else if(!mConfig.MsiFilter.isEmpty())
            {
                boolean sampleHasMsi = sampleHasFeature(sample, "MSI");

                if(mConfig.MsiFilter.equals("Include") && !sampleHasMsi)
                {
                    sample.setExcluded(true);
                }
                else if(mConfig.MsiFilter.equals("Exclude") && sampleHasMsi)
                {
                    sample.setExcluded(true);
                }
            }

            mSampleData.add(sample);
        }

        populateCancerSamplesMap();

        if(cmd.hasOption(BA_SAMPLE_CALC_DATA_FILE))
        {
            final String fileName = cmd.getOptionValue(BA_SAMPLE_CALC_DATA_FILE);
            GenericDataCollection bgSampleAllocations = GenericDataLoader.loadFile(fileName, GD_TYPE_STRING);
            List<List<String>> dataSet = bgSampleAllocations.getStringData();

            for(final List<String> sampleData : dataSet)
            {
                // first 2 fields are sampleId and sampleName, other fields are data related
                if(sampleData.size() < 3)
                {
                    mHasErrors = true;
                    break;
                }

                int sampleId = Integer.parseInt(sampleData.get(SCD_COL_SAMPLE_ID));
                double bgAllocation = Double.parseDouble(sampleData.get(SCD_COL_BG_ALLOC));
                mSampleBgAllocations.add(sampleId, bgAllocation);
            }

            LOGGER.debug("loaded {} sample calc data fields", dataSet.size());
            mLoadedSampleCalcData = true;
        }

        mReporter.setInitialState(
                mDataCollection, mOutputDir, mOutputFileId, mSampleCounts, mSampleData,
                mExtSampleData, mExtCategoriesMap, mCancerSamplesMap, mFinalBucketGroups, mBackgroundGroups);

        mSigDiscovery.setInitialState(mConfig, mSampleData, mSampleCounts);

        return !mHasErrors;
    }

    public void run()
    {
        if(mHasErrors)
        {
            LOGGER.warn("failed to initialise, aborting run");
            return;
        }

        mPerfCounter. start();

        PerformanceCounter perfCounter = new PerformanceCounter("BucketMeanRatios");

        perfCounter.start("SplitCounts");
        calcBucketMedianData();

        if(mHasErrors)
            return;

        calcSampleBackgroundCounts();
        splitSampleCounts();
        calcCountsNoise();
        collectElevatedSampleBuckets();

        mReporter.setPreRunState(mSampleTotals, mBackgroundCounts, mElevatedCounts, mTotalCount, mElevatedCount, mActiveSampleCount);

        // back-ground groups using expected counts
        formBackgroundBucketGroups();
        mReporter.logOverallStats();
        perfCounter.stop();

        if(mConfig.ApplyPredefinedSigCount > 0)
        {
            applyPredefinedSigs();
            mReporter.logOverallStats();
        }

        boolean onFinalRun = false;
        boolean runDiscovery = true;
        boolean lastGroupWasMajor = false;

        for(mRunId = 0; mRunId < mConfig.RunCount; ++mRunId)
        {
            perfCounter.start(String.format("FindBucketGroup run %d", mRunId));

            if(!onFinalRun && mRunId == mConfig.RunCount - 1)
                onFinalRun = true;

            if(runDiscovery)
            {
                if (!onFinalRun)
                {
                    clearBucketGroups(false);
                }
                else
                {
                    // clear all state and try again in case anything was missed (due to logical state bugs)
                    mReassessSamples.clear();
                    mBucketGroups.clear();
                }
            }
            else
            {
                // keep the same groups, but clear recently allocated samples so they'll be reassessed
                clearBucketGroups(true);
            }

            mLastRunGroupCount = mBucketGroups.size();

            // find candidate bucket groups via various methods
            if(runDiscovery)
            {
                LOGGER.debug("run {}: running discovery to find new bucket groups", mRunId);

                if (mConfig.ExcessDiscoveryRunId >= 0 && mRunId >= mConfig.ExcessDiscoveryRunId && !lastGroupWasMajor)
                    mSigDiscovery.formExcessBucketGroups();

                mSigDiscovery.formBucketGroupsFromSamplePairs();
                runDiscovery = !mConfig.ThrottleDiscovery;
            }

            if(mBucketGroups.isEmpty())
            {
                LOGGER.debug("run {}: no groups found", mRunId);

                if(onFinalRun)
                    break;

                onFinalRun = true;
                runDiscovery = true;
                continue;
            }

            double prevAllocCount = mReporter.getAllocatedCount();

            populateTopBucketGroups();

            // uncomment to log interim group data
            // analyseGroupsVsExtData(mTopAllocBucketGroups, false);
            // writeInterimBucketGroups();

            //if(mRunId > 0 && mUniqueDiscoveryRunId && !lastGroupWasMajor)
            if(mRunId > 0 && mConfig.UniqueDiscoveryRunId >= 0 && (!lastGroupWasMajor || mRunId >= mConfig.UniqueDiscoveryRunId))
            {
                findUniqueBucketGroups();
            }

            BucketGroup nextBestGroup = null;
            if(nextBestGroup == null && !mTopAllocBucketGroups.isEmpty())
            {
                nextBestGroup = mTopAllocBucketGroups.get(0);
            }

            // allocated sample counts to the best group
            if(nextBestGroup != null)
            {
                allocateTopBucketGroup(nextBestGroup);

                if(mFinalBucketGroups.contains(nextBestGroup))
                {
                    LOGGER.error("run {}: attempted to add bg({}) again", mRunId, nextBestGroup.getId());
                    mHasErrors = true;
                    break;
                }

                mReporter.logOverallStats();
                mFinalBucketGroups.add(nextBestGroup);
                updateGroupSimiliarityData();

                lastGroupWasMajor = isMajorGroup(nextBestGroup);

                if(!lastGroupWasMajor && !runDiscovery)
                    runDiscovery = true;

                // assessSampleGroupAllocations(nextBestGroup);
            }

            perfCounter.stop();

            analyseGroupsVsExtData(mFinalBucketGroups, false);
            mReporter.logBucketGroups(true);

            if (nextBestGroup == null)
            {
                if(onFinalRun)
                    break;

                LOGGER.debug("run {}: no top grounp found, starting final run", mRunId);
                onFinalRun = true;
                runDiscovery = true;
                continue;
            }

            double newAllocCount = mReporter.getAllocatedCount();
            if(mRunId > 0 && (newAllocCount - prevAllocCount) / mElevatedCount < 0.0001) // eg 5K out of 55M
            {
                LOGGER.debug(String.format("run %d: negligible allocPercChange(%s -> %s)", mRunId, doubleToStr(prevAllocCount), doubleToStr(newAllocCount)));

                if(onFinalRun)
                    break;

                onFinalRun = true;
                runDiscovery = true;
                continue;
            }

            if(mHasErrors)
            {
                LOGGER.warn("run {}: exiting run with errors", mRunId);
                return;
            }
        }

        perfCounter.start("FinalFit");

        mergeSimilarGroups();

        // clear out all allocations and do again from scratch
        fitAllSamples();

        // tweak bucket ratio ranges and allocate any additional counts
        assessBucketGroupSampleRanges();
        checkSampleRefitWithRanges();

        perfCounter.stop();

        perfCounter.start("AnalyseResults");
        // assessUnallocatedCounts();
        analyseGroupsVsExtData(mFinalBucketGroups, true);
        mReporter.postRunAnalysis();
        perfCounter.stop();

        if(mConfig.MaxProposedSigs > 0)
        {
            createSignatures();
            writeSampleContributions();
            writeSampleSigBucketCounts();
            writeSampleSigAllocations();
        }

        mReporter.logOverallStats();

        writeFinalBucketGroups();
        writeSampleData();
        writeSampleGroupData();
        writeSampleCalcData();

        perfCounter.logStats();

        finalise();

        mPerfCounter.stop();
        mPerfCounter.logStats();
    }

    private void finalise()
    {
        try
        {
            if(mBgInterimFileWriter != null)
                mBgInterimFileWriter.close();

            if(mBgRatioRangeFileWriter != null)
                mBgRatioRangeFileWriter.close();
        }
        catch(IOException e)
        {
            LOGGER.error("failed to close bucket groups file");
        }
    }

    public boolean isMajorGroup(final BucketGroup bucketGroup)
    {
        if(bucketGroup.getTotalCount() < MAJOR_GROUP_ALLOC_PERC * mTotalCount)
            return false;

        if(bucketGroup.getSampleIds().size() < MAJOR_GROUP_SAMPLE_PERC * mActiveSampleCount)
            return false;

        return true;
    }

    private void calcBucketMedianData()
    {
        mSampleTotals = new double[mSampleCount];

        for (int i = 0; i < mSampleCount; ++i)
        {
            mSampleTotals[i] = sumVector(mSampleCounts.getCol(i));
        }

        mTotalCount = sumVector(mSampleTotals);

        mBucketMediansMap = new HashMap();

        // double bucketMedRange = 0.005;

        double[][] scData = mSampleCounts.getData();

        for (Map.Entry<String, List<Integer>> entry : mCancerSamplesMap.entrySet())
        {
            final String cancerType = entry.getKey();
            final List<Integer> sampleIds = entry.getValue();

            // add the counts from every sample with mutational load below the threshold
            // and then infer a background signature (literally bucket ratios) from those only
            int samplesIncluded = 0;

            List<List<Double>> sampleBucketRatios = Lists.newArrayList();

            for (int j = 0; j < sampleIds.size(); ++j)
            {
                int sampleId = sampleIds.get(j);

                double sampleTotal = mSampleTotals[sampleId];

                if (sampleTotal > mConfig.MutationalLoadCap)
                    continue;

                ++samplesIncluded;

                List<Double> bucketRatios = Lists.newArrayList();

                for (int i = 0; i < mBucketCount; ++i)
                {
                    bucketRatios.add(scData[i][sampleId] / sampleTotal);
                }

                sampleBucketRatios.add(bucketRatios);
            }

            double includedSamplesPerc = samplesIncluded / (double)sampleIds.size();
            if(samplesIncluded < 5 || includedSamplesPerc < 0.1)
            {
                // now convert back to average counts
                LOGGER.warn("cancerType({}) has too few({}) low mutational load samples vs total({})", cancerType, samplesIncluded, sampleIds.size());

                if(mConfig.UseBackgroundCounts)
                {
                    mHasErrors = true;
                    break;
                }
            }

            if(samplesIncluded > 0)
            {
                // now convert back to average counts
                LOGGER.debug("cancerType({}) has {} low mutational load samples", cancerType, samplesIncluded);

                int medianIndex = samplesIncluded / 2;

                double[] medianBucketRatios = new double[mBucketCount];

                for (int i = 0; i < mBucketCount; ++i)
                {
                    double[] bucketRatios = new double[samplesIncluded];

                    for (int j = 0; j < sampleBucketRatios.size(); ++j)
                    {
                        List<Double> sampleBucketRatio = sampleBucketRatios.get(j);
                        bucketRatios[j] = sampleBucketRatio.get(i);
                    }

                    // sort these and then take the median
                    List<Integer> sortedRatioIndices = getSortedVectorIndices(bucketRatios, true);
                    int medianRatioIndex = sortedRatioIndices.get(medianIndex);
                    double medianRatio = bucketRatios[medianRatioIndex];
                    medianBucketRatios[i] = medianRatio;
                }

                // convert to a percent
                List<Double> medianRatios = Lists.newArrayList();

                double ratioTotal = sumVector(medianBucketRatios);

                for (int i = 0; i < mBucketCount; ++i)
                {
                    medianRatios.add(medianBucketRatios[i] / ratioTotal);
                }

                mBucketMediansMap.put(cancerType, medianRatios);
            }
        }
    }

    private void calcSampleBackgroundCounts()
    {
        if(!mSampleBgAllocations.isEmpty())
            return;

        LOGGER.debug("calculating sample background counts");

        for(int s = 0; s < mSampleCount; ++s)
        {
            mSampleBgAllocations.add(s, 0.0);
        }

        if(!mConfig.UseBackgroundCounts)
            return;

        final double[][] scData = mSampleCounts.getData();

        for (Map.Entry<String, List<Double>> entry : mBucketMediansMap.entrySet())
        {
            final String type = entry.getKey();
            List<Integer> sampleIds = mCancerSamplesMap.get(type);

            List<Double> medianRatios = entry.getValue();
            final double[] bucketRatios = listToArray(medianRatios);

            for (Integer sampleId : sampleIds)
            {
                double[] sampleBgCounts = new double[mBucketCount];

                for (int i = 0; i < mBucketCount; ++i)
                {
                    int expectedCount = (int) round(bucketRatios[i] * min(mSampleTotals[sampleId], mConfig.MutationalLoadCap));
                    sampleBgCounts[i] = min(expectedCount, scData[i][sampleId]);
                }

                double optimalBgCount = calcBestFitWithinProbability(sampleId, bucketRatios, sampleBgCounts, 0.99, 0.01);
                mSampleBgAllocations.set(sampleId, optimalBgCount);
            }
        }
    }

    private void splitSampleCounts()
    {
        LOGGER.debug("splitting sample counts");

        // work out bucket median values (literally 50th percentile values)
        mBucketProbs = new SigMatrix(mBucketCount, mSampleCount);
        mBackgroundCounts = new SigMatrix(mBucketCount, mSampleCount);
        mElevatedCounts = new SigMatrix(mBucketCount, mSampleCount);

        double[][] probData = mBucketProbs.getData();
        double[][] bgData = mBackgroundCounts.getData();
        double[][] elevData = mElevatedCounts.getData();
        final double[][] scData = mSampleCounts.getData();

        int probExpMax = 15;
        int[] probFrequencies = new int[probExpMax+1];
        double minProb = pow(10, -probExpMax);
        int zeroProbIndex = probFrequencies.length-1;

        int gridSize = mSampleCount * mBucketCount;

        for(Map.Entry<String, List<Double>> entry: mBucketMediansMap.entrySet())
        {
            final String type = entry.getKey();

            List<Double> medianRatios = entry.getValue();

            List<Integer> sampleIds = mCancerSamplesMap.get(type);

            for (int i = 0; i < mBucketCount; ++i)
            {
                double bucketMedianRatio = medianRatios.get(i);

                for (int samIndex = 0; samIndex < sampleIds.size(); ++samIndex)
                {
                    int j = sampleIds.get(samIndex);

                    double bgAllocation = mSampleBgAllocations.get(samIndex);

                    int sbCount = (int)scData[i][j];

                    int backgroundCount = (int)round(bucketMedianRatio * bgAllocation);

                    if(sbCount < backgroundCount) // must be capped by the actual count
                        backgroundCount = sbCount;

                    bgData[i][j] = backgroundCount;

                    int elevatedCount = sbCount - backgroundCount;

                    // compute a range for Poisson noise around this elevated count
                    if (elevatedCount > 0)
                    {
                        elevData[i][j] = elevatedCount;
                    }

                    double prob = 1;

                    if (backgroundCount == 0)
                    {
                        prob = 0;
                    }
                    else if (elevatedCount > 0)
                    {
                        PoissonDistribution poisDist = new PoissonDistribution(backgroundCount);
                        prob = 1 - poisDist.cumulativeProbability(sbCount - 1);
                        prob = min(prob * gridSize, 1); // apply false discovery rate being # tests
                    }

                    probData[i][j] = prob;

                    if (prob > minProb && prob < 0.1)
                    {
                        int baseProb = -(int) round(log10(prob));

                        if (baseProb >= 0 && baseProb <= probExpMax)
                            probFrequencies[baseProb] += 1;
                    }
                    else if (prob < minProb)
                    {
                        // allocate to the last slot
                        probFrequencies[zeroProbIndex] += 1;
                    }
                }
            }
        }

        // if configured, switch all background counts to elevated
        if(!mConfig.UseBackgroundCounts)
        {
            if(mBucketMediansMap.isEmpty())
            {
                for (int i = 0; i < mBucketCount; ++i)
                {
                    for (int j = 0; j < mSampleCount; ++j)
                    {
                        elevData[i][j] = scData[i][j];
                        bgData[i][j] = 0;
                    }
                }
            }
            else
            {
                for (int i = 0; i < mBucketCount; ++i)
                {
                    for (int j = 0; j < mSampleCount; ++j)
                    {
                        elevData[i][j] += bgData[i][j];
                        bgData[i][j] = 0;
                    }
                }
            }
        }

        mBackgroundCounts.cacheTranspose();
        mElevatedCounts.cacheTranspose();

        mBackgroundCount = mBackgroundCounts.sum();
        mElevatedCount = mElevatedCounts.sum();

        LOGGER.debug(String.format("total counts: background(%s perc=%.3f) elevated(%s perc=%.3f) of total(%s)",
                sizeToStr(mBackgroundCount), mBackgroundCount/mTotalCount,
                sizeToStr(mElevatedCount), mElevatedCount/mTotalCount, sizeToStr(mTotalCount)));

        for (int i = 0; i < probFrequencies.length-1; ++i)
        {
            LOGGER.debug(String.format("probability(1e-%d) freq(%d) percOfTotal(%.4f)",
                    i, probFrequencies[i], probFrequencies[i]/(double)gridSize));
        }

        LOGGER.debug(String.format("probability(zero) freq(%d) percOfTotal(%.4f)",
                probFrequencies[zeroProbIndex], probFrequencies[zeroProbIndex]/(double)gridSize));
    }

    private void calcCountsNoise()
    {
        mPermittedElevRange = new SigMatrix(mBucketCount, mSampleCount);
        mPermittedBgRange = new SigMatrix(mBucketCount, mSampleCount);

        if(!mConfig.ApplyNoise)
            return;

        double[][] bgData = mBackgroundCounts.getData();
        double[][] elevData = mElevatedCounts.getData();
        double[][] permBgRangeData = mPermittedBgRange.getData();
        double[][] permElevRangeData = mPermittedElevRange.getData();

        Map<Integer, Integer> rangeMap = new HashMap(); // cache range values

        for (int i = 0; i < mBucketCount; ++i)
        {
            for (int j = 0; j < mSampleCount; ++j)
            {
                if(mConfig.UseBackgroundCounts)
                {
                    int backgroundCount = (int) bgData[i][j];

                    if (backgroundCount > 0)
                    {
                        Integer rangeVal = rangeMap.get(backgroundCount);
                        if (rangeVal == null)
                        {
                            rangeVal = CosineSim.calcPoissonRangeGivenProb(backgroundCount, PERMITTED_PROB_NOISE);
                            permBgRangeData[i][j] = rangeVal;
                            rangeMap.put(backgroundCount, rangeVal);
                        }
                        else
                        {
                            permBgRangeData[i][j] = rangeVal;
                        }
                    }
                }

                int elevatedCount = (int)elevData[i][j];

                // compute a range for Poisson noise around this elevated count
                elevData[i][j] = elevatedCount;

                Integer rangeVal = rangeMap.get(elevatedCount);
                if (rangeVal == null)
                {
                    rangeVal = CosineSim.calcPoissonRangeGivenProb(elevatedCount, PERMITTED_PROB_NOISE);
                    permElevRangeData[i][j] = rangeVal;
                    rangeMap.put(elevatedCount, rangeVal);
                }
                else
                {
                    permElevRangeData[i][j] = rangeVal;
                }
            }
        }

        // ensure that noise counts aren't disproportionately large compared with the actual counts
        // NOTE: they will be further capped when allocations are made per bucket (by MAX_NOISE_ALLOC_PERCENT)
        if(mConfig.ApplyNoise)
        {
            for (int i = 0; i < mSampleCount; ++i)
            {
                double noiseCountsTotal = 0;

                for (int j = 0; j < mBucketCount; ++j)
                {
                    noiseCountsTotal += permElevRangeData[j][i];
                }

                double sampleNoiseTotal = CosineSim.calcPoissonRangeGivenProb((int) round(mSampleTotals[i]), PERMITTED_PROB_NOISE);

                if (noiseCountsTotal > MAX_NOISE_TO_SAMPLE_RATIO * sampleNoiseTotal)
                {
                    double reduceRatio = sampleNoiseTotal * MAX_NOISE_TO_SAMPLE_RATIO / noiseCountsTotal;

                    for (int j = 0; j < mBucketCount; ++j)
                    {
                        permElevRangeData[j][i] = round(permElevRangeData[j][i] * reduceRatio);
                    }
                }
            }
        }

        mPermittedElevRange.cacheTranspose();
        mPermittedBgRange.cacheTranspose();

        // TEMP to write out sample noise values
        // writeSampleCountsNoise();
    }

    private void collectElevatedSampleBuckets()
    {
        int totalCount = 0;
        int elevSampleCount = 0;
        double[][] probData = mBucketProbs.getData();

        for (int i = 0; i < mSampleCount; ++i)
        {
            List<Integer> bucketList = Lists.newArrayList();

            for (int j = 0; j < mBucketCount; ++j)
            {
                if (probData[j][i] > MAX_ELEVATED_PROB)
                {
                    continue;
                }

                bucketList.add(j);
                ++totalCount;
            }

            SampleData sample = mSampleData.get(i);
            sample.setElevatedBucketCounts(mElevatedCounts.getCol(i), mPermittedElevRange.getCol(i));

            if (!bucketList.isEmpty())
            {
                sample.setElevatedBuckets(bucketList);
                ++elevSampleCount;
            }
        }

        // recalc totals just for the applicable cancer type
        if(!mConfig.SpecificCancer.isEmpty())
        {
            mTotalCount = 0;
            mBackgroundCount = 0;
            mElevatedCount = 0;

            // calculate manually
            for(final SampleData sample : mSampleData)
            {
                if(sample.isExcluded())
                    continue;

                ++mActiveSampleCount;
                mTotalCount += sample.getTotalCount();
                mBackgroundCount += sample.getTotalCount() - sample.getElevatedCount();
                mElevatedCount += sample.getElevatedCount();
            }
        }
        else
        {
            mActiveSampleCount = mSampleCount;
        }

        LOGGER.debug(String.format("samples with elevated buckets: count(%d perc=%.2f), buckets(%d perc=%.3f)",
                elevSampleCount, elevSampleCount/(double)mActiveSampleCount, totalCount, totalCount/(double)(mBucketCount * mActiveSampleCount)));
    }

    private void formBackgroundBucketGroups()
    {
        if(!mConfig.UseBackgroundCounts)
            return;

        for (Map.Entry<String, List<Integer>> entry : mCancerSamplesMap.entrySet())
        {
            final String cancerType = entry.getKey();
            List<Integer> sampleIds = entry.getValue();

            if(!mConfig.SpecificCancer.isEmpty() && !mConfig.SpecificCancer.equals(cancerType))
                continue;

            assignToBackgroundBucketGroups(cancerType, sampleIds);
        }
    }

    private void assignToBackgroundBucketGroups(final String cancerType, final List<Integer> sampleIds)
    {
        LOGGER.debug("cancerType({}) creating background groups for {} samples", cancerType, sampleIds.size());

        final List<Double> medianCounts = mBucketMediansMap.get(cancerType);

        if(medianCounts == null)
            return;

        double[] bucketRatios = listToArray(medianCounts);

        List<Integer> bucketIds = Lists.newArrayList();
        for (int i = 0; i < mBucketCount; ++i)
        {
            if(bucketRatios[i] > 0)
                bucketIds.add(i);
        }

        BucketGroup bucketGroup = new BucketGroup(mNextBucketId++);
        bucketGroup.addBuckets(bucketIds);
        bucketGroup.setCancerType(cancerType);
        bucketGroup.setGroupType(BG_TYPE_BACKGROUND);
        // bucketGroup.setTag(BG_TYPE_BACKGROUND);
        bucketGroup.setBucketRatios(bucketRatios);

        if(mConfig.UseRatioRanges)
        {
            bucketGroup.setRatioRangePerc(DEFAULT_SIG_RATIO_RANGE_PERCENT);
        }

        mBackgroundGroups.add(bucketGroup);

        // for now add all samples to a single group per cancer type
        for (int index = 0; index < sampleIds.size(); ++index)
        {
            int sampleId = sampleIds.get(index);
            SampleData sample = mSampleData.get(sampleId);

            if(sample.isExcluded())
                continue;

            double[] sampleCounts = mBackgroundCounts.getCol(sampleId);
            double bgAllocTotal = sumVector(sampleCounts);
            double allocPerc = bgAllocTotal / sample.getTotalCount();
            sample.addBucketGroup(bucketGroup, allocPerc);

            LOGGER.debug(String.format("sample(%d) added to background group(%d) alloc(%s perc=%.3f of %s)",
                    sampleId, bucketGroup.getId(), sizeToStr(bgAllocTotal), allocPerc,
                    sizeToStr(sample.getTotalCount())));

            bucketGroup.addSample(sampleId, sampleCounts);
        }

        LOGGER.debug(String.format("background group(%d) samples(%d) totalAlloc(%s)",
                bucketGroup.getId(), bucketGroup.getSampleCount(), sizeToStr(bucketGroup.getTotalCount())));
    }


    private void populateTopBucketGroups()
    {
        mTopAllocBucketGroups.clear();

        LOGGER.debug("finding top potential bucket group from count({} new={})",
                mBucketGroups.size(), max(mBucketGroups.size() - mLastRunGroupCount, 0));

        int maxCandidateGroups = MAX_CANDIDATE_GROUPS;

        SigContribOptimiser sigOptim = new SigContribOptimiser(mBucketCount, false, SAMPLE_ALLOCATED_PERCENT);

        int exceededOnSoloAlloc = 0;
        int exceededOnUnalloc = 0;
        int exceededOnFit = 0;
        int skippedRetry = 0;

        // if there are bucket groups from before this last discovery phase, their sample allocations
        // will be maintained (except for reassessed sample) to save recomputing the same allocations
        boolean keepPreviousAllocs = mLastRunGroupCount > 0;

        // first clear all existing allocations of samples to groups and vice versa
        for (int bgIndex = 0; bgIndex < mBucketGroups.size(); ++bgIndex)
        {
            BucketGroup bucketGroup = mBucketGroups.get(bgIndex);

            if(!bucketGroup.isValid() || !Doubles.equal(sumVector(bucketGroup.getBucketRatios()), 1))
            {
                bucketGroup.recalcBucketRatios(mConfig.MutLoadWeightFactor);

                if(!bucketGroup.isValid())
                {
                    LOGGER.warn("bg({}) skipped since invalid", bucketGroup.getId());
                    continue;
                }
            }

            double[] bgRatios = new double[mBucketCount];
            copyVector(bucketGroup.getBucketRatios(), bgRatios); // won't be recomputed as sample counts are added

            if(!keepPreviousAllocs || bgIndex >= mLastRunGroupCount)
            {
                // clear any existing allocations for any new, unassessed groups
                bucketGroup.clearSamples();
                bucketGroup.resetPotentialAllocation();
            }

            final List<Integer> groupBuckets = bucketGroup.getBucketIds();

            for (int sampleId = 0; sampleId < mSampleCount; ++sampleId)
            {
                final SampleData sample = mSampleData.get(sampleId);

                if(sample.isExcluded())
                    continue;

                // mConfig.logSample(sampleId);

                /*
                if(mConfig.logSample(sampleId) && bucketGroup.getId() == 123)
                {
                    LOGGER.debug("spec sample");
                }
                */

                double reqAllocPercent = minAllocPercent(sample, false);
                boolean exceedsMinAllocPerc = false;
                double[] allocCounts = null;
                double allocCountTotal = 0;
                double allocPercent = 0;

                if(keepPreviousAllocs)
                {
                    // pre-existing bucket groups (ie those not just proposed) and samples just not allocated can be left alone
                    if (bgIndex < mLastRunGroupCount && !mReassessSamples.contains(sampleId))
                    {
                        // look for an existing allocation in this group
                        if (bucketGroup.hasSample(sampleId))
                        {
                            allocCountTotal = bucketGroup.getSampleCount(sampleId);

                            if(allocCountTotal/sample.getElevatedCount() < reqAllocPercent - 0.01)
                            {
                                LOGGER.error(String.format("sample(%d) part of existing bg(%d) with alloc(%s perc=%.3f)",
                                        sampleId, bucketGroup.getId(), sizeToStr(allocCountTotal), allocCountTotal/sample.getElevatedCount()));
                                mHasErrors = true;
                            }
                        }
                        else
                        {
                            // no point trying again
                        }

                        ++skippedRetry;
                        continue;
                    }
                }

                if(bucketGroup.hasSample(sampleId))
                {
                    // if this sample was previously in the group and then reassessed and about to be added again,
                    // need to first of all remove its existing allocation
                    bucketGroup.removeSampleAllocation(sample, -1, true);
                }

                // skip if already largely allocated, even though reshuffling could potentially lead to an alloc above the min %
                if (sample.getUnallocPercent() < reqAllocPercent)
                    continue;

                // optimisation: check whether the buckets for this group and sample
                // could possibly exceed the min % threshold with a perfect fit, otherwise skip it
                final double[] bgSampleElevCounts = sample.getPotentialElevCounts(bgRatios, groupBuckets, bucketGroup.getRatioRanges());
                double maxPotentialPerc = sumVector(bgSampleElevCounts) / sample.getElevatedCount();

                if(maxPotentialPerc < reqAllocPercent)
                    continue;

                allocCounts = sample.getPotentialUnallocCounts(bgRatios, groupBuckets, null); // bucketGroup.getRatioRanges()
                allocCountTotal = sumVector(allocCounts);
                allocPercent = allocCountTotal / sample.getElevatedCount();

                exceedsMinAllocPerc = allocPercent >= reqAllocPercent;

                if(exceedsMinAllocPerc)
                {
                    ++exceededOnUnalloc;
                }
                else
                {
                    // if the potential allocation taking no existing allocations into account would increase
                    // a sample's overall allocation by more than the upper threshold, it must satisfy the test
                    // to be added to this candidate - but the counts still adjusting with the fit routine - for now too hard
                    if(maxPotentialPerc - sample.getAllocPercent() >= reqAllocPercent)
                    {
                        // exceedsMinAllocPerc = true;
                        ++exceededOnSoloAlloc;
                    }
                }

                if (!exceedsMinAllocPerc)
                {
                    if(sample.getElevBucketGroups().isEmpty())
                        continue;

                    // see if a fit with sig along with all the other allocated one for this sample would then meet the min % threshold
                    // it is the overall change to the sample's allocation that is tested, not just this proposed group's contribution
                    List<double[]> ratiosCollection = Lists.newArrayList();
                    int bgGroupIndex = -1;

                    for (final BucketGroup samGroup : sample.getElevBucketGroups())
                    {
                        if(samGroup == sample.getBackgroundGroup())
                            bgGroupIndex = ratiosCollection.size();

                        ratiosCollection.add(samGroup.getBucketRatios());
                    }

                    ratiosCollection.add(bgRatios);

                    double[] prevContribs = new double[ratiosCollection.size()];
                    int candidateSigIndex = prevContribs.length - 1;

                    sigOptim.initialise(sample.Id, sample.getElevatedBucketCounts(), sample.getCountRanges(), ratiosCollection,
                            reqAllocPercent, mConfig.MinSampleAllocCount);

                    // sigOptim.setLogVerbose(mConfig.logSample(sampleId));
                    sigOptim.setTargetSig(candidateSigIndex);
                    sigOptim.setRequiredSig(bgGroupIndex);

                    boolean validCalc = sigOptim.fitToSample();

                    if (!validCalc) // couldn't reach the required percent for this canididate sig
                    {
                        LOGGER.warn("sample({}) fit with existing sigs failed", sample.Id);
                        mHasErrors = true;
                        continue;
                    }

                    double candidateAlloc = sigOptim.getContribs()[candidateSigIndex];
                    allocCountTotal = candidateAlloc;
                    allocPercent = allocCountTotal / sample.getElevatedCount();

                    if (allocPercent < reqAllocPercent || allocCountTotal < mConfig.MinSampleAllocCount)
                        continue;

                    // translate the fitted contribution into new counts
                    if(candidateAlloc < allocCountTotal * 0.99)
                        continue; // shouldn't happen but in case a reshuffle of sigs didn't actually include the new sig

                    for(int b = 0; b < mBucketCount; ++b)
                    {
                        allocCounts[b] = bgRatios[b] * candidateAlloc;
                    }

                    exceedsMinAllocPerc = true;
                    ++exceededOnFit;
                }

                bucketGroup.addPotentialAllocation(allocCountTotal);
                bucketGroup.addPotentialAdjAllocation(allocCountTotal * allocPercent);
                bucketGroup.addSample(sampleId, allocCounts);
            }
        }

        LOGGER.debug("processed {} bucket groups, method(solo={} unalloc={} fit={} skipped={})",
                mBucketGroups.size(), exceededOnSoloAlloc, exceededOnUnalloc, exceededOnFit, skippedRetry);

        LOGGER.debug(String.format("sig-optim stats: instances(%d) avgIters(%.1f) avgImprovePerc(%.3f)",
                sigOptim.getInstances(), sigOptim.getAvgIterations(), sigOptim.getAvgImprovePerc()));

        // now that all samples have been tested and allocated, force a recalc of the ratios
        // and then check for overlap with existing bucket groups
        int bgIndex = 0;
        int removedGroups = 0;
        while(bgIndex < mBucketGroups.size())
        {
            BucketGroup bucketGroup = mBucketGroups.get(bgIndex);
            bucketGroup.recalcBucketRatios(mConfig.MutLoadWeightFactor);

            if (similarToExistingGroup(bucketGroup))
            {
                mBucketGroups.remove(bgIndex);
                ++removedGroups;
            }
            else
            {
                ++bgIndex;
            }
        }

        if(removedGroups > 0)
        {
            LOGGER.debug("removed {} bucket groups similar to existing selected groups", removedGroups);
        }

        removeSkippedAllocations(mBucketGroups);

        // sort into order
        for(BucketGroup bucketGroup : mBucketGroups)
        {
            int findBgIndex = 0;
            while (findBgIndex < mTopAllocBucketGroups.size())
            {
                if (bucketGroup.getPotentialAdjAllocation() >= mTopAllocBucketGroups.get(findBgIndex).getPotentialAdjAllocation())
                    break;

                ++findBgIndex;
            }

            if (findBgIndex < maxCandidateGroups || maxCandidateGroups == 0)
            {
                mTopAllocBucketGroups.add(findBgIndex, bucketGroup);
            }

            if (maxCandidateGroups > 0 && mTopAllocBucketGroups.size() > maxCandidateGroups)
            {
                mTopAllocBucketGroups.remove(mTopAllocBucketGroups.size() - 1);
            }
        }
    }

    private boolean similarToExistingGroup(BucketGroup bucketGroup)
    {
        setGroupSimiliarityData(bucketGroup);
        return bucketGroup.getMaxSimiliarScore() >= SIG_SIMILAR_CSS;
    }

    private void updateGroupSimiliarityData()
    {
        for(final BucketGroup bucketGroup : mFinalBucketGroups)
        {
            setGroupSimiliarityData(bucketGroup);
        }
    }

    private void setGroupSimiliarityData(BucketGroup bucketGroup)
    {
        // find the maximum similarity for a given group
        final List<Integer> bucketIds = bucketGroup.getBucketIds();
        final double[] bucketRatios = bucketGroup.getBucketRatios();

        double maxSimilarity = 0;
        BucketGroup maxSimilarGroup = null;

        for(final BucketGroup otherGroup : mFinalBucketGroups)
        {
            if(bucketGroup == otherGroup)
                continue;

            double css = calcCSS(bucketGroup.getBucketRatios(), otherGroup.getBucketRatios());

            if(css > maxSimilarity)
            {
                maxSimilarGroup = otherGroup;
                maxSimilarity = css;
            }

            final double[] otherRatios = otherGroup.getBucketRatios();

            // also check whether the new ratio ranges are within the bounds of existing group ratios
            // eg if all are, the similarity score will be 1 (ie 100% similar)
            double similarBucketPerc = 0;
            for(Integer bucket : bucketIds)
            {
                if(bucketRatios[bucket] >= otherRatios[bucket] * (1 - BUCKET_RANGE_MAX_PERCENT)
                && bucketRatios[bucket] <= otherRatios[bucket] * (1 + BUCKET_RANGE_MAX_PERCENT))
                {
                    similarBucketPerc += bucketRatios[bucket];
                }
                else
                {
                    // apply the difference to the similarity total - eg if ref = 0.40 and comparison is 0.25, then add 0.25
                    // if the difference is beyond the value being compared, counts this for zero
                    double ratioDiff = min(abs(bucketRatios[bucket] - otherRatios[bucket]), bucketRatios[bucket]);
                    similarBucketPerc += bucketRatios[bucket] - ratioDiff;
                }
            }

            if(similarBucketPerc > maxSimilarity)
            {
                maxSimilarGroup = otherGroup;
                maxSimilarity = similarBucketPerc;
            }
        }

        bucketGroup.setMaxSimiliarGroup(maxSimilarGroup);
        bucketGroup.setMaxSimiliarScore(maxSimilarity);
    }

    private void removeSkippedAllocations(List<BucketGroup> bucketGroups)
    {
        // remove any sample which could be much better allocated elsewhere
        for (BucketGroup bucketGroup : bucketGroups)
        {
            List<Integer> sampleIds = bucketGroup.getSampleIds();
            List<Double> sampleAllocTotals = bucketGroup.getSampleCountTotals();

            int samIndex = 0;
            while ( samIndex < sampleIds.size())
            {
                Integer sampleId = sampleIds.get(samIndex);
                double proposedAllocTotal = sampleAllocTotals.get(samIndex);

                final SampleData sample = mSampleData.get(sampleId);

                BucketGroup maxOtherGroup = getSampleMaxAllocationGroup(sample, bucketGroup);
                double maxOtherGroupAlloc = maxOtherGroup != null ? maxOtherGroup.getSampleCount(sampleId) : 0;
                double maxFinalGroupAlloc = getMaxAllocationAgainstFinalGroups(sample);
                double maxOtherAlloc = max(maxOtherGroupAlloc, maxFinalGroupAlloc);

                if (maxOtherAlloc < SKIP_ALLOC_FACTOR * proposedAllocTotal || maxOtherAlloc < mConfig.MinSampleAllocCount)
                {
                    ++samIndex;
                    continue;
                }

                // remove this potential alloc
                bucketGroup.removeSampleAllocation(sample, samIndex,true);
            }
        }
    }

    private void allocateTopBucketGroup(BucketGroup topBucketGroup)
    {
        if(topBucketGroup == null)
            return;

        // if (mTopAllocBucketGroups.isEmpty())
        //    return null;

        mReassessSamples.clear();

        // BucketGroup topBucketGroup = mTopAllocBucketGroups.get(0);

        LOGGER.debug(String.format("top bg(%d) type(%s) with buckets(%d) samples(%d) potential allocation(%s adj=%s)",
                topBucketGroup.getId(), topBucketGroup.getTag(), topBucketGroup.getBucketIds().size(), topBucketGroup.getSampleIds().size(),
                sizeToStr(topBucketGroup.getPotentialAllocation()), sizeToStr(topBucketGroup.getPotentialAdjAllocation())));

        List<Integer> topSampleIds = Lists.newArrayList();
        topSampleIds.addAll(topBucketGroup.getSampleIds());

        List<Double> sampleAllocTotals = Lists.newArrayList();
        sampleAllocTotals.addAll(topBucketGroup.getSampleCountTotals());

        List<double[]> sampleCounts = Lists.newArrayList();
        sampleCounts.addAll(topBucketGroup.getSampleCounts());

        refineTopBucketGroup(topBucketGroup, topSampleIds, sampleAllocTotals, sampleCounts);

        // now allocate samples to this top group
        topBucketGroup.clearSamples();

        SigContribOptimiser sigOptim = new SigContribOptimiser(mBucketCount, false, SAMPLE_ALLOCATED_PERCENT);
        List<BucketGroup> sampleGroupList = Lists.newArrayList();

        List<Integer> skippedSamples = Lists.newArrayList();

        double skippedAllocTotal = 0;
        double missedAllocTotal = 0;
        int missedCount = 0;

        for(int samIndex = 0; samIndex < topSampleIds.size(); ++samIndex)
        {
            Integer sampleId = topSampleIds.get(samIndex);
            double newAllocTotal = sampleAllocTotals.get(samIndex);
            double[] sampleCountAllocations = sampleCounts.get(samIndex);

            final SampleData sample = mSampleData.get(sampleId);

            // mConfig.logSample(sampleId);

            BucketGroup maxOtherGroup = getSampleMaxAllocationGroup(sample, topBucketGroup);
            double maxOtherGroupAlloc = maxOtherGroup != null ? maxOtherGroup.getSampleCount(sampleId) : 0;
            double maxFinalGroupAlloc = getMaxAllocationAgainstFinalGroups(sample);
            double maxOtherAlloc = max(maxOtherGroupAlloc, maxFinalGroupAlloc);

            if (maxOtherAlloc > SKIP_ALLOC_FACTOR * newAllocTotal && maxOtherAlloc >= mConfig.MinSampleAllocCount)
            {
                if (maxOtherGroupAlloc > maxFinalGroupAlloc)
                {
                    LOGGER.debug(String.format("sample(%d) skipped bg(%d) alloc(%s) for other candidate bg(%d alloc=%s)",
                            sampleId, topBucketGroup.getId(), sizeToStr(newAllocTotal), maxOtherGroup.getId(), sizeToStr(maxOtherGroupAlloc)));

                    if (!mSkippedBucketGroups.contains(maxOtherGroup))
                    {
                        mSkippedBucketGroups.add(maxOtherGroup);
                    }
                }
                else
                {
                    LOGGER.debug(String.format("sample(%d) skipped bg(%d) alloc(%s) for final bg alloc(%s)", sampleId, topBucketGroup.getId(), sizeToStr(newAllocTotal), sizeToStr(maxFinalGroupAlloc)));
                }

                skippedAllocTotal += newAllocTotal;
                skippedSamples.add(sampleId);
                continue;
            }

            if (newAllocTotal < mConfig.MinSampleAllocCount)
                continue;

            double reqAllocPercent = minAllocPercent(sample, false);

            if (sample.getElevBucketGroups().isEmpty())
            {
                // apply to the remaining unallocated elevated counts for this sample
                double actualAlloc = sample.allocateBucketCounts(sampleCountAllocations, reqAllocPercent);
                double allocPerc = actualAlloc / sample.getElevatedCount();

                if (allocPerc >= reqAllocPercent)
                {
                    topBucketGroup.addSample(sampleId, sampleCountAllocations);
                    sample.addBucketGroup(topBucketGroup, allocPerc);

                    LOGGER.debug(String.format("sample(%d) added to bg(%d) count(pot=%s act=%s of %s) allocatedPerc(+%.3f -> %.3f) noise(%s %.3f/%.3f) groupCount(%d)",
                            sampleId, topBucketGroup.getId(), sizeToStr(newAllocTotal), sizeToStr(actualAlloc),
                            sizeToStr(sample.getElevatedCount()), sample.lastAllocPercChange(), sample.getAllocPercent(),
                            sizeToStr(sample.getAllocNoise()), sample.getNoisePerc(), sample.getNoiseOfTotal(), sample.getElevBucketGroups().size()));
                }
            }
            else
            {
                // repeat the sig-contribution-optimised allocation to a) adhere to the allocation-based candidacy selection
                // and b) ensure the best allocs are then used for the rest of discovery
                double prevAllocPerc = sample.getAllocPercent();

                sampleGroupList.clear();
                for (int grpIndex = 0; grpIndex < sample.getElevBucketGroups().size(); ++grpIndex)
                {
                    final BucketGroup samGroup = sample.getElevBucketGroups().get(grpIndex);
                    sampleGroupList.add(samGroup);

                    // not critical to do since all reset in final fit, but reassess sample-group allocations
                    samGroup.removeSampleAllocation(sample,  -1,false);
                }

                sampleGroupList.add(topBucketGroup);

                /*
                if(mConfig.logSample(sampleId) && mConfig.logSample(topBucketGroup.getId()))
                {
                    LOGGER.debug("spec");
                }
                */

                sample.clearAllocations(true);
                boolean fitAllocated = fitSampleWithGroups(sigOptim, sample, sampleGroupList, prevAllocPerc, reqAllocPercent, false);

                if(!fitAllocated)
                {
                    LOGGER.warn("sample({}) fit failed", sample.Id);
                    mHasErrors = true;
                }
                else if(!sample.getElevBucketGroups().contains(topBucketGroup))
                {
                    LOGGER.debug(String.format("sample(%d) missed bg(%d) proposed fit alloc(%s)", sample.Id, topBucketGroup.getId(), sizeToStr(newAllocTotal)));

                    missedAllocTotal += newAllocTotal;
                    ++missedCount;
                }
                else
                {
                    String allocResult = "unch";

                    if(sample.getAllocPercent() <= prevAllocPerc - 0.01)
                        allocResult = "worse";
                    else if(sample.getAllocPercent() >= prevAllocPerc + 0.01)
                        allocResult = "better";

                    LOGGER.debug(String.format("sample(%d) new fit alloc(%s perc=%.3f) %s than prev(%.3f)",
                            sample.Id, sizeToStr(sample.getAllocatedCount()), sample.getAllocPercent(), allocResult, prevAllocPerc));
                }
            }
        }

        LOGGER.debug(String.format("new top bg(%d) added %d samples, totalAllocatedCount(%s) missed(%d: %s) skipped(%d: %s)",
                topBucketGroup.getId(), topBucketGroup.getSampleIds().size(), sizeToStr(topBucketGroup.getTotalCount()),
                missedCount, sizeToStr(missedAllocTotal), skippedSamples.size(), sizeToStr(skippedAllocTotal)));

        // recheck any sample previously skipped against the fixed groups so see if they can now be allocated
        checkSkippedSamples(skippedSamples, topBucketGroup.getSampleIds());

        // for now clear all top groups to force a reassessment, since after allocation of elevated counts
        // to the top bucket, the similarities could increase across more buckets and samples
        // mTopAllocBucketGroups.clear();

        if(topBucketGroup.getSampleIds().isEmpty())
            return;

        mReassessSamples.addAll(topBucketGroup.getSampleIds());

        if(mSkippedBucketGroups.contains(topBucketGroup))
        {
            LOGGER.debug("previously skipped group({}) now added to final list", topBucketGroup.getId());
            mSkippedBucketGroups.remove(topBucketGroup);
        }
    }

    private void refineTopBucketGroup(BucketGroup topBucketGroup, List<Integer> topSampleIds, List<Double> sampleAllocTotals, List<double[]> sampleCounts)
    {
        // walk down list, recalculating the ratios, expanding their range and comparing to groups further down
        final double[] topBucketRatios = topBucketGroup.getBucketRatios();
        double[] newBucketRatios = new double[mBucketCount];
        copyVector(topBucketRatios, newBucketRatios);

        List<Integer> candidateNewBuckets = Lists.newArrayList();
        List<BucketGroup> similarGroups = Lists.newArrayList();

        for (int bgIndex = 1; bgIndex < mTopAllocBucketGroups.size(); ++bgIndex)
        {
            BucketGroup bucketGroup = mTopAllocBucketGroups.get(bgIndex);

            double[] bucketRatios = bucketGroup.getBucketRatios();

            // if(mConfig.logSample(bucketGroup.getId()) && mConfig.logSample(topBucketGroup.getId()))

            double groupCss = calcCSS(topBucketRatios, bucketRatios);
            if (groupCss < SIG_SIMILAR_CSS)
                continue;

            // if groups aren't very similar, don't merge their samples or buckets
            if (groupCss < mConfig.HighCssThreshold)
            {
                similarGroups.add(bucketGroup);
                continue;
            }

            List<Integer> deficientBuckets = getDiffList(topBucketGroup.getBucketIds(), bucketGroup.getBucketIds());

            if(!deficientBuckets.isEmpty())
            {
                similarGroups.add(bucketGroup);
                continue;
            }

            // don't merge the group if there are no new samples to add
            List<Integer> newSamples = getDiffList(bucketGroup.getSampleIds(), topSampleIds);

            if(newSamples.isEmpty())
            {
                similarGroups.add(bucketGroup);
                continue;
            }

            List<Integer> extraBuckets = getDiffList(bucketGroup.getBucketIds(), topBucketGroup.getBucketIds());

            for (Integer bucket : extraBuckets)
            {
                if (!candidateNewBuckets.contains(bucket))
                    candidateNewBuckets.add(bucket);
            }

            // merge in any difference in samples
            final List<Integer> bgSamples = bucketGroup.getSampleIds();
            final List<Double> bgSampleTotals = bucketGroup.getSampleCountTotals();
            final List<double[]> bgSampleCounts = bucketGroup.getSampleCounts();

            int samplesAdded = 0;
            for (int samIndex = 0; samIndex < bgSamples.size(); ++samIndex)
            {
                Integer sampleId = bgSamples.get(samIndex);

                if (newSamples.contains(sampleId))
                {
                    topSampleIds.add(sampleId);
                    sampleAllocTotals.add(bgSampleTotals.get(samIndex));
                    sampleCounts.add(bgSampleCounts.get(samIndex));
                    ++samplesAdded;
                }
            }

            LOGGER.debug(String.format("top bg(%d) merged with bg(%d) alloc(%s adj=%s) css(%.4f) samples(bg1=%d bg2=%d added=%d) diffBuckets(%d)",
                    topBucketGroup.getId(), bucketGroup.getId(), sizeToStr(bucketGroup.getPotentialAllocation()),
                    sizeToStr(bucketGroup.getPotentialAdjAllocation()), groupCss, topSampleIds.size(), bgSamples.size(), samplesAdded, extraBuckets.size()));

            // remove this from the candidate set so it doesn't impact the skipped-sample logic
            similarGroups.add(bucketGroup);
        }

        if(!similarGroups.isEmpty())
        {
            for (BucketGroup similarGroup : similarGroups)
            {
                mBucketGroups.remove(similarGroup);
            }

            LOGGER.debug("top bg({}) removed {} similar groups", topBucketGroup.getId(), similarGroups.size());
        }

        List<SampleData> samples = Lists.newArrayList();
        List<double[]> proposedAllocs = Lists.newArrayList();
        for(int samIndex = 0; samIndex < topSampleIds.size(); ++samIndex)
        {
            int sampleId = topSampleIds.get(samIndex);
            samples.add(mSampleData.get(sampleId));
            proposedAllocs.add(sampleCounts.get(samIndex));
        }

        /* DISABLED UNTIL CAN IMPROVE - is currently lowering allocations

        SigOptimiser sigOptim = new SigOptimiser(topBucketGroup.getId(), samples, proposedAllocs, newBucketRatios, candidateNewBuckets);
        sigOptim.setLogVerbose(true);
        sigOptim.setCacheBucketInfo(true);

        boolean validCalc = sigOptim.optimiseBucketRatios(false, mConfig.UseRatioRanges);

        if(validCalc && sigOptim.hasChanged())
        {
            copyVector(sigOptim.getFittedRatios(), newBucketRatios);
            topBucketGroup.setBucketRatioRanges(sigOptim.getRatioRanges());

            final List<Integer> newBuckets = sigOptim.getNewBuckets();
            for (Integer newBucket : newBuckets)
            {
                topBucketGroup.addBucket(newBucket, false);
            }

            // take the new samples allocations as well
            for (int samIndex = 0; samIndex < topSampleIds.size(); ++samIndex)
            {
                sampleAllocTotals.set(samIndex, sigOptim.getRevisedSampleAlloc(samIndex));
                sampleCounts.set(samIndex, sigOptim.getRevisedSampleAllocCounts(samIndex));
            }

            // writeBucketGroupRatioRangeData(topBucketGroup, sigOptim);
        }
        else if(mConfig.UseRatioRanges)
        {
            topBucketGroup.setRatioRangePerc(DEFAULT_SIG_RATIO_RANGE_PERCENT);
        }
        */

        topBucketGroup.setRatioRangePerc(DEFAULT_SIG_RATIO_RANGE_PERCENT);

        topBucketGroup.setBucketRatios(newBucketRatios);
    }

    private void findUniqueBucketGroups()
    {
        // looking for very unique groups and groups which overlap existing groups significantly
        if(mFinalBucketGroups.isEmpty())
            return;

        // also consider extremely unique samples
        double maxAllocPercent = UNIQUE_SIG_MIN_ALLOC_PERCENT;

        List<BucketGroup> possibleUniqueGroups = Lists.newArrayList();

        for(final SampleData sample : mSampleData)
        {
            if(sample.isExcluded())
                continue;

            // mConfig.logSample(sample.Id)

            if(sample.getAllocPercent() >= maxAllocPercent || sample.getTotalCount() < 5 * mConfig.MutationalLoadCap)
                continue;

            // find buckets largely unaccounted for
            List<Integer> elevatedBuckets = sample.getElevatedBuckets();
            final double[] allCounts = sample.getElevatedBucketCounts();
            final double[] unallocCounts = sample.getUnallocBucketCounts();
            double sampleUnallocTotal = sample.getUnallocatedCount();

            double[] bucketRatios = new double[mBucketCount];
            double maxRatio = 0;

            List<Integer> candidateBucketIds = Lists.newArrayList();
            for(Integer bucket : elevatedBuckets)
            {
                if(unallocCounts[bucket] / allCounts[bucket] > (1 - maxAllocPercent))
                {
                    candidateBucketIds.add(bucket);
                    double ratio = unallocCounts[bucket] / sampleUnallocTotal;
                    maxRatio = max(maxRatio, ratio);
                    bucketRatios[bucket] = ratio;
                }
            }

            // remove tiny contributing buckets
            List<Integer> bucketIds = Lists.newArrayList();
            for(Integer bucket : candidateBucketIds)
            {
                if(bucketRatios[bucket] >= maxRatio * SMALL_RATIO_PERC_CUTOFF)
                    bucketIds.add(bucket);
            }

            if(bucketIds.size() >= mConfig.MinBucketCountOverlap)
            {
                BucketGroup bucketGroup = new BucketGroup(++mNextBucketId);
                bucketGroup.addInitialSample(sample.Id);
                bucketGroup.addBuckets(bucketIds);
                bucketGroup.setTag(sample.getSampleName());

                double[] sampleCounts = new double[mBucketCount];
                for(Integer bucket : bucketIds)
                {
                    sampleCounts[bucket] = unallocCounts[bucket];
                }

                bucketGroup.addSample(sample.Id, sampleCounts);
                double allocCountTotal = sumVector(sampleCounts);
                double allocPercent = allocCountTotal / sample.getElevatedCount();
                bucketGroup.addPotentialAllocation(allocCountTotal);
                bucketGroup.addPotentialAdjAllocation(allocCountTotal * allocPercent);

                convertToPercentages(bucketRatios);
                bucketGroup.setBucketRatios(bucketRatios);
                possibleUniqueGroups.add(bucketGroup);
            }
        }

        /*
        List<BucketGroup> allCandidateGroups = Lists.newArrayList();

        for(BucketGroup bucketGroup : mBucketGroups)
        {
            addBucketGroupSortedByPotentialAlloc(allCandidateGroups, bucketGroup);
        }
        */

        for(final BucketGroup bucketGroup : possibleUniqueGroups)
        {
            double maxCss = 0;
            int maxBucketOverlap = 0;

            // mConfig.logSample(bucketGroup.getId());

            double maxBucketOverlapPerc = 0;

            for(final BucketGroup existingGroup : mFinalBucketGroups)
            {
                double css = calcCSS(bucketGroup.getBucketRatios(), existingGroup.getBucketRatios());

                maxCss = max(maxCss, css);

                List<Integer> commonBuckets = getMatchingList(bucketGroup.getBucketIds(), existingGroup.getBucketIds());

                double bucketOverlapPerc = min(commonBuckets.size() / (double)bucketGroup.getBucketCount(), commonBuckets.size() / (double)existingGroup.getBucketIds().size());
                maxBucketOverlapPerc = max(maxBucketOverlapPerc, bucketOverlapPerc);
            }

            // check average percent alloc for samples in this candidate group
            double allocPercTotal = 0;
            for(int sampleId : bucketGroup.getSampleIds())
            {
                double allocPerc = bucketGroup.getSampleCount(sampleId) / mSampleTotals[sampleId];
                allocPercTotal += allocPerc;
            }

            double avgAllocPerc = allocPercTotal / bucketGroup.getSampleCount();

            if(avgAllocPerc < UNIQUE_SIG_MIN_ALLOC_PERCENT || maxCss >= UNIQUE_SIG_CSS_THRESHOLD)
                continue;

            setBucketGroupFeatures(bucketGroup, false);

            LOGGER.debug(String.format("bg(%d) unique candidate: tag(%s) maxCss(%.3f) bucketOverlap(%d of %d, perc=%.2f) samples(%d) avgAllocPerc(%.3f) potAlloc(%s) ct(%s) effects(%s)",
                    bucketGroup.getId(), bucketGroup.getTag(), maxCss, maxBucketOverlap, bucketGroup.getBucketCount(), maxBucketOverlapPerc, bucketGroup.getSampleCount(),
                    avgAllocPerc, sizeToStr(bucketGroup.getPotentialAllocation()), bucketGroup.getCancerType(), bucketGroup.getEffects()));


            bucketGroup.setGroupType(BG_TYPE_UNIQUE);

            // add in allocation priority order
            addBucketGroupSortedByPotentialAlloc(mTopAllocBucketGroups, bucketGroup);
        }

        /*
        // take the group with the top potential allocation from amongst the existing bucket groups and these unique ones
        if(allCandidateGroups.isEmpty())
            return null;

        BucketGroup topUniqueGroup = allCandidateGroups.get(0);

        if(topUniqueGroup.getPotentialAllocation() > 0)
            return topUniqueGroup;
        else
            return null;
        */
    }

    private static void addBucketGroupSortedByPotentialAlloc(List<BucketGroup> bucketGroups, BucketGroup newGroup)
    {
        int bgIndex = 0;
        while (bgIndex < bucketGroups.size())
        {
            if (newGroup.getPotentialAdjAllocation() >= bucketGroups.get(bgIndex).getPotentialAdjAllocation())
                break;

            ++bgIndex;
        }

        bucketGroups.add(bgIndex, newGroup);
    }

    private BucketGroup getSampleMaxAllocationGroup(final SampleData sample, final BucketGroup excludeGroup)
    {
        double maxAlloc = 0;

        // test against the candidate list of bucket groups
        BucketGroup bestGroup = null;

        for (final BucketGroup bucketGroup : mBucketGroups)
        {
            if (bucketGroup == excludeGroup)
                continue;

            double alloc = bucketGroup.getSampleCount(sample.Id);
            if (alloc > maxAlloc)
            {
                maxAlloc = alloc;
                bestGroup = bucketGroup;
            }
        }

        return bestGroup;
    }

    private double getMaxAllocationAgainstFinalGroups(final SampleData sample)
    {
        double maxAlloc = 0;

        for (final BucketGroup bucketGroup : mFinalBucketGroups)
        {
            if(bucketGroup.hasSample(sample.Id))
                continue;

            double[] allocCounts = sample.getPotentialUnallocCounts(bucketGroup.getBucketRatios(), bucketGroup.getBucketIds(), null); // bucketGroup.getRatioRanges()

            double alloc = sumVector(allocCounts);

            if (alloc > maxAlloc)
            {
                maxAlloc = alloc;
            }
        }

        return maxAlloc;
    }

    private void checkSkippedSamples(List<Integer> newSkippedSamples, List<Integer> newAddedSamples)
    {
        // for samples previously skipped, check if can now be allocated to one or more of the final bucket groups
        //if(mFinalBucketGroups.isEmpty())
        //    return;

        if(newSkippedSamples.isEmpty() && mSkippedSamples.isEmpty())
            return;

        int initSkippedSamples = mSkippedSamples.size();
        int allocatedSamples = 0;
        int reskippedSamples = 0;

        // first remove the ones just added to a group
        for(Integer sampleId : newAddedSamples)
        {
            if(mSkippedSamples.contains(sampleId))
                mSkippedSamples.remove(sampleId);
        }

        // now check whether any others previously skipped could now be added to one of the final groups
        if(!mFinalBucketGroups.isEmpty())
        {
            int samIndex = 0;
            while (samIndex < mSkippedSamples.size())
            {
                Integer sampleId = mSkippedSamples.get(samIndex);

                if (newSkippedSamples.contains(sampleId))
                {
                    ++samIndex;
                    continue;
                }

                SampleData sample = mSampleData.get(sampleId);

                // first check against the candidate groups
                double maxOtherGroupAlloc = 0;
                double maxOtherGroupAllocPerc = 0;
                BucketGroup maxOtherGroup = getSampleMaxAllocationGroup(sample, null);

                if (maxOtherGroup != null)
                {
                    maxOtherGroupAlloc = maxOtherGroup.getSampleCount(sample.Id);
                    maxOtherGroupAllocPerc = maxOtherGroupAlloc / sample.getElevatedCount();
                }

                double reqAllocPercent = minAllocPercent(sample, false);

                boolean wasAllocated = false;

                // now also check against the final groups
                for (final BucketGroup bucketGroup : mFinalBucketGroups)
                {
                    if (bucketGroup.hasSample(sampleId))
                        continue;

                    double[] allocCounts = sample.getPotentialUnallocCounts(bucketGroup.getBucketRatios(), bucketGroup.getBucketIds(), null); // bucketGroup.getRatioRanges()

                    double proposedAllocTotal = sumVector(allocCounts);
                    double proposedAllocPerc = proposedAllocTotal / sample.getElevatedCount();

                    if (proposedAllocPerc < reqAllocPercent || proposedAllocTotal < mConfig.MinSampleAllocCount)
                        continue;

                    // continue to hold out if there is a better candidate group yet to come
                    if (maxOtherGroupAlloc > SKIP_ALLOC_FACTOR * proposedAllocTotal)
                    {
                        ++reskippedSamples;
                        continue;
                    }

                    // apply to the remaining unallocated elevated counts for this sample
                    double actualAlloc = sample.allocateBucketCounts(allocCounts, reqAllocPercent);
                    double allocPerc = actualAlloc / sample.getElevatedCount();

                    if (allocPerc >= reqAllocPercent)
                    {
                        bucketGroup.addSample(sampleId, allocCounts);
                        sample.addBucketGroup(bucketGroup, allocPerc);
                        wasAllocated = true;
                        ++allocatedSamples;
                        mReassessSamples.add(sampleId);

                        LOGGER.debug(String.format("sample(%d) skipped now added to bg(%d) count(prop=%s act=%s of %s) allocatedPerc(+%.3f -> %.3f) groupCount(%d)",
                                sampleId, bucketGroup.getId(), sizeToStr(proposedAllocTotal), sizeToStr(actualAlloc), sizeToStr(sample.getElevatedCount()),
                                sample.lastAllocPercChange(), sample.getAllocPercent(), sample.getElevBucketGroups().size()));
                    }
                }

                if (!wasAllocated)
                {
                    if (maxOtherGroupAllocPerc >= reqAllocPercent)
                    {
                        //  LOGGER.debug(String.format("sample(%d) skipped again with better allocation(%s perc=%.3f)",
                        //      sampleId, sizeToStr(maxOtherGroupAlloc), maxOtherGroupAllocPerc));

                        ++samIndex;
                        continue;
                    }
                    else
                    {
                        LOGGER.debug("sample({}) skipped & not allocated, being removed", sampleId);
                    }
                }

                mSkippedSamples.remove(samIndex);
            }
        }

        // finally add in the newly skipped samples
        for(Integer sampleId : newSkippedSamples)
        {
            if(!mSkippedSamples.contains(sampleId))
                mSkippedSamples.add(sampleId);
        }

        if(initSkippedSamples > mSkippedSamples.size())
        {
            LOGGER.debug("skipped samples: new({}) initial({}) current({}) allocated({}) reskipped({})",
                    newSkippedSamples.size(), initSkippedSamples, mSkippedSamples.size(), allocatedSamples, reskippedSamples);
        }
    }

    private void clearBucketGroups(boolean finalGroupsOnly)
    {
        if(mBucketGroups.isEmpty())
            return;

        int initBgCount = mBucketGroups.size();

        int bgIndex = 0;
        while(bgIndex < mBucketGroups.size())
        {
            BucketGroup bucketGroup = mBucketGroups.get(bgIndex);

            if(mFinalBucketGroups.contains(bucketGroup))
            {
                mBucketGroups.remove(bgIndex);
                continue;
            }

            if(finalGroupsOnly)
            {
                ++bgIndex;
                continue;
            }

            // if any of the founding samples has now been allocated, this group
            // can be purged. Those sample will re-create it during the group-formation routine if warranted
            boolean initSampleAllocated = false;
            for(Integer initSampleId : bucketGroup.getInitialSampleIds())
            {
                if(mReassessSamples.contains(initSampleId))
                {
                    initSampleAllocated = true;
                    break;
                }
            }

            if(initSampleAllocated)
            {
                mBucketGroups.remove(bgIndex);
                continue;
            }

            // remove any samples from this group which were just allocated
            for(int samIndex = 0; samIndex < bucketGroup.getSampleCount(); ++samIndex)
            {
                int sampleId = bucketGroup.getSampleIds().get(samIndex);

                if(!mReassessSamples.contains(sampleId))
                    continue;

                SampleData sample = mSampleData.get(sampleId);

                // remove this sample's allocation to the group
                boolean ok = bucketGroup.removeSampleAllocation(sample, samIndex,true);

                if(!ok)
                {
                    LOGGER.debug("bg({}) removal of sample({})", bucketGroup.getId(), sampleId);
                    mHasErrors = true;
                    return;
                }
            }

            if(bucketGroup.getSampleIds().isEmpty())
            {
                // assume this group needs reassessing since the bulk of its samples have just been allocated
                mBucketGroups.remove(bgIndex);
                continue;
            }

            ++bgIndex;
        }

        if(initBgCount > mBucketGroups.size())
        {
            LOGGER.debug("bucket groups clean-up({} -> {}) from {} reassess samples, skipped groups({})",
                    initBgCount, mBucketGroups.size(), mReassessSamples.size(), mSkippedBucketGroups.size());
        }
    }

    private void applyPredefinedSigs()
    {
        if (mPredefinedSigs == null || mConfig.ApplyPredefinedSigCount < 1)
            return;

        // int bgSigCount = mSpecificCancer.isEmpty() || mBackgroundGroups.size() == 1 ? mBackgroundGroups.size() : mCancerSamplesMap.size();
        int bgSigCount = mCancerSamplesMap.size();

        List<Integer> predefefinedToSkip = Lists.newArrayList();

        if(mReporter.getReferenceSigs().equals(mPredefinedSigs))
        {
            bgSigCount = 0;

            // keep 0-17 and 32, 39, 40, 41, 43, 44, 45, 48, 60, 61
            for(int i = 18; i < mPredefinedSigs.Cols; ++i)
            {
                if (i != 32 && i != 39 && i != 40 && i != 41 && i != 43 && i != 44 && i != 45 && i != 48 && i != 60 && i != 61)
                    predefefinedToSkip.add(i);
            }
        }

        // assume that the first X sigs are background, so start with the first elevated sig
        int sigsApplied = 0;
        for(int sig = bgSigCount; sig < mPredefinedSigs.Cols; ++sig)
        {
            if(predefefinedToSkip.contains(sig))
                continue;

            double[] bucketRatios = mPredefinedSigs.getCol(sig);

            convertToPercentages(bucketRatios); // in case file values are lacking enough precision to add to 1

            BucketGroup bucketGroup = new BucketGroup(++mNextBucketId);
            bucketGroup.setTag("predefined");
            bucketGroup.setBucketRatios(bucketRatios);

            if(mConfig.UseRatioRanges)
                bucketGroup.setRatioRangePerc(DEFAULT_SIG_RATIO_RANGE_PERCENT);

            List<Integer> bucketIds = Lists.newArrayList();

            for(int b = 0; b < mBucketCount; ++b)
            {
                if(bucketRatios[b] == 0)
                    continue;

                bucketIds.add(b);
                bucketGroup.addBucket(b, true);
            }

            LOGGER.debug("created predefined bg({}) with {} buckets", bucketGroup.getId(), bucketIds.size());
            mFinalBucketGroups.add(bucketGroup);

            ++sigsApplied;

            if(sigsApplied >= mConfig.ApplyPredefinedSigCount)
                break;
        }

        if(mFinalFitOnly)
            return;

        if(!mFinalBucketGroups.isEmpty())
        {
            // ratio ranges aren't currently loaded so don't try to apply them when sigs are pre-loaded
            List<BucketGroup> elevatedGroups = Lists.newArrayList();
            elevatedGroups.addAll(mFinalBucketGroups);

            fitAllSamples();

            // restore the final BGs so they only include elevated groups
            mFinalBucketGroups.clear();
            mFinalBucketGroups.addAll(elevatedGroups);

            analyseGroupsVsExtData(mFinalBucketGroups, false);
            mReporter.logBucketGroups(true);
        }
    }

    private void mergeSimilarGroups()
    {
        if(mConfig.GroupMergeScoreThreshold == 0)
            return;

        // criteria for merging groups:
        // similar score above the specified threshold
        // merge the small group into the larger group
        // what to do about bucket mismatches??
        /*
        int bgIndex = 0;
        while(bgIndex < mFinalBucketGroups.size())
        {
            final BucketGroup bucketGroup = mFinalBucketGroups.get()
        }

        */
    }

    private void fitAllSamples()
    {
        // in the final fit, background groups are included and no distinction is made between elevated and background counts
        LOGGER.debug("applying final fit with {} bucket groups to all samples", mFinalBucketGroups.size());

        double reqAllocPercent = MIN_GROUP_ALLOC_PERCENT_LOWER;

        SigContribOptimiser sigOptim = new SigContribOptimiser(mBucketCount, false, SAMPLE_ALLOCATED_PERCENT);

        if(mConfig.UseBackgroundCounts)
        {
            LOGGER.debug("including {} background group(s)", mBackgroundGroups.size());

            List<BucketGroup> elevatedGroups = Lists.newArrayList();
            elevatedGroups.addAll(mFinalBucketGroups);

            // put the BG groups first
            mFinalBucketGroups.clear();
            mFinalBucketGroups.addAll(mBackgroundGroups);
            mFinalBucketGroups.addAll(elevatedGroups);
        }

        for(BucketGroup bucketGroup : mFinalBucketGroups)
        {
            bucketGroup.clearSamples();
        }

        List<BucketGroup> prevGroupList = Lists.newArrayList();
        List<BucketGroup> potentialGroupList = Lists.newArrayList();

        List<Double> sampleGroupCounts = Lists.newArrayList();

        for (SampleData sample : mSampleData)
        {
            if (sample.isExcluded())
                continue;

            mConfig.logSample(sample.Id);

            double prevAllocPerc = (sample.getAllocatedCount() + sample.getBackgroundCount()) / sample.getTotalCount();

            // keep track of the groups allocated during discovery in case the final fit is worse
            prevGroupList.clear();
            prevGroupList.addAll(sample.getBucketGroups());
            int prevGroupCount = prevGroupList.size();

            sample.clearAllocations(false);

            double sampleCount = sample.getTotalCount();

            List<Double> potentialAllocTotals = Lists.newArrayList();
            List<double[]> potentialAllocCounts = Lists.newArrayList();

            potentialGroupList.clear();

            for(int bgIndex = 0; bgIndex < mFinalBucketGroups.size(); ++bgIndex)
            {
                BucketGroup bucketGroup = mFinalBucketGroups.get(bgIndex);

                if(bucketGroup.isBackground())
                {
                    // assign if applicable by cancer type
                    if(sample.getBackgroundGroup() != bucketGroup)
                        continue;
                }

                // re-test with all elevated counts now on offer
                double[] allocCounts = sample.getPotentialUnallocCounts(bucketGroup.getBucketRatios(), bucketGroup.getBucketIds(), bucketGroup.getRatioRanges());
                double allocTotal = sumVector(allocCounts);

                if (sample.getBackgroundGroup() != bucketGroup && (allocTotal / sampleCount < reqAllocPercent || allocTotal < mConfig.MinSampleAllocCount))
                    continue;

                // add in descending order
                int index = 0;
                while(index < potentialAllocTotals.size())
                {
                    if(allocTotal > potentialAllocTotals.get(index))
                        break;

                    ++index;
                }

                potentialGroupList.add(index, bucketGroup);
                potentialAllocTotals.add(index, allocTotal);
                potentialAllocCounts.add(allocCounts);
            }

            if(potentialGroupList.isEmpty())
            {
                LOGGER.debug("sample({}) found no potential groups to fit", sample.Id);
                continue;
            }

            boolean useNewFit = false;

            if(potentialGroupList.size() > 1)
            {
                useNewFit = fitSampleWithGroups(sigOptim, sample, potentialGroupList, prevAllocPerc, reqAllocPercent, true);
                boolean usePrevFit = false;

                if (!useNewFit)
                {
                    // revert back to the previous set of groups added through discovery
                    sample.clearAllocations(false);
                    usePrevFit = fitSampleWithGroups(sigOptim, sample, prevGroupList, 0, reqAllocPercent, false);
                }

                if (!usePrevFit && !useNewFit)
                {
                    LOGGER.warn("sample({}) left unallocated", sample.Id);
                }
            }

            if(potentialGroupList.size() == 1 || (!useNewFit && prevGroupList.size() == 1))
            {
                // allocate the single group, no need to first work out an optimal fit
                BucketGroup bucketGroup = potentialGroupList.get(0);
                double[] allocCounts = potentialAllocCounts.get(0);
                double actualAlloc = sample.allocateBucketCounts(allocCounts, 0);

                bucketGroup.addSample(sample.Id, allocCounts);
                double allocPerc = actualAlloc / sampleCount;

                if(!bucketGroup.isBackground())
                    sample.addBucketGroup(bucketGroup, allocPerc);

                LOGGER.debug(String.format("sample(%d) added to single bg(%d) fit(%s of %s, sc=%.2f) allocatedPerc(+%.3f -> %.3f) noise(%s %.3f/%.3f)",
                        sample.Id, bucketGroup.getId(), sizeToStr(actualAlloc), sizeToStr(sampleCount), bucketGroup.calcSampleFitScore(sample, true), sample.lastAllocPercChange(),
                        sample.getAllocPercent(), sizeToStr(sample.getAllocNoise()), sample.getNoisePerc(), sample.getNoiseOfTotal()));
            }

            if(mConfig.UseRatioRanges)
            {
                // tweak each group in turn to allocate the max possible using ratio ranges
                for(final BucketGroup bucketGroup : sample.getBucketGroups())
                {
                    final double[] ratioRanges = bucketGroup.getRatioRanges();
                    final double[] bucketRatios = bucketGroup.getBucketRatios();

                    int samIndex = bucketGroup.getSampleCount() - 1;

                    // the current sample is likely to be the last one added
                    if(samIndex >= bucketGroup.getSampleIds().size() || bucketGroup.getSampleIds().get(samIndex) != sample.Id)
                    {
                        samIndex = bucketGroup.getSampleIndex(sample.Id);
                    }

                    double[] sampleAllocCounts = bucketGroup.getSampleCounts().get(samIndex);
                    final List<Integer> bucketIds = bucketGroup.getBucketIds();

                    double[] newAllocs = SigOptimiser.optimiseSampleFit(sample, bucketGroup.getId(), bucketIds, bucketRatios, ratioRanges, sampleAllocCounts, false);

                    if(newAllocs == null) // could do no better
                        continue;

                    sample.allocateBucketCounts(newAllocs, 0);
                    bucketGroup.addSampleCounts(samIndex, newAllocs);
                }
            }

            String allocResult = "unch";

            if(sample.getAllocPercent() >= prevAllocPerc + 0.01)
                allocResult = "better";
            else if(sample.getAllocPercent() <= prevAllocPerc - 0.01)
                allocResult = "worse";

            LOGGER.debug(String.format("sample(%d) final fit: method(%s) groups(%d prev=%d pot=%d) %s allocation(prev=%.3f new=%.3f, act=%s of %s) noise(%s %.3f/%.3f)",
                    sample.Id, useNewFit ? "all" : "prev", sample.getBucketGroups().size(), prevGroupCount, potentialGroupList.size(),
                    allocResult, prevAllocPerc, sample.getAllocPercent(), sizeToStr(sample.getAllocatedCount()), sizeToStr(sampleCount),
                    sizeToStr(sample.getAllocNoise()), sample.getNoisePerc(), sample.getNoiseOfTotal()));

            if(!sample.getBucketGroups().isEmpty())
                sampleGroupCounts.add((double)sample.getBucketGroups().size());
        }

        LOGGER.debug(String.format("sig-optim stats: instances(%d) avgIters(%.1f) avgImprovePerc(%.3f)",
                sigOptim.getInstances(), sigOptim.getAvgIterations(), sigOptim.getAvgImprovePerc()));

        // report range of group counts across the samples
        double[] groupCounts = listToArray(sampleGroupCounts);
        List<Integer> sortedIndicesGCs = getSortedVectorIndices(groupCounts, false);

        if(sortedIndicesGCs.size() > 2)
        {
            int medianIndex = sortedIndicesGCs.size() /2;
            double avg = sumVector(groupCounts) / groupCounts.length;
            LOGGER.debug(String.format("sample group count stats: total(%d) max(%.0f) median(%.0f) avg(%.1f)",
                    groupCounts.length, groupCounts[sortedIndicesGCs.get(0)], groupCounts[sortedIndicesGCs.get(medianIndex)], avg));
        }
    }

    private boolean fitSampleWithGroups(SigContribOptimiser sigOptim, SampleData sample, List<BucketGroup> bucketGroups,
            double prevAllocPerc, double reqAllocPerc, boolean removeAllocsOnFail)
    {
        double sampleCount = sample.getElevatedCount();

        int groupCount = bucketGroups.size();
        boolean hasBackgroundGroup = false;
        List<BucketGroup> addedGroups = Lists.newArrayList();

        if(groupCount > 1)
        {
            List<double[]> ratiosCollection = Lists.newArrayList();
            List<Integer> sigIds = Lists.newArrayList();

            // add each group's data
            int backgroundGroupIndex = -1;
            for (int index = 0; index < groupCount; ++index)
            {
                final BucketGroup bucketGroup = bucketGroups.get(index);
                ratiosCollection.add(bucketGroup.getBucketRatios());
                sigIds.add(bucketGroup.getId());

                if (bucketGroup == sample.getBackgroundGroup())
                {
                    backgroundGroupIndex = index;
                    hasBackgroundGroup = true;
                }
            }

            sigOptim.initialise(
                    sample.Id,
                    hasBackgroundGroup ? sample.getBucketCounts() : sample.getElevatedBucketCounts(),
                    sample.getCountRanges(),
                    ratiosCollection,
                    reqAllocPerc, mConfig.MinSampleAllocCount);

            sigOptim.setSigIds(sigIds);
            // sigOptim.setLogVerbose(mConfig.logSample(sample.Id));

            // each sample's background sig will remain in the list even if it drops below the required threshold
            sigOptim.setRequiredSig(backgroundGroupIndex);
            boolean validCalc = sigOptim.fitToSample();

            if (!validCalc)
            {
                LOGGER.error("sample({}) refit of {} sigs failed", sample.Id, groupCount);
                mHasErrors = true;
                return false;
            }

            // if all ok, allocate each contribution to the sample
            final double[] newGroupContribs = sigOptim.getContribs();

            double fitAllocPerc = sigOptim.getAllocPerc();

            if (fitAllocPerc < prevAllocPerc - 0.001)
            {
                LOGGER.debug(String.format("sample(%d) fit(%.3f) sigs(%d from %d) below required(%.3f)",
                        sample.Id, fitAllocPerc, sigOptim.contributingSigCount(), groupCount, prevAllocPerc));

                if (removeAllocsOnFail)
                    return false;
            }

            // finally allocate any group more than the min allocation percent
            List<Integer> sortedAllocIndices = getSortedVectorIndices(newGroupContribs, false);

            for (Integer index : sortedAllocIndices)
            {
                final BucketGroup bucketGroup = bucketGroups.get(index);
                double fitAlloc = newGroupContribs[index];

                boolean isBackground = bucketGroup == sample.getBackgroundGroup();

                if (fitAlloc == 0 && !isBackground)
                    break;

                double grpReqAllocPerc = isBackground ? 0 : reqAllocPerc;

                if(isBackground)
                {
                    if(fitAlloc == 0)
                    {
                        LOGGER.debug(String.format("sample(%d) missed background bg(%d) fit allocation", sample.Id, bucketGroup.getId()));
                        // force a test of allocation to the BG sig once the others have been applied
                    }
                }
                else
                {
                    if(fitAlloc / sampleCount < grpReqAllocPerc || fitAlloc < mConfig.MinSampleAllocCount)
                    {
                        LOGGER.debug(String.format("sample(%d) missed fit contrib bg(%d) fit(%s perc=%.3f of %s)",
                                sample.Id, bucketGroup.getId(), sizeToStr(fitAlloc), fitAlloc / sampleCount, sizeToStr(sampleCount)));
                        break;
                    }
                }

                double[] allocCounts = new double[mBucketCount];

                // override with the fitted contribution
                final double[] bucketRatios = bucketGroup.getBucketRatios();
                for (int b = 0; b < mBucketCount; ++b)
                {
                    double ratio = bucketRatios[b];
                    allocCounts[b] = fitAlloc * ratio;
                }

                double actualAlloc = sample.allocateBucketCounts(allocCounts, grpReqAllocPerc);
                double allocPerc = actualAlloc / sample.getElevatedCount();

                if (allocPerc >= grpReqAllocPerc)
                {
                    bucketGroup.addSample(sample.Id, allocCounts);
                    sample.addBucketGroup(bucketGroup, allocPerc);

                    LOGGER.debug(String.format("sample(%d) added to bg(%d) fit(%s act=%s of %s sc=%.2f) allocatedPerc(+%.3f -> %.3f) noise(%s %.3f/%.3f)",
                            sample.Id, bucketGroup.getId(), sizeToStr(fitAlloc), sizeToStr(actualAlloc), sizeToStr(sampleCount), bucketGroup.calcSampleFitScore(sample, true),
                            sample.lastAllocPercChange(), sample.getAllocPercent(), sizeToStr(sample.getAllocNoise()), sample.getNoisePerc(), sample.getNoiseOfTotal()));

                    addedGroups.add(bucketGroup);
                }
                else
                {
                    LOGGER.debug(String.format("sample(%d) missed alloc to bg(%d) fit(%s perc=%.3f) vs actual(%s perc=%.3f)",
                            sample.Id, bucketGroup.getId(), sizeToStr(fitAlloc), fitAlloc / sampleCount, sizeToStr(actualAlloc), actualAlloc / sampleCount));
                }
            }
        }

        if(sample.getAllocPercent() < prevAllocPerc - 0.001)
        {
            LOGGER.debug(String.format("sample(%d) fit with all alloc total(%s perc=%.3f) below required(%.3f)",
                    sample.Id, sizeToStr(sample.getAllocatedCount()), sample.getAllocPercent(), prevAllocPerc));

            if(removeAllocsOnFail)
            {
                // remove the allocs just made
                for (BucketGroup bucketGroup : addedGroups)
                {
                    int samIndex = bucketGroup.getSampleCount() - 1;
                    bucketGroup.removeSampleAllocation(sample, samIndex,false);
                }

                return false;
            }
        }

        return true;
    }

    private void checkSampleRefitWithRanges()
    {
        LOGGER.debug("checking sample refit within bucket ratio ranges");

        double reqAllocPercent = MIN_GROUP_ALLOC_PERCENT_LOWER;

        for(SampleData sample : mSampleData)
        {
            if(sample.isExcluded())
                continue;

            double sampleCount = sample.getElevatedCount();

            mConfig.logSample(sample.Id);

            // tweak each group in turn to allocate the max possible using ratio ranges
            for(final BucketGroup bucketGroup : sample.getBucketGroups())
            {
                final double[] ratioRanges = bucketGroup.getRatioRanges();
                final double[] bucketRatios = bucketGroup.getBucketRatios();

                int samIndex = bucketGroup.getSampleCount() - 1;

                if(bucketGroup.getSampleIds().get(samIndex) != sample.Id)
                {
                    samIndex = bucketGroup.getSampleIndex(sample.Id);

                    if(samIndex == -1)
                    {
                        LOGGER.error("sample({}) not found in bg({})", sample.Id, bucketGroup.getId());
                        continue;
                    }
                }

                double[] sampleAllocCounts = bucketGroup.getSampleCounts().get(samIndex);
                final List<Integer> bucketIds = bucketGroup.getBucketIds();

                // determine if by using ratio ranges any buckets can allow extra allocations
                // note: the vector returned is in additional to the count already allocated for this bucket group
                double[] additionalAllocs = SigOptimiser.optimiseSampleFit(sample, bucketGroup.getId(), bucketIds, bucketRatios,
                        ratioRanges, sampleAllocCounts, false);

                if(additionalAllocs == null) // could do no better
                    continue;

                sample.allocateBucketCounts(additionalAllocs, 0);
                bucketGroup.addSampleCounts(samIndex, additionalAllocs);
            }

            // and for any signficantly unallocated samples test them one more time against the expand ratio ranges
            if(sample.getAllocPercent() >= 0.8)
                continue;

            // check whether any large or larger potential allocs didn't make the cut
            for (BucketGroup bucketGroup : mFinalBucketGroups)
            {
                if(sample.getBucketGroups().contains(bucketGroup))
                    continue;

                double[] allocCounts = sample.getPotentialUnallocCounts(bucketGroup.getBucketRatios(), bucketGroup.getBucketIds(), bucketGroup.getRatioRanges());
                double allocTotal = sumVector(allocCounts);

                if (allocTotal / sampleCount < reqAllocPercent || allocTotal < mConfig.MinSampleAllocCount)
                    continue;

                final double[] ratioRanges = bucketGroup.getRatioRanges();
                final double[] bucketRatios = bucketGroup.getBucketRatios();
                final List<Integer> bucketIds = bucketGroup.getBucketIds();

                double[] newAllocs = SigOptimiser.optimiseSampleFit(sample, bucketGroup.getId(), bucketIds, bucketRatios, ratioRanges, allocCounts, false);

                if (newAllocs != null)
                {
                    // take the improved allocation
                    copyVector(newAllocs, allocCounts);
                    allocTotal = sumVector(allocCounts);
                }

                double refitTotalPerc = allocTotal / sampleCount;

                if (refitTotalPerc < reqAllocPercent)
                    continue;

                double actualAlloc = sample.allocateBucketCounts(allocCounts, reqAllocPercent);
                double allocPerc = actualAlloc / sample.getElevatedCount();

                if (allocPerc >= reqAllocPercent)
                {
                    bucketGroup.addSample(sample.Id, allocCounts);
                    sample.addBucketGroup(bucketGroup, allocPerc);

                    LOGGER.debug(String.format("sample(%d) added to bg(%d) refit(%s act=%s of %s) allocatedPerc(+%.3f -> %.3f) noise(%s %.3f/%.3f) groupCount(%d)",
                            sample.Id, bucketGroup.getId(), sizeToStr(allocTotal), sizeToStr(actualAlloc), sizeToStr(sampleCount), sample.lastAllocPercChange(),
                            sample.getAllocPercent(), sizeToStr(sample.getAllocNoise()), sample.getNoisePerc(), sample.getNoiseOfTotal(), sample.getBucketGroups().size()));
                }
            }
        }
    }

    private void assessBucketGroupSampleRanges()
    {
        LOGGER.debug("assessing bucket ratio range expansion");

        // first purge any empty groups following the final fit
        int bgIndex = 0;
        while(bgIndex < mFinalBucketGroups.size())
        {
            BucketGroup bucketGroup = mFinalBucketGroups.get(bgIndex);
            if(bucketGroup.getSampleCount() == 0)
            {
                LOGGER.debug("bg({}) has no samples and removed", bucketGroup.getId());
                mFinalBucketGroups.remove(bgIndex);
                continue;
            }

            ++bgIndex;
        }

        List<SampleData> groupSamples = Lists.newArrayList();
        List<double[]> sampleCountsList = Lists.newArrayList();

        for (BucketGroup bucketGroup : mFinalBucketGroups)
        {
            if(bucketGroup.isBackground())
            {
                // leave at the default
                continue;
            }

            final List<double[]> sampleAllocCounts = bucketGroup.getSampleCounts();

            sampleCountsList.clear();
            groupSamples.clear();

            for(int samIndex = 0; samIndex < bucketGroup.getSampleCount(); ++samIndex)
            {
                Integer sampleId = bucketGroup.getSampleIds().get(samIndex);
                final SampleData sample = mSampleData.get(sampleId);
                groupSamples.add(sample);

                double[] sampleCounts = new double[mBucketCount];
                copyVector(sample.getUnallocBucketCounts(), sampleCounts);
                addVector(sampleAllocCounts.get(samIndex), sampleCounts);

                sampleCountsList.add(sampleCounts);
            }

            // now run the sig-optimiser for all samples in this group (not restricted to pure samples any longer)
            // and take the ratio ranges from this routine before the final sample allocation adjustment
            SigOptimiser sigOptim = new SigOptimiser(bucketGroup.getId(), groupSamples, sampleCountsList, bucketGroup.getBucketRatios());
            sigOptim.setCacheBucketInfo(true);
            sigOptim.setRatioRangePercentile(0.9);
            sigOptim.setLogVerbose(true);
            sigOptim.calcBucketDistributions();

            if(sigOptim.hasChanged())
            {
                copyVector(sigOptim.getRatioRanges(), bucketGroup.getRatioRanges());
            }

            // writeBucketGroupRatioRangeData(bucketGroup.getId(), bucketGroup.getTag(), bucketGroup.getCancerType(), bucketGroup.getEffects(), bucketGroup.getSampleCount(), sigOptim);
        }
    }

    private void assessUnallocatedCounts()
    {
        List<SampleData> groupSamples = Lists.newArrayList();
        List<double[]> sampleCountsList = Lists.newArrayList();

        for(final SampleData sample : mSampleData)
        {
            if(sample.isExcluded())
                continue;

            if(sample.getAllocPercent() >= 0.95)
                continue;

            groupSamples.add(sample);
            sampleCountsList.add(sample.getUnallocBucketCounts());
        }

        double[] emptyRatios = new double[mBucketCount];

        SigOptimiser sigOptim = new SigOptimiser(-1, groupSamples, sampleCountsList, emptyRatios);
        sigOptim.setCacheBucketInfo(true);
        sigOptim.setRatioRangePercentile(0.9);
        sigOptim.setLogVerbose(true);
        sigOptim.calcBucketDistributions();

        writeBucketGroupRatioRangeData(-1, "Unalloc", "", "", groupSamples.size(), sigOptim);
    }

    public static double minAllocPercent(final SampleData sample, boolean scaleToLower)
    {
        if(!scaleToLower)
            return MIN_GROUP_ALLOC_PERCENT;

        // require say 10% of the remaining unallocated count, but with an upper minimum limit
        return max(sample.getUnallocPercent() * MIN_GROUP_ALLOC_PERCENT, MIN_GROUP_ALLOC_PERCENT_LOWER);
    }

    private void analyseGroupsVsExtData(List<BucketGroup> bgList, boolean verbose)
    {
        if(mExtSampleData == null)
            return;

        LOGGER.debug("analysing {} bucket groups", bgList.size());

        // check counts for each external data category against the samples in each bucket group

        // want to know if:
        // a) the samples have 1 or more identical categories and
        // b) the proportion of these categories out of the cohort (ie missing other samples, linked to other bucket groups)

        for (BucketGroup bucketGroup : bgList)
        {
            setBucketGroupFeatures(bucketGroup, verbose);
        }
    }

    private void setBucketGroupFeatures(BucketGroup bucketGroup, boolean verbose)
    {
        final List<Integer> sampleIds = bucketGroup.getSampleIds();
        int sampleCount = sampleIds.size();

        // clear any previous
        bucketGroup.setCancerType("");
        bucketGroup.setEffects("");

        String groupEffectsStr = "";
        String groupCancerTypeStr = "";
        String catEffectsStr = "";
        String catCancerTypeStr = "";

        HashMap<String,Integer> categoryCounts = populateSampleCategoryMap(sampleIds);

        for(Map.Entry<String,Integer> entrySet : categoryCounts.entrySet())
        {
            final String catName = entrySet.getKey();
            int catSampleCount = entrySet.getValue();
            double samplesPerc = catSampleCount/(double)sampleCount; // percentage of samples within the group

            int catAllCount = mExtCategoriesMap.get(catName);
            double catPerc = catSampleCount / (double) catAllCount; // percentage of all instances of this

            // ignore small & rare effects unless they're the majority of this group
            boolean ignoreCatPercent = catAllCount < 0.5 * sampleCount && catAllCount < 30;
            boolean hasDominantSamplePerc = samplesPerc >= DOMINANT_CATEGORY_PERCENT;
            boolean hasProminentSamplePerc = samplesPerc >= DOMINANT_CATEGORY_PERCENT * 0.5;
            boolean hasDominantCatPerc = catPerc >= DOMINANT_CATEGORY_PERCENT && !ignoreCatPercent;
            boolean hasProminentCatPerc = (catPerc >= DOMINANT_CATEGORY_PERCENT * 0.5) && !ignoreCatPercent;

            if(!hasProminentSamplePerc && !hasProminentCatPerc)
                continue;

            if(verbose)
            {
                LOGGER.debug(String.format("bg(%d) category(%s) count(%d of %d) perc(group=%.3f category=%.3f)",
                        bucketGroup.getId(), catName, catSampleCount, sampleCount, samplesPerc, catPerc));
            }

            if(extractCategoryName(catName).equals(CATEGORY_CANCER_TYPE))
            {
                String cancerType = extractCategoryValue(catName);

                if(hasDominantSamplePerc)
                {
                    groupCancerTypeStr = String.format("%s=%.2f", cancerType, samplesPerc);
                }
                else if(hasProminentSamplePerc)
                {
                    if(!groupCancerTypeStr.isEmpty())
                        groupCancerTypeStr += ";";

                    groupCancerTypeStr += String.format("%s=%.2f", cancerType, samplesPerc);
                }

                if(hasDominantCatPerc)
                {
                    if(!catCancerTypeStr.isEmpty())
                        catCancerTypeStr += ";";

                    catCancerTypeStr += String.format("%s=%.2f", cancerType, catPerc);
                }
            }
            else
            {
                String effect = catName.length() > 10 ? catName.substring(0, 10) : catName;

                if(hasDominantSamplePerc)
                {
                    if (!groupEffectsStr.isEmpty())
                        groupEffectsStr += ";";

                    groupEffectsStr += String.format("%s=%.2f", effect, samplesPerc);
                }

                if(hasDominantCatPerc)
                {
                    if(!catEffectsStr.isEmpty())
                        catEffectsStr += ";";

                    catEffectsStr += String.format("%s=%.2f", effect, catPerc);
                }
            }
        }

        String cancerTypeStr = groupCancerTypeStr;

        if(!catCancerTypeStr.isEmpty())
        {
            if(!cancerTypeStr.isEmpty())
                cancerTypeStr += ";";

            cancerTypeStr += "cat:" + catCancerTypeStr;
        }

        bucketGroup.setCancerType(cancerTypeStr);

        String effectsStr = groupEffectsStr;

        if(!catEffectsStr.isEmpty())
        {
            if(!effectsStr.isEmpty())
                effectsStr += ";";

            effectsStr += "cat:" + catEffectsStr;
        }

        bucketGroup.setEffects(effectsStr);
    }

    private void writeInterimBucketGroups()
    {
        try
        {
            if(mBgInterimFileWriter == null)
            {
                mBgInterimFileWriter = getNewFile(mOutputDir, mOutputFileId + "_ba_interim_groups.csv");

                mBgInterimFileWriter.write("RunId,Rank,BgId,Type,CancerType,Effects,SampleCount,BucketCount,MutLoad,PotAlloc,PotAllocAdj");

                for(int i = 0; i < mBucketCount; ++i)
                {
                    mBgInterimFileWriter.write(String.format(",%d", i));
                }

                mBgInterimFileWriter.newLine();
            }

            BufferedWriter writer = mBgInterimFileWriter;

            for (int bgIndex = 0; bgIndex < mTopAllocBucketGroups.size(); ++bgIndex)
            {
                BucketGroup bucketGroup = mTopAllocBucketGroups.get(bgIndex);

                String groupType = bucketGroup.isBackground() ? BG_TYPE_BACKGROUND : bucketGroup.getTag();

                writer.write(String.format("%d,%d,%d,%s,%s,%s,%d,%d,%.0f,%.0f,%.0f",
                        mRunId, bgIndex, bucketGroup.getId(), groupType, bucketGroup.getCancerType(), bucketGroup.getEffects(),
                        bucketGroup.getSampleCount(), bucketGroup.getBucketCount(),
                        sumVector(bucketGroup.getBucketCounts()), bucketGroup.getPotentialAllocation(), bucketGroup.getPotentialAdjAllocation()));

                double[] bucketRatios = bucketGroup.getBucketRatios();

                for(int i = 0; i < mBucketCount; ++i)
                {
                    writer.write(String.format(",%.6f", bucketRatios[i]));
                }

                writer.newLine();
            }
        }
        catch(IOException exception)
        {
            LOGGER.error("failed to write output file: interim bucket groups");
        }
    }

    private void writeBucketGroupRatioRangeData(final int bgId, final String tag, final String cancerType, final String effects, int sampleCount, final SigOptimiser sigOptim)
    {
        try
        {
            if(mBgRatioRangeFileWriter == null)
            {
                mBgRatioRangeFileWriter = getNewFile(mOutputDir, mOutputFileId + "_ba_grp_ratio_ranges.csv");

                mBgRatioRangeFileWriter.write("BgId,Type,CancerType,Effects,SampleCount,BucketCount,Bucket,");

                mBgRatioRangeFileWriter.write(SigOptimiser.getBucketInfoHeader());

                mBgRatioRangeFileWriter.newLine();
            }

            BufferedWriter writer = mBgRatioRangeFileWriter;

            List<Integer> bucketIds = Lists.newArrayList();
            bucketIds.addAll(sigOptim.getNonZeroBuckets());

            for(Integer bucket : bucketIds)
            {
                String bucketData = sigOptim.getBucketInfo(bucket);

                if(bucketData == null)
                    continue;

                writer.write(String.format("%d,%s,%s,%s,%d,%d,%d,",
                        bgId, tag, cancerType, effects, sampleCount, sigOptim.getNonZeroBuckets().size(), bucket));

                writer.write(bucketData);

                writer.newLine();
            }
        }
        catch(IOException exception)
        {
            LOGGER.error("failed to write output file: bucket group ratio range data");
        }
    }

    private void writeFinalBucketGroups()
    {
        try
        {
            BufferedWriter writer = getNewFile(mOutputDir, mOutputFileId + "_ba_group_data.csv");

            writer.write("Rank,BgId,Type,Discovery,CancerType,Effects,SampleCount,BucketCount,MutLoad,PotentialAlloc,RefSigs,GrpLinks,ParentId,SimGrp,SimGrpScore");

            // bucket ratios follow
            for(int i = 0; i < mBucketCount; ++i)
            {
                writer.write(String.format(",%d", i));
            }

            writer.newLine();

            for (int bgIndex = 0; bgIndex < mFinalBucketGroups.size(); ++bgIndex)
            {
                BucketGroup bucketGroup = mFinalBucketGroups.get(bgIndex);

                // check for a parent group amongst the majors
                int parentId = -1;
                for (int bgIndex2 = 0; bgIndex2 < bgIndex; ++bgIndex2)
                {
                    BucketGroup otherGroup = mFinalBucketGroups.get(bgIndex2);

                    if(!otherGroup.getGroupType().equals(BG_TYPE_MAJOR))
                        continue;

                    String searchStr = String.format("bg_%d", otherGroup.getId());
                    if(bucketGroup.getGroupLinks().contains(searchStr))
                    {
                        parentId = otherGroup.getId();
                        break;
                    }
                }

                writer.write(String.format("%d,%d,%s,%s,%s,%s,%d,%d,%.0f,%.0f",
                        bgIndex, bucketGroup.getId(), bucketGroup.getGroupType(), bucketGroup.getTag(), bucketGroup.getCancerType(), bucketGroup.getEffects(),
                        bucketGroup.getSampleCount(), bucketGroup.getBucketCount(),
                        sumVector(bucketGroup.getBucketCounts()), bucketGroup.getPotentialAllocation()));

                writer.write(String.format(",%s,%s,%s,%d,%.4f",
                        bucketGroup.getRefSigs(), bucketGroup.getGroupLinks(), parentId >= 0 ? String.valueOf(parentId) : "",
                        bucketGroup.getMaxSimiliarGroup() != null ? bucketGroup.getMaxSimiliarGroup().getId() : -1, bucketGroup.getMaxSimiliarScore()));

                double[] bucketRatios = bucketGroup.getBucketRatios();

                for(int i = 0; i < mBucketCount; ++i)
                {
                    writer.write(String.format(",%.6f", bucketRatios[i]));
                }

                writer.newLine();
            }

            writer.close();

        }
        catch(IOException exception)
        {
            LOGGER.error("failed to write output file: bucket groups");
        }
    }

    private void createSignatures()
    {
        if(mConfig.MaxProposedSigs == 0 || (mFinalFitOnly && !mUsingRefSigs))
            return;

        int proposedSigCount = min(mConfig.MaxProposedSigs, mFinalBucketGroups.size());

        LOGGER.debug("creating {} signatures", proposedSigCount);

        mProposedSigs = new SigMatrix(mBucketCount, proposedSigCount);

        int sigId = 0;

        List<String> sigNames = Lists.newArrayList();

        for(int bgIndex = 0; bgIndex < mFinalBucketGroups.size(); ++bgIndex)
        {
            final BucketGroup bucketGroup = mFinalBucketGroups.get(bgIndex);

            String sigName = "";

            if(bucketGroup.isBackground())
            {
                sigName = "BG_";

                if(bucketGroup.getCancerType().length() > 5)
                    sigName += bucketGroup.getCancerType().substring(0,5);
                else
                    sigName += bucketGroup.getCancerType();

                sigName = sigName.replaceAll("/", "");
            }
            else
            {
                // take any dominant effect
                sigName = String.format("ELV_%d", bucketGroup.getId());

                String details = "";
                if(!bucketGroup.getCancerType().isEmpty())
                {
                    details += "_" + bucketGroup.getCancerType().substring(0,3);
                }

                if(!bucketGroup.getEffects().isEmpty())
                {
                    if(bucketGroup.getEffects().length() > 5)
                        details += "_" + bucketGroup.getEffects().substring(0,5);
                    else
                        details += "_" + bucketGroup.getEffects();

                }

                sigName += details.replaceAll("[0123456789.=]", "");
            }

            sigNames.add(sigName);


            final double[] bucketRatios = bucketGroup.getBucketRatios();

            mProposedSigs.setCol(sigId, bucketRatios);
            mSigToBgMapping.add(bgIndex);

            ++sigId;

            if(sigId >= proposedSigCount)
                break;
        }

        if(sigId != proposedSigCount)
        {
            mProposedSigs = redimension(mProposedSigs, mProposedSigs.Rows, sigId);
        }

        writeSignatures(mProposedSigs, "_ba_sigs.csv", sigNames);

        mReporter.setFinalState(mProposedSigs, mSigToBgMapping);
        mReporter.compareSignatures();
    }

    public static double calcSharedCSS(final double[] set1, final double[] set2)
    {
        return calcCSS(set1, set2, true);
        // return calcCSSRelative(set1, set2);
    }

    private void populateCancerSamplesMap()
    {
        if (mSampleData.isEmpty())
            return;

        mCancerSamplesMap = new HashMap();

        List<String> cancerTypes = Lists.newArrayList();

        for (final SampleData sample : mSampleData)
        {
            final String cancerType = sample.getCancerType();
            List<Integer> samplesList = mCancerSamplesMap.get(cancerType);

            if (samplesList == null)
            {
                samplesList = Lists.newArrayList();
                mCancerSamplesMap.put(cancerType, samplesList);
                cancerTypes.add(cancerType);
            }

            samplesList.add(sample.Id);
        }

        // put all low sample count by cancer type into the 'Other' cancer type mapping group
        String otherType = "Other";
        int minSamples = 20;

        List<Integer> otherTypeList = mCancerSamplesMap.get(otherType);
        if (otherTypeList == null)
        {
            otherTypeList = Lists.newArrayList();
        }

        for (final String cancerType : cancerTypes)
        {
            if (cancerType.equals(otherType))
                continue;

            List<Integer> samplesList = mCancerSamplesMap.get(cancerType);

            if (samplesList.size() < minSamples)
            {
                otherTypeList.addAll(samplesList);
                mCancerSamplesMap.remove(cancerType);
            }
        }

        if (!otherTypeList.isEmpty())
        {
            mCancerSamplesMap.put(otherType, otherTypeList);
        }

        // check all samples were allocated
        int sampleCount = 0;
        for (Map.Entry<String, List<Integer>> entry : mCancerSamplesMap.entrySet())
        {
            final List<Integer> sampleIds = entry.getValue();
            sampleCount += sampleIds.size();
        }

        if(sampleCount != mSampleCount)
        {
            LOGGER.error("sample count mismatch({} vs {})", sampleCount, mSampleCount);
            mHasErrors = true;
        }
    }

    private HashMap<String,Integer> populateSampleCategoryMap(List<Integer> sampleIds)
    {
        final List<String> fieldNames = mExtSampleData.getFieldNames();
        final List<String> sampleNames = mDataCollection.getFieldNames();

        int categoryCount = fieldNames.size() - 1; // since sampleId is the first column

        HashMap<String,Integer> categoryCounts = new HashMap();

        for(Integer sampleId : sampleIds)
        {
            final String sampleName = sampleNames.get(sampleId);

            // now locate all info about this sample from the file
            List<String> sampleData = getSampleExtData(sampleName);

            if(sampleData == null)
                continue;

            for(int c = 1; c <= categoryCount; ++c) // since field name is first
            {
                final String catValue = sampleData.get(c);

                if (catValue.isEmpty())
                    continue;

                String catName = fieldNames.get(c);

                if (c <= CATEGORY_COL_COUNT)
                    catName += CATEGORY_DELIM + catValue;

                if (!categoryCounts.containsKey(catName))
                    categoryCounts.put(catName, 1);
                else
                    categoryCounts.put(catName, categoryCounts.get(catName) + 1);
            }
        }

        return categoryCounts;
    }

    private boolean sampleHasFeature(final SampleData sample, final String categoryName)
    {
        final List<String> fieldNames = mExtSampleData.getFieldNames();
        final List<String> categoryData = sample.getCategoryData();
        for(int i = 0; i < fieldNames.size(); ++i)
        {
            if(fieldNames.get(i).equals(categoryName))
            {
                if(i >= categoryData.size())
                    return false;

                return !categoryData.get(i).isEmpty();
            }
        }

        return false;
    }

    private String CATEGORY_DELIM = "-";

    private String extractCategoryName(String categoryVal)
    {
        if(!categoryVal.contains(CATEGORY_DELIM))
            return "";

        return categoryVal.substring(0, categoryVal.indexOf(CATEGORY_DELIM));
    }

    private String extractCategoryValue(String categoryVal)
    {
        if(!categoryVal.contains(CATEGORY_DELIM))
            return categoryVal;

        return categoryVal.substring(categoryVal.indexOf(CATEGORY_DELIM)+1);
    }

    private final List<String> getSampleExtData(final String sampleName)
    {
        final List<List<String>> extSampleData = mExtSampleData.getStringData();

        for(final List<String> sampleData : extSampleData)
        {
            if(sampleData.get(COL_SAMPLE_ID).equals(sampleName))
                return sampleData;
        }

        return null;
    }

    private void writeSampleData()
    {
        try
        {
            BufferedWriter writer = getNewFile(mOutputDir,mOutputFileId + "_ba_sample_alloc.csv");

            writer.write("SampleIndex,SampleId,CancerType,MutLoad,BackgroundCount,ElevCount,AllocPerc,BgCount,BgIds,BgCancerType,BgEffects,ElevBuckets");

            // all unallocated bucket counts will be written out
            for(int i = 0; i < mBucketCount; ++i)
            {
                writer.write(String.format(",%d", i));
            }

            writer.newLine();

            for(final SampleData sample : mSampleData)
            {
                if(sample.isExcluded())
                    continue;

                final List<Integer> sampleBuckets = sample.getElevatedBuckets();

                writer.write(String.format("%d,%s,%s", sample.Id, sample.getSampleName(), sample.getCancerType()));

                double bgTotal = sumVector(mBackgroundCounts.getCol(sample.Id));

                writer.write(String.format(",%.0f,%.0f,%.0f", sample.getTotalCount(), bgTotal, sample.getElevatedCount()));

                if(sampleBuckets == null)
                {
                    writer.write(",NoElevation,0,0,,,,");
                }
                else
                {
                    double allocPerc = sample.getAllocPercent();
                    final List<BucketGroup> bucketGroups = sample.getBucketGroups();
                    final List<Double> bgAllocPercents = sample.getGroupAllocPercents();

                    writer.write(String.format(",%.3f,%d", allocPerc, bucketGroups.size()));

                    if (!bucketGroups.isEmpty())
                    {
                        String bgIdsStr = "";

                        for (int bgIndex = 0; bgIndex < bucketGroups.size(); ++bgIndex)
                        {
                            final BucketGroup bucketGroup = bucketGroups.get(bgIndex);

                            if (!bgIdsStr.isEmpty())
                                bgIdsStr += ";";

                            if(bgAllocPercents.size() == bucketGroups.size())
                                bgIdsStr += String.format("%d=%.3f", bucketGroup.getId(), bgAllocPercents.get(bgIndex));
                            else
                                bgIdsStr += bucketGroup.getId();
                        }

                        writer.write(String.format(",%s,%s,%s", bgIdsStr, bucketGroups.get(0).getCancerType(), bucketGroups.get(0).getEffects()));
                    }
                    else
                    {
                        writer.write(",,,");
                    }

                    String bucketIdsStr = sampleBuckets.toString().substring(1, sampleBuckets.toString().length() - 1); // remove []
                    if (sampleBuckets.size() > 1)
                        bucketIdsStr = bucketIdsStr.replaceAll(", ", ";");

                    writer.write(String.format(",%s", bucketIdsStr));
                }

                final double[] unallocCounts = sample.getUnallocBucketCounts();

                for(int i = 0; i < mBucketCount; ++i)
                {
                    writer.write(String.format(",%.0f", unallocCounts[i]));
                }

                writer.newLine();
            }

            writer.close();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to outputFile");
        }
    }

    private void writeSampleGroupData()
    {
        // write actual and potential bucket group info
        try
        {
            BufferedWriter writer = getNewFile(mOutputDir,mOutputFileId + "_ba_sample_grp_data.csv");

            writer.write("SampleIndex,SampleId,CancerType,MutLoad,AllocPerc,BgId,Type,Potential,PotPerc,PotGrossScore,PotNetScore,Actual,ActPerc,ActGrossScore,ActNetScore");
            writer.newLine();

            for(final SampleData sample : mSampleData)
            {
                if(sample.isExcluded())
                    continue;

                double sampleTotal = sample.getElevatedCount();
                double allocPerc = sample.getAllocPercent();

                // first write out the actual group allocations
                for(final BucketGroup bucketGroup : sample.getBucketGroups())
                {
                    int samIndex = bucketGroup.getSampleIndex(sample.Id);
                    double actualTotal = bucketGroup.getSampleCountTotals().get(samIndex);
                    double[] actualAllocs = bucketGroup.getSampleCounts().get(samIndex);
                    double actualGrossScore = bucketGroup.calcSampleFitScore(actualAllocs, actualTotal, true);
                    double actualNetScore = bucketGroup.calcSampleFitScore(actualAllocs, actualTotal, false);

                    final double[] potentialAllocs = sample.getPotentialElevCounts(bucketGroup.getBucketRatios(), bucketGroup.getBucketIds(), bucketGroup.getRatioRanges());
                    double potentialTotal = sumVector(potentialAllocs);

                    double potentialGrossScore = 0;
                    double potentialNetScore = 0;

                    if(potentialTotal > actualTotal)
                    {
                        potentialGrossScore = potentialTotal > 0 ? bucketGroup.calcSampleFitScore(potentialAllocs, potentialTotal, true) : 0;
                        potentialNetScore = potentialTotal > 0 ? bucketGroup.calcSampleFitScore(potentialAllocs, potentialTotal, false) : 0;
                    }
                    else
                    {
                        potentialGrossScore = actualGrossScore;
                        potentialNetScore = actualNetScore;
                        potentialTotal = actualTotal;
                    }

                    writer.write(String.format("%d,%s,%s,%.0f,%.3f,%d,Assigned",
                            sample.Id, sample.getSampleName(), sample.getCancerType(), sampleTotal, allocPerc, bucketGroup.getId()));

                    writer.write(String.format(",%.0f,%.3f,%.3f,%.3f,%.0f,%.3f,%.3f,%.3f",
                            potentialTotal, potentialTotal/sampleTotal, potentialGrossScore, potentialNetScore,
                            actualTotal, actualTotal/sampleTotal, actualGrossScore, actualNetScore));

                    writer.newLine();
                }

                for (BucketGroup bucketGroup : mFinalBucketGroups)
                {
                    if (sample.getBucketGroups().contains(bucketGroup))
                        continue;

                    final double[] potentialAllocs = sample.getPotentialElevCounts(bucketGroup.getBucketRatios(), bucketGroup.getBucketIds(), bucketGroup.getRatioRanges());
                    double potentialTotal = sumVector(potentialAllocs);

                    if(potentialTotal/sampleTotal < MIN_GROUP_ALLOC_PERCENT)
                        continue;

                    writer.write(String.format("%d,%s,%s,%.0f,%.3f,%d,Unassigned",
                            sample.Id, sample.getSampleName(), sample.getCancerType(), sampleTotal, allocPerc, bucketGroup.getId()));

                    writer.write(String.format(",%.0f,%.3f,%.3f,%.3f,0,0,0,0",
                            potentialTotal, potentialTotal / sampleTotal, bucketGroup.calcSampleFitScore(potentialAllocs, potentialTotal, true),
                            bucketGroup.calcSampleFitScore(potentialAllocs, potentialTotal, false)));

                    writer.newLine();
                }
            }

            writer.close();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing sample group alloc file");
        }
    }

    public void writeSignatures(final SigMatrix signatures, final String fileId, final List<String> sigIds)
    {
        try
        {
            BufferedWriter writer = getNewFile(mOutputDir,mOutputFileId + fileId); // eg "_ba_sigs.csv");

            if(sigIds != null)
            {
                // write some meaningful names for the sigs
                for(int i = 0; i < sigIds.size(); ++i)
                {
                    if(i > 0)
                        writer.write(String.format(",%s", sigIds.get(i)));
                    else
                        writer.write(String.format("%s", sigIds.get(i)));
                }
            }
            else
            {
                int i = 0;
                for (; i < signatures.Cols - 1; ++i)
                {
                    writer.write(String.format("%d,", i));
                }
                writer.write(String.format("%d", i));
            }

            writer.newLine();

            writeMatrixData(writer, signatures, false);

            writer.close();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to outputFile");
        }
    }

    public void writeSampleContributions()
    {
        int sigCount = mFinalBucketGroups.size();

        SigMatrix contribMatrix = new SigMatrix(sigCount, mSampleCount);
        double[][] contribData = contribMatrix.getData();

        for(int bgIndex = 0; bgIndex < mFinalBucketGroups.size(); ++bgIndex)
        {
            final BucketGroup bucketGroup = mFinalBucketGroups.get(bgIndex);
            final List<Integer> sampleIds = bucketGroup.getSampleIds();
            final List<Double> sampleContribs = bucketGroup.getSampleCountTotals();

            for(int samIndex = 0; samIndex < sampleIds.size(); ++samIndex)
            {
                Integer sampleId = sampleIds.get(samIndex);
                double sampleContrib = sampleContribs.get(samIndex);

                contribData[bgIndex][sampleId] = sampleContrib;
            }
        }

        contribMatrix.cacheTranspose();

        try
        {
            BufferedWriter writer = getNewFile(mOutputDir, mOutputFileId + "_ba_contribs.csv");

            List<String> sampleNames = mDataCollection.getFieldNames();

            int i = 0;
            for(; i < sampleNames.size()-1; ++i)
            {
                writer.write(String.format("%s,", sampleNames.get(i)));
            }
            writer.write(String.format("%s", sampleNames.get(i)));

            writer.newLine();

            writeMatrixData(writer, contribMatrix, false);

            writer.close();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing sample contrib file");
        }
    }

    public void writeSampleSigBucketCounts()
    {
        // write out the actual sample bucket allocations for each of a sample's sigs
        try
        {
            BufferedWriter writer = getNewFile(mOutputDir, mOutputFileId + "_ba_sample_sig_bucket_counts.csv");

            writer.write("SigId,SampleId");

            for(int b = 0; b < mBucketCount; ++b)
            {
                writer.write(String.format(",%d", b));
            }

            writer.newLine();

            for(int bgIndex = 0; bgIndex < mFinalBucketGroups.size(); ++bgIndex)
            {
                final BucketGroup bucketGroup = mFinalBucketGroups.get(bgIndex);
                final List<Integer> sampleIds = bucketGroup.getSampleIds();
                final List<double[]> sampleCountsList = bucketGroup.getSampleCounts();

                for(int samIndex = 0; samIndex < sampleIds.size(); ++samIndex)
                {
                    final SampleData sample = mSampleData.get(sampleIds.get(samIndex));

                    writer.write(String.format("%d,%s", bgIndex, sample.getSampleName()));

                    final double[] sampleCounts = sampleCountsList.get(samIndex);

                    for(int b = 0; b < mBucketCount; ++b)
                    {
                        writer.write(String.format(",%.1f", sampleCounts[b]));
                    }

                    writer.newLine();
                }
            }

            writer.close();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing sig sample counts file");
        }
    }

    public void writeSampleSigAllocations()
    {
        // write out the sample bucket group allocations plus excess (from overfitting with ratio ranges) and under allocations
        try
        {
            BufferedWriter writer = getNewFile(mOutputDir, mOutputFileId + "_ba_sample_sig_allocs.csv");

            writer.write("Signature,BgId,SampleId,AllocTotal,AllocPerc");

            writer.newLine();

            // first write out bucket group allocations
            for(int bgIndex = 0; bgIndex < mFinalBucketGroups.size(); ++bgIndex)
            {
                final BucketGroup bucketGroup = mFinalBucketGroups.get(bgIndex);
                final List<Integer> sampleIds = bucketGroup.getSampleIds();
                final List<Double> sampleAllocTotals = bucketGroup.getSampleCountTotals();

                for (int samIndex = 0; samIndex < sampleIds.size(); ++samIndex)
                {
                    final SampleData sample = mSampleData.get(sampleIds.get(samIndex));
                    double sampleAllocTotal = sampleAllocTotals.get(samIndex);

                    writer.write(String.format("%d,%s,%s,%.1f,%.3f",
                            bgIndex + 1, bucketGroup.getId(), sample.getSampleName(), sampleAllocTotal, sampleAllocTotal/sample.getElevatedCount()));

                    writer.newLine();
                }
            }

            // then write out unallocated and excess counts
            int unallocatedId = mFinalBucketGroups.size() + 1;
            int excessId = unallocatedId + 1;

            for(final SampleData sample : mSampleData)
            {
                if(sample.isExcluded())
                    continue;

                writer.write(String.format("%d,%s,%s,%.1f,%.3f",
                        unallocatedId, "Unalloc", sample.getSampleName(), sample.getUnallocatedCount(), sample.getUnallocPercent()));
                writer.newLine();

                writer.write(String.format("%d,%s,%s,%.1f,%.3f",
                        excessId, "Excess", sample.getSampleName(), sample.getAllocNoise(), sample.getNoiseOfTotal()));
                writer.newLine();
            }

            writer.close();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing sig sample counts file");
        }
    }

    public void writeSampleCalcData()
    {
        if(mLoadedSampleCalcData)
            return;

        try
        {
            BufferedWriter writer = getNewFile(mOutputDir, mOutputFileId + "_ba_sam_calc_data.csv");

            writer.write("SampleId,SampleName,BgAllocation");
            writer.newLine();

            for(int sampleId = 0; sampleId < mSampleCount; ++sampleId)
            {
                final SampleData sample = mSampleData.get(sampleId);

                writer.write(String.format("%d,%s", sampleId, sample.getSampleName()));

                // computed fields to cache
                writer.write(String.format(",%.0f", mSampleBgAllocations.get(sampleId)));

                writer.newLine();
            }

            writer.close();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to outputFile");
        }
    }

    public void writeSampleCountsNoise()
    {
        try
        {
            BufferedWriter writer = getNewFile(mOutputDir, mOutputFileId + "_ba_sample_noise.csv");

            List<String> sampleNames = mDataCollection.getFieldNames();

            int i = 0;
            for (; i < sampleNames.size() - 1; ++i)
            {
                writer.write(String.format("%s,", sampleNames.get(i)));
            }
            writer.write(String.format("%s", sampleNames.get(i)));

            writer.newLine();

            writeMatrixData(writer, mPermittedElevRange, true);

            writer.close();
        } catch (final IOException e)
        {
            LOGGER.error("error writing sample noise file");
        }
    }

    private boolean isWithinProbability(int sampleId, final double[] ratios, final double[] sampleData, boolean useElevated)
    {
        int[] result = checkPermittedSampleCounts(sampleId, ratios, sampleData, useElevated, false);
        return result[PI_LOW] == 0 && result[PI_HIGH] == 0;
    }

    private static int PI_COUNT = 0;
    private static int PI_LOW = 1;
    private static int PI_HIGH = 2;

    private int[] checkPermittedSampleCounts(int sampleId, final double[] ratios, final double[] sampleData, boolean useElevated, boolean verbose)
    {
        double sampleTotal = sumVector(sampleData);

        final double[][] prData = useElevated ? mPermittedElevRange.getData() : mPermittedBgRange.getData();
        final double[][] countData = useElevated ? mElevatedCounts.getData() : mBackgroundCounts.getData();

        int[] result = new int[3];

        for(int i = 0; i < sampleData.length; ++i)
        {
            if(sampleData[i] == 0)
                continue;

            if(prData[i][sampleId] == 0)
                continue;

            ++result[PI_COUNT];

            double bucketValue = ratios[i] * sampleTotal;
            double rangeHigh = countData[i][sampleId] + prData[i][sampleId];
            double rangeLow = countData[i][sampleId] - prData[i][sampleId];

            if(verbose && (bucketValue > rangeHigh || bucketValue < rangeLow))
            {
                LOGGER.debug("sample({}) value({}) vs range({} -> {})", sampleId, round(bucketValue), rangeLow, rangeHigh);
            }

            if(bucketValue > rangeHigh)
                ++result[PI_HIGH];
            else if(bucketValue < rangeLow)
                ++result[PI_LOW];
        }

        // double llProb = calcLogLikelihood(sampleData, impliedCounts, false);

        return result;
    }


}
