package com.hartwig.hmftools.common.doid;

import static com.hartwig.hmftools.common.utils.json.JsonFunctions.nullableString;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalJsonArray;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalJsonObject;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalString;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.optionalStringList;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.string;
import static com.hartwig.hmftools.common.utils.json.JsonFunctions.stringList;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.hartwig.hmftools.common.utils.json.JsonDatamodelChecker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DiseaseOntology {

    private DiseaseOntology() {
    }

    @NotNull
    public static List<DoidEntry> readDoidJsonFile(@NotNull String doidJsonFile) throws IOException {
        JsonParser parser = new JsonParser();
        JsonReader reader = new JsonReader(new FileReader(doidJsonFile));
        reader.setLenient(true);
        List<DoidEntry> doids = Lists.newArrayList();
        while (reader.peek() != JsonToken.END_DOCUMENT) {
            JsonObject doidObject = parser.parse(reader).getAsJsonObject();

            JsonArray graphArray = doidObject.getAsJsonArray("graphs");
            for (JsonElement graph : graphArray) {
                JsonArray nodeArray = graph.getAsJsonObject().getAsJsonArray("nodes");
                for (JsonElement nodeElement : nodeArray) {
                    JsonObject node = nodeElement.getAsJsonObject();
                    String url = string(node, "id");
                    doids.add(ImmutableDoidEntry.builder()
                            .doid(extractDoid(url))
                            .url(url)
                            .doidMetadata(extractDoidMetadata(optionalJsonObject(node, "meta")))
                            .type(optionalString(node, "type"))
                            .doidTerm(optionalString(node, "lbl"))
                            .build());
                }
            }
        }
        return doids;
    }

    @NotNull
    private static String extractDoid(@NotNull String url) {
        return url.replace("http://purl.obolibrary.org/obo/DOID_", "");
    }

    @Nullable
    private static DoidMetadata extractDoidMetadata(@Nullable JsonObject metadataObject) {
        if (metadataObject == null) {
            return null;
        }

        JsonDatamodelChecker metadataChecker = DoidDatamodelCheckerFactory.doidMetadataChecker();
        metadataChecker.check(metadataObject);

        JsonArray xrefArray = metadataObject.getAsJsonArray("xrefs");
        List<DoidXref> xrefValList = Lists.newArrayList();
        if (xrefArray != null) {
            for (JsonElement xref : xrefArray) {
                xrefValList.add(ImmutableDoidXref.builder().val(string(xref.getAsJsonObject(), "val")).build());
            }
        }

        ImmutableDoidMetadata.Builder doidMetadataBuilder = ImmutableDoidMetadata.builder();
        doidMetadataBuilder.synonyms(extractDoidSynonyms(optionalJsonArray(metadataObject, "synonyms")));
        doidMetadataBuilder.basicPropertyValues(extractBasicPropertyValues(optionalJsonArray(metadataObject, "basicPropertyValues")));
        doidMetadataBuilder.doidDefinition(extractDoidDefinition(optionalJsonObject(metadataObject, "definition")));
        doidMetadataBuilder.subsets(optionalStringList(metadataObject, "subsets"));
        doidMetadataBuilder.xrefs(xrefValList);
        return doidMetadataBuilder.build();
    }

    @Nullable
    private static List<DoidSynonym> extractDoidSynonyms(@Nullable JsonArray synonymArray) {
        if (synonymArray == null) {
            return null;
        }

        List<DoidSynonym> doidSynonymList = Lists.newArrayList();
        for (JsonElement synonymElement : synonymArray) {
            JsonObject synonym = synonymElement.getAsJsonObject();
            doidSynonymList.add(ImmutableDoidSynonym.builder()
                    .pred(string(synonym, "pred"))
                    .val(string(synonym, "val"))
                    .xrefs(stringList(synonym, "xrefs"))
                    .build());
        }

        return doidSynonymList;
    }

    @Nullable
    private static List<DoidBasicPropertyValue> extractBasicPropertyValues(@Nullable JsonArray basicPropertyValueArray) {
        if (basicPropertyValueArray == null) {
            return null;
        }

        List<DoidBasicPropertyValue> doidBasicPropertyValueList = Lists.newArrayList();
        JsonDatamodelChecker basicPropertyValuesChecker = DoidDatamodelCheckerFactory.doidBasicPropertyValuesChecker();

        for (JsonElement basicPropertyElement : basicPropertyValueArray) {
            JsonObject basicProperty = basicPropertyElement.getAsJsonObject();
            basicPropertyValuesChecker.check(basicProperty);

            doidBasicPropertyValueList.add(ImmutableDoidBasicPropertyValue.builder()
                    .pred(string(basicProperty, "pred"))
                    .val(string(basicProperty, "val"))
                    .build());
        }

        return doidBasicPropertyValueList;
    }

    @Nullable
    private static DoidDefinition extractDoidDefinition(@Nullable JsonObject definitionObject) {
        if (definitionObject == null) {
            return null;
        }

        ImmutableDoidDefinition.Builder doidDefinitionBuilder = ImmutableDoidDefinition.builder();
        doidDefinitionBuilder.definitionVal(nullableString(definitionObject, "val"));
        doidDefinitionBuilder.definitionXrefs(stringList(definitionObject, "xrefs"));
        return doidDefinitionBuilder.build();
    }
}
