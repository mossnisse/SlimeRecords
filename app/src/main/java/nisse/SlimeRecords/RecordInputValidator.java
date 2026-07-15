package nisse.SlimeRecords;

/** Platform-neutral validation for values entered on the record screen. */
final class RecordInputValidator {
    private RecordInputValidator() {}

    static Integer parseOptionalQuantity(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return null;

        try {
            int quantity = Integer.parseInt(value);
            if (quantity < 0) throw new IllegalArgumentException("Quantity must not be negative");
            return quantity;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantity must be a whole number", e);
        }
    }
}
