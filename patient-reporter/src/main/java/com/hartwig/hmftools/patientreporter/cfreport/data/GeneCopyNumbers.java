package com.hartwig.hmftools.patientreporter.cfreport.data;

import com.hartwig.hmftools.common.copynumber.CopyNumberAlteration;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;
import com.hartwig.hmftools.patientreporter.report.data.GeneCopyNumberDataSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GeneCopyNumbers {

    @NotNull
    public static List<GeneCopyNumber> sort(@NotNull List<GeneCopyNumber> geneCopyNumbers) {
        return geneCopyNumbers.stream().sorted((copyNumber1, copyNumber2) -> {
            String location1 = zeroPrefixed(copyNumber1.chromosome() + copyNumber1.chromosomeBand());
            String location2 = zeroPrefixed(copyNumber2.chromosome() + copyNumber2.chromosomeBand());

            if (location1.equals(location2)) {
                return copyNumber1.gene().compareTo(copyNumber2.gene());
            } else {
                return location1.compareTo(location2);
            }
        }).collect(Collectors.toList());
    }

    @NotNull
    private static String zeroPrefixed(@NotNull String location) {

        // First remove q or p arm if present.
        int armStart = location.indexOf("q");
        if (armStart < 0) {
            armStart = location.indexOf("p");
        }

        String chromosome = armStart > 0 ? location.substring(0, armStart) : location;

        try {
            int chromosomeIndex = Integer.valueOf(chromosome);
            if (chromosomeIndex < 10) {
                return "0" + location;
            } else {
                return location;
            }
        } catch (NumberFormatException exception) {
            return location;
        }
    }

    @NotNull
    public static String[] amplificationGenes(@NotNull List<GeneCopyNumber> copyNumbers) {
        final List<String> returnVariants = new ArrayList<>();
        for (GeneCopyNumber copyNumber : copyNumbers) {
            if (GeneCopyNumberDataSource.type(copyNumber).equals("gain")) {
                returnVariants.add(copyNumber.gene());
            }
        }
        return returnVariants.toArray(new String[0]);
    }

    @NotNull
    public static String[] lossGenes(@NotNull List<GeneCopyNumber> copyNumbers) {
        final List<String> returnVariants = new ArrayList<>();
        for (GeneCopyNumber copyNumber : copyNumbers) {
            if (GeneCopyNumberDataSource.type(copyNumber).equals("full loss") || GeneCopyNumberDataSource.type(copyNumber)
                    .equals("partial loss")) {
                returnVariants.add(copyNumber.gene());
            }
        }
        return returnVariants.toArray(new String[0]);
    }

    @NotNull
    public static String getType(@NotNull GeneCopyNumber geneCopyNumber) {
        if (geneCopyNumber.alteration() == CopyNumberAlteration.GAIN) {
            return "gain";
        } else {
            // At this point we only have losses and gains.
            assert geneCopyNumber.alteration() == CopyNumberAlteration.LOSS;
            if (geneCopyNumber.maxCopyNumber() < 0.5) {
                return "full loss";
            } else {
                return "partial loss";
            }
        }
    }
}
