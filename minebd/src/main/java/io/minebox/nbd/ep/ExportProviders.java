package io.minebox.nbd.ep;

import java.io.File;

import io.minebox.nbd.ep.file.FileExportProvider;

public class ExportProviders {
	public static ExportProvider getNewDefault(int clientFlags) {
		return new FileExportProvider(new File("nbd-server"));
//		return new GdExportProvider();
	}
}
