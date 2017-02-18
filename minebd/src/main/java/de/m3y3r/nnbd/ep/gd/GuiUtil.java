package de.m3y3r.nnbd.ep.gd;

import java.io.IOException;
import java.net.URI;

public class GuiUtil {
	public static void openUrl(URI uri) throws IOException {
		ProcessBuilder pb;

		String os = System.getProperty("os.name").toLowerCase();
		if(os.indexOf("nux") >= 0) {
			pb = new ProcessBuilder("xdg-open", uri.toURL().toExternalForm());
		} else if(os.indexOf("win") >= 0) {
																							// WTF!!?
			pb = new ProcessBuilder("cmd", "/c", "start", uri.toURL().toExternalForm().replaceAll("&", "^&"));
		} else {
			throw new UnsupportedOperationException("Unsupported os: " + os);
		}
		pb.start();
	}
}
