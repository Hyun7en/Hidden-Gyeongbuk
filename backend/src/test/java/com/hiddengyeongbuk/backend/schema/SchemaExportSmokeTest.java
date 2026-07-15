package com.hiddengyeongbuk.backend.schema;

import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetail;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceImage;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummary;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class SchemaExportSmokeTest {

    @Test
    void createsSchemaForAllEntitiesOnInMemoryDatabase() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:schema_smoke_test;DB_CLOSE_DELAY=-1")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create")
                .build();

        try {
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

            try (SessionFactory sessionFactory = metadata.buildSessionFactory()) {
                sessionFactory.getSchemaManager().validateMappedObjects();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
