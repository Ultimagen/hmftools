package com.hartwig.hmftools.cup.liftover;

import static com.hartwig.hmftools.common.utils.config.ConfigUtils.addLoggingOptions;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.addSampleIdFile;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.loadSampleIdsFile;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.addOutputOptions;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.parseOutputDir;
import static com.hartwig.hmftools.common.utils.TaskExecutor.addThreadOptions;
import static com.hartwig.hmftools.common.utils.TaskExecutor.parseThreads;

import com.hartwig.hmftools.common.genome.refgenome.GenomeLiftoverCache;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class LiftoverConfig
{
    public final String OutputDir;
    public final String SampleVcfDir;
    public final int Threads;
    public final boolean ApplyFilters;
    public final boolean KeepExisting;

    public static final String SAMPLE = "sample";
    public static final String SAMPLE_VCF_DIR = "sample_vcf_dir";
    public static final String APPLY_FILTERS = "apply_filters";
    public static final String KEEP_EXISTING = "keep_existing";

    public LiftoverConfig(final CommandLine cmd)
    {
        OutputDir = parseOutputDir(cmd);
        SampleVcfDir = cmd.getOptionValue(SAMPLE_VCF_DIR);
        Threads = parseThreads(cmd);
        ApplyFilters = cmd.hasOption(APPLY_FILTERS);
        KeepExisting = cmd.hasOption(KEEP_EXISTING);
    }

    public static void addOptions(final Options options)
    {
        addSampleIdFile(options);
        addOutputOptions(options);
        addThreadOptions(options);
        addLoggingOptions(options);
        GenomeLiftoverCache.addConfig(options);

        options.addOption(SAMPLE, true, "Sample ID");
        options.addOption(SAMPLE_VCF_DIR, true, "Path to sample VCF(s)");
        options.addOption(APPLY_FILTERS, false, "Only convert and write variants used by Cuppa's somatic classifier");
        options.addOption(KEEP_EXISTING, false, "Do not overwrite existing output sample files");
    }

}
