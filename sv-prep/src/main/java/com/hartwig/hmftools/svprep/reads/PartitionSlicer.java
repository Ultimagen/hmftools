package com.hartwig.hmftools.svprep.reads;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion.V37;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion.V38;
import static com.hartwig.hmftools.svprep.CombinedReadGroups.externalReadChrPartition;
import static com.hartwig.hmftools.svprep.CombinedReadGroups.formChromosomePartition;
import static com.hartwig.hmftools.svprep.SvCommon.SV_LOGGER;
import static com.hartwig.hmftools.svprep.SvConstants.DOWN_SAMPLE_FRACTION;
import static com.hartwig.hmftools.svprep.SvConstants.DOWN_SAMPLE_THRESHOLD;
import static com.hartwig.hmftools.svprep.SvConstants.EXCLUDED_REGION_1_REF_37;
import static com.hartwig.hmftools.svprep.SvConstants.EXCLUDED_REGION_1_REF_38;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.samtools.BamSlicer;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;
import com.hartwig.hmftools.svprep.CombinedReadGroups;
import com.hartwig.hmftools.svprep.CombinedStats;
import com.hartwig.hmftools.svprep.ResultsWriter;
import com.hartwig.hmftools.svprep.SvConfig;
import com.hartwig.hmftools.svprep.WriteType;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

public class PartitionSlicer
{
    private final int mId;
    private final SvConfig mConfig;
    private final ChrBaseRegion mRegion;
    private final CombinedReadGroups mCombinedReadGroups;
    private final ResultsWriter mWriter;
    private final ReadFilters mReadFilters;
    private final ChrBaseRegion mFilterRegion;

    private final SamReader mSamReader;
    private final BamSlicer mBamSlicer;

    private final JunctionTracker mJunctionTracker;
    private final PartitionBuckets mBuckets;

    private final PartitionStats mStats;
    private final CombinedStats mCombinedStats;

    private final Map<String,ReadGroup> mReadGroups;

    private final ReadRateTracker mReadRateTracker;
    private boolean mRateLimitTriggered;
    private boolean mLogReadIds;
    private final PerformanceCounter[] mPerCounters;

    private static final int PC_SLICE = 0;
    private static final int PC_JUNCTIONS = 1;
    private static final int PC_TOTAL = 2;

    public PartitionSlicer(
            final int id, final ChrBaseRegion region, final SvConfig config, final CombinedReadGroups combinedReadGroups,
            final ResultsWriter writer, final CombinedStats combinedStats)
    {
        mId = id;
        mConfig = config;
        mReadFilters = config.ReadFiltering;
        mCombinedReadGroups = combinedReadGroups;
        mWriter = writer;
        mRegion = region;
        mCombinedStats = combinedStats;

        mJunctionTracker = new JunctionTracker(mRegion, mConfig.ReadFiltering.config(), mConfig.Hotspots);

        mSamReader = mConfig.BamFile != null ?
                SamReaderFactory.makeDefault().referenceSequence(new File(mConfig.RefGenomeFile)).open(new File(mConfig.BamFile)) : null;

        mBamSlicer = new BamSlicer(0, false, true, false);

        mBuckets = new PartitionBuckets(mRegion, mConfig.PartitionSize, mConfig.BucketSize);

        int rateSegmentLength = mConfig.PartitionSize / DOWN_SAMPLE_FRACTION;
        int downsampleThreshold = DOWN_SAMPLE_THRESHOLD / DOWN_SAMPLE_FRACTION;
        mReadRateTracker = new ReadRateTracker(rateSegmentLength, mRegion.start(), downsampleThreshold);
        mRateLimitTriggered = false;

        mFilterRegion = mConfig.RefGenVersion == V37 && region.overlaps(EXCLUDED_REGION_1_REF_37) ? EXCLUDED_REGION_1_REF_37
                : (mConfig.RefGenVersion == V38 && region.overlaps(EXCLUDED_REGION_1_REF_38) ? EXCLUDED_REGION_1_REF_38 : null);

        mReadGroups = Maps.newHashMap();

        mStats = new PartitionStats();

        mPerCounters = new PerformanceCounter[PC_TOTAL+1];
        mPerCounters[PC_SLICE] = new PerformanceCounter("Slice");
        mPerCounters[PC_JUNCTIONS] = new PerformanceCounter("Junctions");
        mPerCounters[PC_TOTAL] = new PerformanceCounter("Total");

        mLogReadIds = !mConfig.LogReadIds.isEmpty();
    }

    public void run()
    {
        SV_LOGGER.debug("processing region({})", mRegion);

        mPerCounters[PC_TOTAL].start();

        mPerCounters[PC_SLICE].start();

        mBamSlicer.slice(mSamReader, Lists.newArrayList(mRegion), this::processSamRecord);

        mPerCounters[PC_SLICE].stop();

        mPerCounters[PC_JUNCTIONS].start();

        mReadGroups.values().forEach(x -> processGroup(x));

        mJunctionTracker.createJunctions();
        mJunctionTracker.filterJunctions();

        mBuckets.processBuckets(-1, this::processBucket);

        mPerCounters[PC_JUNCTIONS].stop();

        if(mStats.TotalReads > 0)
        {
            SV_LOGGER.debug("region({}) complete, stats({}) incompleteGroups({})",
                    mRegion, mStats.toString(), mReadGroups.size());

            SV_LOGGER.debug("region({}) filters({})",
                    mRegion, ReadFilterType.filterCountsToString(mStats.ReadFilterCounts));

            writeData();

            mCombinedStats.addPartitionStats(mStats);
            mCombinedStats.addPerfCounters(mPerCounters);
        }

        mPerCounters[PC_TOTAL].stop();

        if(mRateLimitTriggered)
            System.gc();
    }

    private static final boolean LOG_READ_ONLY = false;

    private void processSamRecord(final SAMRecord record)
    {
        int readStart = record.getAlignmentStart();

        if(!mRegion.containsPosition(readStart))
            return;

        ++mStats.TotalReads;

        if(mFilterRegion != null)
        {
            if(mFilterRegion.containsPosition(readStart) || mFilterRegion.containsPosition(readStart + mConfig.ReadLength))
                return;
        }

        if(mConfig.MaxPartitionReads > 0 && mStats.TotalReads >= mConfig.MaxPartitionReads)
        {
            SV_LOGGER.warn("region({}) readCount({}) exceeds maximum, stopping slice", mRegion, mStats.TotalReads);
            mBamSlicer.haltProcessing();
            return;
        }

        if(mLogReadIds) // debugging only
        {
            if(mConfig.LogReadIds.contains(record.getReadName()))
                SV_LOGGER.debug("specific readId({})", record.getReadName());
            else if(LOG_READ_ONLY)
                return;
        }

        if(!checkReadRateLimits(readStart))
            return;

        int filters = mReadFilters.checkFilters(record);

        if(filters != 0)
        {
            // allow low map quality through at this stage
            if(filters != ReadFilterType.MIN_MAP_QUAL.flag())
            {
                processFilteredRead(record, filters);
                return;
            }
        }

        ReadRecord read = ReadRecord.from(record);
        read.setFilters(filters);

        mJunctionTracker.processRead(read);

        if(!mRegion.containsPosition(read.MateChromosome, read.MatePosStart))
        {
            processSingleRead(read);
            return;
        }

        ReadGroup readGroup = mReadGroups.get(read.id());

        if(readGroup == null)
        {
            // cache the read waiting for its mate
            mReadGroups.put(read.id(), new ReadGroup(read));
            return;
        }

        readGroup.addRead(read);

        if(readGroup.isComplete())
        {
            processGroup(readGroup);
            mReadGroups.remove(readGroup.id());
            return;
        }

        // if either read has a supplementary in another partition then process this incomplete group now
        SupplementaryReadData suppData = readGroup.reads().stream()
                .filter(x -> x.hasSuppAlignment()).findFirst().map(x -> x.supplementaryAlignment()).orElse(null);

        if(suppData != null && !mRegion.containsPosition(suppData.Chromosome, suppData.Position))
        {
            processGroup(readGroup);
            mReadGroups.remove(readGroup.id());
        }
    }

    private void processSingleRead(final ReadRecord read)
    {
        BucketData bucket = mBuckets.findBucket(read.start());
        bucket.addReadGroup(new ReadGroup(read));
    }

    private void processGroup(final ReadGroup readGroup)
    {
        BucketData bucket = mBuckets.findBucket(readGroup.minStartPosition());
        bucket.addReadGroup(readGroup);
    }

    private void processFilteredRead(final SAMRecord record, final int filters)
    {
        // check criteria to keep an otherwise filtered, to see if it supports a non-filtered read or location
        // record filters by type
        for(ReadFilterType type : ReadFilterType.values())
        {
            if(type.isSet(filters))
                ++mStats.ReadFilterCounts[type.index()];
        }

        // check for any evidence of support for an SV
        if(!mReadFilters.isCandidateSupportingRead(record))
            return;

        ReadRecord read = ReadRecord.from(record);
        read.setFilters(filters);

        BucketData bucket = mBuckets.findBucket(read.start());
        bucket.addSupportingRead(read);

        mJunctionTracker.processRead(read);
    }

    private void writeData()
    {
        if(mConfig.WriteTypes.contains(WriteType.BAM))
        {
            // the BAM file writes records by readId, so needs to combine all reads for a given fragment
            Map<String,Map<String,ReadGroup>> partialGroupsMap = Maps.newHashMap();
            List<ReadGroup> localCompleteGroups = Lists.newArrayList();

            String chrPartition = formChromosomePartition(mRegion.Chromosome, mRegion.start(), mConfig.PartitionSize);

            for(ReadGroup readGroup : mJunctionTracker.readGroups().values())
            {
                if(readGroup.isComplete())
                {
                    localCompleteGroups.add(readGroup);
                }
                else
                {
                    String remoteChrPartition = externalReadChrPartition(mRegion, mConfig.PartitionSize, readGroup.reads());

                    Map<String,ReadGroup> groups = partialGroupsMap.get(remoteChrPartition);

                    if(groups == null)
                    {
                        groups = Maps.newHashMap();
                        partialGroupsMap.put(remoteChrPartition, groups);
                    }

                    groups.put(readGroup.id(), readGroup);
                }
            }

            List<ReadGroup> remoteCompleteGroups = mCombinedReadGroups.addIncompleteReadGroup(chrPartition, partialGroupsMap);

            SV_LOGGER.debug("region({}) readGroups({}) complete(local={} remote={}) partials({})",
                    mRegion, mJunctionTracker.readGroups().values().size(), localCompleteGroups.size(), remoteCompleteGroups.size(),
                    partialGroupsMap.size());

            mWriter.writeBamRecords(localCompleteGroups);
            mWriter.writeBamRecords(remoteCompleteGroups);
        }

        if(mConfig.WriteTypes.contains(WriteType.JUNCTIONS))
        {
            mWriter.writeJunctionData(mRegion.Chromosome, mJunctionTracker.junctions());
        }

        for(JunctionData junctionData : mJunctionTracker.junctions())
        {
            ++mStats.JunctionCount;
            mStats.JunctionFragmentCount += junctionData.exactFragmentCount();
            mStats.SupportingReadCount += junctionData.supportingReadCount();

            if(mConfig.WriteTypes.contains(WriteType.READS))
            {
                for(ReadGroup readGroup : junctionData.JunctionGroups)
                {
                    String groupStatus = readGroup.groupStatus();
                    readGroup.reads().forEach(x -> mWriter.writeReadData(x, mId, junctionData.Position, "SV", groupStatus));
                }

                junctionData.SupportingReads.forEach(x -> mWriter.writeReadData(
                        x, mId, junctionData.Position, "SUPPORTING", "UNKNOWN"));
            }
        }
    }

    private void processBucket(final BucketData bucket)
    {
        // establish junction positions and any supporting read evidence
        bucket.assignJunctionReads(mReadFilters.config());

        // pass on any junctions and supporting reads that belong in the next bucket
        mBuckets.transferToNext(bucket);

        // apply basic filters
        bucket.filterJunctions(mConfig.Hotspots, mReadFilters.config());

        if(bucket.junctions().isEmpty())
        {
            ++mStats.FilteredBuckets;
            return;
        }

        ++mStats.Buckets;

        mStats.InitialSupportingReadCount += bucket.initialSupportingReadCount();

        for(JunctionData junctionData : bucket.junctions())
        {
            ++mStats.JunctionCount;
            mStats.JunctionFragmentCount += junctionData.exactFragmentCount();
            mStats.SupportingReadCount += junctionData.supportingReadCount();
        }

        if(mConfig.WriteTypes.contains(WriteType.BUCKET_STATS))
        {
            mWriter.writeBucketData(bucket, mId);
        }

        if(mConfig.WriteTypes.contains(WriteType.JUNCTIONS))
        {
            mWriter.writeJunctionData(mRegion.Chromosome, bucket.junctions());
        }

        if(mConfig.WriteTypes.contains(WriteType.BAM))
        {
            mWriter.writeBamRecords(bucket);
        }

        if(mConfig.WriteTypes.contains(WriteType.READS))
        {
            for(ReadGroup readGroup : bucket.readGroups())
            {
                boolean groupComplete = readGroup.isComplete();
                readGroup.reads().forEach(x -> mWriter.writeReadData(x, mId, bucket.id(), "SV", readGroup.groupStatus()));
            }

            // group complete set false since reads are not grouped for now
            bucket.supportingReads().forEach(x -> mWriter.writeReadData(x, mId, bucket.id(), "SUPPORTING", "UNKNOWN"));
        }
    }

    private boolean checkReadRateLimits(int positionStart)
    {
        boolean wasLimited = mReadRateTracker.isRateLimited();
        int lastSegementReadCount = mReadRateTracker.readCount();

        boolean handleRead = mReadRateTracker.handleRead(positionStart);

        if(wasLimited != mReadRateTracker.isRateLimited())
        {
            if(mReadRateTracker.isRateLimited())
            {
                SV_LOGGER.info("region({}) rate limited with read count({}) at position({})",
                        mRegion, lastSegementReadCount, positionStart);
                mRateLimitTriggered = true;
            }
            else
            {
                SV_LOGGER.info("region({}) rate limit cleared at position({}), last read count({})",
                        mRegion, positionStart, lastSegementReadCount);
            }
        }

        return handleRead;
    }
}