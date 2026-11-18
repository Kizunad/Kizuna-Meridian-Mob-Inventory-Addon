package dev.kizuna.meridian.inventory;

public interface GroundItemAccess {
    boolean meridian$reserved();

    boolean meridian$isPlayerDrop();

    void meridian$markPlayerDrop();
}
