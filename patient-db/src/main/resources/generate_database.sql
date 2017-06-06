CREATE TABLE patient
(   id int NOT NULL AUTO_INCREMENT,
    cpctId varchar(255) DEFAULT NULL,
    registrationDate DATE,
    gender varchar(10),
    ethnicity varchar(255),
    hospital varchar(255),
    birthYear int,
    primaryTumorLocation varchar(255),
    deathDate DATE,
    PRIMARY KEY (id)
);

CREATE TABLE sample
(   sampleId varchar(20) NOT NULL,
    patientId int NOT NULL,
    arrivalDate DATE NOT NULL,
    PRIMARY KEY (sampleId),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

CREATE TABLE biopsy
(   id int NOT NULL,
    sampleId varchar(20),
    patientId int NOT NULL,
    biopsyLocation varchar(255),
    biopsyDate DATE,
    PRIMARY KEY (id),
    FOREIGN KEY (sampleId) REFERENCES sample(sampleId),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

CREATE TABLE treatment
 (  id int NOT NULL,
    biopsyId int,
    patientId int NOT NULL,
    treatmentGiven varchar(3),
    startDate DATE,
    endDate DATE,
    name varchar(255),
    type varchar(255),
    PRIMARY KEY (id),
    FOREIGN KEY (biopsyId) REFERENCES biopsy(id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
 );

CREATE TABLE drug
 (  id int NOT NULL AUTO_INCREMENT,
    treatmentId int,
    patientId int NOT NULL,
    startDate DATE,
    endDate DATE,
    name varchar(255),
    type varchar(255),
    PRIMARY KEY (id),
    FOREIGN KEY (treatmentId) REFERENCES treatment(id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
 );

 CREATE TABLE treatmentResponse
  (  id int NOT NULL AUTO_INCREMENT,
     treatmentId int,
     patientId int NOT NULL,
     measurementDone varchar(5),
     responseDate DATE,
     response varchar(25),
     PRIMARY KEY (id),
     FOREIGN KEY (treatmentId) REFERENCES treatment(id),
     FOREIGN KEY (patientId) REFERENCES patient(id)
  );

CREATE TABLE somaticVariant
(   id int NOT NULL AUTO_INCREMENT,
    sampleId varchar(20) NOT NULL,
    patientId int NOT NULL,
    gene varchar(255) NOT NULL,
    position varchar(255) NOT NULL,
    ref varchar(255) NOT NULL,
    alt varchar(255) NOT NULL,
    cosmicId varchar(255),
    alleleReadCount int NOT NULL,
    totalReadCount int NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (sampleId) REFERENCES sample(sampleId),
    FOREIGN KEY (patientId) references patient(id)
);

CREATE TABLE comprehensiveSomaticVariant
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(20) NOT NULL,
    chromosome varchar(255) NOT NULL,
    position int not null,
    filter varchar(255) NOT NULL,
    ref varchar(255) NOT NULL,
    alt varchar(255) NOT NULL,
    alleleReadCount int NOT NULL,
    totalReadCount int NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId),
    INDEX(filter)
);

CREATE TABLE copyNumber
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(20) NOT NULL,
    chromosome varchar(255) NOT NULL,
    start int not null,
    end int not null,
    bafCount int not null,
    observedBaf DOUBLE PRECISION not null,
    actualBaf DOUBLE PRECISION not null,
    copyNumber DOUBLE PRECISION not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

CREATE TABLE purityRange
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(20) NOT NULL,
    purity DOUBLE PRECISION not null,
    normFactor DOUBLE PRECISION not null,
    score DOUBLE PRECISION not null,
    ploidy DOUBLE PRECISION not null,
    diploidProportion DOUBLE PRECISION not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

CREATE TABLE purityScore
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(20) NOT NULL,
    polyclonalProportion DOUBLE PRECISION not null,
    minPurity DOUBLE PRECISION not null,
    maxPurity DOUBLE PRECISION not null,
    minPloidy DOUBLE PRECISION not null,
    maxPloidy DOUBLE PRECISION not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

CREATE VIEW purity AS
SELECT p.*, s.polyclonalProportion, s.minPurity, s.maxPurity, s.minPloidy, s.maxPloidy
FROM purityRange p, purityScore s
WHERE p.sampleId = s.sampleId
  AND (p.sampleId, p.score) IN (SELECT sampleId, MIN(score) FROM purityRange GROUP BY sampleId);

CREATE TABLE copyNumberRegion
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(20) NOT NULL,
    chromosome varchar(255) NOT NULL,
    start int not null,
    end int not null,
    bafCount int not null,
    observedBaf DOUBLE PRECISION not null,
    observedTumorRatio DOUBLE PRECISION not null,
    observedNormalRatio DOUBLE PRECISION not null,
    modelPloidy int not null,
    modelBaf DOUBLE PRECISION not null,
    modelTumorRatio DOUBLE PRECISION not null,
    actualTumorCopyNumber DOUBLE PRECISION not null,
    cnvDeviation DOUBLE PRECISION not null,
    bafDeviation DOUBLE PRECISION not null,
    highConfidenceBaf DOUBLE PRECISION not null,
    highConfidenceCopyNumber DOUBLE PRECISION not null,
    fittedBaf DOUBLE PRECISION not null,
    fittedCopyNumber DOUBLE PRECISION not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

CREATE TABLE structuralVariant
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(20) NOT NULL,
    startChromosome varchar(255) NOT NULL,
    endChromosome varchar(255) NOT NULL,
    startPosition int not null,
    endPosition int not null,
    type varchar(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId)
);