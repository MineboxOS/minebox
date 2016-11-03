package com.io.minebox;

public class MineboxBackend {

    public static final int SIZE = 4096;

    public void unmapSector(long blockId) {
        System.out.println("unmap " + blockId);
    }

    public void putBlock(long blockId, byte[] bytes, int blockOffset, boolean forceToDisk) {

    }

    public void putBlock(long blockId, byte[] buffer, boolean forceToDisk) {

    }

    public int getBlockSize() {
        return SIZE;

    }

    public void startTransaction() {
        //do nothing now
    }

    public void commitTransaction() {
        //do nothing now
    }

    public byte[] readBlock(long blockNumber) {
        return new byte[SIZE];
    }
}
