package xdu.undarubra.esmp.core.storage;

import org.jooq.impl.AbstractConverter;

import java.util.UUID;

/**
 * SQLite TEXT storage remains unchanged; generated bindings expose UUID, including nullable IDs.
 */
public final class UuidConverter extends AbstractConverter<String, UUID> {
    private static final long serialVersionUID = 1L;

    public UuidConverter() {
        super(String.class, UUID.class);
    }

    @Override
    public UUID from(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    @Override
    public String to(UUID value) {
        return value == null ? null : value.toString();
    }
}
