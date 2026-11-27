package uk.gov.defra.trade.imports.ins.backend.integration;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.connection.ClusterDescription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigIT extends IntegrationBase {

    private static final String SCRATCH_COLLECTION = "test-collection";

    @Autowired
    private MongoClient mongoClient;

    @AfterEach
    void dropScratchCollection() {
        mongoTemplate.dropCollection(SCRATCH_COLLECTION);
    }

    @Test
    void mongoTemplate_shouldBeConfigured() {
        assertThat(mongoTemplate).isNotNull();
        assertThat(mongoTemplate.getDb()).isNotNull();
        assertThat(mongoTemplate.getDb().getName()).isEqualTo("trade-imports-ins-backend");
    }

    @Test
    void mongoClient_shouldBeConnected() {
        assertThat(mongoClient).isNotNull();

        ClusterDescription clusterDescription = mongoClient.getClusterDescription();
        assertThat(clusterDescription).isNotNull();
        assertThat(clusterDescription.getServerDescriptions()).isNotEmpty();
    }

    @Test
    void mongoClient_shouldReadFromSecondary() {
        ReadPreference readPreference = mongoClient.getDatabase("test").getReadPreference();
        assertThat(readPreference).isEqualTo(ReadPreference.secondary());
    }

    @Test
    void mongoClient_shouldHaveCorrectWriteConcern() {
        WriteConcern writeConcern = mongoClient.getDatabase("test").getWriteConcern();
        assertThat(writeConcern).isNotNull();
        assertThat(writeConcern.getWObject()).isEqualTo("majority");
    }

    @Test
    void mongoTemplate_shouldPerformBasicOperations() {
        TestDocument testDoc = new TestDocument("test-id", "Test Name");

        TestDocument savedDoc = mongoTemplate.insert(testDoc, SCRATCH_COLLECTION);
        assertThat(savedDoc).isNotNull();
        assertThat(savedDoc.getId()).isEqualTo("test-id");

        TestDocument foundDoc = mongoTemplate.findById("test-id", TestDocument.class, SCRATCH_COLLECTION);
        assertThat(foundDoc).isNotNull();
        assertThat(foundDoc.getName()).isEqualTo("Test Name");
    }

    static class TestDocument {
        private String id;
        private String name;

        public TestDocument() {
        }

        public TestDocument(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
