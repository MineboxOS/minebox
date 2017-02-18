package de.m3y3r.nnbd.ep.gd;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import de.m3y3r.nnbd.ep.ExportProvider;

public class GdExportProvider implements ExportProvider {

	private static final String EXPORT_CONFIG_FILE = "export-config.json";

	private static final String NNBD_FOLDER_NAME = "nnbd";

	private static final CharSequence APP_DATA_FOLDER = "appDataFolder";

	private static final String apiUrl = "https://www.googleapis.com/";
	private static final int BLOCK_SIZE = 4096;

	private CharSequence accessToken;
	private CharSequence state;
	private WebTarget target;

	private CharSequence exportName;
	private long exportSize;
	int exportBlockSize;
	private String exportFolderId;

	private ClientConfig clientConfig;

	public GdExportProvider() {
		clientConfig = new ClientConfig();
//		clientConfig.connectorProvider(new NettyConnectorProvider());
		target = ClientBuilder.newClient(clientConfig)
				.target(apiUrl)
				.register(ByteBufferWriter.class);
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public String create(CharSequence exportName, long exportSize) throws IOException {

		CharSequence parentFolder = null;
		CharSequence folderName = NNBD_FOLDER_NAME;

		JsonObject nnbdFolder;
		JsonObject nnbdFolders = listFolders(folderName, parentFolder);
		JsonArray nnbdFolderJa = nnbdFolders.getJsonArray("files");
		if(nnbdFolderJa.size() == 0) {
			// create folder
			nnbdFolder = createFile("application/vnd.google-apps.folder", folderName, parentFolder, null, null);
		} else {
			nnbdFolder = nnbdFolderJa.getJsonObject(0);
		}

		folderName = exportName;
		parentFolder = nnbdFolder.getString("id");
		JsonObject exportFolder;
		JsonObject exportFolders = listFolders(exportName, parentFolder);
		JsonArray ja = exportFolders.getJsonArray("files");
		if(ja.size() == 0) {
			// create folder
			exportFolder = createFile("application/vnd.google-apps.folder", exportName, parentFolder, null, null);
		} else {
			// export name already exists!
			return null;
		}

		// 3. create file "config"
		{
			JsonObject cfg = Json.createObjectBuilder()
					.add("size", exportSize)
					.add("blockSize", BLOCK_SIZE)
					.build();
			JsonObject configFile = createFile(MediaType.APPLICATION_JSON, EXPORT_CONFIG_FILE, exportFolder.getString("id"), cfg, MediaType.APPLICATION_JSON_TYPE);
		}
		return exportFolder.getString("id");
	}

	private JsonObject createFile(CharSequence mimeType, CharSequence fileName, CharSequence parent, Object data, MediaType dataMimeType) throws IOException {

		JsonObjectBuilder job = Json.createObjectBuilder()
				.add("name", fileName.toString())
				.add("mimeType", mimeType.toString());
		if(parent != null)
			job.add("parents", Json.createArrayBuilder().add(parent.toString()));
		JsonObject meta = job.build();

		if(data == null) {
			return createFileMetadataOnly(meta);
		}

		MultiPart multiPartEntity = new MultiPart(new MediaType("multipart", "related"))
				.bodyPart(new BodyPart(meta, MediaType.APPLICATION_JSON_TYPE))
				.bodyPart(new BodyPart(data, dataMimeType));

		ensureToken();
		Response r = target.path("upload/drive/v3/files")
				.register(MultiPartFeature.class)
				.queryParam("uploadType", "multipart")
				.request()
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.post(Entity.entity(multiPartEntity, multiPartEntity.getMediaType()));
		checkResponse(r, Response.Status.OK.getStatusCode());
		JsonObject jo = r.readEntity(JsonObject.class);
		return jo;
	}

	private JsonObject createFileMetadataOnly(JsonObject meta) throws IOException {
		ensureToken();
		Response r = target.path("/drive/v3/files")
				.request()
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.post(Entity.json(meta));
		checkResponse(r, Response.Status.OK.getStatusCode());
		JsonObject jo = r.readEntity(JsonObject.class);
		return jo;
	}

	@Override
	public long open(CharSequence exportName) throws IOException {
		CharSequence parentFolder = null;
		CharSequence folderName = NNBD_FOLDER_NAME;

		JsonObject nnbdFolder;
		JsonObject nnbdFolders = listFolders(folderName, parentFolder);
		JsonArray nnbdFolderJa = nnbdFolders.getJsonArray("files");
		if(nnbdFolderJa.size() == 0) {
			throw new IOException("folder not found! " + folderName);
		}
		nnbdFolder = nnbdFolderJa.getJsonObject(0);

		parentFolder = nnbdFolder.getString("id");
		folderName = exportName;
		JsonObject exportFolder;
		JsonObject exportFolders = listFolders(folderName, parentFolder);
		JsonArray ja = exportFolders.getJsonArray("files");
		if(ja.size() == 0) {
			throw new IOException("export name not found!");
		}

		exportFolder = ja.getJsonObject(0);
		JsonObject configFiles = listFiles(MediaType.APPLICATION_JSON, EXPORT_CONFIG_FILE, exportFolder.getString("id"));
		if(configFiles.getJsonArray("files").size() == 0) {
			throw new IOException("config file for export name not found!");
		}
		Response r = readFileUnconsumed(configFiles.getJsonArray("files").getJsonObject(0).getString("id"), null, null);
		JsonObject cfg = r.readEntity(JsonObject.class);
		exportSize = cfg.getJsonNumber("size").longValue();
		exportBlockSize = cfg.getJsonNumber("blockSize").intValue();
		exportFolderId = exportFolder.getString("id");

		this.exportName = exportName;
		return exportSize;
	}

	private JsonObject listFolders(CharSequence fileName, CharSequence parent) throws IOException {
		return listFiles("application/vnd.google-apps.folder", fileName, parent);
	}

	private JsonObject listFiles(CharSequence mimeType, CharSequence fileName, CharSequence parent) throws IOException {
		ensureToken();

		WebTarget wt = target.path("drive/v3/files");
		String query = "name = '" + fileName + "'";
		if(mimeType != null)
			query += " and " + "mimeType = '" + mimeType + "'";
		if(parent != null)
			query += " and " + "'" + parent + "'" + " in parents";

		Response r = wt
				.queryParam("q", query)
				.request(MediaType.APPLICATION_JSON_TYPE)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.get();
		checkResponse(r, Response.Status.OK.getStatusCode());
		JsonObject jo = r.readEntity(JsonObject.class);
		return jo;
	}

	private static void checkResponse(Response r, int statusCode) throws IOException {
		if(r.getStatus() != statusCode) throw new IOException("statusCode=" + statusCode + " rStatusCode=" + r.getStatus());
	}

	private void ensureToken() throws IOException {
		if(accessToken == null) {
			accessToken = OAuth.getInstance().getToken();
		}
		//TODO:
//		if(!token.isValid()) {
//			getToken();
//		}
	}

	public Response readFileUnconsumed(CharSequence fileId, Long offset, Long length) throws IOException {
		ensureToken();
		WebTarget wt = target.path("drive/v3/files/{fileId}");
		Invocation.Builder b = wt.resolveTemplate("fileId", fileId)
				.queryParam("alt", "media")
				.request(MediaType.APPLICATION_JSON_TYPE)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
//				.get();
		if(offset != null && length != null) {
			b.header("Range", "bytes=" + offset + '-' + offset + length);
		}
		Response r = b.get();
		checkResponse(r, Response.Status.OK.getStatusCode());
		return r;
	}

	@Override
	public ByteBuffer read(long offset, long length, boolean sync) throws IOException {

		Object[][] fileIdsFromOffset = getFileIdsForOffsetLength(offset,length);

		ByteBuffer bb = ByteBuffer.allocate((int) length);

		for(int i = 0, n = fileIdsFromOffset.length; i < n; i++) {
			Object[] fileIdOffsetLength = fileIdsFromOffset[i];

			String fileId = (String) fileIdOffsetLength[0];
			Long off = (Long) fileIdOffsetLength[1];
			Long len = (Long) fileIdOffsetLength[2];

			if(fileId == null) {
				bb.position(bb.position() + len.intValue());
			} else {
				Response r = readFileUnconsumed(fileId, off, len);

				InputStream is = (InputStream) r.getEntity();
				is.read(bb.array(), 0, len.intValue());
			}
		}
		bb.flip();
		return bb;
	}

	Object[][] getFileIdsForOffsetLength(final long offset, final long length) throws IOException {
		long off = offset;
		long len = length;

		int n = (int) (length / exportBlockSize + 1);
		Object[][] res = new Object[n][];
		int i = 0;
		while(len > 0) {
			int blockNo = (int) (off / exportBlockSize);
			int blockOffset = (int) (off % exportBlockSize);
			int blockLen;

			if(len > exportBlockSize) {
				blockLen = exportBlockSize - blockOffset;
			} else {
				blockLen = (int) len;
			}
			System.out.println("block no=" + blockNo + " block offset = " + blockOffset + " blockLen=" + blockLen);

			String fileId = getFileIdForBlock(blockNo);
			assert i < n;
			res[i++] = new Object[] { fileId, Long.valueOf(blockOffset), Long.valueOf(blockLen), Long.valueOf(blockNo)};

			off += blockLen;
			len -= blockLen;
		}

		if(i < n) {
			res = Arrays.copyOf(res, i);
		}
		return res;
	}

	private String getFileIdForBlock(int blockNo) throws IOException {
		String fileId = getFileIdForBlockCached(blockNo);
		if(fileId == null) {
			JsonObject configFiles = listFiles(MediaType.APPLICATION_OCTET_STREAM, "block-" + blockNo, exportFolderId);
			if(configFiles.getJsonArray("files").size() == 0) {
				return null;
			} else {
				return configFiles.getJsonArray("files").getJsonObject(0).getString("id");
			}
		}
		return null;
	}

	private String getFileIdForBlockCached(int blockNo) {
		return null;
	}

	@Override
	public void write(long offset, ByteBuffer message, boolean sync) throws IOException {
		Object[][] fileIdsFromOffset = getFileIdsForOffsetLength(offset, message.remaining());

		for(int i = 0, n = fileIdsFromOffset.length; i < n; i++) {
			Object[] fileIdOffsetLength = fileIdsFromOffset[i];

			String fileId = (String) fileIdOffsetLength[0];
			Long off = (Long) fileIdOffsetLength[1];
			Long len = (Long) fileIdOffsetLength[2];
			Long blockNo = (Long) fileIdOffsetLength[3];
			assert offset == 0; // FIXME: what do to when offset is not 0!?
			// corresponding block file does not exists yet!
			if(fileId == null) {
				// create file and write data
				ByteBuffer bb = message.slice();
				int len2 = message.remaining();
				if(len2 >= exportBlockSize) {
					len2 = exportBlockSize;
					bb.limit(len.intValue()); //TODO: correct?
				} else {
					ByteBuffer bb2 = ByteBuffer.allocate(exportBlockSize);
					bb2.put(bb);
					bb2.position(exportBlockSize);
					bb2.flip();
					bb = bb2;
				}
				JsonObject file = createFile(MediaType.APPLICATION_JSON, "block-" + blockNo, exportFolderId, bb, MediaType.APPLICATION_OCTET_STREAM_TYPE);
				message.position(message.position() + len2);
			} else {
				ByteBuffer bb = ByteBuffer.allocate(exportBlockSize);
				if(off > 0) {
					Long lm = off + 1;
					// read missing data for current block
					Response r = readFileUnconsumed(fileId, 0l, lm);

					InputStream is = (InputStream) r.getEntity();
					is.read(bb.array(), 0, lm.intValue());
				}
				int ol = message.limit();
				int lbs = (int) (exportBlockSize - off);
				int nl = message.position() + lbs;
				message.limit(nl);
				bb.put(message);
				message.limit(ol);
			}
		}
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void trim() throws IOException {
	}

	void setToken(String accessToken) {
		this.accessToken = accessToken;
	}
}
