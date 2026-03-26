package allmart.payservice.adapter.toss;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!local")
@Getter
@Component
public class TossProperties {

    @Value("${toss.secret-key}")
    private String secretKey;

    @Value("${toss.base-url:https://api.tosspayments.com}")
    private String baseUrl;
}
