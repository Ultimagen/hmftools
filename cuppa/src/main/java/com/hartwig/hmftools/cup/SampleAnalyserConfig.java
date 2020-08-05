package com.hartwig.hmftools.cup;

import static com.hartwig.hmftools.sig_analyser.SigAnalyser.LOG_DEBUG;
import static com.hartwig.hmftools.sig_analyser.SigAnalyser.OUTPUT_DIR;
import static com.hartwig.hmftools.sig_analyser.SigAnalyser.OUTPUT_FILE_ID;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SampleAnalyserConfig
{
    // sample data, if not sourced from the database
    public final String SampleDataFile;
    public final String SampleDriversFile;
    public final String SampleTraitsFile;
    public final String SampleSnvCountsFile;

    // reference data
    public final String RefSampleDataFile;
    public final String RefSnvCountsFile;
    public final String RefSnvSignaturesFile;
    public final String RefDriverPrevFile;
    public final String RefTraitPercFile;
    public final String RefTraitRateFile;

    public final String OutputDir;
    public final String OutputFileId;

    // config string
    public static final String SPECIFIC_SAMPLE_DATA = "sample_data";
    public static final String SAMPLE_DATA_FILE = "sample_data_file";
    private static final String SAMPLE_DRIVERS_FILE = "sample_drivers_file";
    private static final String SAMPLE_TRAITS_FILE = "sample_traits_file";
    private static final String SAMPLE_SNV_COUNTS_FILE = "sample_snv_counts_file";

    public static final String REF_SAMPLE_DATA_FILE = "ref_sample_data_file";
    private static final String REF_SNV_COUNTS_FILE = "ref_snv_counts_file";
    private static final String REF_SNV_SIGNATURES = "ref_snv_signatures";
    private static final String REF_DRIVER_PREV_FILE = "ref_driver_prev_file";
    private static final String REF_TRAIT_PERC_FILE = "ref_trait_perc_file";
    private static final String REF_TRAIT_RATE_FILE = "ref_trait_rate_file";

    public static final Logger CUP_LOGGER = LogManager.getLogger(SampleAnalyserConfig.class);

    public static final String CANCER_SUBTYPE_OTHER = "Other";
    public static final String DATA_DELIM = ",";
    public static final String SUBSET_DELIM = ";";

    public SampleAnalyserConfig(final CommandLine cmd)
    {
        SampleDataFile = cmd.getOptionValue(SAMPLE_DATA_FILE, "");
        SampleTraitsFile = cmd.getOptionValue(SAMPLE_TRAITS_FILE, "");
        SampleDriversFile = cmd.getOptionValue(SAMPLE_DRIVERS_FILE, "");
        SampleSnvCountsFile = cmd.getOptionValue(SAMPLE_SNV_COUNTS_FILE, "");

        RefSampleDataFile = cmd.getOptionValue(REF_SAMPLE_DATA_FILE, "");
        RefSnvCountsFile = cmd.getOptionValue(REF_SNV_COUNTS_FILE, "");
        RefSnvSignaturesFile = cmd.getOptionValue(REF_SNV_SIGNATURES, "");
        RefDriverPrevFile = cmd.getOptionValue(REF_DRIVER_PREV_FILE, "");
        RefTraitPercFile = cmd.getOptionValue(REF_TRAIT_PERC_FILE, "");
        RefTraitRateFile = cmd.getOptionValue(REF_TRAIT_RATE_FILE, "");

        String outputDir = cmd.getOptionValue(OUTPUT_DIR);
        if (!outputDir.endsWith(File.separator))
            outputDir += File.separator;

        OutputDir = outputDir;
        OutputFileId = cmd.getOptionValue(OUTPUT_FILE_ID, "");
    }

    public boolean isValid()
    {
        return !OutputDir.isEmpty();
    }

    public String formOutputFilename(final String fileId)
    {
        String outputFile = OutputDir + "CUP";

        if(!OutputFileId.isEmpty())
            outputFile += "." + OutputFileId;

        return outputFile + "." + fileId + ".csv";
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(SPECIFIC_SAMPLE_DATA, true, "Specific sample in form 'SampleId;CancerType;CancerSubtype' (last 2 optional)");
        options.addOption(SAMPLE_DATA_FILE, true, "Sample data file");
        options.addOption(SAMPLE_SNV_COUNTS_FILE, true, "Sample SNV counts");
        options.addOption(SAMPLE_DRIVERS_FILE, true, "Sample drivers");
        options.addOption(SAMPLE_TRAITS_FILE, true, "Sample traits");

        options.addOption(REF_SAMPLE_DATA_FILE, true, "Reference sample data");
        options.addOption(REF_SNV_COUNTS_FILE, true, "Reference SNV sample counts");
        options.addOption(REF_SNV_SIGNATURES, true, "SNV signatures");
        options.addOption(REF_DRIVER_PREV_FILE, true, "Reference driver prevalence");
        options.addOption(REF_TRAIT_PERC_FILE, true, "Reference traits percentiles file");
        options.addOption(REF_TRAIT_RATE_FILE, true, "Reference traits rates file");

        options.addOption(OUTPUT_DIR, true, "Path to output files");
        options.addOption(OUTPUT_FILE_ID, true, "Output file ID");
        options.addOption(LOG_DEBUG, false, "Sets log level to Debug, off by default");
    }

}
