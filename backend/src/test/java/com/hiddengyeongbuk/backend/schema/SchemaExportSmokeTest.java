package com.hiddengyeongbuk.backend.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetail;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceImage;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummary;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import java.io.File;
import java.nio.file.Files;
import java.util.EnumSet;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SchemaExportSmokeTest {

    private File outputFile;

    @Test
    void generatesDdlForAllEntitiesWithoutLiveDatabase() throws Exception {
        outputFile = File.createTempFile("schema-export", ".sql");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .build();

        Metadata metadata = new MetadataSources(registry)
                .addAnnotatedClass(Hub.class)
                .addAnnotatedClass(Region.class)
                .addAnnotatedClass(Place.class)
                .addAnnotatedClass(PlaceTag.class)
                .addAnnotatedClass(PlaceImage.class)
                .addAnnotatedClass(FestivalDetail.class)
                .addAnnotatedClass(PlaceSummary.class)
                .addAnnotatedClass(DataSyncLog.class)
                .buildMetadata();

        SchemaExport schemaExport = new SchemaExport();
        schemaExport.setOutputFile(outputFile.getAbsolutePath());
        schemaExport.setDelimiter(";");
        schemaExport.setFormat(true);
        schemaExport.setHaltOnError(true);
        schemaExport.createOnly(EnumSet.of(TargetType.SCRIPT), metadata);

        assertFalse(
                schemaExport.getExceptions().stream().findAny().isPresent(),
                () -> "Schema export failed: " + schemaExport.getExceptions());

        System.out.println(Files.readString(outputFile.toPath()));
    }

    @AfterEach
    void cleanup() {
        if (outputFile != null) {
            outputFile.delete();
        }
    }
}
