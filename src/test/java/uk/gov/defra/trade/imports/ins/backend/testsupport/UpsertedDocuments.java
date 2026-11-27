package uk.gov.defra.trade.imports.ins.backend.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.bson.Document;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.gov.defra.trade.imports.ins.backend.notification.AggregatedNotification;

public final class UpsertedDocuments {

  private UpsertedDocuments() {}

  public static Document lastUpsertUpdateDocument(MongoTemplate mongoTemplate) {
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .upsert(any(Query.class), updateCaptor.capture(), eq(AggregatedNotification.class));
    return updateCaptor.getValue().getUpdateObject();
  }
}
