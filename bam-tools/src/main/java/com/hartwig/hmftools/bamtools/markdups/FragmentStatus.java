package com.hartwig.hmftools.bamtools.markdups;

public enum FragmentStatus
{
    UNSET,
    NONE,
    PRIMARY,
    DUPLICATE,
    CANDIDATE,
    SUPPLEMENTARY;

    public boolean isResolved() { return this == PRIMARY || this == DUPLICATE || this == NONE; }
    public boolean isDuplicate() { return this == PRIMARY || this == DUPLICATE; }
}
