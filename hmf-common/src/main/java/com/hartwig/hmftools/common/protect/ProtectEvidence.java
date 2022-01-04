package com.hartwig.hmftools.common.protect;

import java.util.Objects;
import java.util.Set;

import com.hartwig.hmftools.common.serve.Knowledgebase;
import com.hartwig.hmftools.common.serve.actionability.EvidenceDirection;
import com.hartwig.hmftools.common.serve.actionability.EvidenceLevel;

import org.apache.commons.lang3.StringUtils;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class ProtectEvidence implements Comparable<ProtectEvidence> {

    @Value.Derived
    @NotNull
    public String genomicEvent() {
        return gene() != null ? gene() + " " + event() : event();
    }

    @Nullable
    public abstract String gene();

    @NotNull
    public abstract String event();

    @NotNull
    public abstract ProtectEvidenceType evidenceType();

    @Nullable
    public abstract Integer rangeRank();

    public abstract boolean germline();

    public abstract boolean reported();

    @NotNull
    public abstract String treatment();

    public abstract boolean onLabel();

    @NotNull
    public abstract EvidenceLevel level();

    @NotNull
    public abstract EvidenceDirection direction();

    @NotNull
    public abstract Set<Knowledgebase> sources();

    @NotNull
    public abstract Set<String> urls();

    @Override
    public int compareTo(@NotNull final ProtectEvidence o) {
        int reportedCompare = -Boolean.compare(reported(), o.reported());
        if (reportedCompare != 0) {
            return reportedCompare;
        }

        int genomicEventCompare = StringUtils.compare(genomicEvent(), o.genomicEvent());
        if (genomicEventCompare != 0) {
            return genomicEventCompare;
        }

        int evidenceTypeCompare = evidenceType().compareTo(o.evidenceType());
        if (evidenceTypeCompare != 0) {
            return evidenceTypeCompare;
        }

        int rangeRankCompare = compareInteger(rangeRank(), o.rangeRank());
        if (rangeRankCompare != 0) {
            return rangeRankCompare;
        }

        int levelCompare = level().compareTo(o.level());
        if (levelCompare != 0) {
            return levelCompare;
        }

        int onLabelCompare = -Boolean.compare(onLabel(), o.onLabel());
        if (onLabelCompare != 0) {
            return onLabelCompare;
        }

        int treatmentCompare = treatment().compareTo(o.treatment());
        if (treatmentCompare != 0) {
            return treatmentCompare;
        }

        int directionCompare = direction().compareTo(o.direction());
        if (directionCompare != 0) {
            return directionCompare;
        }

        return 0;
    }

    public static int compareInteger(@Nullable Integer int1, @Nullable Integer int2) {
        if (Objects.equals(int1, int2)) {
            return 0;
        } else if (int1 == null) {
            return -1;
        } else if (int2 == null) {
            return 1;
        } else {
            return int1.compareTo(int2);
        }
    }
}
