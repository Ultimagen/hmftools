package com.hartwig.hmftools.sage.candidate;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspotComparator;
import com.hartwig.hmftools.sage.select.TierSelector;

public class Candidates
{
    private final List<VariantHotspot> mHotspots;
    private final List<ChrBaseRegion> mPanel;
    private final List<ChrBaseRegion> mHighConfidence;
    private final Map<VariantHotspot,Candidate> mCandidateMap;
    private List<Candidate> mCandidateList;

    public Candidates(final List<VariantHotspot> hotspots, final List<ChrBaseRegion> panel, final List<ChrBaseRegion> highConfidence)
    {
        mHotspots = hotspots;
        mPanel = panel;
        mHighConfidence = highConfidence;

        mCandidateMap = Maps.newHashMap();
    }

    public void add(final Collection<AltContext> altContexts)
    {
        if(mCandidateList != null)
        {
            throw new IllegalStateException("Cannot add more alt contexts");
        }

        final TierSelector tierSelector = new TierSelector(mHotspots, mPanel, mHighConfidence);

        for(final AltContext altContext : altContexts)
        {
            Candidate candidate = mCandidateMap.get(altContext);

            if(candidate == null)
            {
                candidate = new Candidate(tierSelector.tier(altContext), altContext);
                mCandidateMap.put(altContext, candidate);
            }

            candidate.update(altContext);
        }
    }

    public List<Candidate> candidates()
    {
        if(mCandidateList == null)
        {
            final VariantHotspotComparator variantHotspotComparator = new VariantHotspotComparator();
            mCandidateList = Lists.newArrayList(mCandidateMap.values());
            mCandidateList.sort((o1, o2) -> variantHotspotComparator.compare(o1.variant(), o2.variant()));
        }

        return mCandidateList;
    }
}
