package allmart.payservice.application.required.outbox;

import allmart.payservice.domain.outbox.OutboxEvent;
import org.springframework.data.repository.Repository;

public interface OutboxEventRepository extends Repository<OutboxEvent, String> {

    OutboxEvent save(OutboxEvent event);
}
