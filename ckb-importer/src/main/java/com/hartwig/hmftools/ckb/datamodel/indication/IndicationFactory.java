package com.hartwig.hmftools.ckb.datamodel.indication;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.ckb.datamodel.therapy.Therapy;
import com.hartwig.hmftools.ckb.datamodel.therapy.TherapyFactory;
import com.hartwig.hmftools.ckb.json.CkbJsonDatabase;
import com.hartwig.hmftools.ckb.json.common.IndicationInfo;
import com.hartwig.hmftools.ckb.json.common.TherapyInfo;
import com.hartwig.hmftools.ckb.json.indication.JsonIndication;

import org.jetbrains.annotations.NotNull;

public final class IndicationFactory {

    private IndicationFactory() {
    }

    @NotNull
    public static List<Indication> extractIndications(@NotNull CkbJsonDatabase ckbJsonDatabase,
            @NotNull List<IndicationInfo> indicationInfos) {
        List<Indication> indications = Lists.newArrayList();
        for (IndicationInfo indicationInfo : indicationInfos) {
            indications.add(resolveIndication(ckbJsonDatabase, indicationInfo));
        }
        return indications;
    }

    @NotNull
    public static Indication resolveIndication(@NotNull CkbJsonDatabase ckbJsonDatabase, @NotNull IndicationInfo indicationInfo) {
        for (JsonIndication indication : ckbJsonDatabase.indications()) {
            if (indicationInfo.id().equals(indication.id())) {
                return ImmutableIndication.builder()
                        .id(indication.id())
                        .name(indication.name())
                        .source(indication.source())
                        .definition(indication.definition())
                        .currentPreferredTerm(indication.currentPreferredTerm())
                        .lastUpdateDateFromDO(indication.lastUpdateDateFromDO())
                        .altIds(indication.altIds())
                        .termId(indication.termId())
                        .build();
            }
        }

        throw new IllegalStateException("Could not resolve CKB indication with id '" + indicationInfo.id() + "'");
    }
}
