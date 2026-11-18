package ru.itmo.students.entity;

import org.eclipse.persistence.mappings.DatabaseMapping;
import org.eclipse.persistence.mappings.converters.Converter;
import org.eclipse.persistence.sessions.Session;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.sql.Types;

/** Binds the PostgreSQL enum with its actual type, without global string casts. */
public class RoleConverter implements Converter {
    @Override
    public Object convertObjectValueToDataValue(Object value, Session session) {
        if (value == null) return null;
        var result = new PGobject();
        result.setType("user_role");
        try { result.setValue(((Role) value).name()); }
        catch (SQLException exception) { throw new IllegalArgumentException("Cannot encode role", exception); }
        return result;
    }

    @Override
    public Object convertDataValueToObjectValue(Object value, Session session) {
        return value == null ? null : Role.valueOf(value.toString());
    }

    @Override public boolean isMutable() { return false; }

    @Override
    public void initialize(DatabaseMapping mapping, Session session) {
        mapping.getField().setSqlType(Types.OTHER);
        mapping.getField().setType(PGobject.class);
    }
}
