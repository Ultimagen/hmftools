package com.hartwig.hmftools.neo.bind;

import static java.lang.Math.round;

import static com.hartwig.hmftools.common.utils.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.createFieldsIndexMap;
import static com.hartwig.hmftools.neo.NeoCommon.NE_LOGGER;
import static com.hartwig.hmftools.neo.bind.BindCommon.DELIM;
import static com.hartwig.hmftools.neo.bind.BindCommon.FLD_DOWN_FLANK;
import static com.hartwig.hmftools.neo.bind.BindCommon.FLD_PEPTIDE;
import static com.hartwig.hmftools.neo.bind.BindCommon.FLD_UP_FLANK;
import static com.hartwig.hmftools.neo.bind.BindConstants.INVALID_SCORE;
import static com.hartwig.hmftools.neo.bind.BindConstants.PAN_PEPTIDE_LENGTH;
import static com.hartwig.hmftools.neo.bind.BindConstants.PAN_PEPTIDE_MAX_LENGTH;
import static com.hartwig.hmftools.neo.bind.BinderConfig.FILE_ID_LIKELIHOOD_RAND_DIST;
import static com.hartwig.hmftools.neo.bind.BinderConfig.FILE_ID_RAND_DIST;
import static com.hartwig.hmftools.neo.bind.BinderConfig.formFilename;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.utils.VectorUtils;
import com.hartwig.hmftools.common.utils.Doubles;

public class RandomPeptideDistribution
{
    private final RandomPeptideConfig mConfig;
    private boolean mDataLoaded;

    private final Map<Integer,List<RandomPeptideData>> mRandomPeptideMap; // by length and with flanking data

    private final Map<String,Map<Integer,List<ScoreDistributionData>>> mAlleleScoresMap; // allele to peptide length to distribution
    private final Map<String,List<ScoreDistributionData>> mAlleleLikelihoodsMap; // allele to distribution of likelihoods
    private final List<double[]> mDiscreteScoreData;

    private static final int SCORE_SIZE = 0;
    private static final int SCORE_BRACKET = 1;

    public RandomPeptideDistribution(final RandomPeptideConfig config)
    {
        mConfig = config;

        mAlleleScoresMap = Maps.newHashMap();
        mAlleleLikelihoodsMap = Maps.newHashMap();
        mRandomPeptideMap = Maps.newHashMap();
        mDataLoaded = false;

        mDiscreteScoreData = Lists.newArrayList();
        mDiscreteScoreData.add(new double[] {0.0001, 0.01});
        mDiscreteScoreData.add(new double[] {0.001, 0.1});
        mDiscreteScoreData.add(new double[] {0.01, 0.25});
        mDiscreteScoreData.add(new double[] {0.05, 1.0});
    }

    public boolean loadData()
    {
        mDataLoaded = loadDistribution() && loadLikelihoodDistribution();
        return mDataLoaded;
    }

    public boolean hasData() { return mDataLoaded; }

    public Map<String,Map<Integer,List<ScoreDistributionData>>> getAlleleScoresMap() { return mAlleleScoresMap; }

    public double getScoreRank(final String allele, final int peptideLength, double score)
    {
        Map<Integer,List<ScoreDistributionData>> peptideLengthMap = mAlleleScoresMap.get(allele);

        if(peptideLengthMap == null)
            return INVALID_SCORE;

        List<ScoreDistributionData> scores = peptideLengthMap.get(peptideLength);

        if(scores == null || scores.size() < 2)
            return INVALID_SCORE;

        return getRank(scores, score);
    }

    public double getLikelihoodRank(final String allele, double likelihood)
    {
        List<ScoreDistributionData> likelihoodDist = mAlleleLikelihoodsMap.get(allele);

        if(likelihoodDist == null || likelihoodDist.size() < 2)
            return INVALID_SCORE;

        return getRank(likelihoodDist, likelihood);
    }

    private static double getRank(final List<ScoreDistributionData> distribution, double score)
    {
        boolean isAscending = distribution.get(0).Score < distribution.get(1).Score;

        if((isAscending && score < distribution.get(0).Score) || (!isAscending && score > distribution.get(0).Score))
            return 0; // zero-th percentile if the score is better than any in the random distribution

        for(int i = 0; i < distribution.size(); ++i)
        {
            ScoreDistributionData scoreData = distribution.get(i);

            if(Doubles.equal(score, scoreData.Score))
                return scoreData.ScoreBucket;

            ScoreDistributionData nextScoreData = i < distribution.size() - 1 ? distribution.get(i + 1) : null;

            if(nextScoreData != null && Doubles.equal(score, nextScoreData.Score))
                return nextScoreData.ScoreBucket;

            if((isAscending && score > scoreData.Score) || (!isAscending && score < scoreData.Score))
            {
                if(nextScoreData == null)
                    break;

                if((isAscending && score < nextScoreData.Score) || (!isAscending && score > nextScoreData.Score))
                {
                    // interpolate between the distribution to set the rank
                    if(isAscending)
                    {
                        double upperPerc = (score - scoreData.Score) / (nextScoreData.Score - scoreData.Score);
                        return upperPerc * nextScoreData.ScoreBucket + (1 - upperPerc) * scoreData.ScoreBucket;
                    }
                    else
                    {
                        double upperPerc = (score - nextScoreData.Score) / (scoreData.Score - nextScoreData.Score);
                        return upperPerc * scoreData.ScoreBucket + (1 - upperPerc) * nextScoreData.ScoreBucket;
                    }
                }
            }
        }

        return 1;
    }

    private void loadRandomPeptides(final Set<Integer> peptideLengths)
    {
        if(!mRandomPeptideMap.isEmpty())
            return;

        if(mConfig.RandomPeptidesFile == null)
        {
            NE_LOGGER.error("missing random peptides file");
        }

        try
        {
            List<String> lines = Files.readAllLines(new File(mConfig.RandomPeptidesFile).toPath());

            final Map<String,Integer> fieldsIndexMap = createFieldsIndexMap(lines.get(0), DELIM);
            lines.remove(0);

            int peptideIndex = fieldsIndexMap.get(FLD_PEPTIDE);
            Integer upFlankIndex = fieldsIndexMap.get(FLD_UP_FLANK);
            Integer downFlankIndex = fieldsIndexMap.get(FLD_DOWN_FLANK);
            if(upFlankIndex != null && downFlankIndex != null)
            {
                List<RandomPeptideData> peptideList = null;
                int curentLength = 0;

                for(String line : lines)
                {
                    String[] values = line.split(DELIM, -1);
                    String peptide = values[peptideIndex];

                    int peptideLength = peptide.length();
                    if(peptideLength != curentLength)
                    {
                        curentLength = peptideLength;
                        peptideList = Lists.newArrayList();
                        mRandomPeptideMap.put(peptideLength, peptideList);
                    }

                    peptideList.add(new RandomPeptideData(peptide, values[upFlankIndex], values[downFlankIndex]));
                }
            }
            else
            {
                for(Integer peptideLength : peptideLengths)
                {
                    List<RandomPeptideData> peptideList = Lists.newArrayList();
                    mRandomPeptideMap.put(peptideLength, peptideList);

                    for(String line : lines)
                    {
                        String[] values = line.split(DELIM, -1);
                        String peptide = values[peptideIndex].substring(0, peptideLength);
                        peptideList.add(new RandomPeptideData(peptide, "", ""));
                    }
                }
            }

            NE_LOGGER.info("loaded {} random peptides", lines.size());
        }
        catch(IOException e)
        {
            NE_LOGGER.error("failed to load random peptide file: {}", e.toString());
        }
    }

    public void buildDistribution(final Map<String,Map<Integer,BindScoreMatrix>> alleleBindMatrixMap, final FlankScores flankScores)
    {
        Set<Integer> peptideLengths = Sets.newHashSet();

        for(Map<Integer,BindScoreMatrix> pepLenEntry : alleleBindMatrixMap.values())
        {
            peptideLengths.addAll(pepLenEntry.keySet());
            break;
        }

        loadRandomPeptides(peptideLengths);

        if(mRandomPeptideMap.isEmpty())
            return;

        // score each against each allele and build up a percentiles for each
        int alleleCount = 0;

        for(Map.Entry<String,Map<Integer,BindScoreMatrix>> alleleEntry : alleleBindMatrixMap.entrySet())
        {
            final String allele = alleleEntry.getKey();

            if(!mConfig.RequiredOutputAlleles.isEmpty() && !mConfig.RequiredOutputAlleles.contains(allele))
                continue;

            NE_LOGGER.debug("building distribution for allele({})", allele);

            final Map<Integer,BindScoreMatrix> peptideLengthMatrixMap = alleleEntry.getValue();

            Map<Integer,List<ScoreDistributionData>> peptideLengthMap = Maps.newHashMap();
            mAlleleScoresMap.put(allele, peptideLengthMap);

            for(BindScoreMatrix matrix : peptideLengthMatrixMap.values())
            {
                List<RandomPeptideData> randomPeptides = mRandomPeptideMap.get(matrix.PeptideLength);

                if(randomPeptides == null || randomPeptides.isEmpty())
                {
                    NE_LOGGER.error("missing random peptide data for length({})", matrix.PeptideLength);
                    return;
                }

                List<Double> peptideScores = Lists.newArrayListWithExpectedSize(randomPeptides.size());

                int count = 0;

                for(RandomPeptideData peptideData : randomPeptides)
                {
                    double score = BindScorer.calcScore(matrix, flankScores, peptideData.Peptide, peptideData.UpFlank, peptideData.DownFlank);

                    VectorUtils.optimisedAdd(peptideScores, score, false);

                    ++count;

                    if(count > 0 && (count % 500000) == 0)
                    {
                        NE_LOGGER.debug("added {} sorted random peptide scores", count);
                    }
                }

                List<ScoreDistributionData> scoresDistributions = generateDistribution(matrix.Allele, matrix.PeptideLength, peptideScores);
                peptideLengthMap.put(matrix.PeptideLength, scoresDistributions);
            }

            ++alleleCount;

            if(alleleCount > 0 && (alleleCount % 10) == 0)
            {
                NE_LOGGER.info("generated distributions for {} alleles", alleleCount);
            }
        }

        if(mConfig.WriteRandomDistribution)
            writeDistribution();
    }

    public void buildLikelihoodDistribution(
            final Map<String,Map<Integer,BindScoreMatrix>> alleleBindMatrixMap, final FlankScores flankScores,
            final BindingLikelihood bindingLikelihood)
    {
        if(mRandomPeptideMap.isEmpty())
            return;

        int alleleCount = 0;

        for(Map.Entry<String,Map<Integer,BindScoreMatrix>> alleleEntry : alleleBindMatrixMap.entrySet())
        {
            final String allele = alleleEntry.getKey();

            if(!mConfig.RequiredOutputAlleles.isEmpty() && !mConfig.RequiredOutputAlleles.contains(allele))
                continue;

            NE_LOGGER.debug("building likelihood distribution for allele({})", allele);

            List<Double> likelihoodScores = Lists.newArrayList();
            final Map<Integer,BindScoreMatrix> peptideLengthMatrixMap = alleleEntry.getValue();

            for(BindScoreMatrix matrix : peptideLengthMatrixMap.values())
            {
                // for now hard-code exclusion of length 12 since other tools are 8-11
                if(matrix.PeptideLength > PAN_PEPTIDE_MAX_LENGTH)
                    continue;

                int count = 0;

                List<RandomPeptideData> randomPeptides = mRandomPeptideMap.get(matrix.PeptideLength);

                if(randomPeptides == null || randomPeptides.isEmpty())
                    return;

                for(RandomPeptideData peptideData : randomPeptides)
                {
                    double score = BindScorer.calcScore(matrix, flankScores, peptideData.Peptide, peptideData.UpFlank, peptideData.DownFlank);
                    double rank = getScoreRank(allele, matrix.PeptideLength, score);
                    double likelihood = bindingLikelihood.getBindingLikelihood(allele, peptideData.Peptide, rank);

                    VectorUtils.optimisedAdd(likelihoodScores, likelihood, false);

                    ++count;

                    if(count > 0 && (count % 500000) == 0)
                    {
                        NE_LOGGER.debug("added {} sorted random peptide likelihood scores", count);
                    }
                }
            }

            List<ScoreDistributionData> likelihoodDistributions = generateDistribution(allele, PAN_PEPTIDE_LENGTH, likelihoodScores);
            mAlleleLikelihoodsMap.put(allele, likelihoodDistributions);

            ++alleleCount;

            if(alleleCount > 0 && (alleleCount % 10) == 0)
            {
                NE_LOGGER.info("generated likelihood distributions for {} alleles", alleleCount);
            }
        }

        writeLikelihoodDistribution();
    }

    public List<ScoreDistributionData> generateDistribution(final String allele, final int peptideLength, final List<Double> peptideScores)
    {
        // write the distribution as 0.0001 up to 0.01, 0.001 up to 0.01, then 0.01 up to 100%
        int totalScores = peptideScores.size();
        int discreteIndex = 0;
        double currentSize = mDiscreteScoreData.get(discreteIndex)[SCORE_SIZE];
        double currentBracket = mDiscreteScoreData.get(discreteIndex)[SCORE_BRACKET];
        int requiredScores = (int) round(totalScores * currentSize);

        double scoreTotal = 0;
        int currentScoreCount = 0;
        double currentSizeTotal = 0;
        int cumulativeScores = 0;

        List<ScoreDistributionData> scoresDistributions = Lists.newArrayList();

        for(Double score : peptideScores)
        {
            scoreTotal += score;
            ++currentScoreCount;
            ++cumulativeScores;

            if(currentScoreCount >= requiredScores)
            {
                double avgScore = scoreTotal / currentScoreCount;

                scoresDistributions.add(new ScoreDistributionData(
                        allele, peptideLength, currentSizeTotal, avgScore, currentScoreCount, cumulativeScores));

                scoreTotal = 0;
                currentScoreCount = 0;
                currentSizeTotal += currentSize;

                if(Doubles.equal(currentSizeTotal, currentBracket))
                {
                    ++discreteIndex;

                    if(discreteIndex < mDiscreteScoreData.size())
                    {
                        currentSize = mDiscreteScoreData.get(discreteIndex)[SCORE_SIZE];
                        currentBracket = mDiscreteScoreData.get(discreteIndex)[SCORE_BRACKET];
                        requiredScores = (int) round(totalScores * currentSize);
                    }
                }
            }
        }

        return scoresDistributions;
    }

    public static BufferedWriter initialiseWriter(final String filename)
    {
        try
        {
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("Allele,PeptideLength,ScoreBucket,Score,BucketCount,CumulativeCount");
            // writer.write(",CurrentScores,CurrentSize,CurrentBracket,CurrentSizeTotal");
            writer.newLine();
            return writer;
        }
        catch(IOException e)
        {
            NE_LOGGER.error("failed to write random peptide file: {}", e.toString());
            return null;
        }
    }

    public static void writeDistribution(final BufferedWriter writer, final List<ScoreDistributionData> scoreDistributionData)
    {
        try
        {
            for(ScoreDistributionData scoreDist : scoreDistributionData)
            {
                writer.write(String.format("%s,%d,%f,%.4f,%d,%d",
                        scoreDist.Allele, scoreDist.PeptideLength, scoreDist.ScoreBucket, scoreDist.Score,
                        scoreDist.BucketCount, scoreDist.CumulativeCount));

                writer.newLine();
            }
        }
        catch(IOException e)
        {
            NE_LOGGER.error("failed to write random peptide file: {}", e.toString());
            return;
        }
    }

    private void writeDistribution()
    {
        NE_LOGGER.info("writing random peptide scoring distribution for {} alleles",
                !mConfig.RequiredOutputAlleles.isEmpty() ? mConfig.RequiredOutputAlleles.size() : "all");

        final String distFilename = formFilename(FILE_ID_RAND_DIST, mConfig.OutputDir, mConfig.OutputId);

        try
        {
            BufferedWriter writer = initialiseWriter(distFilename);

            for(Map.Entry<String,Map<Integer,List<ScoreDistributionData>>> alleleEntry : mAlleleScoresMap.entrySet())
            {
                final Map<Integer,List<ScoreDistributionData>> pepLenDistMap = alleleEntry.getValue();

                for(List<ScoreDistributionData> pepLenScoreDist : pepLenDistMap.values())
                {
                    writeDistribution(writer, pepLenScoreDist);
                }
            }

            writer.close();
        }
        catch(IOException e)
        {
            NE_LOGGER.error("failed to write random peptide file: {}", e.toString());
        }
    }

    private void writeLikelihoodDistribution()
    {
        NE_LOGGER.info("writing random peptide likelihood distribution for {} alleles",
                !mConfig.RequiredOutputAlleles.isEmpty() ? mConfig.RequiredOutputAlleles.size() : "all");

        final String filename = formFilename(FILE_ID_LIKELIHOOD_RAND_DIST, mConfig.OutputDir, mConfig.OutputId);

        try
        {
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("Allele,Bucket,Likelihood");
            writer.newLine();

            for(Map.Entry<String,List<ScoreDistributionData>> alleleEntry : mAlleleLikelihoodsMap.entrySet())
            {
                for(ScoreDistributionData scoreDist : alleleEntry.getValue())
                {
                    writer.write(String.format("%s,%f,%.8f",
                            scoreDist.Allele, scoreDist.ScoreBucket, scoreDist.Score));

                    writer.newLine();
                }
            }

            writer.close();
        }
        catch(IOException e)
        {
            NE_LOGGER.error("failed to write random peptide likelihood file: {}", e.toString());
        }
    }

    private boolean loadDistribution()
    {
        if(mConfig.RandomPeptideDistributionFile == null)
            return false;

        try
        {
            final List<String> lines = Files.readAllLines(new File(mConfig.RandomPeptideDistributionFile).toPath());

            final Map<String,Integer> fieldsIndexMap = createFieldsIndexMap(lines.get(0), DELIM);
            lines.remove(0);

            for(String line : lines)
            {
                ScoreDistributionData data = ScoreDistributionData.fromCsv(line, fieldsIndexMap);

                Map<Integer,List<ScoreDistributionData>> peptideLengthMap = mAlleleScoresMap.get(data.Allele);

                if(peptideLengthMap == null)
                {
                    peptideLengthMap = Maps.newHashMap();
                    mAlleleScoresMap.put(data.Allele, peptideLengthMap);
                }

                List<ScoreDistributionData> dataList = peptideLengthMap.get(data.PeptideLength);

                if(dataList == null)
                {
                    dataList = Lists.newArrayList();
                    peptideLengthMap.put(data.PeptideLength, dataList);
                }

                dataList.add(data);
            }

            NE_LOGGER.info("loaded {} alleles with random peptide score distribution data", mAlleleScoresMap.size());
        }
        catch(IOException e)
        {
            NE_LOGGER.error("failed to read random distribution file: {}", e.toString());
            return false;
        }

        return true;
    }

    private boolean loadLikelihoodDistribution()
    {
        if(mConfig.RandomLikelihoodDistributionFile == null)
            return true;

        try
        {
            final List<String> lines = Files.readAllLines(new File(mConfig.RandomLikelihoodDistributionFile).toPath());

            final Map<String,Integer> fieldsIndexMap = createFieldsIndexMap(lines.get(0), DELIM);
            lines.remove(0);

            for(String line : lines)
            {
                ScoreDistributionData data = ScoreDistributionData.fromLikelihoodCsv(line, fieldsIndexMap);

                List<ScoreDistributionData> dataList = mAlleleLikelihoodsMap.get(data.Allele);

                if(dataList == null)
                {
                    dataList = Lists.newArrayList();
                    mAlleleLikelihoodsMap.put(data.Allele, dataList);
                }

                dataList.add(data);
            }

            NE_LOGGER.info("loaded {} alleles with random peptide likelihood distribution data", mAlleleScoresMap.size());
        }
        catch(IOException e)
        {
            NE_LOGGER.error("failed to read random likelihood distribution file: {}", e.toString());
            return false;
        }

        return true;
    }

    private class RandomPeptideData
    {
        public final String Peptide;
        public final String UpFlank;
        public final String DownFlank;

        public RandomPeptideData(final String peptide, final String upFlank, final String downFlank)
        {
            Peptide = peptide;
            UpFlank = upFlank;
            DownFlank = downFlank;
        }
    }
}
