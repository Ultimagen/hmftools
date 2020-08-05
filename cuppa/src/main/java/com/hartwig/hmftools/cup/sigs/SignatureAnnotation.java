package com.hartwig.hmftools.cup.sigs;

import static java.lang.Math.pow;

import static com.hartwig.hmftools.common.sigs.VectorUtils.sumVector;
import static com.hartwig.hmftools.common.utils.Strings.appendStr;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.cup.SampleAnalyserConfig.CUP_LOGGER;
import static com.hartwig.hmftools.cup.common.CupConstants.SNV_CSS_THRESHOLD;
import static com.hartwig.hmftools.cup.common.CupConstants.SNV_SIG_MIN_COUNT;
import static com.hartwig.hmftools.cup.common.CupConstants.SNV_SIG_MIN_PERCENT;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.sigs.DataUtils;
import com.hartwig.hmftools.common.sigs.LeastSquaresFit;
import com.hartwig.hmftools.common.sigs.SigMatrix;
import com.hartwig.hmftools.common.utils.GenericDataCollection;
import com.hartwig.hmftools.common.utils.GenericDataLoader;
import com.hartwig.hmftools.cup.SampleAnalyserConfig;
import com.hartwig.hmftools.cup.common.SampleData;
import com.hartwig.hmftools.cup.common.SampleDataCache;
import com.hartwig.hmftools.sig_analyser.common.CosineSim;

public class SignatureAnnotation
{
    private final SampleAnalyserConfig mConfig;

    private SigMatrix mRefSampleCounts;
    private final List<String> mRefSampleNames;

    private final SampleDataCache mSampleDataCache;

    private SigMatrix mSampleCounts;
    private final Map<String,Integer> mSampleCountsIndex;

    private SigMatrix mSnvSignatures;
    private final List<String> mSnvSigNames;
    private LeastSquaresFit mLeastSquaresFit;

    private BufferedWriter mCssPairsWriter;
    private BufferedWriter mSampleDataWriter;

    public SignatureAnnotation(final SampleAnalyserConfig config, final SampleDataCache sampleDataCache)
    {
        mConfig = config;

        mSampleCounts = null;
        mRefSampleCounts = null;
        mRefSampleNames = Lists.newArrayList();
        mSampleDataCache = sampleDataCache;

        mSnvSignatures = null;
        mLeastSquaresFit = null;
        mSnvSigNames = Lists.newArrayList();
        mSampleCountsIndex = Maps.newHashMap();

        mSampleDataWriter = null;
        mCssPairsWriter = null;

        loadSnvSignatures();
        loadRefSampleCounts();
        loadSampleCounts();
        initialiseOutputFile();
    }

    private boolean loadRefSampleCounts()
    {
        final GenericDataCollection collection = GenericDataLoader.loadFile(mConfig.RefSnvCountsFile);

        mRefSampleNames.addAll(collection.getFieldNames());
        mRefSampleCounts = DataUtils.createMatrixFromListData(collection.getData());
        mRefSampleCounts.cacheTranspose();

        return true;
    }
    private boolean loadSampleCounts()
    {
        final GenericDataCollection collection = GenericDataLoader.loadFile(mConfig.SampleSnvCountsFile);

        for(int s = 0; s < collection.getFieldNames().size(); ++s)
        {
            final String sampleId = collection.getFieldNames().get(s);
            mSampleCountsIndex.put(sampleId, s);
        }

        mSampleCounts = DataUtils.createMatrixFromListData(collection.getData());
        mSampleCounts.cacheTranspose();

        return true;
    }

    public void processCohort()
    {
        for(SampleData sample : mSampleDataCache.SampleDataList)
        {
            processSample(sample);
        }

        closeBufferedWriter(mSampleDataWriter);
    }

    public void processSample(SampleData sample)
    {
        Integer sampleCountsIndex = mSampleCountsIndex.get(sample.Id);

        if(sampleCountsIndex == null)
        {
            CUP_LOGGER.error("sample({}) has no SNV data", sample.Id);
            return;
        }

        final double[] sampleCounts = mSampleCounts.getCol(sampleCountsIndex);

        sample.setSnvCount((int)sumVector(sampleCounts));

        int refSampleCount = mRefSampleCounts.Cols;

        for(int s = 0; s < refSampleCount; ++s)
        {
            final String refSampleId = mRefSampleNames.get(s);

            if(refSampleId.equals(sample.Id))
                continue;

            final double[] otherSampleCounts = mRefSampleCounts.getCol(s);

            double css = CosineSim.calcCSS(sampleCounts, otherSampleCounts);

            if(css < SNV_CSS_THRESHOLD)
                continue;

            final String refCancerType = mSampleDataCache.RefSampleCancerTypeMap.get(refSampleId);

            int cancerTypeCount = mSampleDataCache.RefCancerSampleData.get(refCancerType).size();
            double weightedCss = pow((css - SNV_CSS_THRESHOLD) * 100, 2) / cancerTypeCount;

            sample.addSampleCss(refCancerType, weightedCss);

            // writeSampleCssPairData(mSampleIds.get(s1), mSampleIds.get(s2), css, mSampleTotals[s1], mSampleTotals[s2]);
        }

        fitSnvSignatures(sample, sampleCountsIndex);

        writeSampleData(sample);
    }

    private void fitSnvSignatures(final SampleData sampleData, int sampleCountsIndex)
    {
        if(mSnvSignatures == null)
            return;

        final double[] sampleCounts = mSampleCounts.getCol(sampleCountsIndex);

        mLeastSquaresFit.initialise(mSnvSignatures.getData(), sampleCounts);
        mLeastSquaresFit.solve();

        final double[] sigAllocs = mLeastSquaresFit.getContribs();

        double sampleTotal = sampleData.getSnvCount();

        for(int sig = 0; sig < sigAllocs.length; ++sig)
        {
            double sigAlloc = sigAllocs[sig];
            double allocPerc = sigAlloc / sampleTotal;

            if(sigAlloc >= SNV_SIG_MIN_COUNT && allocPerc >= SNV_SIG_MIN_PERCENT)
            {
                sampleData.getSnvSigAllocations().put(mSnvSigNames.get(sig), allocPerc);
            }
        }
    }

    public String getHeader()
    {
        return "SnvCount,TotalWeightedCss,TopMatchCancerType,TopMatchCancerCss,SigData";
    }

    public String getSampleOutput(final SampleData sampleData)
    {
        String sampleDataStr = String.format("%d", sampleData.getSnvCount());

        double totalWeightedCss = sampleData.getTotalWeightedCss();

        String sigDataStr = "";
        for(Map.Entry<String,Double> entry : sampleData.getSnvSigAllocations().entrySet())
        {
            sigDataStr = appendStr(sigDataStr, String.format("%s=%.2f", entry.getKey(), entry.getValue()), ';');
        }

        final Map<String,Double> cssDataMap = sampleData.getCancerCssTotals();

        String cssDataStr;
        if(cssDataMap.isEmpty())
        {
            cssDataStr = "0,NONE,0";
        }
        else
        {
            String topType = "";
            double topCss = 0;

            for(Map.Entry<String,Double> cancerCssData : cssDataMap.entrySet())
            {
                if(cancerCssData.getValue() > topCss)
                {
                    topCss = cancerCssData.getValue();
                    topType = cancerCssData.getKey();
                }
            }

            cssDataStr = String.format("%.4f,%s,%.4f", totalWeightedCss, topType, topCss);
        }

        return String.format("%s,%s,%s", sampleDataStr,cssDataStr,sigDataStr);
    }

    private void initialiseOutputFile()
    {
        try
        {
            mSampleDataWriter = createBufferedWriter(mConfig.formOutputFilename("SNV"), false);

            mSampleDataWriter.write("SampleId,SnvCount,TotalWeightedCss,MatchedCancerType,MatchedCancerCss,SigData");
            mSampleDataWriter.newLine();
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write SNV sample CSS output: {}", e.toString());
        }
    }

    private void writeSampleData(final SampleData sampleData)
    {
        try
        {
            final String sampleStr = String.format("%s,%d", sampleData.Id, sampleData.getSnvCount());

            double totalWeightedCss = sampleData.getTotalWeightedCss();

            String sigDataStr = "";
            for(Map.Entry<String,Double> entry : sampleData.getSnvSigAllocations().entrySet())
            {
                sigDataStr = appendStr(sigDataStr, String.format("%s=%.2f", entry.getKey(), entry.getValue()), ';');
            }

            final Map<String,Double> cssDataMap = sampleData.getCancerCssTotals();

            if(cssDataMap.isEmpty())
            {
                mSampleDataWriter.write(String.format("%s,0,NONE,0,%s", sampleStr, sigDataStr));
                mSampleDataWriter.newLine();
                return;
            }

            for(Map.Entry<String,Double> cancerCssData : cssDataMap.entrySet())
            {
                mSampleDataWriter.write(String.format("%s,%.4f,%s,%.4f,%s",
                        sampleStr, totalWeightedCss, cancerCssData.getKey(), cancerCssData.getValue(), sigDataStr));
                mSampleDataWriter.newLine();
            }
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write SNV sample CSS output: {}", e.toString());
        }
    }

    private void loadSnvSignatures()
    {
        if(mConfig.RefSnvSignaturesFile.isEmpty())
            return;

        // cosmic_sig_subset.csv
        final GenericDataCollection sigsCollection = GenericDataLoader.loadFile(mConfig.RefSnvSignaturesFile);
        mSnvSigNames.addAll(sigsCollection.getFieldNames());
        mSnvSignatures = DataUtils.createMatrixFromListData(sigsCollection.getData());
        mLeastSquaresFit = new LeastSquaresFit(mSnvSignatures.Rows, mSnvSignatures.Cols);
    }

    public void close() { closeBufferedWriter(mSampleDataWriter); }

    private void writeSampleCssPairData(final String sampleId1, final String sampleId2, double css, int sampleTotal1, int sampleTotal2)
    {
        try
        {
            mCssPairsWriter.write(String.format("%s,%s,%.6f,%d,%d", sampleId1, sampleId2, css, sampleTotal1, sampleTotal2));
            mCssPairsWriter.newLine();
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write SNV sample CSS output: {}", e.toString());
        }
    }
}
