package com.hartwig.hmftools.patientreporter.actionability;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.protect.ImmutableProtectEvidence;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.protect.ProtectSource;
import com.hartwig.hmftools.common.serve.Knowledgebase;

import org.jetbrains.annotations.NotNull;

public final class ClinicalTrialFactory {

    private ClinicalTrialFactory() {
    }

    @NotNull
    public static List<ProtectEvidence> extractOnLabelTrials(@NotNull List<ProtectEvidence> evidenceItems) {
        List<ProtectEvidence> trials = Lists.newArrayList();
        for (ProtectEvidence evidence : evidenceItems) {
            Set<ProtectSource> protectSources = Sets.newHashSet();
            for (ProtectSource protectSource: evidence.protectSources()) {
                if (protectSource.source() == Knowledgebase.ICLUSION && evidence.onLabel()) {
                    protectSources.add(protectSource);
                }
            }

            if (protectSources.size() >= 1) {
                trials.add(ImmutableProtectEvidence.builder().from(evidence).protectSources(protectSources).build());
            }

        }
        return trials;
    }
}