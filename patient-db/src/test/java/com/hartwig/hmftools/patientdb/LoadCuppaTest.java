package com.hartwig.hmftools.patientdb;
import com.hartwig.hmftools.common.cuppa2.CuppaPredictions;
import java.io.IOException;

public class LoadCuppaTest {
    /*
    // For CUPPA v2

    // Run this in bash
    mysql --user="writer" --password

    // Run this while in the mysql process
    USE patientdb;
    source /Users/lnguyen/Hartwig/hartwigmedical/hmftools/patient-db/src/main/resources/generate_database.sql; // This initializes/resets the database

    // For CUPPA v2, run the LoadCuppa2 class in IntelliJ's run config with the below params:
    -cuppa_vis_data_tsv /Users/lnguyen/Hartwig/hartwigmedical/hmftools/hmf-common/src/test/resources/cuppa/cuppa_vis_data.tsv
    -db_user writer
    -db_pass writer_password
    -db_url "mysql://localhost:3306/patientdb?serverTimezone=UTC"

    // For CUPPAv2
    -sample TEST_SAMPLE_CUPPA_V1
    -cuppa_results_csv /Users/lnguyen/Hartwig/hartwigmedical/hmftools/hmf-common/src/test/resources/cuppa/sample.cup.data.csv
    -db_user writer
    -db_pass writer_password
    -db_url "mysql://localhost:3306/patientdb?serverTimezone=UTC"
    */

    private static final String CUPPA_VIS_DATA_TSV = "/Users/lnguyen/Hartwig/hartwigmedical/hmftools/hmf-common/src/test/resources/cuppa/cuppa_vis_data.tsv";

    public static void main(String[] args) throws IOException
    {
        CuppaPredictions cuppaPredictions = CuppaPredictions.fromTsv(CUPPA_VIS_DATA_TSV);
        cuppaPredictions.getTopPredictions(3).sortByRank().printPredictions();
    }
}
