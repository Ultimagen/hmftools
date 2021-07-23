package com.hartwig.hmftools.neo.bind;

import static com.hartwig.hmftools.common.neo.NeoEpitopeFile.ITEM_DELIM;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.OUTPUT_DIR;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.parseOutputDir;
import static com.hartwig.hmftools.neo.NeoCommon.LOG_DEBUG;
import static com.hartwig.hmftools.neo.NeoCommon.NE_LOGGER;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.compress.utils.Lists;

public class BinderConfig
{
    public final String TrainingDataFile;
    public final String RandomPeptidesFile;
    public final String RandomPeptideDistributionFile;
    public final String OutputDir;
    public final String OutputId;

    public final List<String> SpecificAlleles;

    public final CalcConstants Constants;
    public final boolean CalcPairs;
    public final boolean WriteFrequencyData;
    public final boolean WriteScoreMatrix;

    private static final String TRAINING_DATA_FILE = "training_data_file";
    private static final String RANDOM_PEPTIDES_FILE = "random_peptides_file";
    private static final String RANDOM_PEPTIDE_DIST_FILE = "random_peptide_dist_file";
    private static final String SPECIFIC_ALLELES = "specific_alleles";
    private static final String OUTPUT_ID = "output_id";

    private static final String MAX_AFFINITY = "max_affinity";
    private static final String BINDING_AFFINITY_LOW = "binding_affinity_low";
    private static final String BINDING_AFFINITY_HIGH = "binding_aff_high";
    private static final String APPLY_SCALED_COUNT = "apply_scaled";
    private static final String CALC_PAIRS = "calc_pairs";

    private static final String WRITE_SCORE_MATRIX = "write_score_matrix";
    private static final String WRITE_FREQ_DATA = "write_freq_data";

    public BinderConfig(final CommandLine cmd)
    {
        TrainingDataFile = cmd.getOptionValue(TRAINING_DATA_FILE);
        RandomPeptidesFile = cmd.getOptionValue(RANDOM_PEPTIDES_FILE);
        RandomPeptideDistributionFile = cmd.getOptionValue(RANDOM_PEPTIDE_DIST_FILE);

        OutputDir = parseOutputDir(cmd);
        OutputId = cmd.getOptionValue(OUTPUT_ID);

        Constants = new CalcConstants(
                Double.parseDouble(cmd.getOptionValue(MAX_AFFINITY, "50000")),
                Double.parseDouble(cmd.getOptionValue(BINDING_AFFINITY_LOW, "100")),
                Double.parseDouble(cmd.getOptionValue(BINDING_AFFINITY_HIGH, "500")),
                cmd.hasOption(APPLY_SCALED_COUNT));

        SpecificAlleles = Lists.newArrayList();

        if(cmd.hasOption(SPECIFIC_ALLELES))
        {
            Arrays.stream(cmd.getOptionValue(SPECIFIC_ALLELES).split(ITEM_DELIM, -1)).forEach(x -> SpecificAlleles.add(x));
            NE_LOGGER.info("filtering for {} alleles: {}", SpecificAlleles.size(), SpecificAlleles);
        }

        CalcPairs = cmd.hasOption(CALC_PAIRS);
        WriteScoreMatrix = cmd.hasOption(WRITE_SCORE_MATRIX);
        WriteFrequencyData = cmd.hasOption(WRITE_FREQ_DATA);
    }

    public String formFilename(final String fileId)
    {
        if(OutputId.isEmpty())
            return String.format("%sbind_%s.csv", OutputDir, fileId);
        else
            return String.format("%sbind_%s_%s.csv", OutputDir, OutputId, fileId);
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(TRAINING_DATA_FILE, true, "Training data file");
        options.addOption(RANDOM_PEPTIDES_FILE, true, "Random peptide file");
        options.addOption(RANDOM_PEPTIDE_DIST_FILE, true, "Random peptide distribution file");
        options.addOption(SPECIFIC_ALLELES, true, "List of alleles separated by ';'");
        options.addOption(BINDING_AFFINITY_HIGH, true, "Upper binding affinity threshold");
        options.addOption(BINDING_AFFINITY_LOW, true, "Lower binding affinity threshold");
        options.addOption(MAX_AFFINITY, true, "Binding affinity exponent  for score calc: 1 - log(exp,affinity)");
        options.addOption(CALC_PAIRS, false, "Calculate amino-acid pairs and their coocurrence");
        options.addOption(WRITE_SCORE_MATRIX, false, "Write computed amino-acid + position matrix data");
        options.addOption(WRITE_FREQ_DATA, false, "Write amino-acid + position frequency data");
        options.addOption(APPLY_SCALED_COUNT, false, "Calculate amino-acid pairs and their coocurrence");

        options.addOption(OUTPUT_DIR, true, "Output directory");
        options.addOption(OUTPUT_ID, true, "Output file id");
        options.addOption(LOG_DEBUG, false, "Log verbose");
    }
}