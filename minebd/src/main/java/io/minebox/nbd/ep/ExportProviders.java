package io.minebox.nbd.ep;

import io.minebox.nbd.ep.chunked.MineboxExport;

public class ExportProviders {
    public static ExportProvider getNewDefault(int clientFlags) {
        return new MineboxExport(new MineboxExport.Config());
//        return new FileExportProvider(new File("nbd-server"));
    }
}
