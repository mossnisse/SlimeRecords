package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RecordInputValidatorTest {
    @Test
    public void parsesOptionalWholeNumber() {
        assertNull(RecordInputValidator.parseOptionalQuantity("  "));
        assertEquals(Integer.valueOf(0), RecordInputValidator.parseOptionalQuantity("0"));
        assertEquals(Integer.valueOf(42), RecordInputValidator.parseOptionalQuantity(" 42 "));
    }

    @Test
    public void rejectsMalformedNegativeAndOverflowingValues() {
        assertThrows(IllegalArgumentException.class,
                () -> RecordInputValidator.parseOptionalQuantity("4.2"));
        assertThrows(IllegalArgumentException.class,
                () -> RecordInputValidator.parseOptionalQuantity("-1"));
        assertThrows(IllegalArgumentException.class,
                () -> RecordInputValidator.parseOptionalQuantity("999999999999999"));
    }
}
