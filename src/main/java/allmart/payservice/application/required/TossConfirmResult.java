package allmart.payservice.application.required;

public record TossConfirmResult(
        boolean approved,
        String tossStatus,
        String rawJson
) {
    public static TossConfirmResult approved(String tossStatus, String rawJson) {
        return new TossConfirmResult(true, tossStatus, rawJson);
    }

    public static TossConfirmResult failed(String tossStatus, String rawJson) {
        return new TossConfirmResult(false, tossStatus, rawJson);
    }
}
