package xdu.undarubra.esmp.civilization.settlement;

public enum Role {
    OWNER, MANAGER, MEMBER, VISITOR;

    public boolean canBuild() { return this != VISITOR; }
    public boolean canManage() { return this == OWNER || this == MANAGER; }
}
