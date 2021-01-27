package com.hartwig.hmftools.ckb.reference;

import java.io.File;
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
import com.hartwig.hmftools.ckb.common.EvidenceInfo;
import com.hartwig.hmftools.ckb.common.GeneInfo;
import com.hartwig.hmftools.ckb.common.ImmutableEvidenceInfo;
import com.hartwig.hmftools.ckb.common.ImmutableGeneInfo;
import com.hartwig.hmftools.ckb.common.ImmutableIndicationInfo;
import com.hartwig.hmftools.ckb.common.ImmutableMolecularProfileInfo;
import com.hartwig.hmftools.ckb.common.ImmutableReferenceInfo;
import com.hartwig.hmftools.ckb.common.ImmutableTherapyInfo;
import com.hartwig.hmftools.ckb.common.ImmutableTreatmentApproach;
import com.hartwig.hmftools.ckb.common.ImmutableVariantInfo;
import com.hartwig.hmftools.ckb.common.IndicationInfo;
import com.hartwig.hmftools.ckb.common.MolecularProfileInfo;
import com.hartwig.hmftools.ckb.common.ReferenceInfo;
import com.hartwig.hmftools.ckb.common.TherapyInfo;
import com.hartwig.hmftools.ckb.common.TreatmentApproach;
import com.hartwig.hmftools.ckb.common.VariantInfo;
import com.hartwig.hmftools.common.utils.json.JsonDatamodelChecker;
import com.hartwig.hmftools.common.utils.json.JsonFunctions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ReferenceFactory {

    private static final Logger LOGGER = LogManager.getLogger(ReferenceFactory.class);

    private ReferenceFactory() {
    }

    @NotNull
    public static List<Reference> readingReference(@NotNull String referenceDir) throws IOException {
        LOGGER.info("Start reading indications");

        List<Reference> references = Lists.newArrayList();
        File[] filesReferences = new File(referenceDir).listFiles();

        if (filesReferences != null) {
            LOGGER.info("The total files in the reference dir is {}", filesReferences.length);

            for (File reference : filesReferences) {
                JsonParser parser = new JsonParser();
                JsonReader reader = new JsonReader(new FileReader(reference));
                reader.setLenient(true);

                while (reader.peek() != JsonToken.END_DOCUMENT) {
                    JsonObject referenceEntryObject = parser.parse(reader).getAsJsonObject();
                    JsonDatamodelChecker referenceChecker = ReferenceDataModelChecker.referenceObjectChecker();
                    referenceChecker.check(referenceEntryObject);

                    references.add(ImmutableReference.builder()
                            .id(JsonFunctions.string(referenceEntryObject, "id"))
                            .pubmedId(JsonFunctions.nullableString(referenceEntryObject, "pubMedId"))
                            .title(JsonFunctions.nullableString(referenceEntryObject, "title"))
                            .url(JsonFunctions.nullableString(referenceEntryObject, "url"))
                            .authors(JsonFunctions.nullableString(referenceEntryObject, "authors"))
                            .journal(JsonFunctions.nullableString(referenceEntryObject, "journal"))
                            .volume(JsonFunctions.nullableString(referenceEntryObject, "volume"))
                            .issue(JsonFunctions.nullableString(referenceEntryObject, "issue"))
                            .date(JsonFunctions.nullableString(referenceEntryObject, "date"))
                            .abstractText(JsonFunctions.nullableString(referenceEntryObject, "abstractText"))
                            .year(JsonFunctions.nullableString(referenceEntryObject, "year"))
                            .drug(extractDrug(referenceEntryObject.getAsJsonArray("drugs")))
                            .gene(extractGene(referenceEntryObject.getAsJsonArray("genes")))
                            .evidence(extractEvidence(referenceEntryObject.getAsJsonArray("evidence")))
                            .therapy(extractTherapy(referenceEntryObject.getAsJsonArray("therapies")))
                            .treatmentApproach(extractTreatmentApproach(referenceEntryObject.getAsJsonArray("treatmentApproaches")))
                            .variant(extarctVariant(referenceEntryObject.getAsJsonArray("variants")))
                            .build());
                }
                reader.close();
            }
        }
        LOGGER.info("Finished reading reference");

        return references;
    }

    @NotNull
    public static List<ReferenceDrug> extractDrug(@NotNull JsonArray jsonArray) {
        List<ReferenceDrug> referenceDrugs = Lists.newArrayList();
        JsonDatamodelChecker drugChecker = ReferenceDataModelChecker.referenceDrugObjectChecker();

        for (JsonElement drug : jsonArray) {
            JsonObject drugJsonObject = drug.getAsJsonObject();
            drugChecker.check(drugJsonObject);

            referenceDrugs.add(ImmutableReferenceDrug.builder()
                    .id(JsonFunctions.string(drugJsonObject, "id"))
                    .drugName(JsonFunctions.string(drugJsonObject, "drugName"))
                    .terms(JsonFunctions.stringList(drugJsonObject, "terms"))
                    .build());

        }
        return referenceDrugs;
    }

    @NotNull
    public static List<GeneInfo> extractGene(@NotNull JsonArray jsonArray) {
        List<GeneInfo> referenceGenes = Lists.newArrayList();
        JsonDatamodelChecker geneChecker = ReferenceDataModelChecker.referenceGeneObjectChecker();

        for (JsonElement gene : jsonArray) {
            JsonObject geneJsonObject = gene.getAsJsonObject();
            geneChecker.check(geneJsonObject);

            referenceGenes.add(ImmutableGeneInfo.builder()
                    .id(JsonFunctions.string(geneJsonObject, "id"))
                    .geneSymbol(JsonFunctions.string(geneJsonObject, "geneSymbol"))
                    .terms(JsonFunctions.stringList(geneJsonObject, "terms"))
                    .build());

        }
        return referenceGenes;
    }

    @NotNull
    public static List<EvidenceInfo> extractEvidence(@NotNull JsonArray jsonArray) {
        List<EvidenceInfo> referenceEvidences = Lists.newArrayList();
        JsonDatamodelChecker evidenceChecker = ReferenceDataModelChecker.referenceEvidenceObjectChecker();

        for (JsonElement evidence : jsonArray) {
            JsonObject evidenceJsonObject = evidence.getAsJsonObject();
            evidenceChecker.check(evidenceJsonObject);

            referenceEvidences.add(ImmutableEvidenceInfo.builder()
                    .id(JsonFunctions.string(evidenceJsonObject, "id"))
                    .approvalStatus(JsonFunctions.string(evidenceJsonObject, "approvalStatus"))
                    .evidenceType(JsonFunctions.string(evidenceJsonObject, "evidenceType"))
                    .efficacyEvidence(JsonFunctions.string(evidenceJsonObject, "efficacyEvidence"))
                    .molecularProfile(extractMolecularProfile(evidenceJsonObject.getAsJsonObject("molecularProfile")))
                    .therapy(extractTherapy(evidenceJsonObject.getAsJsonObject("therapy")))
                    .indication(extractIndication(evidenceJsonObject.getAsJsonObject("indication")))
                    .responseType(JsonFunctions.string(evidenceJsonObject, "responseType"))
                    .reference(extractReference(evidenceJsonObject.getAsJsonArray("references")))
                    .ampCapAscoEvidenceLevel(JsonFunctions.string(evidenceJsonObject, "ampCapAscoEvidenceLevel"))
                    .ampCapAscoInferredTier(JsonFunctions.string(evidenceJsonObject, "ampCapAscoInferredTier"))
                    .build());

        }

        return referenceEvidences;
    }

    @NotNull
    public static MolecularProfileInfo extractMolecularProfile(@NotNull JsonObject jsonObject) {
        JsonDatamodelChecker molecularProfileChecker = ReferenceDataModelChecker.referenceMolecularProfileObjectChecker();
        molecularProfileChecker.check(jsonObject);

        return ImmutableMolecularProfileInfo.builder()
                .id(JsonFunctions.string(jsonObject, "id"))
                .profileName(JsonFunctions.string(jsonObject, "profileName"))
                .build();
    }

    @NotNull
    public static TherapyInfo extractTherapy(@NotNull JsonObject jsonObject) {
        JsonDatamodelChecker therapyChecker = ReferenceDataModelChecker.referenceTherapyChecker();
        therapyChecker.check(jsonObject);

        return ImmutableTherapyInfo.builder()
                .id(JsonFunctions.string(jsonObject, "id"))
                .therapyName(JsonFunctions.string(jsonObject, "therapyName"))
                .synonyms(JsonFunctions.nullableString(jsonObject, "synonyms"))
                .build();
    }

    @NotNull
    public static IndicationInfo extractIndication(@NotNull JsonObject jsonObject) {
        JsonDatamodelChecker indicationChecker = ReferenceDataModelChecker.referenceIndicationChecker();
        indicationChecker.check(jsonObject);

        return ImmutableIndicationInfo.builder()
                .id(JsonFunctions.string(jsonObject, "id"))
                .name(JsonFunctions.string(jsonObject, "name"))
                .source(JsonFunctions.string(jsonObject, "source"))
                .build();
    }

    @NotNull
    public static List<ReferenceInfo> extractReference(@NotNull JsonArray jsonArray) {
        List<ReferenceInfo> references = Lists.newArrayList();
        JsonDatamodelChecker referenceChecker = ReferenceDataModelChecker.referenceReferenceChecker();

        for (JsonElement reference : jsonArray) {
            JsonObject referenceJsonObject = reference.getAsJsonObject();
            referenceChecker.check(referenceJsonObject);

            references.add(ImmutableReferenceInfo.builder()
                    .id(JsonFunctions.string(referenceJsonObject, "id"))
                    .pubMedId(JsonFunctions.nullableString(referenceJsonObject, "pubMedId"))
                    .title(JsonFunctions.nullableString(referenceJsonObject, "title"))
                    .url(JsonFunctions.nullableString(referenceJsonObject, "url"))
                    .build());
        }
        return references;
    }

    @NotNull
    public static List<TherapyInfo> extractTherapy(@NotNull JsonArray jsonArray) {
        List<TherapyInfo> referenceTherapies = Lists.newArrayList();
        JsonDatamodelChecker therapyChecker = ReferenceDataModelChecker.referenceTherapyObjectChecker();

        for (JsonElement therapy : jsonArray) {
            JsonObject therapyJsonObject = therapy.getAsJsonObject();
            therapyChecker.check(therapyJsonObject);

            referenceTherapies.add(ImmutableTherapyInfo.builder()
                    .id(JsonFunctions.string(therapyJsonObject, "id"))
                    .therapyName(JsonFunctions.string(therapyJsonObject, "therapyName"))
                    .synonyms(JsonFunctions.nullableString(therapyJsonObject, "synonyms"))
                    .build());

        }

        return referenceTherapies;
    }

    @NotNull
    public static List<TreatmentApproach> extractTreatmentApproach(@NotNull JsonArray jsonArray) {
        List<TreatmentApproach> referenceTreatmentApproaches = Lists.newArrayList();
        JsonDatamodelChecker treatmentApproachChecker = ReferenceDataModelChecker.referenceTreatmentApprochObjectChecker();

        for (JsonElement treatmentApproach : jsonArray) {
            JsonObject treatmentApproachJsonObject = treatmentApproach.getAsJsonObject();
            treatmentApproachChecker.check(treatmentApproachJsonObject);

            referenceTreatmentApproaches.add(ImmutableTreatmentApproach.builder()
                    .id(JsonFunctions.string(treatmentApproachJsonObject, "id"))
                    .name(JsonFunctions.string(treatmentApproachJsonObject, "name"))
                    .profileName(JsonFunctions.string(treatmentApproachJsonObject, "profileName"))
                    .build());

        }

        return referenceTreatmentApproaches;
    }

    @NotNull
    public static List<VariantInfo> extarctVariant(@NotNull JsonArray jsonArray) {
        List<VariantInfo> referenceVariants = Lists.newArrayList();
        JsonDatamodelChecker variantChecker = ReferenceDataModelChecker.referenceVariantObjectChecker();

        for (JsonElement variant : jsonArray) {
            JsonObject variantJsonObject = variant.getAsJsonObject();
            variantChecker.check(variantJsonObject);

            referenceVariants.add(ImmutableVariantInfo.builder()
                    .id(JsonFunctions.string(variantJsonObject, "id"))
                    .fullName(JsonFunctions.string(variantJsonObject, "fullName"))
                    .impact(JsonFunctions.nullableString(variantJsonObject, "impact"))
                    .proteinEffect(JsonFunctions.nullableString(variantJsonObject, "proteinEffect"))
                    .build());

        }
        return referenceVariants;
    }

}
