package com.hartwig.hmftools.patientdb.curators;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.patientdb.Utils;
import com.hartwig.hmftools.patientdb.data.CuratedCancerType;
import com.hartwig.hmftools.patientdb.data.ImmutableCuratedCancerType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TumorLocationCurator {

    private static final Logger LOGGER = LogManager.getLogger(TumorLocationCurator.class);

    @NotNull
    private final Map<String, CuratedCancerType> tumorLocationMap = Maps.newHashMap();
    @NotNull
    private final Set<String> unusedSearchTerms;

    public TumorLocationCurator(@NotNull final InputStream mappingInputStream) throws IOException {
        final CSVParser parser = CSVParser.parse(mappingInputStream, Charset.defaultCharset(), CSVFormat.DEFAULT.withHeader());
        for (final CSVRecord record : parser) {
            final String location = record.get("primaryTumorLocation");
            final String category = record.get("category");
            final String subcategory = record.get("subcategory");
            tumorLocationMap.put(location.toLowerCase(),
                    ImmutableCuratedCancerType.of(Utils.capitalize(category), Utils.capitalize(subcategory), location));
        }
        // KODU: Need to create a copy of the key set so that we can remove elements from it without affecting the curation.
        unusedSearchTerms = Sets.newHashSet(tumorLocationMap.keySet());
    }

    @NotNull
    public CuratedCancerType search(@Nullable final String searchTerm) {
        if (searchTerm != null) {
            String effectiveSearchTerm = searchTerm.toLowerCase();
            unusedSearchTerms.remove(effectiveSearchTerm);
            final CuratedCancerType result = tumorLocationMap.get(effectiveSearchTerm);

            if (result != null) {
                return result;
            }
        }

        // KODU: File encoding is expected to be UTF-8 (see also DEV-275)
        LOGGER.warn("Could not curate tumor location (using " + System.getProperty("file.encoding") + "): " + searchTerm);
        return ImmutableCuratedCancerType.of(null, null, searchTerm);
    }

    @NotNull
    public Set<String> unusedSearchTerms() {
        return unusedSearchTerms;
    }
}
