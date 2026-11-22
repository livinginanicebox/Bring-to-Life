package com.bringtolife.game;

public interface EnclosureView {

    boolean isPassable(int x, int y, int z);

    boolean isLeaky(int x, int y, int z);

    boolean isLoaded(int x, int y, int z);
}
