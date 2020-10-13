package com.hartwig.hmftools.cup.rna;

import static java.lang.Math.log;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createFieldsIndexMap;
import static com.hartwig.hmftools.cup.SampleAnalyserConfig.CUP_LOGGER;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.sigs.SigMatrix;
import com.hartwig.hmftools.cup.common.SampleDataCache;
import com.hartwig.hmftools.cup.ref.RefDataConfig;

public class RefRnaExpression
{
    private final RefDataConfig mConfig;
    private final SampleDataCache mSampleDataCache;

    private SigMatrix mGeneCancerExpressionData;
    private SigMatrix mGeneSampleExpressionData;
    private final List<String> mGeneIds;
    private final List<String> mGeneNames;
    private final List<String> mSampleIds;
    private final List<String> mCancerTypes;

    public RefRnaExpression(final RefDataConfig config, final SampleDataCache sampleDataCache)
    {
        mConfig = config;
        mSampleDataCache = sampleDataCache;

        mGeneCancerExpressionData = null;
        mGeneSampleExpressionData = null;
        mGeneIds = Lists.newArrayList();
        mGeneNames = Lists.newArrayList();
        mSampleIds = Lists.newArrayList();
        mCancerTypes = Lists.newArrayList();
    }

    public void buildRefDataSets()
    {
        if(mConfig.RefRnaGeneExpFile.isEmpty())
            return;

        CUP_LOGGER.debug("loading RNA gene expression data");

        loadRefRnaGeneExpression(mConfig.RefRnaGeneExpFile);

        if(mGeneSampleExpressionData == null || mGeneCancerExpressionData == null)
        {
            CUP_LOGGER.warn("RNA gene expression data load failed");
            return;
        }

        CUP_LOGGER.debug("writing RNA gene expression reference data");

        writeMatrixData(mGeneCancerExpressionData, mCancerTypes, "cup_ref_ct_rna_gene_exp.csv");
        writeMatrixData(mGeneSampleExpressionData, mSampleIds, "cup_ref_rna_gene_exp.csv");
    }

    private int getCancerTypeIndex(final String cancerType)
    {
        int index = 0;
        for(; index < mCancerTypes.size(); ++index)
        {
            if(mCancerTypes.get(index).equals(cancerType))
                return index;
        }

        mCancerTypes.add(index, cancerType);
        return index;
    }

    private void loadRefRnaGeneExpression(final String filename)
    {
        try
        {
            BufferedReader fileReader = new BufferedReader(new FileReader(filename));

            String line = fileReader.readLine();

            // SampleId,CancerType,GeneId,GeneName,TPM
            final Map<String,Integer> fieldsIndexMap = createFieldsIndexMap(line, ",");

            int sampleIdCol = fieldsIndexMap.get("SampleId");
            int geneIdCol = fieldsIndexMap.get("GeneId");
            int geneNameCol = fieldsIndexMap.get("GeneName");
            int tpmCol = fieldsIndexMap.get("TPM");

            line = fileReader.readLine(); // skip header

            int sampleIndex = 0;
            int cancerTypeIndex = 0;
            int geneIndex = 0;
            String currentSample = "";
            String cancerType = "";
            final List<Double> tpmList = Lists.newArrayList(); // only for the initial sample
            long manualLookupCount = 0;

            double[][] sampleMatrixData = null;
            double[][] cancerMatrixData = null;

            while (line != null)
            {
                final String[] items = line.split(",", -1);
                String sampleId = items[sampleIdCol];

                if(!currentSample.equals(sampleId))
                {
                    if(!mSampleDataCache.SampleIds.contains(sampleId))
                    {
                        line = fileReader.readLine();
                        continue;
                    }

                    if(!currentSample.isEmpty())
                        ++sampleIndex;

                    currentSample = sampleId;
                    cancerType = mSampleDataCache.RefSampleCancerTypeMap.get(sampleId);
                    cancerTypeIndex = getCancerTypeIndex(cancerType);
                    mSampleIds.add(sampleId);
                    geneIndex = 0;

                    if(sampleIndex == 1)
                    {
                        // build the matrix that all genes have been seen
                        mGeneSampleExpressionData = new SigMatrix(mGeneIds.size(), mSampleDataCache.SampleIds.size());
                        sampleMatrixData = mGeneSampleExpressionData.getData();

                        mGeneCancerExpressionData = new SigMatrix(mGeneIds.size(), mSampleDataCache.RefCancerSampleData.size());
                        cancerMatrixData = mGeneCancerExpressionData.getData();

                        // add in the first sample's data
                        for(int i = 0; i < tpmList.size(); ++i)
                        {
                            sampleMatrixData[i][0] = tpmList.get(i);
                            cancerMatrixData[i][0] = tpmList.get(i);
                        }

                        tpmList.clear();
                    }

                    if(sampleIndex > 0 && (sampleIndex % 100) == 0)
                    {
                        CUP_LOGGER.info("processed {} RNA samples", sampleIndex);
                    }
                }

                String geneId = items[geneIdCol];
                String geneName = items[geneNameCol];

                double tpm = Double.parseDouble(items[tpmCol]);
                double scaledTpm = scaleTpm(tpm);

                if(sampleIndex == 0)
                {
                    mGeneIds.add(geneId);
                    mGeneNames.add(geneName);

                    tpmList.add(scaledTpm);
                }
                else
                {
                    if(!mGeneIds.get(geneIndex).equals(geneId))
                    {
                        ++manualLookupCount;

                        // locate manually
                        boolean found = false;
                        for(geneIndex = 0; geneIndex < mGeneIds.size(); ++geneIndex)
                        {
                            if(mGeneIds.get(geneIndex).equals(geneId))
                            {
                                found = true;
                                break;
                            }
                        }

                        if(!found)
                        {
                            CUP_LOGGER.error("gene({}:{}) not present in initial samples", geneId, geneName);
                            return;
                        }
                    }

                    sampleMatrixData[geneIndex][sampleIndex] = scaledTpm;
                    cancerMatrixData[geneIndex][cancerTypeIndex] += scaledTpm;
                    ++geneIndex;
                }

                line = fileReader.readLine();
            }

            if(manualLookupCount > 0)
            {
                CUP_LOGGER.info("manual geneId lookup({})", manualLookupCount);
            }
        }
        catch (IOException e)
        {
            CUP_LOGGER.error("failed to read RNA gene expression data file({}): {}", filename, e.toString());
        }
    }

    private double scaleTpm(double tpm)
    {
        if(tpm <= 1)
            return 0;

        return log(tpm);
    }

    private void writeMatrixData(final SigMatrix tpmMatrix, final List<String> headers, final String fileId)
    {
        try
        {
            final String filename = mConfig.OutputDir + fileId;
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("GeneId,GeneName");

            for(final String header : headers)
            {
                writer.write(String.format(",%s", header));
            }

            writer.newLine();

            final double[][] matrixData = tpmMatrix.getData();

            for(int i = 0; i < tpmMatrix.Rows; ++i)
            {
                writer.write(String.format("%s,%s", mGeneIds.get(i), mGeneNames.get(i)));

                for(int j = 0; j < tpmMatrix.Cols; ++j)
                {
                    writer.write(String.format(",%.2f", matrixData[i][j]));
                }

                writer.newLine();
            }

            closeBufferedWriter(writer);
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write ref RNA gene expression output: {}", e.toString());
        }
    }
}
