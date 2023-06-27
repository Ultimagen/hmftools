package com.hartwig.hmftools.markdups;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.REF_GENOME;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.addRefGenomeConfig;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.loadRefGenome;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion.V37;
import static com.hartwig.hmftools.common.samtools.BamUtils.addValidationStringencyOption;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.LOG_READ_IDS;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.LOG_READ_IDS_DESC;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.PERF_DEBUG;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.PERF_DEBUG_DESC;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.SAMPLE;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.SAMPLE_DESC;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.parseLogReadIds;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.addLoggingOptions;
import static com.hartwig.hmftools.common.utils.file.FileDelimiters.TSV_EXTENSION;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.OUTPUT_DIR;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.OUTPUT_ID;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.addOutputOptions;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.checkAddDirSeparator;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.parseOutputDir;
import static com.hartwig.hmftools.common.utils.TaskExecutor.addThreadOptions;
import static com.hartwig.hmftools.common.utils.TaskExecutor.parseThreads;
import static com.hartwig.hmftools.common.utils.sv.ChrBaseRegion.SPECIFIC_CHROMOSOMES;
import static com.hartwig.hmftools.common.utils.sv.ChrBaseRegion.SPECIFIC_REGIONS;
import static com.hartwig.hmftools.common.utils.sv.ChrBaseRegion.addSpecificChromosomesRegionsConfig;
import static com.hartwig.hmftools.common.utils.sv.ChrBaseRegion.loadSpecificChromsomesOrRegions;
import static com.hartwig.hmftools.markdups.common.Constants.DEFAULT_DUPLEX_UMI_DELIM;
import static com.hartwig.hmftools.markdups.common.Constants.DEFAULT_PARTITION_SIZE;
import static com.hartwig.hmftools.markdups.common.Constants.DEFAULT_POS_BUFFER_SIZE;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.samtools.BamUtils;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.common.utils.config.ConfigUtils;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;
import com.hartwig.hmftools.markdups.common.FilterReadsType;
import com.hartwig.hmftools.markdups.consensus.GroupIdGenerator;
import com.hartwig.hmftools.markdups.umi.UmiConfig;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import htsjdk.samtools.ValidationStringency;

public class MarkDupsConfig
{
    public final String SampleId;
    public final String BamFile;
    public final String RefGenomeFile;
    public final RefGenomeVersion RefGenVersion;
    public final RefGenomeInterface RefGenome;

    public final int PartitionSize;
    public final int BufferSize;
    public final ValidationStringency BamStringency;

    // UMI group config
    public final UmiConfig UMIs;
    public final boolean FormConsensus;
    public final GroupIdGenerator IdGenerator;

    public final String OutputDir;
    public final String OutputId;
    public final boolean WriteBam;
    public final boolean NoMateCigar;
    public final int Threads;

    // debug
    public final List<String> SpecificChromosomes;
    public final List<String> LogReadIds;
    public final List<ChrBaseRegion> SpecificRegions;
    public final FilterReadsType SpecificRegionsFilterType;
    public final ReadOutput LogReadType;
    public final boolean PerfDebug;
    public final boolean RunChecks;
    public final boolean WriteStats;

    private boolean mIsValid;

    public static final Logger MD_LOGGER = LogManager.getLogger(MarkDupsConfig.class);

    // config strings
    private  static final String BAM_FILE = "bam_file";
    private static final String PARTITION_SIZE = "partition_size";
    private static final String BUFFER_SIZE = "buffer_size";
    private static final String READ_OUTPUTS = "read_output";
    private static final String NO_MATE_CIGAR = "no_mate_cigar";
    private static final String FORM_CONSENSUS = "form_consensus";
    private static final String WRITE_BAM = "write_bam";

    private static final String RUN_CHECKS = "run_checks";
    private static final String WRITE_STATS = "write_stats";
    private static final String SPECIFIC_REGION_FILTER_TYPE = "specific_region_filter";

    public MarkDupsConfig(final ConfigBuilder configBuilder)
    {
        mIsValid = true;
        SampleId = configBuilder.getValue(SAMPLE);
        BamFile = configBuilder.getValue(BAM_FILE);
        RefGenomeFile = configBuilder.getValue(REF_GENOME);
        RefGenome = loadRefGenome(RefGenomeFile);

        if(configBuilder.hasValue(OUTPUT_DIR))
        {
            OutputDir = parseOutputDir(configBuilder);
        }
        else
        {
            OutputDir = checkAddDirSeparator(Paths.get(BamFile).getParent().toString());
        }

        OutputId = configBuilder.getValue(OUTPUT_ID);

        if(SampleId == null || BamFile == null || OutputDir == null || RefGenomeFile == null)
        {
            MD_LOGGER.error("missing config: sample({}) bam({}) refGenome({}) outputDir({})",
                    SampleId != null, BamFile != null, RefGenomeFile != null, OutputDir != null);
            mIsValid = false;
        }

        RefGenVersion = RefGenomeVersion.from(configBuilder);

        MD_LOGGER.info("refGenome({}), bam({})", RefGenVersion, BamFile);
        MD_LOGGER.info("output({})", OutputDir);

        PartitionSize = configBuilder.getInteger(PARTITION_SIZE);
        BufferSize = configBuilder.getInteger(BUFFER_SIZE);
        BamStringency = BamUtils.validationStringency(configBuilder);

        NoMateCigar = configBuilder.hasFlag(NO_MATE_CIGAR);
        UMIs = UmiConfig.from(configBuilder);
        FormConsensus = !UMIs.Enabled && !NoMateCigar && configBuilder.hasFlag(FORM_CONSENSUS);
        IdGenerator = new GroupIdGenerator();

        String duplicateLogic = UMIs.Enabled ? "UMIs" : (FormConsensus ? "consensus" : "max base-qual");
        MD_LOGGER.info("duplicate logic: {}", duplicateLogic);

        SpecificChromosomes = Lists.newArrayList();
        SpecificRegions = Lists.newArrayList();

        try
        {
            loadSpecificChromsomesOrRegions(configBuilder, SpecificChromosomes, SpecificRegions, MD_LOGGER);
            Collections.sort(SpecificRegions);
        }
        catch(ParseException e)
        {
            MD_LOGGER.error("invalid specific regions({}) chromosomes({}) config",
                    configBuilder.getValue(SPECIFIC_REGIONS, ""), configBuilder.getValue(SPECIFIC_CHROMOSOMES, ""));
            mIsValid = false;
        }

        SpecificRegionsFilterType = !SpecificChromosomes.isEmpty() || !SpecificRegions.isEmpty() ?
                FilterReadsType.valueOf(configBuilder.getValue(SPECIFIC_REGION_FILTER_TYPE, FilterReadsType.READ.toString())) :
                FilterReadsType.NONE;

        WriteBam = configBuilder.hasFlag(WRITE_BAM) || !configBuilder.hasFlag(READ_OUTPUTS);
        LogReadType = ReadOutput.valueOf(configBuilder.getValue(READ_OUTPUTS, ReadOutput.NONE.toString()));

        LogReadIds = parseLogReadIds(configBuilder);
        Threads = parseThreads(configBuilder);

        WriteStats = configBuilder.hasFlag(WRITE_STATS);
        PerfDebug = configBuilder.hasFlag(PERF_DEBUG);
        RunChecks = configBuilder.hasFlag(RUN_CHECKS);

        if(RunChecks)
        {
            MD_LOGGER.info("running debug options: read-checks({})", RunChecks);
        }
    }

    public boolean isValid()
    {
        if(!mIsValid)
            return false;

        if(!Files.exists(Paths.get(BamFile)))
        {
            MD_LOGGER.error("invalid bam file path: {}", BamFile);
            return false;
        }

        if(!Files.exists(Paths.get(RefGenomeFile)))
        {
            MD_LOGGER.error("invalid ref genome file: {}", RefGenomeFile);
            return false;
        }

        return true;
    }

    public String formFilename(final String fileType)
    {
        String filename = OutputDir + SampleId;

        filename += "." + fileType;

        if(OutputId != null)
            filename += "." + OutputId;

        filename += TSV_EXTENSION;

        return filename;
    }

    public boolean runReadChecks() { return RunChecks && (!SpecificChromosomes.isEmpty() || !SpecificRegions.isEmpty()); }

    public static void addConfig(final ConfigBuilder configBuilder)
    {
        addOutputOptions(configBuilder);
        ConfigUtils.addLoggingOptions(configBuilder);

        configBuilder.addConfigItem(SAMPLE, true, SAMPLE_DESC);
        configBuilder.addPath(BAM_FILE, true, "BAM file location");
        addRefGenomeConfig(configBuilder, true);
        configBuilder.addInteger(PARTITION_SIZE, "Partition size", DEFAULT_PARTITION_SIZE);
        configBuilder.addInteger(BUFFER_SIZE, "Read buffer size", DEFAULT_POS_BUFFER_SIZE);
        configBuilder.addConfigItem(
                READ_OUTPUTS, false,"Write reads: NONE (default), 'MISMATCHES', 'DUPLICATES', 'ALL'",
                ReadOutput.NONE.toString());

        configBuilder.addFlag(WRITE_BAM, "Write BAM, default is true if not writing other read TSV output");
        configBuilder.addFlag(FORM_CONSENSUS, "Form consensus reads from duplicate groups without UMIs");
        configBuilder.addFlag(NO_MATE_CIGAR, "Mate CIGAR not set by aligner, make no attempt to use it");
        addValidationStringencyOption(configBuilder);
        UmiConfig.addConfig(configBuilder);
        addThreadOptions(configBuilder);

        addSpecificChromosomesRegionsConfig(configBuilder);
        configBuilder.addConfigItem(LOG_READ_IDS, LOG_READ_IDS_DESC);
        configBuilder.addFlag(PERF_DEBUG, PERF_DEBUG_DESC);
        configBuilder.addFlag(RUN_CHECKS, "Run duplicate mismatch checks");
        configBuilder.addFlag(WRITE_STATS, "Write duplicate and UMI-group stats");
        configBuilder.addConfigItem(SPECIFIC_REGION_FILTER_TYPE, "Used with specific regions, to filter mates or supps");
    }

    public MarkDupsConfig(
            int partitionSize, int bufferSize, final RefGenomeInterface refGenome, boolean umiEnabled, boolean duplexUmi, boolean formConsensus)
    {
        mIsValid = true;
        SampleId = "";
        BamFile = null;
        RefGenomeFile = null;
        OutputDir = null;
        OutputId = "";
        RefGenVersion = V37;
        RefGenome = refGenome;

        PartitionSize = partitionSize;
        BufferSize = bufferSize;
        BamStringency = ValidationStringency.STRICT;

        UMIs = new UmiConfig(umiEnabled, duplexUmi, String.valueOf(DEFAULT_DUPLEX_UMI_DELIM), false);
        FormConsensus = formConsensus;
        NoMateCigar = false;
        IdGenerator = new GroupIdGenerator();

        SpecificChromosomes = Lists.newArrayList();
        SpecificRegions = Lists.newArrayList();
        SpecificRegionsFilterType = FilterReadsType.MATE_AND_SUPP;

        WriteBam = false;
        LogReadType = ReadOutput.NONE;

        LogReadIds = Lists.newArrayList();
        Threads = 0;
        PerfDebug = false;
        RunChecks = true;
        WriteStats = false;
    }
}
