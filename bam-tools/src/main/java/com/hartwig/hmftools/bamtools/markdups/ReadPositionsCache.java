package com.hartwig.hmftools.bamtools.markdups;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.bamtools.BmConfig.BM_LOGGER;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.orientation;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import htsjdk.samtools.SAMRecord;

public class ReadPositionsCache
{
    // a ring buffer to store reads at each read starting position
    private final String mChromosome;
    private final PositionFragments[] mForwardPositionGroups;
    private final Map<Integer, PositionFragments> mReversePositionGroups;
    private final Map<String,Fragment> mFragments;
    private final Consumer<PositionFragments> mReadGroupHandler;
    private int mMinPosition;
    private int mMinPositionIndex;

    private final int mCapacity;

    public ReadPositionsCache(final String chromosome, int capacity, final Consumer<PositionFragments> evictionHandler)
    {
        mChromosome = chromosome;
        mReadGroupHandler = evictionHandler;
        mCapacity = capacity;
        mForwardPositionGroups = new PositionFragments[mCapacity];
        mReversePositionGroups = Maps.newHashMap();
        mFragments = Maps.newHashMap();
        mMinPosition = 0;
        mMinPositionIndex = 0;
    }

    public boolean processRead(final SAMRecord read)
    {
        /* scenarios:
            - unpaired or mate is unmapped
                - store as potential duplicate
                - supplementaries - store if within range, to avoid processing later
            - read's mate is on another chromosome - store since can still evaluate if duplicate (from both chromosomes)
            - lower pos with mate higher on same chromosome - store regardless
            - higher pos with mate lower on same chromosome
                - mate within current range - look up and add
                - mate already flushed, return unhandled
            - supplementary
                - with mate within current range - look up and add
                - otherwise return unhandled
        */

        // for supplementaries, add the read if the non-supplmentaries have already been stored
        if(read.getSupplementaryAlignmentFlag())
            return storeAdditionalRead(read);

        if(!read.getReadPairedFlag() || read.getMateUnmappedFlag())
        {
            storeInitialRead(read);
            return true;
        }

        // mate is elsewhere so store this primary read
        if(!read.getMateReferenceName().equals(mChromosome))
        {
            storeInitialRead(read);
            return true;
        }

        Fragment fragment = mFragments.get(read.getReadName());

        if(fragment != null)
        {
            int initialPos = fragment.initialPosition();

            fragment.addRead(read);

            if(initialPos != fragment.initialPosition())
                checkStrandSwitch(fragment);

            return true;
        }

        storeInitialRead(read);
        return true;
    }

    private void checkStrandSwitch(final Fragment fragment)
    {
        // switch fragment from the forward to reverse strand or vice versa if still within the array bounds
        int fragmentStart = fragment.coordinates()[SE_START];
        int fragmentEnd = fragment.coordinates()[SE_END];

        if((fragmentStart > 0) == (fragmentEnd >= 0))
            return;

        if(fragmentEnd < 0)
        {
            // move fragment from reverse strand to forward strand array
            int distanceFromMinPosition = fragmentStart - mMinPosition;

            if(distanceFromMinPosition >= mCapacity)
                return; // already purged

            PositionFragments existingGroup = mReversePositionGroups.get(fragmentEnd);

            if(existingGroup == null)
                return;

            existingGroup.Fragments.remove(fragment);

            if(existingGroup.Fragments.isEmpty())
                mReversePositionGroups.remove(fragmentEnd);
        }
        else
        {
            int index = calcIndex(fragmentEnd);

            PositionFragments element = mForwardPositionGroups[index];
            if(element == null)
                return;

            element.Fragments.remove(fragment);

            if(element.Fragments.isEmpty())
                mForwardPositionGroups[index] = null;
        }

        storeFragment(fragment);
    }

    private void storeInitialRead(final SAMRecord read)
    {
        Fragment fragment = new Fragment(read);
        mFragments.put(read.getReadName(), fragment);

        int fragmentPosition = fragment.initialPosition();

        if(fragmentPosition > 0)
        {
            if(mMinPosition == 0)
                mMinPosition = fragmentPosition;
            else
                checkFlush(fragmentPosition);
        }

        storeFragment(fragment);
    }

    private void storeFragment(final Fragment fragment)
    {
        int fragmentPosition = fragment.initialPosition();

        if(fragmentPosition > 0)
        {
            int index = calcIndex(fragmentPosition);

            PositionFragments element = mForwardPositionGroups[index];
            if(element == null)
            {
                element = new PositionFragments(fragmentPosition, fragment);
                mForwardPositionGroups[index] = element;
            }
            else
            {
                element.Fragments.add(fragment);
            }
        }
        else
        {
            // store in reverse strand map
            PositionFragments element = mReversePositionGroups.get(fragmentPosition);
            if(element == null)
            {
                element = new PositionFragments(fragmentPosition, fragment);
                mReversePositionGroups.put(fragmentPosition, element);
            }
            else
            {
                element.Fragments.add(fragment);
            }
        }
    }

    private boolean storeAdditionalRead(final SAMRecord read)
    {
        Fragment fragment = mFragments.get(read.getReadName());

        if(fragment != null)
        {
            fragment.addRead(read);
            return true;
        }

        return false;
    }

    private int calcIndex(int position)
    {
        // capacity = 10, min position = 1, min index = 0, position of 10 is index 9
        // capacity = 10, min position = 2, min index = 1, position of 10 is index 9, position of 11 is 0
        int distanceFromMinPosition = position - mMinPosition;

        if(mMinPositionIndex + distanceFromMinPosition < mCapacity)
            return mMinPositionIndex + distanceFromMinPosition;

        // index is from start of ring buffer
        return distanceFromMinPosition + mMinPositionIndex - mCapacity;
    }

    private void checkFlush(int position)
    {
        if(mMinPosition == 0)
        {
            resetMinPosition(position);
            return;
        }

        int distanceFromMinPosition = position - mMinPosition;

        if(distanceFromMinPosition < mCapacity)
            return;

        int flushCount = position - mMinPosition - mCapacity + 1;
        Set<String> flushedReadIds = null;

        // only iterate at most once through the array
        for(int i = 0; i < min(flushCount, mCapacity); i++)
        {
            PositionFragments element = mForwardPositionGroups[mMinPositionIndex];

            // clear and process each element and depth
            if(element != null)
            {
                if(flushedReadIds == null)
                    flushedReadIds = Sets.newHashSet();

                for(Fragment fragment : element.Fragments)
                    flushedReadIds.add(fragment.id());

                mReadGroupHandler.accept(element);

                mForwardPositionGroups[mMinPositionIndex] = null;
            }

            mMinPosition++;

            if(mMinPositionIndex + 1 >= mForwardPositionGroups.length)
                mMinPositionIndex = 0;
            else
                ++mMinPositionIndex;
        }

        if(flushCount >= mCapacity)
            resetMinPosition(position);

        if(flushedReadIds != null && !flushedReadIds.isEmpty())
        {
            // flush out any reverse strand position which is now earlier than the current forward strand read start position
            Set<Integer> flushedPositions = Sets.newHashSet();
            for(Map.Entry<Integer, PositionFragments> entry : mReversePositionGroups.entrySet())
            {
                int reversePosition = abs(entry.getKey());
                if(reversePosition < position)
                {
                    flushedPositions.add(entry.getKey());

                    for(Fragment fragment : entry.getValue().Fragments)
                        flushedReadIds.add(fragment.id());

                    mReadGroupHandler.accept(entry.getValue());
                }
            }

            flushedPositions.forEach(x -> mReversePositionGroups.remove(x));
            flushedReadIds.forEach(x -> mFragments.remove(x));
        }
    }

    public void evictAll()
    {
        for(int i = 0; i < mCapacity; i++)
        {
            PositionFragments element = mForwardPositionGroups[i];

            // clear and process each element and depth
            if(element != null)
            {
                mReadGroupHandler.accept(element);
                mForwardPositionGroups[i] = null;
            }
        }

        for(Map.Entry<Integer, PositionFragments> entry : mReversePositionGroups.entrySet())
        {
            mReadGroupHandler.accept(entry.getValue());
        }

        mReversePositionGroups.clear();
        mFragments.clear();
    }

    private void resetMinPosition(int position)
    {
        mMinPositionIndex = 0;
        mMinPosition = max(1, position - (int)round(mCapacity * 0.5));
    }
}