package de.m3y3r.nnbd.ep;

import java.io.File;

import de.m3y3r.nnbd.ep.file.FileExportProvider;
import de.m3y3r.nnbd.ep.gd.GdExportProvider;

public class ExportProviders {
	public static ExportProvider getNewDefault(int clientFlags) {
		return new FileExportProvider(new File("nbd-server"));
//		return new GdExportProvider();
	}
}
