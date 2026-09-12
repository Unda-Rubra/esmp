package xdu.undarubra.esmp.plugin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommandArgumentsTest {
    @Test
    void quotedNamesPreserveSpacesAndArgumentBoundaries() {
        assertArrayEquals(
                new String[] {"rename", "1", "  New  Haven  "},
                CommandArguments.parse("rename 1 \"  New  Haven  \"".split(" ")));
        assertArrayEquals(
                new String[] {"parcel", "info", "New Haven", "Tool Store"},
                CommandArguments.parse("parcel info \"New Haven\" \"Tool Store\"".split(" ")));
        assertArrayEquals(
                new String[] {"rename", "1", ""},
                CommandArguments.parse(new String[] {"rename", "1", "\"\""}));
    }

    @Test
    void malformedQuotesCannotSilentlyRetargetArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandArguments.parse(new String[] {"info", "\"New", "Haven"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandArguments.parse(new String[] {"info", "\"New\"Haven"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandArguments.parse(new String[] {"info", "New\"Haven\""}));
    }
}
