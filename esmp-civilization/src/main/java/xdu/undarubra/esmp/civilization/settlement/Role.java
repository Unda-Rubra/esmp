package xdu.undarubra.esmp.civilization.settlement;

public enum Role {
    OWNER,
    GOVERNOR,
    CITIZEN,
    PUBLIC;

    public boolean canBuild() {
        return this != PUBLIC;
    }

    public boolean canManage() {
        return this == OWNER || this == GOVERNOR;
    }
}
