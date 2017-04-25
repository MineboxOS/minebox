package io.minebox.nbd;

/**
 * Created by andreas on 11.04.17.
 */
public class Constants {
    //base-2 style size
    public static final int KILO = 1024;
    public static final long MEGABYTE = KILO * KILO;
    public static final long GIGABYTE = MEGABYTE * KILO;
    public static final long TERABYTE = GIGABYTE * KILO;
    public static final long PETABYTE = TERABYTE * KILO;
    public static final long MAX_SUPPORTED_SIZE = PETABYTE;
}
